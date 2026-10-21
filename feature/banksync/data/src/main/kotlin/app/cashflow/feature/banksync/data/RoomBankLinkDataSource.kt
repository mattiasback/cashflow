package app.cashflow.feature.banksync.data

import android.database.sqlite.SQLiteException
import app.cashflow.core.database.CashFlowDatabase
import app.cashflow.core.database.entity.BankLinkEntity
import app.cashflow.core.domain.DataError
import app.cashflow.core.domain.EmptyResult
import app.cashflow.core.domain.Result
import app.cashflow.core.domain.model.AccountId
import app.cashflow.feature.banksync.domain.BankLink
import app.cashflow.feature.banksync.domain.BankLinkLocalDataSource
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

class RoomBankLinkDataSource(db: CashFlowDatabase) : BankLinkLocalDataSource {
    private val dao = db.bankLinkDao()

    override fun observeLinks(): Flow<List<BankLink>> = dao.observeLinks().map { list -> list.map { it.toLink() } }
    override suspend fun getLinks(): Result<List<BankLink>, DataError.Local> = call { dao.getLinks().map { it.toLink() } }
    override suspend fun upsert(links: List<BankLink>): EmptyResult<DataError.Local> = call { dao.upsert(links.map { it.toEntity() }) }
    override suspend fun setLastSynced(stableKey: String, date: LocalDate): EmptyResult<DataError.Local> =
        call { dao.setLastSynced(stableKey, date.toEpochDays()) }
    override suspend fun deleteSession(sessionId: String): EmptyResult<DataError.Local> = call { dao.deleteSession(sessionId) }

    private suspend inline fun <T> call(crossinline block: suspend () -> T): Result<T, DataError.Local> = try {
        Result.Success(block())
    } catch (e: SQLiteException) {
        Logger.e(e) { "Bank link storage failed" }
        Result.Error(DataError.Local.UNKNOWN)
    }
}

private fun BankLinkEntity.toLink() = BankLink(
    stableKey = stableKey,
    provider = provider,
    accountId = AccountId(accountId),
    institution = institution,
    sessionId = sessionId,
    providerAccountUid = providerAccountUid,
    validUntil = Instant.fromEpochMilliseconds(validUntilEpochMillis),
    displayName = displayName,
    maskedNumber = maskedNumber,
    lastSynced = lastSyncedEpochDay?.let(LocalDate::fromEpochDays),
)

private fun BankLink.toEntity() = BankLinkEntity(
    stableKey = stableKey,
    provider = provider,
    accountId = accountId.value,
    institution = institution,
    sessionId = sessionId,
    providerAccountUid = providerAccountUid,
    validUntilEpochMillis = validUntil.toEpochMilliseconds(),
    displayName = displayName,
    maskedNumber = maskedNumber,
    lastSyncedEpochDay = lastSynced?.toEpochDays(),
)
