package app.cashflow.core.domain.cashflow

import app.cashflow.core.domain.model.CategoryId
import app.cashflow.core.domain.model.CategoryKind
import app.cashflow.core.domain.model.Money
import app.cashflow.core.domain.model.MortgageSplit
import app.cashflow.core.domain.model.Period
import app.cashflow.core.domain.model.Transaction

/** Månad, År or Alla: one period control drives every Flöde sub-tab. */
enum class PeriodMode { MONTH, YEAR, ALL }

/**
 * A span of months: one month, a calendar year (to date when it is the current year), or every
 * month with data. [months] are the months with data inside it, oldest first.
 */
data class PeriodScope(
    val mode: PeriodMode,
    val key: String,
    val months: List<Period>,
    /** True for the current year while it is still running ("jan–okt 2026"). */
    val yearToDate: Boolean = false,
) {
    val last: Period get() = months.last()
    val first: Period get() = months.first()
    val monthCount: Int get() = months.size
}

/**
 * Every month's summary, computed once per change in the data; years and "Alla" are sums of
 * months. Rows are still never pre-aggregated in storage: this lives only in memory.
 */
class FlowData(
    val months: Map<Period, CashFlowSummary>,
) {
    /** Months with data, oldest first. */
    val periods: List<Period> = months.keys.sorted()
    val years: List<Int> = periods.map { it.year }.distinct()

    val isEmpty: Boolean get() = periods.isEmpty()

    fun month(period: Period): CashFlowSummary = months[period] ?: CashFlowSummary.empty(period)

    /** The sum of [periods]; its period is the last one. */
    fun sum(periods: List<Period>): CashFlowSummary = periods.map(::month).reduceOrNull(CashFlowSummary::plus)
        ?: CashFlowSummary.empty(periods.lastOrNull() ?: Period(1970, 1))

    fun sum(scope: PeriodScope): CashFlowSummary = sum(scope.months)

    fun monthScope(period: Period) = PeriodScope(PeriodMode.MONTH, period.toString(), listOf(period))

    fun yearScope(year: Int): PeriodScope {
        val inYear = periods.filter { it.year == year }
        val last = periods.lastOrNull()
        return PeriodScope(PeriodMode.YEAR, year.toString(), inYear, yearToDate = last != null && last.year == year && last.month < 12)
    }

    fun allScope() = PeriodScope(PeriodMode.ALL, "all", periods)

    /** The scopes of a mode in order, oldest first: the steps of the period stepper and the bars. */
    fun scopes(mode: PeriodMode): List<PeriodScope> = when (mode) {
        PeriodMode.MONTH -> periods.map(::monthScope)
        PeriodMode.YEAR -> years.map(::yearScope)
        PeriodMode.ALL -> if (periods.isEmpty()) emptyList() else listOf(allScope())
    }

    /** The 12 months up to and including [end] that have data. */
    fun twelveMonthsTo(end: Period): List<Period> = periods.filter { it <= end }.takeLast(12)

    /** The scope before [scope] in its mode, for "mot aug 2026". */
    fun previous(scope: PeriodScope): PeriodScope? {
        val list = scopes(scope.mode)
        val i = list.indexOfFirst { it.key == scope.key }
        return list.getOrNull(i - 1)
    }

    companion object {
        /** One pass over the rows: group by month, then summarize each month. */
        fun of(
            transactions: List<Transaction>,
            mortgage: MortgageSplit,
            kindOf: (CategoryId) -> CategoryKind,
            calculator: CashFlowCalculator = CashFlowCalculator(),
        ): FlowData = FlowData(
            transactions.asSequence()
                .filter { it.countsInCashFlow }
                .groupBy { it.period }
                .mapValues { (period, rows) -> calculator.summarize(period, rows, mortgage, kindOf) },
        )
    }
}

/** Two summaries added together; the period is the later one. */
operator fun CashFlowSummary.plus(other: CashFlowSummary): CashFlowSummary {
    fun merge(a: Map<CategoryId, Money>, b: Map<CategoryId, Money>) =
        (a.keys + b.keys).associateWith { Money((a[it]?.ore ?: 0) + (b[it]?.ore ?: 0)) }
    return CashFlowSummary(
        period = maxOf(period, other.period),
        income = income + other.income,
        expenses = expenses + other.expenses,
        fixedExpenses = fixedExpenses + other.fixedExpenses,
        variableExpenses = variableExpenses + other.variableExpenses,
        uncategorizedExpenses = uncategorizedExpenses + other.uncategorizedExpenses,
        savings = savings + other.savings,
        expensesByCategory = merge(expensesByCategory, other.expensesByCategory),
        incomeByCategory = merge(incomeByCategory, other.incomeByCategory),
        savingsByCategory = merge(savingsByCategory, other.savingsByCategory),
    )
}

fun CashFlowSummary.Companion.empty(period: Period) = CashFlowSummary(
    period, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, emptyMap(), emptyMap(), emptyMap(),
)
