package app.cashflow.core.domain.cashflow

import app.cashflow.core.domain.model.CategoryId
import app.cashflow.core.domain.model.Money
import app.cashflow.core.domain.model.Period
import kotlin.math.roundToLong

/**
 * The household buffer. Without bank balances the app only knows flows, so it cannot measure the
 * buffer itself: it recommends a size from the goal and fixed expenses, and counts what has been
 * moved into Buffert (net of withdrawals). Hem's Buffert KPI and Flöde's Buffert tile read the same numbers.
 */
object Buffer {

    /** Net amount moved into Buffert over every imported month; null when nothing was. */
    fun value(data: FlowData): Money? {
        val total = data.periods.sumOf { data.month(it).savingsByCategory[CategoryId.BUFFER]?.ore ?: 0L }
        val any = data.periods.any { data.month(it).savingsByCategory.containsKey(CategoryId.BUFFER) }
        return if (any) Money(total) else null
    }

    /**
     * The buffer to aim for: the goal in months times the average monthly fixed expenses, to the
     * nearest hundred kronor. This is a recommendation; the real balance is not known.
     */
    fun target(goalMonths: Double, fixedPerMonth: Money): Money =
        Money((fixedPerMonth.ore * goalMonths / 10_000).roundToLong() * 10_000)

    /** Net amount moved into Buffert during [months]. */
    fun movedIn(data: FlowData, months: List<Period>): Money =
        Money(months.sumOf { data.month(it).savingsByCategory[CategoryId.BUFFER]?.ore ?: 0L })

    /** Months of fixed expenses the buffer covers; null when there is no buffer or no fixed costs yet. */
    fun months(value: Money?, fixedPerMonth: Money): Double? {
        if (value == null || value.ore <= 0 || fixedPerMonth.ore <= 0) return null
        return value.ore.toDouble() / fixedPerMonth.ore
    }
}
