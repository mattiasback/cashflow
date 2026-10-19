package app.cashflow.core.domain.cashflow

import app.cashflow.core.domain.model.CategoryId
import app.cashflow.core.domain.model.Money

/**
 * The household buffer, measured in months of fixed expenses. Without bank balances the app only
 * knows flows, so the buffer is what has been moved into Buffert (net of withdrawals) over the
 * imported history. Hem's Buffert KPI and Flöde's Buffert tile read the same numbers.
 */
object Buffer {

    /** Net amount moved into Buffert over every imported month; null when nothing was. */
    fun value(data: FlowData): Money? {
        val total = data.periods.sumOf { data.month(it).savingsByCategory[CategoryId.BUFFER]?.ore ?: 0L }
        val any = data.periods.any { data.month(it).savingsByCategory.containsKey(CategoryId.BUFFER) }
        return if (any) Money(total) else null
    }

    /** Months of fixed expenses the buffer covers; null when there is no buffer or no fixed costs yet. */
    fun months(value: Money?, fixedPerMonth: Money): Double? {
        if (value == null || value.ore <= 0 || fixedPerMonth.ore <= 0) return null
        return value.ore.toDouble() / fixedPerMonth.ore
    }
}
