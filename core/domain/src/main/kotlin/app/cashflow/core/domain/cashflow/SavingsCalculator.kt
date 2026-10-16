package app.cashflow.core.domain.cashflow

import app.cashflow.core.domain.model.Account
import app.cashflow.core.domain.model.Money
import app.cashflow.core.domain.model.Movement
import app.cashflow.core.domain.model.Transaction
import kotlinx.datetime.LocalDate

/** What one savings or investment account holds and earned. */
data class SavingsAccountSummary(
    val account: Account,
    /**
     * Deposits − withdrawals ± internal transfers + return − cost. For a savings account this
     * is its balance. Trades are left out, so for an ISK or KF it is the money put in plus
     * return; the market value is [Account.marketValue], which you enter.
     */
    val balance: Money,
    val deposits: Money,
    val withdrawals: Money,
    /** Interest, dividends and refunded tax in [year]. */
    val returnInYear: Money,
    /** Fees and taxes in [year], as a positive amount. */
    val costInYear: Money,
    val year: Int,
    val lastActivity: LocalDate?,
) {
    /** The best value we have: the market value you entered, else the balance. */
    val value: Money get() = account.marketValue ?: balance
}

/**
 * Return and cost of saving per account per year, and the balance of each savings account.
 * Everything here stays inside savings: none of it touches cash flow.
 */
class SavingsCalculator {

    fun summarize(accounts: List<Account>, transactions: List<Transaction>, year: Int): List<SavingsAccountSummary> {
        val byAccount = transactions.filter { !it.countsInCashFlow && !it.hidden }.groupBy { it.accountId }
        return accounts.filter { it.type.holdsSavings }.map { account -> summarize(account, byAccount[account.id].orEmpty(), year) }
    }

    private fun summarize(account: Account, rows: List<Transaction>, year: Int): SavingsAccountSummary {
        var balance = 0L
        var deposits = 0L
        var withdrawals = 0L
        var ret = 0L
        var cost = 0L
        for (tx in rows) {
            val ore = tx.amount.ore
            val inYear = tx.transactionDate.year == year
            when (tx.movement) {
                Movement.DEPOSIT -> deposits += ore
                Movement.WITHDRAWAL -> withdrawals -= ore
                Movement.RETURN -> if (inYear) ret += ore
                Movement.COST -> if (inYear) cost -= ore
                else -> Unit
            }
            if (tx.movement != Movement.TRADE) balance += ore
        }
        return SavingsAccountSummary(
            account = account,
            balance = Money(balance),
            deposits = Money(deposits),
            withdrawals = Money(withdrawals),
            returnInYear = Money(ret),
            costInYear = Money(cost),
            year = year,
            lastActivity = rows.maxOfOrNull { it.transactionDate },
        )
    }

    /** The household's own savings: every savings-like account except pension. */
    fun householdValue(summaries: List<SavingsAccountSummary>): Money =
        Money(summaries.filter { it.account.type.countsAsHouseholdSavings }.sumOf { it.value.ore })
}
