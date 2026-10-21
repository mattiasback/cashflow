package app.cashflow.core.importengine

import app.cashflow.core.categorization.PayeeNormalizer
import app.cashflow.core.domain.model.AccountId
import app.cashflow.core.domain.model.Transaction
import app.cashflow.core.domain.model.TxId
import kotlinx.datetime.LocalDate
import kotlin.math.abs

/** What [SourceMatcher] found for a batch of incoming rows. */
data class SourceMatch(
    /** Incoming id → the existing row of the other origin that is the same bank event. */
    val matched: Map<TxId, TxId>,
    /**
     * Incoming id → the nearest existing row it may duplicate. The other origin covers that day
     * but its candidates were all taken, so the user decides ("Möjlig dubblett").
     */
    val unclear: Map<TxId, TxId>,
)

/**
 * Finds rows that a file import and bank sync both delivered. The two never share an id or a
 * text (`Kortköp 261012 ICA …` in the file, `ICA …` from the bank), so a pair must agree on the
 * account and the amount in öre, with booking dates at most [windowDays] apart. Pairs are
 * ranked by date distance, then by payee, and assigned 1:1 over the whole batch: two genuine
 * 45 kr coffees on one day pair with two rows, never both with one.
 *
 * Only rows of the other origin are candidates, and only those not already standing in for an
 * earlier incoming row ([alreadyAliased]). Same-origin overlap is handled by ids and
 * [DuplicateDetector].
 */
class SourceMatcher(
    private val normalizer: PayeeNormalizer,
    private val windowDays: Int = 3,
) {

    fun match(incoming: List<Transaction>, existing: List<Transaction>, alreadyAliased: Set<TxId>): SourceMatch {
        if (incoming.isEmpty()) return SourceMatch(emptyMap(), emptyMap())
        val origin = incoming.first().origin
        require(incoming.all { it.origin == origin }) { "One batch has one origin" }
        val others = existing.filter { it.origin != origin && it.id !in alreadyAliased }
        if (others.isEmpty()) return SourceMatch(emptyMap(), emptyMap())

        val byKey = others.groupBy { it.accountId to it.amount.ore }
        val coverage: Map<AccountId, ClosedRange<LocalDate>> = others.groupBy { it.accountId }
            .mapValues { (_, rows) -> rows.minOf { it.bookingDate }..rows.maxOf { it.bookingDate } }

        val pairs = ArrayList<Candidate>()
        for (tx in incoming) {
            val key = normalizer.key(tx.rawText)
            for (other in byKey[tx.accountId to tx.amount.ore].orEmpty()) {
                val days = daysBetween(tx.bookingDate, other.bookingDate)
                if (days > windowDays) continue
                val samePayee = key.isNotEmpty() && key == normalizer.key(other.rawText)
                pairs += Candidate(tx, other, days, if (samePayee) 0 else 1, daysBetween(tx.transactionDate, other.transactionDate))
            }
        }
        pairs.sortWith(compareBy<Candidate>({ it.days }, { it.payeeMismatch }, { it.transactionDays }, { it.incoming.bookingDate }, { it.incoming.fileOrder }))

        val matched = HashMap<TxId, TxId>()
        val used = HashSet<TxId>()
        for (pair in pairs) {
            if (pair.incoming.id in matched || pair.existing.id in used) continue
            matched[pair.incoming.id] = pair.existing.id
            used += pair.existing.id
        }

        val unclear = HashMap<TxId, TxId>()
        for (pair in pairs) {
            val tx = pair.incoming
            if (tx.id in matched || tx.id in unclear) continue
            // Past the other origin's last day, a row is simply newer than that file.
            if (coverage[tx.accountId]?.contains(tx.bookingDate) != true) continue
            unclear[tx.id] = pair.existing.id
        }
        return SourceMatch(matched, unclear)
    }

    private data class Candidate(
        val incoming: Transaction,
        val existing: Transaction,
        val days: Int,
        val payeeMismatch: Int,
        val transactionDays: Int,
    )

    private fun daysBetween(a: LocalDate, b: LocalDate): Int = abs(a.toEpochDays() - b.toEpochDays()).toInt()
}
