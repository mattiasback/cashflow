package app.cashflow.core.importengine

import app.cashflow.core.categorization.PayeeNormalizer
import app.cashflow.core.domain.model.Transaction
import app.cashflow.core.domain.model.TxId
import kotlin.math.abs

/**
 * "Möjlig dubblett" for sources without a balance column (Avanza, mapped CSV files). Their
 * ids cannot tell a re-exported row from a new one when the bank changed the text or the date
 * slightly, so a new row is flagged when an earlier import already has a row on the same
 * account with the same amount, a date at most [windowDays] away and the same payee. Rows with
 * a balance never need this: the balance chain already catches overlap.
 *
 * Each earlier row explains at most one new row, so two genuine identical purchases in the
 * new file are not both flagged against one old row.
 */
class DuplicateDetector(
    private val normalizer: PayeeNormalizer,
    private val windowDays: Int = 3,
) {

    fun find(all: List<Transaction>, newIds: Set<TxId>): Map<TxId, TxId> {
        val fresh = all.filter { it.id in newIds && it.balanceAfter == null && !it.duplicateChecked }
        if (fresh.isEmpty()) return emptyMap()
        val older = all.filter { it.id !in newIds && it.duplicateOf == null }
            .groupBy { Triple(it.accountId, it.amount.ore, normalizer.key(it.rawText)) }
        val used = HashSet<TxId>()
        val result = HashMap<TxId, TxId>()
        for (tx in fresh.sortedWith(compareBy({ it.bookingDate }, { it.fileOrder }))) {
            val candidates = older[Triple(tx.accountId, tx.amount.ore, normalizer.key(tx.rawText))].orEmpty()
            val match = candidates
                .filter { it.importId != tx.importId && it.id !in used }
                .map { it to abs(it.bookingDate.toEpochDays() - tx.bookingDate.toEpochDays()) }
                .filter { it.second <= windowDays }
                .minByOrNull { it.second }
                ?.first ?: continue
            used += match.id
            result[tx.id] = match.id
        }
        return result
    }
}
