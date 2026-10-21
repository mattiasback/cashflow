package app.cashflow.core.importengine

import app.cashflow.core.domain.DataError
import app.cashflow.core.domain.Result
import app.cashflow.core.domain.datasource.TransactionLocalDataSource
import app.cashflow.core.domain.model.ImportBatch
import app.cashflow.core.domain.model.InsertSummary
import app.cashflow.core.domain.model.Transaction
import app.cashflow.core.domain.model.TxId

/** One batch written by [ReconcilingInserter]. */
data class ReconciledInsert(
    val summary: InsertSummary,
    /** Rows that were not there before, in neither origin. */
    val newIds: List<TxId>,
    /** Rows the other origin already had: kept as aliases, not inserted. */
    val matchedCount: Int,
    /** New rows that may duplicate a row of the other origin: flag them for review. */
    val unclear: Map<TxId, TxId>,
)

/**
 * Inserts a batch from a file or from bank sync without bringing the same bank event in twice:
 * rows the other origin already has become aliases of the row you already have (with its
 * category and review decisions), and the next import or sync skips them by id.
 */
class ReconcilingInserter(
    private val transactions: TransactionLocalDataSource,
    private val matcher: SourceMatcher,
) {

    suspend fun insert(batch: ImportBatch, rows: List<Transaction>): Result<ReconciledInsert, DataError.Local> {
        val existingBefore = when (val r = transactions.existingIds(rows.map { it.id })) {
            is Result.Error -> return r
            is Result.Success -> r.data
        }
        val incoming = rows.filter { it.id !in existingBefore }
        val match = if (incoming.isEmpty()) {
            SourceMatch(emptyMap(), emptyMap())
        } else {
            val all = when (val r = transactions.getAll()) {
                is Result.Error -> return r
                is Result.Success -> r.data
            }
            val aliased = when (val r = transactions.aliasedIds()) {
                is Result.Error -> return r
                is Result.Success -> r.data
            }
            matcher.match(incoming, all, aliased)
        }
        val toInsert = rows.filter { it.id !in match.matched }
        val summary = when (val r = transactions.insertBatch(batch, toInsert, match.matched)) {
            is Result.Error -> return r
            is Result.Success -> r.data
        }
        return Result.Success(
            ReconciledInsert(
                summary = summary,
                newIds = incoming.map { it.id }.filter { it !in match.matched },
                matchedCount = match.matched.size,
                unclear = match.unclear,
            ),
        )
    }
}
