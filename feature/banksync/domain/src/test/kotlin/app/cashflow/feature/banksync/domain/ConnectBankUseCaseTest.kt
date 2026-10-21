package app.cashflow.feature.banksync.domain

import app.cashflow.core.domain.EmptyResult
import app.cashflow.core.domain.Result
import app.cashflow.core.domain.model.Account
import app.cashflow.core.domain.model.AccountId
import app.cashflow.core.domain.model.AccountType
import app.cashflow.core.domain.testing.InMemoryAccountDataSource
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class ConnectBankUseCaseTest {

    private val store = InMemoryCredentials()
    private val provider = RecordingProvider()
    private val now = Instant.parse("2026-10-21T10:00:00Z")
    private val connect = ConnectBankUseCase(provider, store, object : kotlin.time.Clock { override fun now() = this@ConnectBankUseCaseTest.now })

    @Test
    fun `nothing starts without your key`() = runTest {
        assertThat(connect.start(BankInstitution("Exempelbanken", "SE", 90))).isEqualTo(Result.Error(BankError.NO_CREDENTIALS))
    }

    @Test
    fun `the bank's answer must carry the state this app started with`() = runTest {
        store.save(BankCredentials("app-id", "pem", "https://example.org/cb"))
        connect.start(BankInstitution("Exempelbanken", "SE", 90))
        val state = store.pending()!!.state

        assertThat(connect.complete("https://example.org/cb?code=abc&state=other")).isEqualTo(Result.Error(BankError.STATE_MISMATCH))
        assertThat(connect.complete("cashflow://bank-callback?code=abc&state=$state")).isInstanceOf(Result.Success::class)
        assertThat(provider.code).isEqualTo("abc")
        // A code works once.
        assertThat(store.pending()).isNull()
    }

    @Test
    fun `stopping at the bank is a cancel, and consent never exceeds what the bank allows`() = runTest {
        store.save(BankCredentials("app-id", "pem", "https://example.org/cb"))
        connect.start(BankInstitution("Exempelbanken", "SE", 90))
        assertThat(provider.validUntil).isEqualTo(now + 90.days)
        val state = store.pending()!!.state
        assertThat(connect.complete("https://example.org/cb?error=access_denied&state=$state")).isEqualTo(Result.Error(BankError.CANCELLED))
    }

    @Test
    fun `an account you linked before, or one with the same number, is suggested`() = runTest {
        val accounts = InMemoryAccountDataSource()
        accounts.upsertAccount(Account(AccountId("lon"), "Lönekonto", AccountType.TRANSACTION, accountNumber = "90231234567"))
        val links = InMemoryBankLinks()
        val use = LinkAccountsUseCase(provider, accounts, links)
        val consent = BankConsent(
            "s1", "Exempelbanken", Instant.parse("2027-04-01T00:00:00Z"),
            listOf(
                BankAccount("h1", "u1", "Lönekonto", "SE0000090231234567", "SEK", BankAccountKind.CURRENT),
                BankAccount("h2", "u2", "Sparkonto", "SE0000090239999999", "SEK", BankAccountKind.SAVINGS),
                BankAccount("h3", "u3", "Valutakonto", null, "EUR", BankAccountKind.CURRENT),
            ),
        )
        val suggested = (use.suggest(consent) as Result.Success).data

        assertThat(suggested).isEqualTo(
            mapOf("h1" to LinkTarget.Existing(AccountId("lon")), "h2" to LinkTarget.New("Sparkonto", AccountType.SAVINGS)),
        )
        use.link(consent, suggested)
        assertThat(links.links.value.keys).isEqualTo(setOf("h1", "h2"))
        assertThat(links.links.value.getValue("h1").maskedNumber).isEqualTo("4567")
    }
}

internal class InMemoryCredentials : BankCredentialStore {
    private val credentials = MutableStateFlow<BankCredentials?>(null)
    private var pending: PendingAuthorization? = null
    override val hasCredentials = credentials.map { it != null }
    override suspend fun load() = credentials.value
    override suspend fun save(credentials: BankCredentials): EmptyResult<BankError> {
        this.credentials.value = credentials
        return Result.Success(Unit)
    }
    override suspend fun clear(): EmptyResult<BankError> {
        credentials.value = null
        return Result.Success(Unit)
    }
    override suspend fun pending() = pending
    override suspend fun setPending(pending: PendingAuthorization?) {
        this.pending = pending
    }
}

private class RecordingProvider : BankSyncProvider {
    override val id = "fake"
    override suspend fun checkCredentials(credentials: BankCredentials) = Result.Success(credentials)
    var code: String? = null
    var validUntil: Instant? = null
    override suspend fun institutions(country: String) = Result.Success(emptyList<BankInstitution>())
    override suspend fun startAuthorization(institution: BankInstitution, validUntil: Instant, state: String): Result<AuthorizationStart, BankError> {
        this.validUntil = validUntil
        return Result.Success(AuthorizationStart("https://bank.example/auth"))
    }
    override suspend fun completeAuthorization(code: String): Result<BankConsent, BankError> {
        this.code = code
        return Result.Success(BankConsent("s1", "Exempelbanken", Instant.parse("2027-04-01T00:00:00Z"), emptyList()))
    }
    override suspend fun transactions(providerAccountUid: String, from: LocalDate?, to: LocalDate) = Result.Success(emptyList<BankTransaction>())
    override suspend fun revoke(sessionId: String) = Result.Success(Unit)
}
