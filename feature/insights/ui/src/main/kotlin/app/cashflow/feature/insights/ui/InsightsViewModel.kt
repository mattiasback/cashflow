package app.cashflow.feature.insights.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cashflow.core.domain.cashflow.Buffer
import app.cashflow.core.domain.cashflow.FlowData
import app.cashflow.core.domain.datasource.CategoryLocalDataSource
import app.cashflow.core.domain.datasource.GoalLocalDataSource
import app.cashflow.core.domain.datasource.SettingsDataSource
import app.cashflow.core.domain.datasource.TransactionLocalDataSource
import app.cashflow.core.domain.model.CategoryKind
import app.cashflow.core.domain.model.Goals
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

/**
 * Insikter: suggestions and what-if over the same rows as Flöde. The heavy part (the
 * detectors) runs only when the data changes; the slider and the sheets only rebuild the UI models.
 */
class InsightsViewModel(
    transactions: TransactionLocalDataSource,
    categories: CategoryLocalDataSource,
    goals: GoalLocalDataSource,
    private val settings: SettingsDataSource,
    compute: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val builder = InsightsBuilder()

    private val _state = MutableStateFlow(InsightsState())
    val state: StateFlow<InsightsState> = _state.asStateFlow()

    private val _events = Channel<InsightsEvent>()
    val events = _events.receiveAsFlow()

    private val ui = MutableStateFlow(Ui())

    private data class Ui(val extraKronor: Int = 2_000, val sheet: InsightSheet? = null, val copied: Boolean = false)

    init {
        val model = combine(transactions.observeAll(), categories.observeCategories(), goals.observeGoals(), settings.settings) { txs, cats, goalList, prefs ->
            val byId = cats.associateBy { it.id }
            val data = FlowData.of(txs, prefs.mortgage, { byId[it]?.kind ?: CategoryKind.UNCATEGORIZED })
            val input = InsightsInput(
                data = data,
                transactions = txs,
                categories = byId,
                goals = Goals.of(goalList),
                bufferNow = Buffer.estimate(prefs.bufferBalance, txs),
                done = prefs.insightsDone,
                percentDecimals = prefs.percentDecimals,
            )
            input to builder.model(input)
        }
        combine(model, ui) { (input, m), u ->
            builder.build(input, m, u.extraKronor, u.sheet, u.copied)
        }.flowOn(compute)
            .onEach { _state.value = it }
            .launchIn(viewModelScope)
    }

    fun onAction(action: InsightsAction) {
        when (action) {
            is InsightsAction.OnOpportunityClick -> ui.value = ui.value.copy(sheet = InsightSheet.Suggestion(action.id))
            InsightsAction.OnAboutClick -> ui.value = ui.value.copy(sheet = InsightSheet.About)
            InsightsAction.OnSheetDone -> {
                when (val s = ui.value.sheet) {
                    is InsightSheet.Suggestion -> toggle(s.id)
                    InsightSheet.About, null -> Unit
                }
                ui.value = ui.value.copy(sheet = null)
            }
            InsightsAction.OnSheetDismiss -> ui.value = ui.value.copy(sheet = null)
            is InsightsAction.OnExtraChange -> ui.value = ui.value.copy(extraKronor = action.kronor.coerceIn(0, 10_000))
            is InsightsAction.OnCopy -> {
                ui.value = ui.value.copy(copied = true)
                send(InsightsEvent.Copy(action.text))
            }
            is InsightsAction.OnShare -> send(InsightsEvent.Share(action.text))
            InsightsAction.OnProfileClick -> send(InsightsEvent.OpenProfile)
            InsightsAction.OnImportClick -> send(InsightsEvent.OpenImport)
        }
    }

    private fun toggle(key: String) {
        viewModelScope.launch {
            settings.update { s -> s.copy(insightsDone = if (key in s.insightsDone) s.insightsDone - key else s.insightsDone + key) }
        }
    }


    private fun send(e: InsightsEvent) {
        viewModelScope.launch { _events.send(e) }
    }
}
