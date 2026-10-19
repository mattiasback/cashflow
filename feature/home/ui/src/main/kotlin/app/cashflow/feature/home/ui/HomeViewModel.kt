package app.cashflow.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cashflow.core.domain.cashflow.Buffer
import app.cashflow.core.domain.cashflow.FlowData
import app.cashflow.core.domain.datasource.AccountLocalDataSource
import app.cashflow.core.domain.datasource.CategoryLocalDataSource
import app.cashflow.core.domain.datasource.GoalLocalDataSource
import app.cashflow.core.domain.datasource.ImportBatchLocalDataSource
import app.cashflow.core.domain.datasource.SettingsDataSource
import app.cashflow.core.domain.datasource.TransactionLocalDataSource
import app.cashflow.core.domain.model.CategoryKind
import app.cashflow.core.domain.model.Goals
import app.cashflow.core.domain.model.Period
import app.cashflow.core.ui.UiText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * Hem: the latest month at a glance. It reads the same month sums as Flöde; a trend row or a
 * sheet button stores the Flöde sub-tab and month in settings, then opens Flöde.
 */
class HomeViewModel(
    transactions: TransactionLocalDataSource,
    categories: CategoryLocalDataSource,
    accounts: AccountLocalDataSource,
    batches: ImportBatchLocalDataSource,
    goals: GoalLocalDataSource,
    private val settings: SettingsDataSource,
    compute: CoroutineDispatcher = Dispatchers.Default,
    private val today: () -> LocalDate = { Clock.System.todayIn(TimeZone.currentSystemDefault()) },
) : ViewModel() {

    private val builder = HomeBuilder()

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    private val _events = Channel<HomeEvent>()
    val events = _events.receiveAsFlow()

    private val sheet = MutableStateFlow<HomeSheet?>(null)
    private var input: HomeInput? = null

    init {
        val base = combine(transactions.observeAll(), categories.observeCategories(), accounts.observeAccounts(), batches.observeBatches(), goals.observeGoals()) { txs, cats, accs, imports, goalList ->
            Base(txs, cats, accs, imports, goalList)
        }
        combine(base, settings.settings) { b, prefs ->
            val byId = b.cats.associateBy { it.id }
            val data = FlowData.of(b.txs, prefs.mortgage, { byId[it]?.kind ?: CategoryKind.UNCATEGORIZED })
            HomeInput(
                data = data,
                transactions = b.txs,
                categories = byId,
                accounts = b.accs,
                buffer = Buffer.value(data),
                goals = Goals.of(b.goals),
                percentDecimals = prefs.percentDecimals,
                amortizationAsSavings = prefs.mortgage.enabled,
                batches = b.imports,
                today = today(),
            )
        }.combine(sheet) { i, s ->
            input = i
            builder.build(i, s)
        }.flowOn(compute)
            .onEach { _state.value = it }
            .launchIn(viewModelScope)
    }

    private data class Base(
        val txs: List<app.cashflow.core.domain.model.Transaction>,
        val cats: List<app.cashflow.core.domain.model.Category>,
        val accs: List<app.cashflow.core.domain.model.Account>,
        val imports: List<app.cashflow.core.domain.model.ImportBatch>,
        val goals: List<app.cashflow.core.domain.model.Goal>,
    )

    fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.OnInfoClick -> sheet.value = HomeSheet.NET
            is HomeAction.OnKpiClick -> sheet.value = when (action.id) {
                KpiId.INCOME -> HomeSheet.INCOME
                KpiId.EXPENSES -> HomeSheet.EXPENSES
                KpiId.SAVINGS_RATE -> HomeSheet.SAVINGS_RATE
                KpiId.BUFFER -> HomeSheet.BUFFER
            }
            is HomeAction.OnTrendClick -> openFlow(action.id)
            is HomeAction.OnTodoClick -> when (action.kind) {
                TodoKind.REVIEW, TodoKind.DUPLICATES -> send(HomeEvent.OpenReview)
                TodoKind.IMPORT, TodoKind.ALL_DONE -> send(HomeEvent.OpenImport)
            }
            HomeAction.OnSheetCta -> {
                val target = _state.value.sheet?.target
                sheet.value = null
                when (target) {
                    is HomeTarget.Flow -> openFlow(target.tab)
                    HomeTarget.Accounts -> send(HomeEvent.OpenAccounts)
                    HomeTarget.Review -> send(HomeEvent.OpenReview)
                    null -> Unit
                }
            }
            HomeAction.OnSheetDismiss -> sheet.value = null
            HomeAction.OnScopePrivate -> send(HomeEvent.ShowMessage(UiText.StringResource(R.string.scope_private_later)))
            HomeAction.OnProfileClick -> send(HomeEvent.OpenProfile)
            HomeAction.OnImportClick -> send(HomeEvent.OpenImport)
        }
    }

    /** Flöde reads its sub-tab and period from settings, so store them first. */
    private fun openFlow(tab: TrendId) {
        val month: Period? = input?.data?.periods?.lastOrNull()
        viewModelScope.launch {
            settings.update { it.copy(lastTab = tab.flowTab, lastPeriod = "m:" + (month?.toString() ?: "")) }
            _events.send(HomeEvent.OpenFlow)
        }
    }

    private fun send(e: HomeEvent) {
        viewModelScope.launch { _events.send(e) }
    }
}
