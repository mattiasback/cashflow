package app.cashflow.core.importengine

import app.cashflow.core.domain.model.StatementRow

/** Banks write rows newest-first or oldest-first; the engine always works oldest-first. */
object RowOrder {

    fun oldestFirst(rows: List<StatementRow>): List<StatementRow> {
        if (rows.size < 2) return rows
        if (rows.first().bookingDate < rows.last().bookingDate) return rows
        if (rows.first().bookingDate > rows.last().bookingDate) return rows.asReversed().toList()
        // Same first and last date: use the running balance to decide.
        val ascending = chainScore(rows)
        val descending = chainScore(rows.asReversed())
        return if (descending > ascending) rows.asReversed().toList() else rows
    }

    /** How many consecutive rows satisfy balance[i] == balance[i-1] + amount[i]. */
    fun chainScore(rows: List<StatementRow>): Int =
        rows.zipWithNext().count { (a, b) ->
            val ab = a.balanceAfter
            val bb = b.balanceAfter
            ab != null && bb != null && ab + b.amount == bb
        }

    /** Balance before the first (oldest) row, or null when the bank has no balance column. */
    fun openingBalance(oldestFirst: List<StatementRow>) =
        oldestFirst.firstOrNull()?.let { first -> first.balanceAfter?.let { it - first.amount } }
}
