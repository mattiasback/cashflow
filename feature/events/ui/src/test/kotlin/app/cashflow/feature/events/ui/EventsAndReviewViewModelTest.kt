package app.cashflow.feature.events.ui

import app.cash.turbine.test
import app.cashflow.core.categorization.BundledCatalog
import app.cashflow.core.categorization.Categorizer
import app.cashflow.core.categorization.PayeeNormalizer
import app.cashflow.core.domain.model.CategorizedBy
import app.cashflow.core.domain.model.CategoryId
import app.cashflow.core.domain.model.TransferKind
import app.cashflow.core.domain.model.TxId
import app.cashflow.core.domain.testing.Fixtures.tx
import app.cashflow.core.domain.testing.InMemoryAccountDataSource
import app.cashflow.core.domain.testing.InMemoryCategoryDataSource
import app.cashflow.core.domain.testing.InMemoryImportBatchDataSource
import app.cashflow.core.domain.testing.InMemoryMerchantMemoryDataSource
import app.cashflow.core.domain.testing.InMemoryRuleDataSource
import app.cashflow.core.domain.testing.InMemoryTransactionDataSource
import app.cashflow.core.importengine.CategorizeUseCase
import app.cashflow.core.importengine.ReclassifyUseCase
import app.cashflow.core.importengine.TransferMatcher
import app.cashflow.feature.events.ui.list.EventsAction
import app.cashflow.feature.events.ui.list.EventsEvent
import app.cashflow.feature.events.ui.list.EventsViewModel
import app.cashflow.feature.events.ui.list.StatusFilter
import app.cashflow.feature.events.ui.review.ReviewAction
import app.cashflow.feature.events.ui.review.ReviewViewModel
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class EventsAndReviewViewModelTest {

    private val transactions = InMemoryTransactionDataSource()
    private val categories = InMemoryCategoryDataSource()
    private val batches = InMemoryImportBatchDataSource()
    private val memories = InMemoryMerchantMemoryDataSource()
    private val catalog = BundledCatalog.load()
    private val normalizer = PayeeNormalizer()
    private val categorize = CategorizeUseCase(
        transactions, memories, normalizer,
        ReclassifyUseCase(transactions, InMemoryAccountDataSource(), memories, InMemoryRuleDataSource(), catalog, TransferMatcher(), Categorizer(normalizer)),
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        Locale.setDefault(Locale.forLanguageTag("sv-SE"))
        runBlocking { categories.ensureDefaults(catalog.defaultCategories()) }
        transactions.rows.update {
            listOf(
                tx("a", date = "2026-05-02", kronor = -120.0, text = "Swish till Vän A"),
                tx("b", date = "2026-05-09", kronor = -80.0, text = "Swish till Vän A"),
                tx("c", date = "2026-05-10", kronor = -45.0, text = "OKÄND HANDLARE"),
                tx("d", date = "2026-05-11", kronor = -400.0, text = "ICA EXEMPEL", category = CategoryId("mat"), categorizedBy = CategorizedBy.BUILT_IN_RULE),
                tx("e", date = "2026-05-12", kronor = -5000.0, text = "Överföring", category = CategoryId("overforing"), categorizedBy = CategorizedBy.TRANSFER, transferKind = TransferKind.INTERNAL),
                tx("f", account = "acc-b", date = "2026-05-12", kronor = 5000.0, text = "Överföring", category = CategoryId("overforing"), categorizedBy = CategorizedBy.TRANSFER, transferKind = TransferKind.INTERNAL),
            ).associateBy { it.id }
        }
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `events groups by day newest first and filters by status`() = runTest {
        val vm = EventsViewModel(transactions, categories, batches)
        assertThat(vm.state.value.days.map { it.key }).containsExactly("2026-05-12", "2026-05-11", "2026-05-10", "2026-05-09", "2026-05-02")
        assertThat(vm.state.value.reviewCount).isEqualTo(3)

        vm.onAction(EventsAction.OnStatusChipClick(StatusFilter.REVIEW))
        assertThat(vm.state.value.days.flatMap { r -> r.rows.map { it.id } }).containsExactly("c", "b", "a")
    }

    @Test
    fun `events starts filtered when opened from a flow category`() = runTest {
        val vm = EventsViewModel(transactions, categories, batches, initialCategory = "mat", initialPeriod = "2026-05")
        assertThat(vm.state.value.days.flatMap { d -> d.rows.map { it.id } }).containsExactly("d")
        assertThat(vm.state.value.categoryActive).isTrue()
    }

    @Test
    fun `tapping a row navigates to its detail`() = runTest {
        val vm = EventsViewModel(transactions, categories, batches)
        vm.events.test {
            vm.onAction(EventsAction.OnTransactionClick("d"))
            assertThat(awaitItem()).isEqualTo(EventsEvent.NavigateToDetail("d"))
        }
    }

    @Test
    fun `review groups by payee, most rows first, and one approval clears the group`() = runTest {
        val vm = ReviewViewModel(transactions, categories, categorize)
        val groups = vm.state.value.groups
        assertThat(groups.map { it.title }).containsExactly("Swish till Vän A", "OKÄND HANDLARE")

        val key = groups.first().key
        vm.onAction(ReviewAction.OnCategorySelect(key, "noje"))
        vm.onAction(ReviewAction.OnApprove(key))

        assertThat(vm.state.value.groups).hasSize(1)
        assertThat(transactions.rows.value.getValue(TxId("a")).categoryId).isEqualTo(CategoryId("noje"))
        assertThat(transactions.rows.value.getValue(TxId("b")).categorizedBy).isEqualTo(CategorizedBy.MEMORY)
    }
}
