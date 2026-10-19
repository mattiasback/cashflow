package app.cashflow.feature.insights.domain

import app.cashflow.core.domain.cashflow.FlowData
import app.cashflow.core.domain.model.CategoryId
import app.cashflow.core.domain.model.CategoryKind
import app.cashflow.core.domain.model.Money
import app.cashflow.core.domain.model.Period
import app.cashflow.core.domain.model.Transaction

/** "Spara utan att märka det": a saving found in the household's own rows, worth [perYear]. */
sealed interface Opportunity {
    /** Stable while the finding is the same, so "Jag har fixat det" sticks; a new finding gets a new id. */
    val id: String
    val perYear: Money

    /** Subscriptions and fees that went up: going back to the old price saves the rise. */
    data class PriceRises(val rises: List<RecurringCharge>, val subscriptions: Int) : Opportunity {
        override val id = "rise:" + rises.map { "${it.key}@${it.risenOn}" }.sorted().joinToString("|")
        val perMonth: Money get() = Money(rises.sumOf { it.rise?.ore ?: 0L })
        override val perYear: Money get() = perMonth * 12
    }

    /** Several small charges every month that are easy to forget; stopping them saves all of it. */
    data class SmallCharges(val charges: List<RecurringCharge>) : Opportunity {
        override val id = "small:" + charges.map { it.key }.sorted().joinToString("|")
        val perMonth: Money get() = Money(charges.sumOf { it.monthly.ore })
        override val perYear: Money get() = perMonth * 12
    }

    /** A variable category well above its own average in the latest month: keeping the average saves the gap. */
    data class OverAverage(
        val categoryId: CategoryId,
        val period: Period,
        val now: Money,
        val average: Money,
        /** How many months the average is over. */
        val averageMonths: Int,
    ) : Opportunity {
        override val id = "over:${categoryId.value}:$period"
        val percentOver: Double get() = (now.ore - average.ore).toDouble() / average.ore * 100
        override val perYear: Money get() = (now - average) * 12
    }
}

/**
 * One detector per type, sorted by kr per year. Nothing is suggested on less than [MIN_MONTHS] of
 * data: too little history makes averages and "recurring" guesses.
 */
object OpportunityEngine {

    const val MIN_MONTHS = 3
    const val SMALL_CHARGE_ORE = 100_00L
    private const val OVER_PERCENT = 10.0
    private const val OVER_MIN_ORE = 200_00L
    private const val AVERAGE_MONTHS = 6

    /** Housing and loans recur too, but their price follows the interest rate, not a choice. */
    private val NOT_SUBSCRIPTIONS = setOf(CategoryId.HOUSING, CategoryId("lan"))
    private val SUBSCRIPTION = CategoryId("abonnemang")

    fun find(data: FlowData, transactions: List<Transaction>, kindOf: (CategoryId) -> CategoryKind): List<Opportunity> {
        if (data.periods.size < MIN_MONTHS) return emptyList()
        val recurring = RecurringDetector.find(transactions, kindOf).filter { it.categoryId !in NOT_SUBSCRIPTIONS }
        return (listOfNotNull(priceRises(recurring), smallCharges(recurring)) + overAverage(data, kindOf))
            .filter { it.perYear.ore > 0 }
            .sortedByDescending { it.perYear.ore }
    }

    fun priceRises(recurring: List<RecurringCharge>): Opportunity.PriceRises? {
        val rises = recurring.filter { it.rise != null }
        if (rises.isEmpty()) return null
        val subscriptions = recurring.count { it.categoryId == SUBSCRIPTION || it.previous != null }
        return Opportunity.PriceRises(rises, subscriptions)
    }

    fun smallCharges(recurring: List<RecurringCharge>): Opportunity.SmallCharges? {
        val small = recurring.filter { it.monthly.ore < SMALL_CHARGE_ORE }
        return if (small.size >= 2) Opportunity.SmallCharges(small) else null
    }

    /** At most the two biggest gaps, so one bad month does not fill the list. */
    fun overAverage(data: FlowData, kindOf: (CategoryId) -> CategoryKind): List<Opportunity.OverAverage> {
        val last = data.periods.last()
        val before = data.periods.filter { it < last }.takeLast(AVERAGE_MONTHS)
        if (before.size < MIN_MONTHS - 1) return emptyList()
        val now = data.month(last).expensesByCategory
        return now.filterKeys { kindOf(it) == CategoryKind.VARIABLE }.mapNotNull { (id, spent) ->
            val average = before.sumOf { data.month(it).expensesByCategory[id]?.ore ?: 0L } / before.size
            val gap = spent.ore - average
            if (average <= 0 || gap < OVER_MIN_ORE || gap.toDouble() / average * 100 < OVER_PERCENT) {
                null
            } else {
                Opportunity.OverAverage(id, last, spent, Money(average), before.size)
            }
        }.sortedByDescending { it.perYear.ore }.take(2)
    }
}
