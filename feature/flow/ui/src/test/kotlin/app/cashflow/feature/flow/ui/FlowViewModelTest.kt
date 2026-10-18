package app.cashflow.feature.flow.ui

import app.cashflow.core.domain.cashflow.PeriodMode
import app.cashflow.core.domain.model.AppSettings
import app.cashflow.core.domain.model.Category
import app.cashflow.core.domain.model.CategoryId
import app.cashflow.core.domain.model.CategoryKind
import app.cashflow.core.domain.testing.Fixtures.tx
import app.cashflow.core.domain.testing.InMemoryAccountDataSource
import app.cashflow.core.domain.testing.InMemoryCategoryDataSource
import app.cashflow.core.domain.testing.InMemorySettingsDataSource
import app.cashflow.core.domain.testing.InMemoryTransactionDataSource
import app.cashflow.core.ui.UiText
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Locale

/** All figures are fictional. */
@OptIn(ExperimentalCoroutinesApi::class)
class FlowViewModelTest {

    private val transactions = InMemoryTransactionDataSource()
    private val categories = InMemoryCategoryDataSource(
        listOf(
            Category(CategoryId("lon"), "Lön", CategoryKind.INCOME),
            Category(CategoryId("mat"), "Mat", CategoryKind.VARIABLE),
            Category(CategoryId("boende"), "Boende", CategoryKind.FIXED),
            Category(CategoryId("fonder"), "Fonder", CategoryKind.SAVINGS),
        ),
    )
    private val settings = InMemorySettingsDataSource()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        Locale.setDefault(Locale.forLanguageTag("sv-SE"))
        transactions.rows.update {
            listOf(
                tx("1", date = "2025-12-25", kronor = 20000.0, category = CategoryId("lon")),
                tx("2", date = "2025-12-26", kronor = -25000.0, category = CategoryId("boende")),
                tx("3", date = "2026-01-25", kronor = 21000.0, category = CategoryId("lon")),
                tx("4", date = "2026-01-26", kronor = -9000.0, category = CategoryId("boende")),
                tx("5", date = "2026-02-25", kronor = 22000.0, category = CategoryId("lon")),
                tx("6", date = "2026-02-26", kronor = -9000.0, category = CategoryId("boende")),
                tx("7", date = "2026-02-27", kronor = -3000.0, category = CategoryId("mat")),
                tx("8", date = "2026-02-28", kronor = -2000.0, category = CategoryId("fonder")),
            ).associateBy { it.id }
        }
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = FlowViewModel(transactions, categories, InMemoryAccountDataSource(), settings, UnconfinedTestDispatcher())

    private suspend fun FlowViewModel.awaitState(predicate: (FlowState) -> Boolean) = withTimeout(5_000) { state.first { it.hasData && predicate(it) } }

    @Test
    fun `opens on the latest month and steps back`() = runTest {
        val vm = vm()
        val feb = vm.awaitState { true }
        assertThat(feb.periodPill).isEqualTo("feb 2026")
        assertThat(feb.summaryValue).isEqualTo("+10 000 kr")
        assertThat(feb.barGroups.map { it.key }).containsExactly("2025-12", "2026-01", "2026-02")
        assertThat(feb.barGroups.first().deficit).isEqualTo(true)

        vm.onAction(FlowAction.OnPreviousPeriod)
        val jan = vm.awaitState { it.periodPill == "jan 2026" }
        assertThat(jan.summaryValue).isEqualTo("+12 000 kr")
    }

    @Test
    fun `tab and period are stored and restored`() = runTest {
        val vm = vm()
        vm.awaitState { true }
        vm.onAction(FlowAction.OnTabSelect(FlowTab.EXPENSE))
        vm.onAction(FlowAction.OnModeSelect(PeriodMode.YEAR))
        vm.onAction(FlowAction.OnPeriodSelect("2025"))
        vm.awaitState { it.tab == FlowTab.EXPENSE && it.periodPill == "2025" }
        val stored = settings.settings.first()
        assertThat(stored.lastTab).isEqualTo("expense")
        assertThat(stored.lastPeriod).isEqualTo("y:2025")

        val again = vm().awaitState { true }
        assertThat(again.tab).isEqualTo(FlowTab.EXPENSE)
        assertThat(again.mode).isEqualTo(PeriodMode.YEAR)
        assertThat(again.summaryValue).isEqualTo("25 000 kr")
    }

    @Test
    fun `alla shows a line and no bars, scrubbing moves the chart head`() = runTest {
        val vm = vm()
        vm.awaitState { true }
        vm.onAction(FlowAction.OnModeSelect(PeriodMode.ALL))
        val all = vm.awaitState { it.mode == PeriodMode.ALL }
        assertThat(all.periodPill).isNull()
        assertThat(all.barGroups).isEqualTo(emptyList())
        assertThat(all.line).isNotNull()
        assertThat(all.chartTitle).isEqualTo("feb 2026")
        vm.onAction(FlowAction.OnScrub(0))
        assertThat(vm.awaitState { it.chartTitle == "dec 2025" }.line?.selectedIndex).isEqualTo(0)
    }

    @Test
    fun `donut drills into expenses and a leaf opens its sheet`() = runTest {
        val vm = vm()
        val top = vm.awaitState { true }
        assertThat(top.donut?.segments?.map { it.id }).isEqualTo(listOf("utg", "spar", "kvar"))
        vm.onAction(FlowAction.OnDonutSegment("utg"))
        val utg = vm.awaitState { it.donut?.parentId == "utg" }
        assertThat(utg.donut?.segments?.map { it.id }).isEqualTo(listOf("fasta", "rorliga"))
        vm.onAction(FlowAction.OnDonutSegment("fasta"))
        vm.onAction(FlowAction.OnDonutSegment("boende"))
        val sheet = vm.awaitState { it.sheet != null }.sheet!!
        assertThat(sheet.figure).isEqualTo("−9 000 kr")
        assertThat(sheet.action).isEqualTo(SheetCta.Events("boende"))
    }

    @Test
    fun `a sheet button can switch sub-tab`() = runTest {
        val vm = vm()
        vm.awaitState { true }
        vm.onAction(FlowAction.OnInfoClick)
        assertThat(vm.awaitState { it.sheet != null }.sheet?.action).isEqualTo(SheetCta.Tab(FlowTab.SAVINGS))
        vm.onAction(FlowAction.OnSheetCta)
        val savings = vm.awaitState { it.tab == FlowTab.SAVINGS && it.sheet == null }
        assertThat(savings.categoryBars.map { it.id }).containsExactly("fonder")
        assertThat((savings.summaryLabel as UiText.StringResource).args.size).isEqualTo(2)
    }

    @Test
    fun `old or missing stored periods fall back to the latest month`() {
        assertThat(FlowViewModel.decode(null, null)).isEqualTo(FlowSelection())
        assertThat(FlowViewModel.decode("income", "2026-10")).isEqualTo(FlowSelection(FlowTab.INCOME, PeriodMode.MONTH))
        assertThat(FlowViewModel.decode("savings", "a")).isEqualTo(FlowSelection(FlowTab.SAVINGS, PeriodMode.ALL))
        assertThat(FlowViewModel.encode(FlowSelection(mode = PeriodMode.YEAR, year = 2025))).isEqualTo("y:2025")
        assertThat(AppSettings().lastPeriod).isNull()
    }
}
