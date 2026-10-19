package app.cashflow.core.domain.cashflow

import app.cashflow.core.domain.model.Account
import app.cashflow.core.domain.model.AccountId
import app.cashflow.core.domain.model.AccountType
import app.cashflow.core.domain.model.CategorizedBy
import app.cashflow.core.domain.model.CategoryId
import app.cashflow.core.domain.model.CategoryKind
import app.cashflow.core.domain.model.Money
import app.cashflow.core.domain.model.MortgageSplit
import app.cashflow.core.domain.model.MortgageSplitMode
import app.cashflow.core.domain.model.Movement
import app.cashflow.core.domain.model.Period
import app.cashflow.core.domain.model.TransferKind
import app.cashflow.core.domain.testing.Fixtures.tx
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test

/** Hidden rows, the mortgage split and savings accounts. All figures are fictional. */
class Step2CalculatorTest {

    private val calculator = CashFlowCalculator()
    private val may = Period(2026, 5)
    private val kindOf: (CategoryId) -> CategoryKind = { id ->
        when (id.value) {
            "lon" -> CategoryKind.INCOME
            "boende" -> CategoryKind.FIXED
            "mat" -> CategoryKind.VARIABLE
            "amortering" -> CategoryKind.SAVINGS
            else -> CategoryKind.UNCATEGORIZED
        }
    }
    private val salary = tx("s", kronor = 30000.0, text = "LÖN", category = CategoryId("lon"), categorizedBy = CategorizedBy.BUILT_IN_RULE)
    private val mortgage = tx("m", kronor = -9000.0, text = "BOLÅN 1234", category = CategoryId.HOUSING, categorizedBy = CategorizedBy.BUILT_IN_RULE)
    private val food = tx("f", kronor = -2000.0, text = "MATBUTIK", category = CategoryId("mat"), categorizedBy = CategorizedBy.BUILT_IN_RULE)

    @Test
    fun `a savings transfer set to Överföring by hand is not counted as saving`() {
        val toBuffer = tx("b", kronor = -1000.0, category = CategoryId.BUFFER, categorizedBy = CategorizedBy.TRANSFER, transferKind = TransferKind.TO_SAVINGS)
        val kinds: (CategoryId) -> CategoryKind = { if (it == CategoryId.TRANSFER) CategoryKind.TRANSFER else CategoryKind.SAVINGS }
        assertThat(calculator.summarize(may, listOf(toBuffer), kindOf = kinds).savings).isEqualTo(Money.kr(1000))
        val moved = toBuffer.copy(categoryId = CategoryId.TRANSFER, categorizedBy = CategorizedBy.MANUAL)
        assertThat(calculator.summarize(may, listOf(moved), kindOf = kinds).savings).isEqualTo(Money.ZERO)
    }

    @Test
    fun `a hidden row leaves every total`() {
        val hidden = food.copy(hidden = true)
        val s = calculator.summarize(may, listOf(salary, hidden), kindOf = kindOf)
        assertThat(s.expenses).isEqualTo(Money.ZERO)
    }

    @Test
    fun `a fixed amortization splits each mortgage payment and off restores the totals`() {
        val rows = listOf(salary, mortgage, food)
        val plain = calculator.summarize(may, rows, kindOf = kindOf)
        val split = calculator.summarize(may, rows, MortgageSplit(enabled = true, amortization = Money.kr(2500)), kindOf)
        val off = calculator.summarize(may, rows, MortgageSplit(enabled = false, amortization = Money.kr(2500)), kindOf)

        assertThat(split.expensesByCategory[CategoryId.HOUSING]).isEqualTo(Money.kr(6500))
        assertThat(split.savingsByCategory[CategoryId.AMORTIZATION]).isEqualTo(Money.kr(2500))
        assertThat(split.fixedExpenses).isEqualTo(plain.fixedExpenses - Money.kr(2500))
        assertThat(split.left).isEqualTo(plain.left)
        assertThat(off).isEqualTo(plain)
    }

    @Test
    fun `an interest share splits the rest into amortization`() {
        val s = calculator.summarize(
            may,
            listOf(salary, mortgage),
            MortgageSplit(enabled = true, mode = MortgageSplitMode.INTEREST_SHARE, interestSharePercent = 40),
            kindOf,
        )
        assertThat(s.expensesByCategory[CategoryId.HOUSING]).isEqualTo(Money.kr(3600))
        assertThat(s.savingsByCategory[CategoryId.AMORTIZATION]).isEqualTo(Money.kr(5400))
    }

    @Test
    fun `savings accounts sum deposits, return and cost but not trades`() {
        val isk = Account(AccountId("isk"), "ISK Exempel", AccountType.ISK)
        val pension = Account(AccountId("pen"), "Tjänstepension", AccountType.PENSION)
        val rows = listOf(
            tx("d", "isk", "2026-02-01", 10000.0, countsInCashFlow = false, movement = Movement.DEPOSIT),
            tx("b", "isk", "2026-02-02", -9000.0, countsInCashFlow = false, movement = Movement.TRADE),
            tx("r", "isk", "2026-06-01", 120.0, countsInCashFlow = false, movement = Movement.RETURN),
            tx("c", "isk", "2026-06-01", -30.0, countsInCashFlow = false, movement = Movement.COST),
            tx("old", "isk", "2025-06-01", 50.0, countsInCashFlow = false, movement = Movement.RETURN),
            tx("p", "pen", "2026-03-01", 4000.0, countsInCashFlow = false, movement = Movement.DEPOSIT),
        )
        val savings = SavingsCalculator()
        val summaries = savings.summarize(listOf(isk, pension), rows, 2026)
        val iskSummary = summaries.first { it.account.id == isk.id }

        assertThat(iskSummary.balance).isEqualTo(Money.kr(10140))
        assertThat(iskSummary.returnInYear).isEqualTo(Money.kr(120))
        assertThat(iskSummary.costInYear).isEqualTo(Money.kr(30))
        // Pension is the employer's money, not the household's own savings.
        assertThat(savings.householdValue(summaries)).isEqualTo(Money.kr(10140))
        assertThat(summaries.first { it.account.id == pension.id }.account.marketValue).isNull()
    }
}
