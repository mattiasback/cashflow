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
        val each = accounts(accounts, transactions, year)
        if (each.isEmpty()) return null
        return Money(each.sumOf { it.second.ore })
    }

    /** Each savings account with its value: the entered value, else its imported balance. */
    fun accounts(accounts: List<Account>, transactions: List<Transaction>, year: Int): List<Pair<Account, Money>> {
        val savings = accounts.filter { it.type == AccountType.SAVINGS }
        return SavingsCalculator().summarize(savings, transactions, year).map { it.account to it.value }
    }

    /** Months of fixed expenses the buffer covers; null when there is no buffer or no fixed costs yet. */
    fun months(value: Money?, fixedPerMonth: Money): Double? {
        if (value == null || value.ore <= 0 || fixedPerMonth.ore <= 0) return null
        return value.ore.toDouble() / fixedPerMonth.ore
    }
}
