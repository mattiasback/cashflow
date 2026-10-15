package app.cashflow.core.importengine

import app.cashflow.core.domain.model.Account
import app.cashflow.core.domain.model.AccountType
import app.cashflow.core.domain.model.Transaction
import app.cashflow.core.domain.model.TransferKind
import app.cashflow.core.domain.model.TxId
import kotlin.math.abs

data class TransferResult(
    val kind: TransferKind,
    val partner: TxId?,
)

/**
 * Pairs mirrored rows (same amount, opposite sign) on two different accounts.
 *
 * - Runs over all stored transactions after every import, because the other leg may
 *   arrive in a later file.
 * - Uses a global 1:1 assignment per amount (maximum bipartite matching, cheapest date
 *   distance first), not greedy nearest-neighbour, so weekly identical transfers and
 *   same-day duplicates pair correctly.
 * - Exact amount; the date window depends on the account types (bank–bank 0 days).
 * - Small round amounts need a hint (an account number or a transfer word in the text).
 * - An ambiguous group (unequal legs spread over several accounts) is left unpaired for
 *   review instead of being guessed.
 * - A row that looks like a transfer but has no partner becomes a savings transfer when the
 *   clearing prefix names a savings institution, otherwise a lone leg.
 */
class TransferMatcher(
    private val windowDays: (AccountType, AccountType) -> Int = ::defaultWindow,
) {

    fun match(transactions: List<Transaction>, accounts: List<Account>): Map<TxId, TransferResult> {
        val typeOf = accounts.associate { it.id to it.type }
        val result = HashMap<TxId, TransferResult>()

        transactions.groupBy { abs(it.amount.ore) }.forEach { (ore, sameAmount) ->
            if (ore == 0L) return@forEach
            val outs = sameAmount.filter { it.amount.isNegative }
            val ins = sameAmount.filter { it.amount.isPositive }
            if (outs.isEmpty() || ins.isEmpty()) return@forEach
            val needsHint = isSmallRoundAmount(ore)

            val edges = HashMap<Int, MutableList<Pair<Int, Int>>>() // out index -> (in index, cost)
            outs.forEachIndexed { oi, o ->
                ins.forEachIndexed { ii, i ->
                    if (o.accountId == i.accountId) return@forEachIndexed
                    val window = windowDays(typeOf[o.accountId] ?: AccountType.OTHER, typeOf[i.accountId] ?: AccountType.OTHER)
                    val distance = abs(o.bookingDate.toEpochDays() - i.bookingDate.toEpochDays()).toInt()
                    if (distance > window) return@forEachIndexed
                    if (needsHint && !hasTransferHint(o) && !hasTransferHint(i)) return@forEachIndexed
                    edges.getOrPut(oi) { ArrayList() } += ii to distance
                }
            }
            if (edges.isEmpty()) return@forEach

            for (component in components(outs.size, edges)) {
                val compOuts = component.first
                val compIns = component.second
                if (compOuts.size != compIns.size) {
                    // Unequal legs: only safe when every extra leg is interchangeable (same account).
                    val larger = if (compOuts.size > compIns.size) compOuts.map { outs[it].accountId } else compIns.map { ins[it].accountId }
                    if (larger.distinct().size > 1) continue
                }
                val pairs = maxMatching(compOuts, edges)
                pairs.forEach { (oi, ii) ->
                    val o = outs[oi]
                    val i = ins[ii]
                    val kind = kindFor(typeOf[o.accountId], typeOf[i.accountId])
                    result[o.id] = TransferResult(kind.first, i.id)
                    result[i.id] = TransferResult(kind.second, o.id)
                }
            }
        }

        // Unpaired rows that clearly name another account.
        for (tx in transactions) {
            if (tx.id in result) continue
            val number = TransferHints.accountNumberIn(tx.rawText)
            if (number != null || TransferHints.hasTransferWord(tx.rawText)) {
                val kind = if (number != null && TransferHints.isSavingsInstitution(number)) {
                    if (tx.amount.isNegative) TransferKind.TO_SAVINGS else TransferKind.FROM_SAVINGS
                } else {
                    TransferKind.LONE_LEG
                }
                result[tx.id] = TransferResult(kind, null)
            }
        }
        return result
    }

    private fun kindFor(from: AccountType?, to: AccountType?): Pair<TransferKind, TransferKind> {
        val fromSavings = from?.isSavingsLike == true || from == AccountType.PENSION
        val toSavings = to?.isSavingsLike == true || to == AccountType.PENSION
        return when {
            !fromSavings && toSavings -> TransferKind.TO_SAVINGS to TransferKind.TO_SAVINGS
            fromSavings && !toSavings -> TransferKind.FROM_SAVINGS to TransferKind.FROM_SAVINGS
            else -> TransferKind.INTERNAL to TransferKind.INTERNAL
        }
    }

    private fun isSmallRoundAmount(ore: Long): Boolean = ore < SMALL_AMOUNT_ORE && ore % ROUND_ORE == 0L

    private fun hasTransferHint(tx: Transaction): Boolean =
        TransferHints.accountNumberIn(tx.rawText) != null || TransferHints.hasTransferWord(tx.rawText)

    /** Connected components of the bipartite graph: (out indexes, in indexes). */
    private fun components(outCount: Int, edges: Map<Int, List<Pair<Int, Int>>>): List<Pair<List<Int>, List<Int>>> {
        val inToOuts = HashMap<Int, MutableList<Int>>()
        edges.forEach { (o, list) -> list.forEach { (i, _) -> inToOuts.getOrPut(i) { ArrayList() } += o } }
        val seenOut = BooleanArray(outCount)
        val out = ArrayList<Pair<List<Int>, List<Int>>>()
        for (start in edges.keys.sorted()) {
            if (seenOut[start]) continue
            val compOuts = ArrayList<Int>()
            val compIns = LinkedHashSet<Int>()
            val queue = ArrayDeque(listOf(start))
            seenOut[start] = true
            while (queue.isNotEmpty()) {
                val o = queue.removeFirst()
                compOuts += o
                for ((i, _) in edges[o].orEmpty()) {
                    if (compIns.add(i)) {
                        inToOuts[i].orEmpty().forEach { o2 ->
                            if (!seenOut[o2]) {
                                seenOut[o2] = true
                                queue += o2
                            }
                        }
                    }
                }
            }
            out += compOuts to compIns.toList()
        }
        return out
    }

    /** Kuhn's augmenting paths, trying the closest dates first. */
    private fun maxMatching(outs: List<Int>, edges: Map<Int, List<Pair<Int, Int>>>): List<Pair<Int, Int>> {
        val matchOfIn = HashMap<Int, Int>()
        val sorted = edges.mapValues { (_, list) -> list.sortedWith(compareBy({ it.second }, { it.first })).map { it.first } }
        fun tryAugment(o: Int, visited: MutableSet<Int>): Boolean {
            for (i in sorted[o].orEmpty()) {
                if (!visited.add(i)) continue
                val current = matchOfIn[i]
                if (current == null || tryAugment(current, visited)) {
                    matchOfIn[i] = o
                    return true
                }
            }
            return false
        }
        outs.sorted().forEach { tryAugment(it, HashSet()) }
        return matchOfIn.map { (i, o) -> o to i }
    }

    companion object {
        private const val SMALL_AMOUNT_ORE = 100_000L // 1 000 kr
        private const val ROUND_ORE = 10_000L // whole hundreds

        fun defaultWindow(a: AccountType, b: AccountType): Int =
            if (a == AccountType.TRANSACTION && b == AccountType.TRANSACTION) 0 else 3
    }
}

/** Text hints that a row is a transfer. Account numbers are compared with dots stripped. */
object TransferHints {
    private val accountNumber = Regex("""(?<![\p{L}\d])(\d{4}[ .-]?\d{2}[ .-]?\d{3}[ .-]?\d{2,5}|\d{10,15})(?![\p{L}\d])""")
    private val transferWords = Regex("""(?iU)\b(överföring|overforing|överf|overf)\b""")

    /** Clearing-number prefixes of savings and investment institutions (9550–9569 Avanza, 9100 Nordnet). */
    private val savingsClearing = listOf("955", "956", "9100")

    fun accountNumberIn(text: String): String? =
        accountNumber.find(text)?.value?.let(::normalizeAccountNumber)

    fun hasTransferWord(text: String): Boolean = transferWords.containsMatchIn(text)

    fun isSavingsInstitution(normalizedNumber: String): Boolean = savingsClearing.any { normalizedNumber.startsWith(it) }

    fun normalizeAccountNumber(raw: String): String = raw.filter(Char::isDigit)
}
