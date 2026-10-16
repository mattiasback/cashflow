package app.cashflow.feature.flow.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cashflow.core.domain.cashflow.CashFlowCalculator
import app.cashflow.core.domain.cashflow.CashFlowSummary
import app.cashflow.core.domain.datasource.CategoryLocalDataSource
import app.cashflow.core.domain.datasource.SettingsDataSource
import app.cashflow.core.domain.datasource.TransactionLocalDataSource
import app.cashflow.core.domain.model.Category
import app.cashflow.core.domain.model.CategoryId
import app.cashflow.core.domain.model.CategoryKind
import app.cashflow.core.domain.model.Money
import app.cashflow.core.domain.model.MortgageSplit
import app.cashflow.core.domain.model.Period
import app.cashflow.core.domain.model.Transaction
import app.cashflow.core.ui.FlowTone
import app.cashflow.core.ui.UiText
import app.cashflow.core.ui.categoryName
import app.cashflow.core.ui.format.AppLocale
import app.cashflow.core.ui.format.MoneyFormatter
import app.cashflow.core.ui.format.PercentFormatter
import app.cashflow.core.ui.format.PeriodFormatter
import app.cashflow.core.ui.tone
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class TileUi(val label: UiText, val value: String, val sub: UiText?, val tone: FlowTone, val spoken: String)

@Immutable
data class CategoryBarUi(
    val id: String,
    val name: UiText,
    val amount: String,
    val share: String,
    val fraction: Float,
    val tone: FlowTone,
    val spokenAmount: String,
)

@Stable
data class FlowState(
    val periodLabel: String = "",
    val periodSpoken: String = "",
    val previousLabel: String = "",
    val nextLabel: String = "",
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val net: String = "",
    val netSpoken: String = "",
    val delta: UiText? = null,
    val deltaPositive: Boolean = true,
    val tiles: List<TileUi> = emptyList(),
    val expenseBars: List<CategoryBarUi> = emptyList(),
    val incomeBars: List<CategoryBarUi> = emptyList(),
    val savingsBars: List<CategoryBarUi> = emptyList(),
    val hasData: Boolean = false,
    val isLoading: Boolean = true,
    val error: UiText? = null,
)

sealed interface FlowAction {
    data object OnPreviousPeriod : FlowAction
    data object OnNextPeriod : FlowAction
    data class OnCategoryClick(val categoryId: String) : FlowAction
    data object OnImportClick : FlowAction
    data object OnProfileClick : FlowAction
}

sealed interface FlowEvent {
    data class OpenCategory(val categoryId: String, val period: String) : FlowEvent
    data object OpenImport : FlowEvent
    data object OpenProfile : FlowEvent
}

/** Basic Kassaflöde for one month: net, four tiles and category bars, all pre-formatted. */
class FlowViewModel(
    transactions: TransactionLocalDataSource,
    categories: CategoryLocalDataSource,
    settings: SettingsDataSource,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val calculator = CashFlowCalculator()
    private val locale = AppLocale.current()

    private val _state = MutableStateFlow(FlowState())
    val state: StateFlow<FlowState> = _state.asStateFlow()

    private val _events = Channel<FlowEvent>()
    val events = _events.receiveAsFlow()

    /** null = follow the latest month with data. Survives process death. */
    private val selected = MutableStateFlow(savedStateHandle.get<String>(KEY_PERIOD)?.let(Period::parse))
    private var periods: List<Period> = emptyList()
    private var shown: Period? = null

    init {
        combine(transactions.observeAll(), categories.observeCategories(), settings.settings, selected) { txs, cats, prefs, sel ->
            build(txs, cats, prefs.mortgage, prefs.percentDecimals, sel)
        }.onEach { s -> _state.update { s } }.launchIn(viewModelScope)
    }

    fun onAction(action: FlowAction) {
        when (action) {
            FlowAction.OnPreviousPeriod -> step(-1)
            FlowAction.OnNextPeriod -> step(1)
            is FlowAction.OnCategoryClick -> shown?.let { p -> send(FlowEvent.OpenCategory(action.categoryId, p.toString())) }
            FlowAction.OnImportClick -> send(FlowEvent.OpenImport)
            FlowAction.OnProfileClick -> send(FlowEvent.OpenProfile)
        }
    }

    private fun send(e: FlowEvent) {
        viewModelScope.launch { _events.send(e) }
    }

    private fun step(delta: Int) {
        val current = shown ?: return
        val i = periods.indexOf(current) + delta
        val target = periods.getOrNull(i) ?: return
        savedStateHandle[KEY_PERIOD] = target.toString()
        selected.value = target
    }

    private fun build(txs: List<Transaction>, cats: List<Category>, mortgage: MortgageSplit, percentDecimals: Boolean, sel: Period?): FlowState {
        periods = calculator.periodsWithData(txs)
        val period = sel?.takeIf { it in periods } ?: periods.lastOrNull()
        shown = period
        if (period == null) return FlowState(isLoading = false, hasData = false)
        val byId = cats.associateBy { it.id }
        val kindOf: (CategoryId) -> CategoryKind = { byId[it]?.kind ?: CategoryKind.UNCATEGORIZED }
        val s = calculator.summarize(period, txs, mortgage, kindOf)
        val index = periods.indexOf(period)
        val previous = periods.getOrNull(index - 1)
        val prevSummary = previous?.let { calculator.summarize(it, txs, mortgage, kindOf) }

        val fmt = { m: Money -> MoneyFormatter.format(m, locale = locale) }
        val delta = prevSummary?.let { p ->
            val d = s.net - p.net
            UiText.StringResource(R.string.flow_delta_vs, arrayOf(MoneyFormatter.format(d, signed = true, locale = locale), PeriodFormatter.month(p.period, locale)))
        }
        return FlowState(
            periodLabel = PeriodFormatter.month(period, locale),
            periodSpoken = PeriodFormatter.monthLong(period, locale),
            previousLabel = previous?.let { PeriodFormatter.monthLong(it, locale) }.orEmpty(),
            nextLabel = periods.getOrNull(index + 1)?.let { PeriodFormatter.monthLong(it, locale) }.orEmpty(),
            canGoBack = index > 0,
            canGoForward = index < periods.lastIndex,
            net = MoneyFormatter.format(s.net, signed = true, locale = locale),
            netSpoken = MoneyFormatter.spoken(s.net, locale),
            delta = delta,
            deltaPositive = prevSummary == null || s.net >= prevSummary.net,
            tiles = tiles(s, fmt, percentDecimals),
            expenseBars = bars(s.expensesByCategory, s.expenses, byId),
            incomeBars = bars(s.incomeByCategory, s.income, byId),
            savingsBars = bars(s.savingsByCategory, s.savings, byId),
            hasData = true,
            isLoading = false,
        )
    }

    private fun tiles(s: CashFlowSummary, fmt: (Money) -> String, percentDecimals: Boolean): List<TileUi> {
        val rate = s.savingsRatePercent
        return listOf(
            TileUi(
                UiText.StringResource(R.string.tile_income), fmt(s.income), null, FlowTone.Income,
                MoneyFormatter.spoken(s.income, locale),
            ),
            TileUi(
                UiText.StringResource(R.string.tile_expenses), fmt(s.expenses),
                null,
                FlowTone.Fixed, MoneyFormatter.spoken(s.expenses, locale),
            ),
            TileUi(
                UiText.StringResource(R.string.tile_savings), fmt(s.savings),
                rate?.let { UiText.StringResource(R.string.tile_savings_sub, arrayOf(PercentFormatter.format(it, decimals = if (percentDecimals) 1 else 0, locale = locale))) },
                FlowTone.Savings, MoneyFormatter.spoken(s.savings, locale),
            ),
            TileUi(
                UiText.StringResource(R.string.tile_left), MoneyFormatter.format(s.left, signed = true, locale = locale),
                UiText.StringResource(R.string.tile_left_sub), FlowTone.Neutral, MoneyFormatter.spoken(s.left, locale),
            ),
        )
    }

    private fun bars(values: Map<CategoryId, Money>, total: Money, byId: Map<CategoryId, Category>): List<CategoryBarUi> {
        val positive = values.filterValues { it.ore > 0 }
        val max = positive.values.maxOfOrNull { it.ore } ?: return emptyList()
        return positive.entries.sortedByDescending { it.value.ore }.map { (id, amount) ->
            val c = byId[id]
            CategoryBarUi(
                id = id.value,
                name = categoryName(id, c?.name ?: id.value),
                amount = MoneyFormatter.format(amount, locale = locale),
                share = PercentFormatter.share(amount.ore, total.ore, locale),
                fraction = amount.ore.toFloat() / max,
                tone = (c?.kind ?: CategoryKind.UNCATEGORIZED).tone(),
                spokenAmount = MoneyFormatter.spoken(amount, locale),
            )
        }
    }

    private companion object {
        const val KEY_PERIOD = "period"
    }
}
