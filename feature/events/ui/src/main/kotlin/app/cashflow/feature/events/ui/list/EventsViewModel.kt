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
import app.cashflow.core.ui.retryOn
import app.cashflow.core.ui.withDebugFlags
import app.cashflow.core.domain.model.DebugFlags
import kotlinx.coroutines.flow.MutableSharedFlow
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
    /** A whole FlÃ¶de group (Inkomster, Utgifter, Sparande) instead of one category. */
    val group: CategoryGroup? = null,
    val period: Period? = null,
    val sheet: EventsSheet? = null,
)

/** Category groups HÃ¤ndelser can filter on, keyed "group:income" etc. in navigation and the picker. */
enum class CategoryGroup(val key: String, val kinds: Set<CategoryKind>) {
    INCOME("group:income", setOf(CategoryKind.INCOME)),
    EXPENSES("group:expense", setOf(CategoryKind.FIXED, CategoryKind.VARIABLE, CategoryKind.UNCATEGORIZED)),
    SAVINGS("group:savings", setOf(CategoryKind.SAVINGS)),
    ;

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class EventsViewModel(
    transactions: TransactionLocalDataSource,
    categories: CategoryLocalDataSource,
    batches: ImportBatchLocalDataSource,
    initialCategory: String? = null,
    initialPeriod: String? = null,
    // Debug builds: Profil › Utvecklare can slow or fail this tab's reads.
    debug: DebugFlags = DebugFlags(),
) : ViewModel() {

    private val _state = MutableStateFlow(EventsState())
    val state: StateFlow<EventsState> = _state.asStateFlow()

    private val _events = Channel<EventsEvent>()
    val events = _events.receiveAsFlow()

    private val filters = MutableStateFlow(
        Filters(
            categoryId = initialCategory?.takeIf { CategoryGroup.of(it) == null }?.let(::CategoryId),
            group = CategoryGroup.of(initialCategory),
            period = initialPeriod?.let(Period::parse),
        ),
    )
    private val locale = AppLocale.current()

    private val retries = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    init {
        val searchHits = filters.map { it.query.trim() }
            .flatMapLatest { q -> if (q.isEmpty()) flowOf(null) else transactions.search(q) }

        combine(transactions.observeAll(), categories.observeCategories(), batches.observeBatches(), filters, searchHits) { txs, cats, imports, f, hits ->
            buildState(txs, cats.associateBy { it.id }, imports, f, hits)
        }
            .withDebugFlags(debug)
            .retryOn(retries, onError = { _state.update { it.copy(isLoading = false, loadError = true, days = emptyList()) } }, onRetry = { _state.update { it.copy(isLoading = true, loadError = false) } })
            .onEach { s -> _state.update { s } }.launchIn(viewModelScope)
    }

    fun onAction(action: EventsAction) {
        when (action) {
            EventsAction.OnRetry -> retries.tryEmit(Unit)
            is EventsAction.OnQueryChange -> filters.update { it.copy(query = action.query) }
            is EventsAction.OnStatusChipClick -> filters.update { it.copy(status = action.filter) }
            EventsAction.OnCategoryFilterClick -> filters.update { it.copy(sheet = EventsSheet.CATEGORY) }
            EventsAction.OnPeriodFilterClick -> filters.update { it.copy(sheet = EventsSheet.PERIOD) }
            is EventsAction.OnCategoryPicked -> filters.update {
                it.copy(categoryId = action.id?.takeIf { id -> CategoryGroup.of(id) == null }?.let(::CategoryId), group = CategoryGroup.of(action.id), sheet = null)
            }
            is EventsAction.OnPeriodPicked -> filters.update { it.copy(period = action.key?.let(Period::parse), sheet = null) }
            EventsAction.OnSheetDismiss -> filters.update { it.copy(sheet = null) }
            is EventsAction.OnTransactionClick -> send(EventsEvent.NavigateToDetail(action.id))
            EventsAction.OnImportClick -> send(EventsEvent.OpenImport)
            EventsAction.OnReviewClick -> send(EventsEvent.OpenReview)
            EventsAction.OnProfileClick -> send(EventsEvent.OpenProfile)
        }
    }

    private fun send(event: EventsEvent) {
        viewModelScope.launch { _events.send(event) }
    }

    private fun buildState(
        everything: List<Transaction>,
        categories: Map<CategoryId, Category>,
        imports: List<ImportBatch>,
        f: Filters,
        hits: Set<TxId>?,
    ): EventsState {
        // Händelser is the money flow: bank rows, plus savings rows only while they need a decision
        // ("Möjlig dubblett"). Everything inside savings is listed per account instead.
        val all = everything.filter { it.countsInCashFlow || it.status == TransactionStatus.TO_REVIEW }
        val inPeriod = all.filter { f.period == null || it.period == f.period }
        val rows = inPeriod
            .filter { hits == null || it.id in hits }
            .filter { f.categoryId == null || it.categoryId == f.categoryId }
            .filter { f.group == null || (categories[it.categoryId]?.kind ?: CategoryKind.UNCATEGORIZED) in f.group.kinds }
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
        val categoryLabel = f.group?.let { groupLabel(it) }
            ?: f.categoryId?.let { id -> categoryName(id, categories[id]?.name ?: id.value) }
            ?: UiText.StringResource(R.string.filter_all)
        val counts = inPeriod.groupingBy { it.categoryId }.eachCount()
        val categoryOptions = listOf(CategoryOptionUi(null, UiText.StringResource(R.string.filter_all_categories), null, null, inPeriod.size, f.categoryId == null && f.group == null)) +
            CategoryGroup.entries.map { g ->
                CategoryOptionUi(g.key, groupLabel(g), null, null, inPeriod.count { (categories[it.categoryId]?.kind ?: CategoryKind.UNCATEGORIZED) in g.kinds }, f.group == g)
            } +
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
            categoryActive = f.categoryId != null || f.group != null,
            periodLabel = periodLabel,
            periodActive = f.period != null,
            countText = UiText.PluralResource(R.plurals.transactions_count, rows.size),
            reviewCount = review.size,
            reviewPayees = review.map { it.normalizedText.uppercase() }.distinct().size,
            lastImport = latest?.let {
                LastImportUi(it.fileName, UiText.StringResource(R.string.last_import_meta, arrayOf(imports.size, everything.map { t -> t.accountId }.distinct().size)))
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
        StatusFilter.AUTO -> tx.status == TransactionStatus.AUTO
        StatusFilter.MANUAL -> tx.status == TransactionStatus.MANUAL
    }

    private fun groupLabel(g: CategoryGroup): UiText = UiText.StringResource(
        when (g) {
            CategoryGroup.INCOME -> R.string.filter_group_income
            CategoryGroup.EXPENSES -> R.string.filter_group_expenses
            CategoryGroup.SAVINGS -> R.string.filter_group_savings
        },
    )

    private fun groupOrder(kind: CategoryKind) = when (kind) {
        CategoryKind.UNCATEGORIZED -> 0
        CategoryKind.INCOME -> 1
        CategoryKind.FIXED -> 2
        CategoryKind.VARIABLE -> 3
        CategoryKind.SAVINGS -> 4
        CategoryKind.TRANSFER -> 5
        CategoryKind.INVESTMENT -> 6
    }
}
