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

    suspend fun institutions(country: String): Result<List<BankInstitution>, BankError>

    suspend fun startAuthorization(
        institution: BankInstitution,
        validUntil: Instant,
        state: String,
    ): Result<AuthorizationStart, BankError>

    /** Exchanges the code from the redirect for a consent with its accounts. */
    suspend fun completeAuthorization(code: String): Result<BankConsent, BankError>

    /** Booked rows from [from] (or as far back as the bank allows when null) to [to]. */
    suspend fun transactions(providerAccountUid: String, from: LocalDate?, to: LocalDate): Result<List<BankTransaction>, BankError>

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
