package app.cashflow.feature.flow.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cashflow.core.domain.cashflow.Buffer
import app.cashflow.core.domain.cashflow.FlowData
import app.cashflow.core.domain.cashflow.PeriodMode
import app.cashflow.core.domain.datasource.AccountLocalDataSource
import app.cashflow.core.domain.datasource.CategoryLocalDataSource
import app.cashflow.core.domain.datasource.GoalLocalDataSource
import app.cashflow.core.domain.datasource.SettingsDataSource
import app.cashflow.core.domain.datasource.TransactionLocalDataSource
import app.cashflow.core.domain.model.CategoryKind
import app.cashflow.core.domain.model.Goals
import app.cashflow.core.domain.model.Period
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Flöde: one period control (Månad / År / Alla) drives four sub-tabs. Month sums are computed
 * once per data change; switching tab or period only re-reads them. The chosen tab and period
 * are kept in settings so the app opens where it was left.
 */
class FlowViewModel(
    transactions: TransactionLocalDataSource,
    categories: CategoryLocalDataSource,
    accounts: AccountLocalDataSource,
    private val settings: SettingsDataSource,
    goals: GoalLocalDataSource,
    // Month sums are computed off the main thread; tests pass their own dispatcher.
    compute: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val builder = FlowBuilder()

    private val _state = MutableStateFlow(FlowState())
    val state: StateFlow<FlowState> = _state.asStateFlow()

    private val _events = Channel<FlowEvent>()
    val events = _events.receiveAsFlow()

    /** null until the stored tab and period are read. */
    private val selection = MutableStateFlow<FlowSelection?>(null)
    private var input: FlowInput? = null

    init {
        // The stored tab and period, also when Hem changes them (a trend row opens a sub-tab).
        settings.settings.map { it.lastTab to it.lastPeriod }.distinctUntilChanged().onEach { (tab, period) ->
            val stored = decode(tab, period)
            selection.update { sel ->
                if (sel == null) stored
                else if (sel.tab == stored.tab && encode(sel) == encode(stored)) sel
                else sel.copy(tab = stored.tab, mode = stored.mode, month = stored.month, year = stored.year, path = emptyList(), sheet = null)
            }
        }.launchIn(viewModelScope)
        val model = combine(
            transactions.observeAll(),
            categories.observeCategories(),
            accounts.observeAccounts(),
            settings.settings.map { it.mortgage to it.percentDecimals }.distinctUntilChanged(),
            goals.observeGoals(),
        ) { txs, cats, accs, (mortgage, decimals), goalList ->
            val byId = cats.associateBy { it.id }
            val data = FlowData.of(txs, mortgage, { byId[it]?.kind ?: CategoryKind.UNCATEGORIZED })
            val buffer = Buffer.value(accs, txs, year = data.periods.lastOrNull()?.year ?: 0)
            FlowInput(data, txs, byId, buffer, decimals, Goals.of(goalList))
        }.flowOn(compute)

        combine(model, selection.filterNotNull()) { i, sel ->
            input = i
            builder.build(i, sel)
        }.flowOn(compute)
            .onEach { s -> _state.value = s }
            .launchIn(viewModelScope)
    }

    fun onAction(action: FlowAction) {
        when (action) {
            is FlowAction.OnTabSelect -> select { it.copy(tab = action.tab, path = emptyList(), sheet = null) }
            is FlowAction.OnModeSelect -> select { it.copy(mode = action.mode, path = emptyList(), pickerYear = null) }
            is FlowAction.OnPeriodSelect -> select { sel ->
                when (sel.mode) {
                    PeriodMode.MONTH -> sel.copy(month = Period.parse(action.key), path = emptyList(), picker = false)
                    PeriodMode.YEAR -> sel.copy(year = action.key.toInt(), path = emptyList(), picker = false)
                    PeriodMode.ALL -> sel
                }
            }
            is FlowAction.OnScrub -> local { it.copy(scrub = action.index) }
            FlowAction.OnPreviousPeriod -> step(-1)
            FlowAction.OnNextPeriod -> step(1)
            FlowAction.OnPickerOpen -> local { it.copy(picker = true, pickerYear = null) }
            is FlowAction.OnPickerYear -> select { sel ->
                if (sel.mode == PeriodMode.YEAR) sel.copy(year = action.year, picker = false, path = emptyList()) else sel.copy(pickerYear = action.year)
            }
            FlowAction.OnPickerLatest -> select { it.copy(month = null, year = null, scrub = null, picker = false, path = emptyList()) }
            FlowAction.OnPickerClose -> local { it.copy(picker = false) }
            FlowAction.OnInfoClick -> local { it.copy(sheet = if (it.tab == FlowTab.CASHFLOW) "net" else "total") }
            is FlowAction.OnDonutSegment -> {
                val segment = _state.value.donut?.segments?.firstOrNull { it.id == action.id } ?: return
                local { if (segment.leaf) it.copy(sheet = action.id) else it.copy(path = it.path + action.id) }
            }
            FlowAction.OnDonutBack -> local { it.copy(path = it.path.dropLast(1)) }
            is FlowAction.OnNodeClick -> local { it.copy(sheet = action.id) }
            is FlowAction.OnCategoryClick -> local { it.copy(sheet = action.categoryId) }
            FlowAction.OnSheetDismiss -> local { it.copy(sheet = null) }
            FlowAction.OnSheetCta -> when (val cta = _state.value.sheet?.action) {
                is SheetCta.Tab -> select { it.copy(tab = cta.tab, sheet = null, path = emptyList()) }
                is SheetCta.Events -> {
                    local { it.copy(sheet = null) }
                    send(FlowEvent.OpenEvents(cta.categoryId, eventsPeriod()))
                }
                SheetCta.Close, null -> local { it.copy(sheet = null) }
            }
            FlowAction.OnShowAllEvents -> send(FlowEvent.OpenEvents(null, eventsPeriod()))
            is FlowAction.OnTransactionClick -> send(FlowEvent.OpenTransaction(action.id))
            FlowAction.OnImportClick -> send(FlowEvent.OpenImport)
            FlowAction.OnProfileClick -> send(FlowEvent.OpenProfile)
        }
    }

    /** Händelser filters by one month; a year or Alla opens it unfiltered. */
    private fun eventsPeriod(): String? {
        val sel = selection.value ?: return null
        val data = input?.data ?: return null
        if (sel.mode != PeriodMode.MONTH) return null
        return (sel.month?.takeIf { it in data.periods } ?: data.periods.lastOrNull())?.toString()
    }

    private fun step(delta: Int) {
        val sel = selection.value ?: return
        val data = input?.data ?: return
        if (data.isEmpty) return
        if (sel.mode == PeriodMode.ALL) {
            val i = (sel.scrub ?: data.periods.lastIndex) + delta
            if (i in data.periods.indices) local { it.copy(scrub = i) }
            return
        }
        val scopes = data.scopes(sel.mode)
        val current = builder.resolve(data, sel).scope.key
        val target = scopes.getOrNull(scopes.indexOfFirst { it.key == current } + delta) ?: return
        onAction(FlowAction.OnPeriodSelect(target.key))
    }

    /** A change that is not stored: sheets, the donut path, the scrub position. */
    private fun local(transform: (FlowSelection) -> FlowSelection) {
        selection.update { it?.let(transform) }
    }

    /** A change to tab, mode or period: also stored so the app reopens there. */
    private fun select(transform: (FlowSelection) -> FlowSelection) {
        val next = selection.value?.let(transform) ?: return
        selection.value = next
        viewModelScope.launch { settings.update { it.copy(lastTab = next.tab.key, lastPeriod = encode(next)) } }
    }

    private fun send(e: FlowEvent) {
        viewModelScope.launch { _events.send(e) }
    }

    internal companion object {
        fun encode(sel: FlowSelection): String = when (sel.mode) {
            PeriodMode.MONTH -> "m:" + (sel.month?.toString() ?: "")
            PeriodMode.YEAR -> "y:" + (sel.year?.toString() ?: "")
            PeriodMode.ALL -> "a"
        }

        fun decode(tab: String?, period: String?): FlowSelection {
            val t = FlowTab.of(tab)
            val value = period?.substringAfter(':', "").orEmpty()
            return when (period?.substringBefore(':')) {
                "y" -> FlowSelection(t, PeriodMode.YEAR, year = value.toIntOrNull())
                "a" -> FlowSelection(t, PeriodMode.ALL)
                else -> FlowSelection(t, PeriodMode.MONTH, month = runCatching { Period.parse(value) }.getOrNull())
            }
        }
    }
}
