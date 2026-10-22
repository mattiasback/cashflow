package app.cashflow.feature.banksync.domain

import app.cashflow.core.domain.DataError
import app.cashflow.core.domain.EmptyResult
import app.cashflow.core.domain.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * The whole contract with an open-banking provider. Swapping provider is one new module that
 * implements this and one Koin binding; matching, storage and UI stay as they are. Read-only:
 * there is no payment call.
 */
interface BankSyncProvider {
    /** Stored on each link, e.g. "enablebanking". */
    val id: String

    /**
     * Checks a key and application id before they are used: the key reads and the provider accepts
     * it. Returns them with the redirect URL exactly as the provider registered it.
     */
    suspend fun checkCredentials(credentials: BankCredentials): Result<BankCredentials, BankError>

    suspend fun institutions(country: String): Result<List<BankInstitution>, BankError>

    suspend fun startAuthorization(
        institution: BankInstitution,
        validUntil: Instant,
        state: String,
    ): Result<AuthorizationStart, BankError>

    /** Exchanges the code from the redirect for a consent with its accounts. */
    suspend fun completeAuthorization(code: String): Result<BankConsent, BankError>

    /** Every row from [from] (or as far back as the bank allows when null) to [to], booked or not. */
    suspend fun transactions(providerAccountUid: String, from: LocalDate?, to: LocalDate): Result<FetchedTransactions, BankError>

    /** Ends the consent at the provider. */
    suspend fun revoke(sessionId: String): EmptyResult<BankError>
}

/** Your key and application id, encrypted on the device. */
interface BankCredentialStore {
    val hasCredentials: Flow<Boolean>
    suspend fun load(): BankCredentials?
    suspend fun save(credentials: BankCredentials): EmptyResult<BankError>
    suspend fun clear(): EmptyResult<BankError>

    /** The authorization waiting for the bank's answer; survives the app being closed meanwhile. */
    suspend fun pending(): PendingAuthorization?
    suspend fun setPending(pending: PendingAuthorization?)
}

interface BankLinkLocalDataSource {
    fun observeLinks(): Flow<List<BankLink>>
    suspend fun getLinks(): Result<List<BankLink>, DataError.Local>
    suspend fun upsert(links: List<BankLink>): EmptyResult<DataError.Local>
    suspend fun setLastSynced(stableKey: String, date: LocalDate): EmptyResult<DataError.Local>
    suspend fun deleteSession(sessionId: String): EmptyResult<DataError.Local>
}

// Temporary (step 9): the key import is a stand-in until a backend holds the key (docs/bank-sync.md).
/** Reads the `.pem` file you pick, once; the text then goes encrypted into [BankCredentialStore]. */
interface KeyFileReader {
    /** Null when the file cannot be read or is far larger than a key. */
    suspend fun read(uri: String): String?
}
