package app.cashflow.feature.flow.ui

import androidx.lifecycle.SavedStateHandle
import app.cashflow.core.domain.model.Category
import app.cashflow.core.domain.model.CategoryId
import app.cashflow.core.domain.model.CategoryKind
import app.cashflow.core.domain.testing.Fixtures.tx
import app.cashflow.core.domain.testing.InMemoryCategoryDataSource
import app.cashflow.core.domain.testing.InMemorySettingsDataSource
import app.cashflow.core.domain.testing.InMemoryTransactionDataSource
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import app.cash.turbine.test
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class FlowViewModelTest {

    private val transactions = InMemoryTransactionDataSource()
    private val categories = InMemoryCategoryDataSource(
        listOf(
            Category(CategoryId("lon"), "Lön", CategoryKind.INCOME),
            Category(CategoryId("mat"), "Mat", CategoryKind.VARIABLE),
            Category(CategoryId("boende"), "Boende", CategoryKind.FIXED),
        ),
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        Locale.setDefault(Locale.forLanguageTag("sv-SE"))
        transactions.rows.update {
            listOf(
                tx("1", date = "2026-04-25", kronor = 20000.0, category = CategoryId("lon")),
                tx("2", date = "2026-04-26", kronor = -9000.0, category = CategoryId("boende")),
                tx("3", date = "2026-05-25", kronor = 21000.0, category = CategoryId("lon")),
                tx("4", date = "2026-05-26", kronor = -9000.0, category = CategoryId("boende")),
                tx("5", date = "2026-05-27", kronor = -3000.0, category = CategoryId("mat")),
            ).associateBy { it.id }
        }
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `starts at the latest month with data and steps back`() = runTest {
        val vm = FlowViewModel(transactions, categories, InMemorySettingsDataSource(), SavedStateHandle())

        val may = vm.state.value
        assertThat(may.periodLabel).isEqualTo("maj 2026")
        assertThat(may.net).isEqualTo("+9 000 kr")
        assertThat(may.canGoForward).isFalse()
        assertThat(may.expenseBars.map { it.id }).containsExactly("boende", "mat")

        vm.onAction(FlowAction.OnPreviousPeriod)
        assertThat(vm.state.value.periodLabel).isEqualTo("apr 2026")
        assertThat(vm.state.value.canGoForward).isTrue()
    }

    @Test
    fun `a category bar opens Händelser for that month`() = runTest {
        val vm = FlowViewModel(transactions, categories, InMemorySettingsDataSource(), SavedStateHandle())
        vm.events.test {
            vm.onAction(FlowAction.OnCategoryClick("mat"))
            assertThat(awaitItem()).isEqualTo(FlowEvent.OpenCategory("mat", "2026-05"))
        }
    }

    @Test
    fun `the chosen month survives process death`() = runTest {
        val handle = SavedStateHandle()
        FlowViewModel(transactions, categories, InMemorySettingsDataSource(), handle).onAction(FlowAction.OnPreviousPeriod)
        val restored = FlowViewModel(transactions, categories, InMemorySettingsDataSource(), handle)
        assertThat(restored.state.value.periodLabel).isEqualTo("apr 2026")
    }

    @Test
    fun `no data shows the empty state`() = runTest {
        val vm = FlowViewModel(InMemoryTransactionDataSource(), categories, InMemorySettingsDataSource(), SavedStateHandle())
        assertThat(vm.state.value.hasData).isFalse()
        assertThat(vm.state.value.isLoading).isFalse()
    }
}
