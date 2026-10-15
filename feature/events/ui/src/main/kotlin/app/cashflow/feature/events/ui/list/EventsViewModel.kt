package app.cashflow.feature.events.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cashflow.core.domain.datasource.CategoryLocalDataSource
import app.cashflow.core.domain.datasource.ImportBatchLocalDataSource
import app.cashflow.core.domain.datasource.TransactionLocalDataSource
import app.cashflow.core.domain.model.Category
import app.cashflow.core.domain.model.CategoryId
import app.cashflow.core.domain.model.CategoryKind
import app.cashflow.core.domain.model.ImportBatch
import app.cashflow.core.domain.model.Period
import app.cashflow.core.domain.model.Transaction
import app.cashflow.core.domain.model.TransactionStatus
import app.cashflow.core.domain.model.TransferKind
import app.cashflow.core.domain.model.TxId
import app.cashflow.core.ui.UiText
import app.cashflow.core.ui.categoryName
import app.cashflow.core.ui.format.AppLocale
import app.cashflow.core.ui.format.PeriodFormatter
import app.cashflow.core.ui.groupName
import app.cashflow.core.ui.tone
import app.cashflow.feature.events.ui.R
import app.cashflow.feature.events.ui.common.TxDayUi
import app.cashflow.feature.events.ui.common.toRowUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The filters are the screen's own input; data comes from the data source flows. */
private data class Filters(
    val query: String = "",
    val status: StatusFilter = StatusFilter.ALL,
    val categoryId: CategoryId? = null,
    val period: Period? = null,
    val sheet: EventsSheet? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class EventsViewModel(
    transactions: TransactionLocalDataSource,
    categories: CategoryLocalDataSource,
    batches: ImportBatchLocalDataSource,
    initialCategory: String? = null,
    initialPeriod: String? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(EventsState())
    val state: StateFlow<EventsState> = _state.asStateFlow()

    private val _events = Channel<EventsEvent>()
    val events = _events.receiveAsFlow()

    private val filters = MutableStateFlow(
        Filters(
            categoryId = initialCategory?.let(::CategoryId),
            period = initialPeriod?.let(Period::parse),
        ),
    )
    private val locale = AppLocale.current()

    init {
        val searchHits = filters.map { it.query.trim() }
            .flatMapLatest { q -> if (q.isEmpty()) flowOf(null) else transactions.search(q) }

        combine(transactions.observeAll(), categories.observeCategories(), batches.observeBatches(), filters, searchHits) { txs, cats, imports, f, hits ->
            buildState(txs, cats.associateBy { it.id }, imports, f, hits)
        }.onEach { s -> _state.update { s } }.launchIn(viewModelScope)
    }

    fun onAction(action: EventsAction) {
        when (action) {
            is EventsAction.OnQueryChange -> filters.update { it.copy(query = action.query) }
            is EventsAction.OnStatusChipClick -> filters.update { it.copy(status = action.filter) }
            EventsAction.OnCategoryFilterClick -> filters.update { it.copy(sheet = EventsSheet.CATEGORY) }
            EventsAction.OnPeriodFilterClick -> filters.update { it.copy(sheet = EventsSheet.PERIOD) }
            is EventsAction.OnCategoryPicked -> filters.update { it.copy(categoryId = action.id?.let(::CategoryId), sheet = null) }
            is EventsAction.OnPeriodPicked -> filters.update { it.copy(period = action.key?.let(Period::parse), sheet = null) }
            EventsAction.OnSheetDismiss -> filters.update { it.copy(sheet = null) }
            is EventsAction.OnTransactionClick -> send(EventsEvent.NavigateToDetail(action.id))
            EventsAction.OnImportClick -> send(EventsEvent.OpenImport)
            EventsAction.OnReviewClick -> send(EventsEvent.OpenReview)
        }
    }

    private fun send(event: EventsEvent) {
        viewModelScope.launch { _events.send(event) }
    }

    private fun buildState(
        all: List<Transaction>,
        categories: Map<CategoryId, Category>,
        imports: List<ImportBatch>,
        f: Filters,
        hits: Set<TxId>?,
    ): EventsState {
        val inPeriod = all.filter { f.period == null || it.period == f.period }
        val rows = inPeriod
            .filter { hits == null || it.id in hits }
            .filter { f.categoryId == null || it.categoryId == f.categoryId }
            .filter { matches(it, f.status) }
            .sortedWith(compareByDescending<Transaction> { it.transactionDate }.thenByDescending { it.bookingDate }.thenByDescending { it.fileOrder })

        val days = rows.groupBy { it.transactionDate }.map { (date, txs) ->
            TxDayUi(
                key = date.toString(),
                heading = PeriodFormatter.dayHeading(date, all.maxOfOrNull { it.transactionDate }, locale),
                rows = txs.map { it.toRowUi(categories, locale) },
            )
        }

        val review = all.filter { it.status == TransactionStatus.TO_REVIEW }
        val periodLabel = f.period?.let { UiText.DynamicString(PeriodFormatter.month(it, locale)) } ?: UiText.StringResource(R.string.filter_all)
        val categoryLabel = f.categoryId?.let { id -> categoryName(id, categories[id]?.name ?: id.value) } ?: UiText.StringResource(R.string.filter_all)
        val counts = inPeriod.groupingBy { it.categoryId }.eachCount()
        val categoryOptions = listOf(CategoryOptionUi(null, UiText.StringResource(R.string.filter_all_categories), null, null, inPeriod.size, f.categoryId == null)) +
            categories.values
                .sortedWith(compareBy({ groupOrder(it.kind) }, { it.sortOrder }))
                .filter { counts.containsKey(it.id) || it.id == f.categoryId }
                .map { c ->
                    CategoryOptionUi(c.id.value, categoryName(c.id, c.name), c.kind.groupName(), c.kind.tone(), counts[c.id] ?: 0, c.id == f.categoryId)
                }
        val periods = all.map { it.period }.distinct().sortedDescending()
        val periodOptions = listOf(PeriodOptionUi(null, UiText.StringResource(R.string.filter_all_time), f.period == null)) +
            periods.map { p -> PeriodOptionUi(p.toString(), UiText.DynamicString(PeriodFormatter.month(p, locale)), p == f.period) }

        val latest = imports.maxByOrNull { it.importedAt }
        return EventsState(
            days = days,
            query = f.query,
            statusFilter = f.status,
            categoryLabel = categoryLabel,
            categoryActive = f.categoryId != null,
            periodLabel = periodLabel,
            periodActive = f.period != null,
            countText = UiText.PluralResource(R.plurals.transactions_count, rows.size),
            reviewCount = review.size,
            reviewPayees = review.map { it.normalizedText.uppercase() }.distinct().size,
            lastImport = latest?.let {
                LastImportUi(it.fileName, UiText.StringResource(R.string.last_import_meta, arrayOf(imports.size, all.map { t -> t.accountId }.distinct().size)))
            },
            hasTransactions = all.isNotEmpty(),
            sheet = f.sheet,
            categoryOptions = categoryOptions,
            periodOptions = periodOptions,
            isLoading = false,
        )
    }

    private fun matches(tx: Transaction, filter: StatusFilter) = when (filter) {
        StatusFilter.ALL -> true
        StatusFilter.REVIEW -> tx.status == TransactionStatus.TO_REVIEW
        StatusFilter.AUTO -> tx.status == TransactionStatus.AUTO && tx.transferKind == TransferKind.NONE
        StatusFilter.MANUAL -> tx.status == TransactionStatus.MANUAL
        StatusFilter.TRANSFER -> tx.transferKind != TransferKind.NONE && tx.status != TransactionStatus.TO_REVIEW
    }

    private fun groupOrder(kind: CategoryKind) = when (kind) {
        CategoryKind.UNCATEGORIZED -> 0
        CategoryKind.INCOME -> 1
        CategoryKind.FIXED -> 2
        CategoryKind.VARIABLE -> 3
        CategoryKind.SAVINGS -> 4
        CategoryKind.TRANSFER -> 5
    }
}
