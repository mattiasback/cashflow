package app.cashflow.feature.banksync.domain

import app.cashflow.core.domain.EmptyResult
import app.cashflow.core.domain.Result
import app.cashflow.core.domain.datasource.AccountLocalDataSource
import app.cashflow.core.domain.model.Account
import app.cashflow.core.domain.model.AccountId
import app.cashflow.core.domain.model.AccountType
import java.net.URI
import java.net.URLDecoder
import java.security.SecureRandom
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * "Koppla bank": starts the bank's BankID page in the browser and completes the consent when
 * the bank sends you back. The random `state` is kept until then, so an answer that this app
 * did not ask for is refused.
 */
class ConnectBankUseCase(
    private val provider: BankSyncProvider,
    private val credentials: BankCredentialStore,
    private val clock: Clock = Clock.System,
    private val random: SecureRandom = SecureRandom(),
) {

    suspend fun institutions(country: String = "SE"): Result<List<BankInstitution>, BankError> {
        if (credentials.load() == null) return Result.Error(BankError.NO_CREDENTIALS)
        return when (val r = provider.institutions(country)) {
            is Result.Error -> r
            is Result.Success -> Result.Success(r.data.sortedBy { it.name.lowercase() })
        }
    }

    /** Returns the bank's page to open. Consent lasts as long as the bank allows, at most [MAX_CONSENT_DAYS]. */
    suspend fun start(institution: BankInstitution): Result<AuthorizationStart, BankError> {
        val saved = credentials.load() ?: return Result.Error(BankError.NO_CREDENTIALS)
        // Checked again each time: the application may have changed in the control panel since.
        when (val check = provider.checkCredentials(saved)) {
            is Result.Error -> return check
            is Result.Success -> if (check.data != saved) credentials.save(check.data)
        }
        val days = minOf(institution.maxConsentDays ?: MAX_CONSENT_DAYS, MAX_CONSENT_DAYS)
        val state = ByteArray(STATE_BYTES).also(random::nextBytes).joinToString("") { "%02x".format(it) }
        val now = clock.now()
        credentials.setPending(PendingAuthorization(state, institution.name, now))
        return provider.startAuthorization(institution, now + days.days, state)
    }

    /**
     * Completes from the address the bank sent you to: `…?code=…&state=…`, either caught by the
     * app or pasted by hand. An error from the bank (you stopped there) becomes [BankError.CANCELLED].
     */
    suspend fun complete(callbackUrl: String): Result<BankConsent, BankError> {
        val pending = credentials.pending() ?: return Result.Error(BankError.STATE_MISMATCH)
        val query = queryOf(callbackUrl) ?: return Result.Error(BankError.INVALID_RESPONSE)
        if (query["state"] != pending.state) return Result.Error(BankError.STATE_MISMATCH)
        if (query["error"] != null) {
            credentials.setPending(null)
            return Result.Error(BankError.CANCELLED)
        }
        val code = query["code"]?.takeIf { it.isNotBlank() } ?: return Result.Error(BankError.INVALID_RESPONSE)
        val consent = provider.completeAuthorization(code)
        // A code works once, so the pending state is spent either way.
        credentials.setPending(null)
        return consent
    }

    private fun queryOf(url: String): Map<String, String>? = runCatching {
        val raw = URI(url.trim()).rawQuery ?: return null
        raw.split('&').filter { it.isNotEmpty() }.associate { part ->
            val (k, v) = part.split('=', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            URLDecoder.decode(k, Charsets.UTF_8) to URLDecoder.decode(v, Charsets.UTF_8)
        }
    }.getOrNull()

    companion object {
        /** PSD2 consents in Sweden last up to 180 days. */
        const val MAX_CONSENT_DAYS = 180
        private const val STATE_BYTES = 24
    }
}

/** Where a bank account's rows go. */
sealed interface LinkTarget {
    data class Existing(val accountId: AccountId) : LinkTarget
    data class New(val name: String, val type: AccountType) : LinkTarget

    /** Not synced. */
    data object Skip : LinkTarget
}

/**
 * "Vilket konto?" once per bank account. Suggests the account you already linked it to, then
 * an account whose number ends the same, else a new account; never guesses silently.
 */
class LinkAccountsUseCase(
    private val provider: BankSyncProvider,
    private val accounts: AccountLocalDataSource,
    private val links: BankLinkLocalDataSource,
) {

    /** Only SEK accounts with an id for this consent can be synced. */
    fun syncable(consent: BankConsent): List<BankAccount> =
        consent.accounts.filter { it.currency == "SEK" && it.providerUid != null }

    suspend fun suggest(consent: BankConsent): Result<Map<String, LinkTarget>, BankError> {
        val known = (accounts.getAccounts() as? Result.Success)?.data ?: return Result.Error(BankError.STORAGE)
        val linked = (links.getLinks() as? Result.Success)?.data ?: return Result.Error(BankError.STORAGE)
        return Result.Success(
            syncable(consent).associate { bank ->
                val previous = linked.firstOrNull { it.stableKey == bank.stableKey }?.accountId?.takeIf { id -> known.any { it.id == id } }
                val byNumber = bank.number?.let { number ->
                    known.singleOrNull { a -> a.accountNumber?.takeIf { it.length >= MIN_NUMBER }?.let(number::endsWith) == true }
                }
                bank.stableKey to when {
                    previous != null -> LinkTarget.Existing(previous)
                    byNumber != null -> LinkTarget.Existing(byNumber.id)
                    else -> LinkTarget.New(bankAccountLabel(consent.institution, bank.name, bank.number), typeOf(bank.kind))
                }
            },
        )
    }

    @OptIn(ExperimentalUuidApi::class)
    suspend fun link(consent: BankConsent, choices: Map<String, LinkTarget>): EmptyResult<BankError> {
        val result = ArrayList<BankLink>()
        val previous = (links.getLinks() as? Result.Success)?.data ?: return Result.Error(BankError.STORAGE)
        for (bank in syncable(consent)) {
            val accountId = when (val target = choices[bank.stableKey] ?: LinkTarget.Skip) {
                LinkTarget.Skip -> continue
                is LinkTarget.Existing -> target.accountId
                is LinkTarget.New -> {
                    val account = Account(AccountId(Uuid.random().toString()), target.name, target.type, institution = consent.institution)
                    if (accounts.upsertAccount(account) is Result.Error) return Result.Error(BankError.STORAGE)
                    account.id
                }
            }
            result += BankLink(
                stableKey = bank.stableKey,
                provider = provider.id,
                accountId = accountId,
                institution = consent.institution,
                sessionId = consent.sessionId,
                providerAccountUid = bank.providerUid!!,
                validUntil = consent.validUntil,
                displayName = bankAccountLabel(consent.institution, bank.name, bank.number),
                maskedNumber = bank.number?.takeLast(4),
                // A renewed consent goes on from where the last one stopped.
                lastSynced = previous.firstOrNull { it.stableKey == bank.stableKey && it.accountId == accountId }?.lastSynced,
            )
        }
        return if (links.upsert(result) is Result.Error) Result.Error(BankError.STORAGE) else Result.Success(Unit)
    }

    private fun typeOf(kind: BankAccountKind) = when (kind) {
        BankAccountKind.CURRENT -> AccountType.TRANSACTION
        BankAccountKind.SAVINGS -> AccountType.SAVINGS
        BankAccountKind.OTHER -> AccountType.OTHER
    }

    private companion object {
        const val MIN_NUMBER = 7
    }
}

/** "Koppla från": ends the consent at the provider and forgets the link. Rows stay. */
class DisconnectBankUseCase(
    private val provider: BankSyncProvider,
    private val links: BankLinkLocalDataSource,
) {
    suspend operator fun invoke(sessionId: String): EmptyResult<BankError> {
        // Forget it here even when the provider cannot be reached; the consent then simply runs out.
        provider.revoke(sessionId)
        return if (links.deleteSession(sessionId) is Result.Error) Result.Error(BankError.STORAGE) else Result.Success(Unit)
    }
}

// Temporary (step 9): the key import is a stand-in until a backend holds the key (docs/bank-sync.md).
/** "Nyckel": checks your key and application id with the provider, then keeps them encrypted. */
class BankKeyUseCase(
    private val provider: BankSyncProvider,
    private val store: BankCredentialStore,
    private val reader: KeyFileReader,
) {
    /** The key file's text, or null when it is no key file at all. */
    suspend fun readKey(uri: String): String? = reader.read(uri)?.takeIf { "PRIVATE KEY" in it }

    suspend fun save(credentials: BankCredentials): EmptyResult<BankError> {
        val trimmed = credentials.copy(applicationId = credentials.applicationId.trim(), redirectUrl = credentials.redirectUrl.trim())
        if (trimmed.applicationId.isEmpty() || trimmed.redirectUrl.isEmpty()) return Result.Error(BankError.NO_CREDENTIALS)
        return when (val check = provider.checkCredentials(trimmed)) {
            is Result.Error -> check
            is Result.Success -> store.save(check.data)
        }
    }

    suspend fun remove(): EmptyResult<BankError> = store.clear()
}
