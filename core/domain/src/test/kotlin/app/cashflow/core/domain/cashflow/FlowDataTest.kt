package app.cashflow.core.domain.cashflow

import app.cashflow.core.domain.model.CategorizedBy
import app.cashflow.core.domain.model.CategoryId
import app.cashflow.core.domain.model.CategoryKind
import app.cashflow.core.domain.model.Money
import app.cashflow.core.domain.model.MortgageSplit
import app.cashflow.core.domain.model.Period
import app.cashflow.core.domain.testing.Fixtures.tx
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

/** Months summed into years and "Alla". All figures are fictional. */
class FlowDataTest {

    private val kindOf: (CategoryId) -> CategoryKind = { if (it.value == "lon") CategoryKind.INCOME else CategoryKind.VARIABLE }
    private val rows = listOf(
        tx("a", date = "2025-11-25", kronor = 30000.0, category = CategoryId("lon"), categorizedBy = CategorizedBy.BUILT_IN_RULE),
        tx("b", date = "2025-12-03", kronor = -1000.0, category = CategoryId("mat"), categorizedBy = CategorizedBy.BUILT_IN_RULE),
        tx("c", date = "2026-01-25", kronor = 31000.0, category = CategoryId("lon"), categorizedBy = CategorizedBy.BUILT_IN_RULE),
        tx("d", date = "2026-02-03", kronor = -2000.0, category = CategoryId("mat"), categorizedBy = CategorizedBy.BUILT_IN_RULE),
        tx("e", date = "2026-02-04", kronor = -500.0, countsInCashFlow = false),
    )
    private val data = FlowData.of(rows, MortgageSplit(), kindOf)

    @Test
    fun `months, years and all are built from the months with data`() {
        assertThat(data.periods).containsExactly(Period(2025, 11), Period(2025, 12), Period(2026, 1), Period(2026, 2))
        assertThat(data.scopes(PeriodMode.YEAR).map { it.key }).containsExactly("2025", "2026")
        assertThat(data.yearScope(2026).yearToDate).isEqualTo(true)
        assertThat(data.yearScope(2025).yearToDate).isEqualTo(false)

        val y2026 = data.sum(data.yearScope(2026))
        assertThat(y2026.income).isEqualTo(Money.kr(31000))
        assertThat(y2026.expensesByCategory[CategoryId("mat")]).isEqualTo(Money.kr(2000))
        assertThat(data.sum(data.allScope()).net).isEqualTo(Money.kr(58000))
    }

    @Test
    fun `previous scope and twelve months back`() {
        assertThat(data.previous(data.monthScope(Period(2026, 1)))?.key).isEqualTo("2025-12")
        assertThat(data.previous(data.yearScope(2026))?.key).isEqualTo("2025")
        assertThat(data.twelveMonthsTo(Period(2026, 1))).containsExactly(Period(2025, 11), Period(2025, 12), Period(2026, 1))
    }
}
