package app.cashflow.core.importengine

import app.cashflow.core.domain.model.AccountId
import app.cashflow.core.domain.model.BalanceState
import app.cashflow.core.domain.model.Money
import app.cashflow.core.domain.model.StatementRow
import app.cashflow.core.domain.model.TxId

/** A statement whose rows are already oldest-first. */
data class InferenceInput(
    val key: Int,
    val rows: List<StatementRow>,
)

sealed interface AccountGuess {
    /** The file continues (or overlaps) a known account's ledger. */
    data class Known(val accountId: AccountId, val overlappingRows: Int) : AccountGuess

    /**
     * Inference is unsure. Files in the same [group] chain into each other, so one answer
     * to "Vilket konto?" covers all of them. [candidates] are tied accounts, if any.
     */
    data class Ask(val group: Int, val candidates: List<AccountId>) : AccountGuess

    /** The file names its own accounts (savings exports), so nothing is inferred. */
    data class Named(val accounts: List<NamedAccount>) : AccountGuess
}

/**
 * An account a savings export names. [existing] is set when it was imported before (same
 * [StatementAccount.key]); [type] is null when the name does not tell, and the user is asked.
 */
data class NamedAccount(
    val source: app.cashflow.core.domain.model.StatementAccount,
    val existing: AccountId?,
    val type: app.cashflow.core.domain.model.AccountType?,
)

/**
 * Finds the account of each statement from its balance chain, since the exports carry no
 * account number: a file's opening balance (first balance − first amount) must match a
 * balance the account already had, and rows that already exist count as evidence too.
 * Works for overlapping files, gaps, and several files picked at once in any order; files
 * resolved in this run become evidence for the rest (a topological walk on the chains).
 * It uses the raw imported ledger, never one edited by hide or delete.
 */
class AccountInferrer(
    private val hasher: TransactionHasher,
) {

    suspend fun infer(
        inputs: List<InferenceInput>,
        knownAccounts: List<AccountId>,
        balanceStates: List<BalanceState>,
        existingIds: suspend (Collection<TxId>) -> Set<TxId>,
    ): Map<Int, AccountGuess> {
        val balancesByAccount = HashMap<AccountId, MutableList<BalanceState>>()
        balanceStates.forEach { balancesByAccount.getOrPut(it.accountId) { ArrayList() } += it }
        val idsResolvedThisRun = HashSet<TxId>()

        val result = HashMap<Int, AccountGuess>()
        val ties = HashMap<Int, List<AccountId>>()
        var pending = inputs.filter { it.rows.isNotEmpty() }
        var progress = true
        while (progress && pending.isNotEmpty()) {
            progress = false
            val stillPending = ArrayList<InferenceInput>()
            // Resolve the files that chain to known evidence first; later files then chain to them.
            for (input in pending.sortedBy { it.rows.first().bookingDate }) {
                val opening = RowOrder.openingBalance(input.rows)
                val firstDate = input.rows.first().bookingDate
                val overlaps = HashMap<AccountId, Int>()
                val scores = knownAccounts.associateWith { account ->
                    val ids = hasher.hashAll(account, input.rows)
                    val overlap = existingIds(ids).size + ids.count { it in idsResolvedThisRun }
                    overlaps[account] = overlap
                    overlap * OVERLAP_WEIGHT + chainScore(opening, firstDate, balancesByAccount[account].orEmpty())
                }.filterValues { it > 0 }
                val best = scores.values.maxOrNull()
                val winners = scores.filterValues { it == best }.keys
                if (best != null && winners.size == 1) {
                    val account = winners.single()
                    result[input.key] = AccountGuess.Known(account, overlappingRows = overlaps[account] ?: 0)
                    val states = balancesByAccount.getOrPut(account) { ArrayList() }
                    input.rows.forEach { row -> row.balanceAfter?.let { states += BalanceState(account, row.bookingDate, it) } }
                    idsResolvedThisRun += hasher.hashAll(account, input.rows)
                    progress = true
                } else {
                    if (winners.size > 1) ties[input.key] = winners.toList()
                    stillPending += input
                }
            }
            pending = stillPending
        }

        // Group the unresolved files by their own chains so the question is asked once per account.
        val groups = UnionFind(pending.size)
        pending.forEachIndexed { i, a ->
            pending.forEachIndexed { j, b ->
                if (i >= j) return@forEachIndexed
                if (continues(a.rows, b.rows) || continues(b.rows, a.rows) || sharesRows(a.rows, b.rows)) groups.union(i, j)
            }
        }
        pending.forEachIndexed { i, input ->
            val group = groups.find(i)
            val candidates = pending.indices.filter { groups.find(it) == group }
                .flatMap { ties[pending[it].key].orEmpty() }
                .distinct()
            result[input.key] = AccountGuess.Ask(group = group, candidates = candidates)
        }
        return result
    }

    /**
     * 3 when the file starts exactly where the account's latest known balance before it ends,
     * 1 when the opening balance matches any earlier balance (an overlapping export), else 0.
     */
    private fun chainScore(opening: Money?, firstDate: kotlinx.datetime.LocalDate, states: List<BalanceState>): Int {
        if (opening == null || states.isEmpty()) return 0
        val before = states.filter { it.date <= firstDate }
        val latestDate = before.maxOfOrNull { it.date }
        if (latestDate != null && before.any { it.date == latestDate && it.balance == opening }) return 3
        return if (states.any { it.balance == opening }) 1 else 0
    }

    /** [next] starts where [previous] ends: on or after its last day, at its closing balance. */
    private fun continues(previous: List<StatementRow>, next: List<StatementRow>): Boolean {
        val closing = previous.last().balanceAfter ?: return false
        val opening = RowOrder.openingBalance(next) ?: return false
        return opening == closing && next.first().bookingDate >= previous.last().bookingDate
    }

    private fun sharesRows(a: List<StatementRow>, b: List<StatementRow>): Boolean {
        val keys = a.asSequence().filter { it.balanceAfter != null }.map(::rowKey).toHashSet()
        return b.any { it.balanceAfter != null && rowKey(it) in keys }
    }

    private fun rowKey(r: StatementRow) = "${r.bookingDate}|${r.amount.ore}|${r.rawText.trim()}|${r.balanceAfter?.ore}"

    private companion object {
        const val OVERLAP_WEIGHT = 4
    }

    private class UnionFind(n: Int) {
        private val parent = IntArray(n) { it }
        fun find(x: Int): Int {
            var r = x
            while (parent[r] != r) r = parent[r]
            var c = x
            while (parent[c] != r) {
                val next = parent[c]
                parent[c] = r
                c = next
            }
            return r
        }

        fun union(a: Int, b: Int) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) parent[maxOf(ra, rb)] = minOf(ra, rb)
        }
    }
}
