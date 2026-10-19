package app.cashflow.core.domain.cashflow

import app.cashflow.core.domain.model.Account
import app.cashflow.core.domain.model.AccountType
import app.cashflow.core.domain.model.Money
import app.cashflow.core.domain.model.Transaction

/**
 * The household buffer: what the savings accounts hold, measured in months of fixed expenses.
 * Hem's Buffert KPI and Flöde's Buffert tile read the same numbers.
 */
object Buffer {

    /** Sum of the savings accounts' value (entered value, else the imported balance); null without any. */
    fun value(accounts: List<Account>, transactions: List<Transaction>, year: Int): Money? {
        val savings = accounts.filter { it.type == AccountType.SAVINGS }
        if (savings.isEmpty()) return null
        return Money(SavingsCalculator().summarize(savings, transactions, year).sumOf { it.value.ore })
    }

    /** Months of fixed expenses the buffer covers; null when there is no buffer or no fixed costs yet. */
    fun months(value: Money?, fixedPerMonth: Money): Double? {
        if (value == null || value.ore <= 0 || fixedPerMonth.ore <= 0) return null
        return value.ore.toDouble() / fixedPerMonth.ore
    }
}
