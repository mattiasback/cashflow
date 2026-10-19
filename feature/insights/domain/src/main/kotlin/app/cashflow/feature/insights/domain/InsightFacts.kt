package app.cashflow.feature.insights.domain

import app.cashflow.core.domain.cashflow.Buffer
import app.cashflow.core.domain.cashflow.FlowData
import app.cashflow.core.domain.model.CategoryId
import app.cashflow.core.domain.model.Goals
import app.cashflow.core.domain.model.Money
import kotlin.math.ceil
import kotlin.math.pow

/** The household's figures the what-if, the benchmarks and the AI summary read. Averages are over 12 months. */
data class InsightFacts(
    val months: Int,
    val incomePerMonth: Money,
    val fixedPerMonth: Money,
    val savingsPerMonth: Money,
    val fundsPerMonth: Money,
    /** Net moved into Buffert per month, over the last 6 months. */
    val bufferPerMonth: Money,
    /** Kvar på kontot: money left unplaced each month. */
    val leftPerMonth: Money,
    /** Paid on loans other than the mortgage (category Lån). */
    val loansPerMonth: Money,
    val bufferGoalMonths: Double,
    val bufferTarget: Money,
    /** From the balance you entered; null when none is entered. */
    val bufferNow: Money?,
    val savingsRateGoal: Double,
) {
    val savingsRate: Double get() = if (incomePerMonth.ore > 0) savingsPerMonth.ore.toDouble() / incomePerMonth.ore * 100 else 0.0
    val fixedShare: Double get() = if (incomePerMonth.ore > 0) fixedPerMonth.ore.toDouble() / incomePerMonth.ore * 100 else 0.0
    val bufferMonths: Double? get() = Buffer.months(bufferNow, fixedPerMonth)
    val bufferNeed: Money? get() = bufferNow?.let { Money((bufferTarget.ore - it.ore).coerceAtLeast(0)) }

    companion object {
        private val LOANS = CategoryId("lan")

        fun of(data: FlowData, goals: Goals, bufferNow: Money?): InsightFacts {
            val last = data.periods.last()
            val months12 = data.twelveMonthsTo(last)
            val n = months12.size.coerceAtLeast(1)
            val a = data.sum(months12)
            val months6 = months12.takeLast(6)
            val fixed = Money(a.fixedExpenses.ore / n)
            return InsightFacts(
                months = data.periods.size,
                incomePerMonth = Money(a.income.ore / n),
                fixedPerMonth = fixed,
                savingsPerMonth = Money(a.savings.ore / n),
                fundsPerMonth = Money((a.savingsByCategory[CategoryId.FUNDS]?.ore ?: 0L) / n),
                bufferPerMonth = Money(Buffer.movedIn(data, months6).ore / months6.size.coerceAtLeast(1)),
                leftPerMonth = Money(a.left.ore / n),
                loansPerMonth = Money((a.expensesByCategory[LOANS]?.ore ?: 0L) / n),
                bufferGoalMonths = goals.bufferMonths,
                bufferTarget = Buffer.target(goals.bufferMonths, fixed),
                bufferNow = bufferNow,
                savingsRateGoal = goals.savingsRatePercent,
            )
        }
    }
}

object BufferMath {

    /** Whole months until the buffer is full at [perMonth]; 0 when it already is, null when it never fills. */
    fun monthsToFull(need: Money?, perMonth: Money): Int? = when {
        need == null -> null
        need.ore <= 0 -> 0
        perMonth.ore <= 0 -> null
        else -> ceil(need.ore.toDouble() / perMonth.ore).toInt()
    }
}

/** Tänk om: what extra savings each month would change. */
data class WhatIf(
    val extra: Money,
    /** Months until the buffer is full with the extra, and without it; null when unknown or never. */
    val bufferFullIn: Int?,
    val bufferFullInNow: Int?,
    val savingsRate: Double,
    /** New fund savings after 10 years at [WhatIfCalculator.ANNUAL_RETURN], with and without the extra. */
    val fundsIn10Years: Money,
    val fundsIn10YearsNow: Money,
)

object WhatIfCalculator {

    const val ANNUAL_RETURN = 0.06
    const val YEARS = 10

    /**
     * Extra money goes to the buffer until it is full, then to funds. Without an entered balance the
     * buffer's state is unknown, so the extra goes straight to funds.
     */
    fun calculate(facts: InsightFacts, extra: Money): WhatIf {
        val need = facts.bufferNeed
        val fullIn = BufferMath.monthsToFull(need, facts.bufferPerMonth + extra)
        val fullInNow = BufferMath.monthsToFull(need, facts.bufferPerMonth)
        val toFundsFrom = if (need == null) 0 else fullIn ?: Int.MAX_VALUE
        val rate = if (facts.incomePerMonth.ore > 0) (facts.savingsPerMonth + extra).ore.toDouble() / facts.incomePerMonth.ore * 100 else 0.0
        return WhatIf(
            extra = extra,
            bufferFullIn = fullIn,
            bufferFullInNow = fullInNow,
            savingsRate = rate,
            fundsIn10Years = funds(facts.fundsPerMonth, extra, toFundsFrom),
            fundsIn10YearsNow = funds(facts.fundsPerMonth, Money.ZERO, Int.MAX_VALUE),
        )
    }

    /** Monthly deposits with monthly compounding; the extra joins after month [extraFrom]. */
    private fun funds(perMonth: Money, extra: Money, extraFrom: Int): Money {
        val r = (1 + ANNUAL_RETURN).pow(1.0 / 12) - 1
        var value = 0.0
        for (m in 1..YEARS * 12) {
            value = value * (1 + r) + perMonth.ore.coerceAtLeast(0) + if (m > extraFrom) extra.ore else 0L
        }
        return Money(value.toLong())
    }
}
