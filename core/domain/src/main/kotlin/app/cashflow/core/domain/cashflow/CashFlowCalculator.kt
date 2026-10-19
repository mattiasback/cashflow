package app.cashflow.core.domain.cashflow

import app.cashflow.core.domain.model.CategoryId
import app.cashflow.core.domain.model.CategoryKind
import app.cashflow.core.domain.model.Money
import app.cashflow.core.domain.model.MortgageSplit
import app.cashflow.core.domain.model.Period
import app.cashflow.core.domain.model.Transaction
import app.cashflow.core.domain.model.TransferKind

/** Totals for one period. All amounts are positive magnitudes except [net] and [left]. */
data class CashFlowSummary(
    val period: Period,
    val income: Money,
    val expenses: Money,
    val fixedExpenses: Money,
    val variableExpenses: Money,
    val uncategorizedExpenses: Money,
    val savings: Money,
    /** Expense per category, positive = money out. Refunds net against their category. */
    val expensesByCategory: Map<CategoryId, Money>,
    val incomeByCategory: Map<CategoryId, Money>,
    val savingsByCategory: Map<CategoryId, Money>,
) {
    /** Nettokassaflöde = inkomster − utgifter. */
    val net: Money get() = income - expenses

    /** Kvar på kontot = netto − sparande. */
    val left: Money get() = net - savings

    /** Share of income saved, 0..100; null when there is no income. */
    val savingsRatePercent: Double?
        get() = if (income.ore <= 0) null else savings.ore.toDouble() / income.ore * 100.0

    companion object
}

/**
 * Sums only rows that count in cash flow and are not hidden. Rows are never pre-aggregated,
 * so rule changes, transfer re-matches, the mortgage split and undo always give consistent
 * totals.
 */
class CashFlowCalculator {

    fun summarize(
        period: Period,
        transactions: List<Transaction>,
        mortgage: MortgageSplit = MortgageSplit(),
        kindOf: (CategoryId) -> CategoryKind,
    ): CashFlowSummary {
        val income = HashMap<CategoryId, Long>()
        val expense = HashMap<CategoryId, Long>()
        val savings = HashMap<CategoryId, Long>()
        var fixed = 0L
        var variable = 0L
        var uncategorized = 0L

        for (tx in transactions) {
            if (!tx.countsInCashFlow || tx.hidden || tx.period != period) continue
            val ore = tx.amount.ore
            when (tx.transferKind) {
                TransferKind.INTERNAL -> continue
                TransferKind.LONE_LEG -> if (tx.loneLegConfirmed) continue
                TransferKind.TO_SAVINGS, TransferKind.FROM_SAVINGS -> {
                    // Set to Överföring by hand: just moving money (e.g. into the buffer and back), not saving.
                    if (kindOf(tx.categoryId) != CategoryKind.TRANSFER) savings.merge(tx.categoryId, -ore, Long::plus)
                    continue
                }
                TransferKind.NONE -> Unit
            }
            when (val kind = kindOf(tx.categoryId)) {
                CategoryKind.INCOME -> income.merge(tx.categoryId, ore, Long::plus)
                CategoryKind.FIXED, CategoryKind.VARIABLE -> {
                    // A split mortgage payment is part interest (this expense) and part amortization (savings).
                    val amortization = if (ore < 0 && mortgage.matches(tx.rawText)) mortgage.amortizationOf(Money(-ore)).ore else 0L
                    if (amortization > 0) savings.merge(CategoryId.AMORTIZATION, amortization, Long::plus)
                    val spent = -ore - amortization
                    expense.merge(tx.categoryId, spent, Long::plus)
                    if (kind == CategoryKind.FIXED) fixed += spent else variable += spent
                }
                CategoryKind.SAVINGS -> savings.merge(tx.categoryId, -ore, Long::plus)
                CategoryKind.TRANSFER, CategoryKind.INVESTMENT -> Unit
                CategoryKind.UNCATEGORIZED -> {
                    // Unreviewed money in counts by its sign until someone decides.
                    if (ore > 0) {
                        income.merge(tx.categoryId, ore, Long::plus)
                    } else {
                        expense.merge(tx.categoryId, -ore, Long::plus)
                        uncategorized -= ore
                    }
                }
            }
        }

        return CashFlowSummary(
            period = period,
            income = Money(income.values.sum()),
            expenses = Money(fixed + variable + uncategorized),
            fixedExpenses = Money(fixed),
            variableExpenses = Money(variable),
            uncategorizedExpenses = Money(uncategorized),
            savings = Money(savings.values.sum()),
            expensesByCategory = expense.mapValues { Money(it.value) },
            incomeByCategory = income.mapValues { Money(it.value) },
            savingsByCategory = savings.mapValues { Money(it.value) },
        )
    }

    /** The default period is the latest month with data, not the calendar month. */
    fun latestPeriod(transactions: List<Transaction>): Period? =
        transactions.asSequence().filter { it.countsInCashFlow }.maxOfOrNull { it.transactionDate }?.let(Period::of)

    fun periodsWithData(transactions: List<Transaction>): List<Period> =
        transactions.asSequence().filter { it.countsInCashFlow }.map { it.period }.distinct().sorted().toList()
}
