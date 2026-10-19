package app.cashflow.feature.home.ui

import app.cashflow.core.designsystem.component.TrendDirection
import app.cashflow.core.domain.cashflow.FlowData
import app.cashflow.core.domain.model.CategorizedBy
import app.cashflow.core.domain.model.Category
import app.cashflow.core.domain.model.CategoryId
import app.cashflow.core.domain.model.CategoryKind
import app.cashflow.core.domain.model.Goal
import app.cashflow.core.domain.model.GoalType
import app.cashflow.core.domain.model.Goals
import app.cashflow.core.domain.model.Money
import app.cashflow.core.domain.model.MortgageSplit
import app.cashflow.core.domain.model.Transaction
import app.cashflow.core.domain.testing.Fixtures.tx
import app.cashflow.core.ui.format.AppLocale
import app.cashflow.core.ui.format.MoneyFormatter
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test

/** All figures are fictional. */
class HomeBuilderTest {

    private val categories = listOf(
        Category(CategoryId("lon"), "Lön", CategoryKind.INCOME),
        Category(CategoryId("boende"), "Boende", CategoryKind.FIXED),
        Category(CategoryId("mat"), "Mat", CategoryKind.VARIABLE),
        Category(CategoryId("fonder"), "Fonder", CategoryKind.SAVINGS),
    ).associateBy { it.id }

    private val rows = listOf(
        tx("1", date = "2026-08-25", kronor = 30000.0, category = CategoryId("lon"), categorizedBy = CategorizedBy.BUILT_IN_RULE),
        tx("2", date = "2026-08-01", kronor = -10000.0, category = CategoryId("boende"), categorizedBy = CategorizedBy.BUILT_IN_RULE),
        tx("3", date = "2026-08-10", kronor = -8000.0, category = CategoryId("mat"), categorizedBy = CategorizedBy.BUILT_IN_RULE),
        tx("4", date = "2026-09-25", kronor = 32000.0, category = CategoryId("lon"), categorizedBy = CategorizedBy.BUILT_IN_RULE),
        tx("5", date = "2026-09-01", kronor = -10000.0, category = CategoryId("boende"), categorizedBy = CategorizedBy.BUILT_IN_RULE),
        tx("6", date = "2026-09-10", kronor = -6000.0, category = CategoryId("mat"), categorizedBy = CategorizedBy.BUILT_IN_RULE),
        tx("7", date = "2026-09-26", kronor = -4000.0, category = CategoryId("fonder"), categorizedBy = CategorizedBy.BUILT_IN_RULE),
        tx("8", date = "2026-09-27", kronor = -500.0),
    )

    private fun input(txs: List<Transaction> = rows, buffer: Money? = null, goals: Goals = Goals(), today: String = "2026-10-05") = HomeInput(
        data = FlowData.of(txs, MortgageSplit(), { categories[it]?.kind ?: CategoryKind.UNCATEGORIZED }),
        transactions = txs, categories = categories, accounts = emptyList(), buffer = buffer, goals = goals,
        percentDecimals = false, amortizationAsSavings = false, batches = emptyList(), today = LocalDate.parse(today),
    )

    @Test
    fun `key figures follow the latest month with direction and good or bad`() {
        val state = HomeBuilder(AppLocale.SV).build(input(), null)
        val byId = state.kpis.associateBy { it.id }
        // Income up and expenses down are both good.
        assertThat(byId.getValue(KpiId.INCOME).trend).isEqualTo(TrendDirection.Up)
        assertThat(byId.getValue(KpiId.INCOME).trendGood).isEqualTo(true)
        assertThat(byId.getValue(KpiId.EXPENSES).trend).isEqualTo(TrendDirection.Down)
        assertThat(byId.getValue(KpiId.EXPENSES).trendGood).isEqualTo(true)
        // 4 000 of 32 000 saved is 12,5 %, above the default 10 % goal.
        assertThat(byId.getValue(KpiId.SAVINGS_RATE).goalReached).isTrue()
        assertThat(state.trends.map { it.id }).containsExactly(TrendId.NET, TrendId.INCOME, TrendId.EXPENSES, TrendId.SAVINGS_RATE)
    }

    @Test
    fun `stored goals change the goal bars`() {
        val state = HomeBuilder(AppLocale.SV).build(input(goals = Goals.of(listOf(Goal(GoalType.SAVINGS_RATE, 25.0)))), null)
        val rate = state.kpis.first { it.id == KpiId.SAVINGS_RATE }
        assertThat(rate.goalReached).isFalse()
        assertThat(rate.goal).isEqualTo((12.5 / 25.0).toFloat())
    }

    @Test
    fun `buffer recommends goal months of fixed expenses, with no goal bar`() {
        // Fixed costs are 10 000 kr a month, so the default 6 months recommends 60 000 kr.
        val buffer = HomeBuilder(AppLocale.SV).build(input(buffer = Money.kr(30000)), null).kpis.first { it.id == KpiId.BUFFER }
        assertThat(buffer.value).isEqualTo(MoneyFormatter.format(Money.kr(60000), locale = AppLocale.SV))
        assertThat(buffer.goal).isNull()
        assertThat(buffer.sub).isNotNull()
    }

    @Test
    fun `to do lists the review queue, and asks for an import when data is old`() {
        val fresh = HomeBuilder(AppLocale.SV).build(input(), null)
        assertThat(fresh.todo.map { it.kind }).containsExactly(TodoKind.REVIEW)
        val stale = HomeBuilder(AppLocale.SV).build(input(today = "2026-11-15"), null)
        assertThat(stale.todo.map { it.kind }).containsExactly(TodoKind.REVIEW, TodoKind.IMPORT)
        val done = HomeBuilder(AppLocale.SV).build(input(txs = rows.dropLast(1)), null)
        assertThat(done.todo.map { it.kind }).containsExactly(TodoKind.ALL_DONE)
    }

    @Test
    fun `a key figure sheet opens the matching Flöde sub-tab`() {
        val state = HomeBuilder(AppLocale.SV).build(input(), HomeSheet.EXPENSES)
        assertThat(state.sheet!!.target).isEqualTo(HomeTarget.Flow(TrendId.EXPENSES))
        assertThat(state.sheet!!.detail.figure).isEqualTo("16 500 kr")
    }
}
