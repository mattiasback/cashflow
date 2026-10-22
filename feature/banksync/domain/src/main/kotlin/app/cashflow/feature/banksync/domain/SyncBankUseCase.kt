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

/** What one linked account delivered, so a sync can be explained without showing any row. */
data class AccountSyncReport(
    val label: String,
    val fetched: Int,
    val booked: Int,
    /** Reserved or otherwise not booked yet: not stored until the bank books them. */
    val pending: Int,
    /** Sent by the bank without any date: cannot be placed in a month. */
    val undated: Int,
    val newest: LocalDate?,
    val newCount: Int,
    val matchedCount: Int,
)

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
    val reports: List<AccountSyncReport> = emptyList(),
)

/**
 * "Synka nu": fetches rows for every linked account and runs the booked ones through the same
 * steps as a file import. Each account starts [OVERLAP_DAYS] before its last sync, or before
 * its last file row the first time, so nothing falls in a gap; the overlap is skipped by id
 * (earlier syncs) or matched to the file rows (see SourceMatcher). With [full] every account
 * starts over from that first window: rows already there are skipped the same way, so it only
 * fills what an earlier sync missed.
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
    suspend operator fun invoke(today: LocalDate, full: Boolean = false): Result<SyncOutcome, BankError> {
        val all = (links.getLinks() as? Result.Success)?.data ?: return Result.Error(BankError.STORAGE)
        var known = (accounts.getAccounts() as? Result.Success)?.data ?: return Result.Error(BankError.STORAGE)
        val rows = (transactions.getAll() as? Result.Success)?.data ?: return Result.Error(BankError.STORAGE)
        val now = clock.now()
        val (active, expired) = all.partition { it.validUntil > now }
        if (all.isNotEmpty() && active.isEmpty()) return Result.Error(BankError.CONSENT_EXPIRED)

        var expiredCount = expired.size
        var newCount = 0
        var matched = 0
        val unclear = HashMap<TxId, TxId>()
        val reports = ArrayList<AccountSyncReport>()
        for (original in active) {
            val account0 = known.firstOrNull { it.id == original.accountId } ?: continue
            // An account named after the holder (an earlier version did) gets the number-based name.
            val link = renamed(original, account0.name)
            val account = if (link !== original) {
                val fixed = account0.copy(name = link.displayName)
                if (accounts.upsertAccount(fixed) is Result.Error || links.upsert(listOf(link)) is Result.Error) return Result.Error(BankError.STORAGE)
                known = known.map { if (it.id == fixed.id) fixed else it }
                fixed
            } else {
                account0
            }
            val ownRows = rows.filter { it.accountId == account.id }
            val lastFileDay = ownRows.filter { it.origin == TransactionOrigin.FILE }.maxOfOrNull { it.bookingDate }
            // With nothing synced into the account (e.g. after undoing the syncs) there is nothing to continue from.
            val continueFrom = link.lastSynced.takeIf { !full && ownRows.any { it.origin == TransactionOrigin.BANK } }
            val from = (continueFrom ?: lastFileDay)?.minus(DatePeriod(days = OVERLAP_DAYS))
            val fetched = when (val r = provider.transactions(link.providerAccountUid, from, today)) {
                // Revoked at the bank before its date: the other links still sync.
                is Result.Error -> if (r.error == BankError.CONSENT_EXPIRED) { expiredCount++; continue } else return r
                is Result.Success -> r.data
            }
            val importId = ImportId(Uuid.random().toString())
            val txs = mapper.map(fetched.rows, link, account, importId)
            var accountNew = 0
            var accountMatched = 0
            if (txs.isNotEmpty()) {
                val batch = ImportBatch(
                    id = importId,
                    source = ImportSource.BANK_SYNC,
                    fileName = link.displayName,
                    accountId = account.id,
                    importedAt = now,
                    rowCount = txs.size,
                    newCount = 0,
                    duplicateCount = 0,
                    warnings = emptyList(),
                )
                val inserted = (inserter.insert(batch, txs) as? Result.Success)?.data ?: return Result.Error(BankError.STORAGE)
                accountNew = inserted.summary.newCount
                accountMatched = inserted.matchedCount
                newCount += accountNew
                matched += accountMatched
                unclear += inserted.unclear.filterKeys { it in inserted.newIds }
                accounts.addBalanceStates(txs.mapNotNull { tx -> tx.balanceAfter?.let { BalanceState(account.id, tx.bookingDate, it) } })
            }
            val booked = fetched.rows.count { it.booked }
            reports += AccountSyncReport(
                label = link.displayName,
                fetched = fetched.rows.size + fetched.undated,
                booked = booked,
                pending = fetched.rows.size - booked,
                undated = fetched.undated,
                newest = fetched.rows.filter { it.booked }.maxOfOrNull { it.bookingDate },
                newCount = accountNew,
                matchedCount = accountMatched,
            )
            if (links.setLastSynced(link.stableKey, today) is Result.Error) return Result.Error(BankError.STORAGE)
        }
        if (unclear.isNotEmpty() && transactions.setDuplicates(unclear) is Result.Error) return Result.Error(BankError.STORAGE)
        if (reclassify() is Result.Error) return Result.Error(BankError.STORAGE)
        return Result.Success(
            SyncOutcome(active.size - (expiredCount - expired.size), newCount, matched, unclear.size, expiredCount, reports),
        )
    }

    /**
     * Earlier versions named the CashFlow account (and the link) after the account's holder. When
     * the account still carries that name, it becomes "Bank ··1234"; a name you changed is kept.
     */
    private fun renamed(link: BankLink, accountName: String): BankLink {
        val number = link.maskedNumber ?: return link
        if (accountName != link.displayName) return link
        val label = bankAccountLabel(link.institution, null, number)
        // A label that already starts with the number-based form came from this version.
        if (link.displayName.startsWith("${link.institution} ··")) return link
        return link.copy(displayName = label)
    }

    companion object {
        const val OVERLAP_DAYS = 7
    }
}
