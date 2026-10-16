package app.cashflow.core.domain.cashflow

import app.cashflow.core.domain.model.CategorizedBy
import app.cashflow.core.domain.model.CategoryId
import app.cashflow.core.domain.model.CategoryKind
import app.cashflow.core.domain.model.Money
import app.cashflow.core.domain.model.Period
import app.cashflow.core.domain.model.TransferKind
import app.cashflow.core.domain.testing.Fixtures.tx
import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

/** Committed fixture totals for a fictional household month (maj 2026). */
class CashFlowCalculatorTest {

    private val calculator = CashFlowCalculator()
    private val kinds = mapOf(
        "lon" to CategoryKind.INCOME,
        "mat" to CategoryKind.VARIABLE,
        "boende" to CategoryKind.FIXED,
        "fonder" to CategoryKind.SAVINGS,
        "sparuttag" to CategoryKind.SAVINGS,
        "overforing" to CategoryKind.TRANSFER,
        "okat" to CategoryKind.UNCATEGORIZED,
    )
    private val kindOf: (CategoryId) -> CategoryKind = { kinds.getValue(it.value) }
    private val may = Period(2026, 5)

    private val month = listOf(
        tx("1", kronor = 30000.0, category = CategoryId("lon"), text = "LÖN"),
        tx("2", kronor = -1200.0, category = CategoryId("mat")),
        tx("3", kronor = 200.0, category = CategoryId("mat"), text = "RETUR MATBUTIK"), // refund nets
        tx("4", kronor = -9000.0, category = CategoryId("boende")),
        tx("5", kronor = -3000.0, category = CategoryId("fonder"), text = "LYSA"),
        tx("6", kronor = -5000.0, category = CategoryId("overforing"), transferKind = TransferKind.INTERNAL),
        tx("7", account = "acc-b", kronor = 5000.0, category = CategoryId("overforing"), transferKind = TransferKind.INTERNAL),
        tx("8", kronor = -2000.0, category = CategoryId("sparande"), transferKind = TransferKind.TO_SAVINGS),
        tx("9", kronor = -350.0, category = CategoryId("okat")),
        tx("10", kronor = 999.0, category = CategoryId("fonder"), countsInCashFlow = false), // inside a savings account
        tx("11", date = "2026-06-01", transactionDate = "2026-05-30", kronor = -100.0, category = CategoryId("mat")), // spent in May
        tx("12", date = "2026-05-02", transactionDate = "2026-04-30", kronor = -77.0, category = CategoryId("mat")), // spent in April
    )

    @Test
    fun `sums only rows that count, with transfers neutral and savings separate`() {
        val s = calculator.summarize(may, month, kindOf)

        assertThat(s.income).isEqualTo(Money.kr(30000))
        assertThat(s.variableExpenses).isEqualTo(Money.kr(1100)) // 1200 − 200 refund + 100
        assertThat(s.fixedExpenses).isEqualTo(Money.kr(9000))
        assertThat(s.uncategorizedExpenses).isEqualTo(Money.kr(350))
        assertThat(s.expenses).isEqualTo(Money.kr(10450))
        assertThat(s.savings).isEqualTo(Money.kr(5000)) // 3000 by category + 2000 transfer to savings
        assertThat(s.net).isEqualTo(Money.kr(19550))
        assertThat(s.left).isEqualTo(Money.kr(14550))
        assertThat(s.expensesByCategory.getValue(CategoryId("mat"))).isEqualTo(Money.kr(1100))
    }

    @Test
    fun `a confirmed lone leg is neutral, an unconfirmed one counts by its category`() {
        val lone = tx("l", kronor = -4000.0, category = CategoryId("okat"), transferKind = TransferKind.LONE_LEG)
        assertThat(calculator.summarize(may, listOf(lone), kindOf).expenses).isEqualTo(Money.kr(4000))
        val confirmed = lone.copy(loneLegConfirmed = true, categoryId = CategoryId("overforing"), categorizedBy = CategorizedBy.TRANSFER)
        assertThat(calculator.summarize(may, listOf(confirmed), kindOf).expenses).isEqualTo(Money.ZERO)
    }

    @Test
    fun `moving cash by hand is neither income nor spending`() {
        val rows = listOf(
            // Cash moved between two own accounts, categorized by hand as a transfer.
            tx("m1", kronor = -3000.0, category = CategoryId("overforing"), categorizedBy = CategorizedBy.MANUAL),
            tx("m2", account = "acc-b", kronor = 3000.0, category = CategoryId("overforing"), categorizedBy = CategorizedBy.MANUAL),
            // Buffer money taken back to pay for something: negative savings, not income.
            tx("b1", kronor = 5000.0, category = CategoryId("sparuttag"), categorizedBy = CategorizedBy.MANUAL),
            tx("e1", kronor = -5000.0, category = CategoryId("mat")),
        )
        val s = calculator.summarize(may, rows, kindOf)
        assertThat(s.income).isEqualTo(Money.ZERO)
        assertThat(s.expenses).isEqualTo(Money.kr(5000))
        assertThat(s.savings).isEqualTo(Money.kr(-5000))
        assertThat(s.left).isEqualTo(Money.ZERO)
    }

    @Test
    fun `the default period is the latest month with data`() {
        assertThat(calculator.latestPeriod(month)).isEqualTo(Period(2026, 5))
        assertThat(calculator.periodsWithData(month)).isEqualTo(listOf(Period(2026, 4), Period(2026, 5)))
    }
}
