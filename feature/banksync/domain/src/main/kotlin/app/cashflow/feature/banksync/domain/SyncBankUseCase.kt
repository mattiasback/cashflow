package app.cashflow.feature.banksync.domain

import app.cashflow.core.domain.Result
import app.cashflow.core.domain.datasource.AccountLocalDataSource
import app.cashflow.core.domain.datasource.TransactionLocalDataSource
import app.cashflow.core.domain.model.BalanceState
import app.cashflow.core.domain.model.ImportBatch
import app.cashflow.core.domain.model.ImportId
import app.cashflow.core.domain.model.ImportSource
import app.cashflow.core.domain.model.TransactionOrigin
import app.cashflow.core.domain.model.TxId
import app.cashflow.core.importengine.ReclassifyUseCase
import app.cashflow.core.importengine.ReconcilingInserter
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** The receipt of one "Synka nu". */
data class SyncOutcome(
    val accountCount: Int,
    val newCount: Int,
    /** Rows a file import already had: kept once. */
    val matchedCount: Int,
    /** New rows that may duplicate a file row: waiting in Att granska. */
    val unclearCount: Int,
    /** Links whose consent ran out: link the bank again to keep syncing them. */
    val expiredCount: Int,
)

/**
 * "Synka nu": fetches booked rows for every linked account and runs them through the same
 * steps as a file import. Each account starts [OVERLAP_DAYS] before its last sync, or before
 * its last file row the first time, so nothing falls in a gap; the overlap is skipped by id
 * (earlier syncs) or matched to the file rows (see SourceMatcher).
 */
class SyncBankUseCase(
    private val provider: BankSyncProvider,
    private val links: BankLinkLocalDataSource,
    private val accounts: AccountLocalDataSource,
    private val transactions: TransactionLocalDataSource,
    private val inserter: ReconcilingInserter,
    private val mapper: BankRowMapper,
    private val reclassify: ReclassifyUseCase,
    private val clock: Clock = Clock.System,
) {

    @OptIn(ExperimentalUuidApi::class)
    suspend operator fun invoke(today: LocalDate): Result<SyncOutcome, BankError> {
        val all = (links.getLinks() as? Result.Success)?.data ?: return Result.Error(BankError.STORAGE)
        val known = (accounts.getAccounts() as? Result.Success)?.data ?: return Result.Error(BankError.STORAGE)
        val rows = (transactions.getAll() as? Result.Success)?.data ?: return Result.Error(BankError.STORAGE)
        val now = clock.now()
        val (active, expired) = all.partition { it.validUntil > now }
        if (all.isNotEmpty() && active.isEmpty()) return Result.Error(BankError.CONSENT_EXPIRED)

        var expiredCount = expired.size
        var newCount = 0
        var matched = 0
        val unclear = HashMap<TxId, TxId>()
        for (link in active) {
            val account = known.firstOrNull { it.id == link.accountId } ?: continue
            val lastFileDay = rows.filter { it.accountId == account.id && it.origin == TransactionOrigin.FILE }.maxOfOrNull { it.bookingDate }
            val from = (link.lastSynced ?: lastFileDay)?.minus(DatePeriod(days = OVERLAP_DAYS))
            val fetched = when (val r = provider.transactions(link.providerAccountUid, from, today)) {
                // Revoked at the bank before its date: the other links still sync.
                is Result.Error -> if (r.error == BankError.CONSENT_EXPIRED) { expiredCount++; continue } else return r
                is Result.Success -> r.data
            }
            val importId = ImportId(Uuid.random().toString())
            val txs = mapper.map(fetched, link, account, importId)
            if (txs.isNotEmpty()) {
                val batch = ImportBatch(
                    id = importId,
                    source = ImportSource.BANK_SYNC,
                    fileName = "${link.institution} · ${link.displayName}",
                    accountId = account.id,
                    importedAt = now,
                    rowCount = txs.size,
                    newCount = 0,
                    duplicateCount = 0,
                    warnings = emptyList(),
                )
                val inserted = (inserter.insert(batch, txs) as? Result.Success)?.data ?: return Result.Error(BankError.STORAGE)
                newCount += inserted.summary.newCount
                matched += inserted.matchedCount
                unclear += inserted.unclear.filterKeys { it in inserted.newIds }
                accounts.addBalanceStates(txs.mapNotNull { tx -> tx.balanceAfter?.let { BalanceState(account.id, tx.bookingDate, it) } })
            }
            if (links.setLastSynced(link.stableKey, today) is Result.Error) return Result.Error(BankError.STORAGE)
        }
        if (unclear.isNotEmpty() && transactions.setDuplicates(unclear) is Result.Error) return Result.Error(BankError.STORAGE)
        if (reclassify() is Result.Error) return Result.Error(BankError.STORAGE)
        return Result.Success(SyncOutcome(active.size - (expiredCount - expired.size), newCount, matched, unclear.size, expiredCount))
    }

    companion object {
        const val OVERLAP_DAYS = 7
    }
}
