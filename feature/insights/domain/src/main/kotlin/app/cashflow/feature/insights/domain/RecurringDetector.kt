package app.cashflow.feature.insights.domain

import app.cashflow.core.domain.model.CategoryId
import app.cashflow.core.domain.model.CategoryKind
import app.cashflow.core.domain.model.Money
import app.cashflow.core.domain.model.Period
import app.cashflow.core.domain.model.Transaction
import app.cashflow.core.domain.model.TransferKind
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.math.abs

/** A charge that comes back once a month at a steady price: a subscription, a membership, a fee. */
data class RecurringCharge(
    /** The payee, upper-cased: what groups the charges. */
    val key: String,
    val payee: String,
    val categoryId: CategoryId,
    /** The latest price, positive. */
    val monthly: Money,
    /** The steady price before the latest rise; null when the price has not risen. */
    val previous: Money?,
    /** The first charge at the new price. */
    val risenOn: LocalDate?,
    /** Months in the window it was charged in. */
    val months: Int,
    val last: LocalDate,
) {
    /** How much more it costs per month since the rise. */
    val rise: Money? get() = previous?.let { monthly - it }
}

/**
 * Finds charges that come back once a month. A payee counts when it was charged exactly once in
 * each of at least [MIN_MONTHS] months of the last [WINDOW_MONTHS] at one steady price (or an old
 * and a new one), and the latest charge is recent. Bills that vary (electricity) are left out.
 */
object RecurringDetector {

    const val WINDOW_MONTHS = 6
    const val MIN_MONTHS = 3

    /** A rise smaller than this is rounding or currency noise, not a new price. */
    private const val RISE_PERCENT = 5.0

    fun find(transactions: List<Transaction>, kindOf: (CategoryId) -> CategoryKind): List<RecurringCharge> {
        val expenses = transactions.filter { tx ->
            tx.countsInCashFlow && !tx.hidden && tx.transferKind == TransferKind.NONE && tx.amount.ore < 0 &&
                kindOf(tx.categoryId).let { it.isExpense || it == CategoryKind.UNCATEGORIZED }
        }
        val end = expenses.maxOfOrNull { it.transactionDate } ?: return emptyList()
        val first = Period.of(end).minusMonths(WINDOW_MONTHS - 1)
        return expenses
            .filter { Period.of(it.transactionDate) >= first }
            .groupBy { it.normalizedText.ifBlank { it.rawText }.trim().uppercase() }
            .mapNotNull { (key, rows) -> charge(key, rows, end) }
            .sortedByDescending { it.monthly.ore }
    }

    private fun charge(key: String, rows: List<Transaction>, end: LocalDate): RecurringCharge? {
        val byMonth = rows.groupBy { Period.of(it.transactionDate) }
        if (byMonth.size < MIN_MONTHS || byMonth.values.any { it.size != 1 }) return null
        val sorted = rows.sortedBy { it.transactionDate }
        val last = sorted.last()
        if (last.transactionDate < end.minus(45, DateTimeUnit.DAY)) return null
        val prices = sorted.map { -it.amount.ore }
        val latest = prices.last()
        // At most two steady prices, the old and the current: anything else is a bill that varies.
        val before = prices.indexOfLast { !same(it, latest) }
        if (before >= 0 && prices.take(before + 1).any { !same(it, prices[before]) }) return null
        val rose = before >= 0 && (latest - prices[before]).toDouble() / prices[before] * 100 >= RISE_PERCENT
        return RecurringCharge(
            key = key,
            payee = last.normalizedText.ifBlank { last.rawText },
            categoryId = last.categoryId,
            monthly = Money(latest),
            previous = if (rose) Money(prices[before]) else null,
            risenOn = if (rose) sorted[before + 1].transactionDate else null,
            months = byMonth.size,
            last = last.transactionDate,
        )
    }

    private fun same(a: Long, b: Long) = abs(a - b) <= maxOf(100L, b / 100)
}

internal fun Period.minusMonths(n: Int): Period {
    val index = year * 12 + (month - 1) - n
    return Period(index / 12, index % 12 + 1)
}
