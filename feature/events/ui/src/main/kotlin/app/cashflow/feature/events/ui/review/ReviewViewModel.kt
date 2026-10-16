package app.cashflow.feature.events.ui.review

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cashflow.core.domain.Result
import app.cashflow.core.domain.datasource.CategoryLocalDataSource
import app.cashflow.core.domain.datasource.TransactionLocalDataSource
import app.cashflow.core.domain.model.Category
import app.cashflow.core.domain.model.CategoryId
import app.cashflow.core.domain.model.CategoryKind
import app.cashflow.core.domain.model.Transaction
import app.cashflow.core.domain.model.TransactionStatus
import app.cashflow.core.domain.model.TransferKind
import app.cashflow.core.domain.model.sum
import app.cashflow.core.importengine.CategorizeUseCase
import app.cashflow.core.importengine.CategorizeUndo
import app.cashflow.feature.events.ui.R
import app.cashflow.core.ui.FlowTone
import app.cashflow.core.ui.UiText
import app.cashflow.core.ui.categoryName
import app.cashflow.core.ui.format.AppLocale
import app.cashflow.core.ui.format.MoneyFormatter
import app.cashflow.core.ui.format.PeriodFormatter
import app.cashflow.core.ui.toUiText
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
data class CategoryChipUi(val id: String, val name: UiText, val tone: FlowTone, val selected: Boolean)

@Immutable
data class ReviewGroupUi(
    val key: String,
    val title: String,
    val summary: String,
    val total: String,
    val isIncome: Boolean,
    val isLoneLeg: Boolean,
    val chips: List<CategoryChipUi>,
    val expanded: Boolean,
    val selectedId: String?,
    val remember: Boolean,
    val isSaving: Boolean,
)

@Stable
data class ReviewState(
    val groups: List<ReviewGroupUi> = emptyList(),
    val remainingRows: Int = 0,
    val isLoading: Boolean = true,
    val error: UiText? = null,
)

sealed interface ReviewAction {
    data class OnCategorySelect(val groupKey: String, val categoryId: String) : ReviewAction
    data class OnRememberToggle(val groupKey: String, val remember: Boolean) : ReviewAction
    data class OnExpandToggle(val groupKey: String) : ReviewAction
    data class OnApprove(val groupKey: String) : ReviewAction
    data class OnHousehold(val groupKey: String) : ReviewAction
    data object OnUndoClick : ReviewAction
    data object OnBackClick : ReviewAction
}

sealed interface ReviewEvent {
    data object NavigateBack : ReviewEvent
    data class ShowMessage(val text: UiText) : ReviewEvent

    /** Shown after a decision, with an "Ångra" action that sends [ReviewAction.OnUndoClick]. */
    data class ShowUndo(val text: UiText) : ReviewEvent
}

private data class Choice(val categoryId: CategoryId? = null, val remember: Boolean = true, val expanded: Boolean = false)

/**
 * Review by payee: every row waiting for a category, grouped by normalized payee and sorted
 * by row count, so one decision ("Kom ihåg" is on by default) clears many rows.
 */
class ReviewViewModel(
    transactions: TransactionLocalDataSource,
    categories: CategoryLocalDataSource,
    private val categorize: CategorizeUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(ReviewState())
    val state: StateFlow<ReviewState> = _state.asStateFlow()

    private val _events = Channel<ReviewEvent>()
    val events = _events.receiveAsFlow()

    private val choices = MutableStateFlow<Map<String, Choice>>(emptyMap())
    private val saving = MutableStateFlow<Set<String>>(emptySet())
    private var rowsByGroup: Map<String, List<Transaction>> = emptyMap()

    /** The latest decision, so "Ångra" can put it back. */
    private var lastUndo: CategorizeUndo? = null
    private val locale = AppLocale.current()

    init {
        combine(transactions.observeAll(), categories.observeCategories(), choices, saving) { txs, cats, ch, sv ->
            build(txs, cats, ch, sv)
        }.onEach { s -> _state.update { s } }.launchIn(viewModelScope)
    }

    fun onAction(action: ReviewAction) {
        when (action) {
            is ReviewAction.OnCategorySelect -> choose(action.groupKey) { it.copy(categoryId = CategoryId(action.categoryId)) }
            is ReviewAction.OnRememberToggle -> choose(action.groupKey) { it.copy(remember = action.remember) }
            is ReviewAction.OnExpandToggle -> choose(action.groupKey) { it.copy(expanded = !it.expanded) }
            is ReviewAction.OnApprove -> approve(action.groupKey)
            is ReviewAction.OnHousehold -> household(action.groupKey)
            ReviewAction.OnUndoClick -> undo()
            ReviewAction.OnBackClick -> viewModelScope.launch { _events.send(ReviewEvent.NavigateBack) }
        }
    }

    private fun undo() {
        val token = lastUndo ?: return
        lastUndo = null
        viewModelScope.launch {
            val r = categorize.undo(token)
            if (r is Result.Error) _events.send(ReviewEvent.ShowMessage(r.error.toUiText()))
        }
    }

    private fun choose(key: String, change: (Choice) -> Choice) =
        choices.update { it + (key to change(it[key] ?: Choice())) }

    private fun approve(key: String) {
        val rows = rowsByGroup[key] ?: return
        val choice = choices.value[key] ?: return
        val category = choice.categoryId ?: return
        viewModelScope.launch {
            saving.update { it + key }
            when (val r = categorize.categorize(rows, category, choice.remember)) {
                is Result.Error -> _events.send(ReviewEvent.ShowMessage(r.error.toUiText()))
                is Result.Success -> {
                    choices.update { it - key }
                    lastUndo = r.data
                    _events.send(ReviewEvent.ShowUndo(UiText.StringResource(R.string.review_saved, arrayOf(rows.first().normalizedText.ifBlank { rows.first().rawText }))))
                }
            }
            saving.update { it - key }
        }
    }

    private fun household(key: String) {
        val rows = rowsByGroup[key] ?: return
        viewModelScope.launch {
            saving.update { it + key }
            when (val r = categorize.confirmHouseholdTransfer(rows)) {
                is Result.Error -> _events.send(ReviewEvent.ShowMessage(r.error.toUiText()))
                is Result.Success -> {
                    lastUndo = r.data
                    _events.send(ReviewEvent.ShowUndo(UiText.StringResource(R.string.review_saved, arrayOf(rows.first().normalizedText.ifBlank { rows.first().rawText }))))
                }
            }
            saving.update { it - key }
        }
    }

    private fun build(txs: List<Transaction>, cats: List<Category>, ch: Map<String, Choice>, sv: Set<String>): ReviewState {
        val waiting = txs.filter { it.status == TransactionStatus.TO_REVIEW }
        val groups = waiting.groupBy { categorize.payeeKey(it) }
        rowsByGroup = groups
        val byId = cats.associateBy { it.id }
        val ui = groups.entries
            .sortedWith(compareByDescending<Map.Entry<String, List<Transaction>>> { it.value.size }.thenByDescending { e -> e.value.sumOf { -it.amount.ore } })
            .map { (key, rows) ->
                val total = rows.map { it.amount }.sum()
                val isIncome = total.isPositive
                val choice = ch[key] ?: Choice()
                val options = options(cats, isIncome, choice.expanded)
                val selected = choice.categoryId
                val chips = (options + listOfNotNull(selected?.let { byId[it] }).filter { s -> options.none { it.id == s.id } })
                    .map { CategoryChipUi(it.id.value, categoryName(it.id, it.name), it.kind.tone(), it.id == selected) }
                val first = rows.minOf { it.transactionDate }
                val last = rows.maxOf { it.transactionDate }
                val range = if (first == last) PeriodFormatter.iso(first, locale) else "${PeriodFormatter.iso(first, locale)} – ${PeriodFormatter.iso(last, locale)}"
                ReviewGroupUi(
                    key = key,
                    title = rows.first().normalizedText.ifBlank { rows.first().rawText },
                    summary = range,
                    total = MoneyFormatter.format(total, signed = true, locale = locale),
                    isIncome = isIncome,
                    isLoneLeg = rows.any { it.transferKind == TransferKind.LONE_LEG },
                    chips = chips,
                    expanded = choice.expanded,
                    selectedId = selected?.value,
                    remember = choice.remember,
                    isSaving = key in sv,
                )
            }
        return ReviewState(groups = ui, remainingRows = waiting.size, isLoading = false)
    }

    private fun options(cats: List<Category>, income: Boolean, expanded: Boolean): List<Category> {
        val suggested = if (income) INCOME_SUGGESTIONS else EXPENSE_SUGGESTIONS
        // Moving cash between accounts (including taking money back from a buffer) is
        // "Överföring", offered both ways: it is never income or spending.
        val pool = cats.filter { c ->
            c.kind == CategoryKind.TRANSFER ||
                if (income) c.kind == CategoryKind.INCOME else c.kind == CategoryKind.FIXED || c.kind == CategoryKind.VARIABLE || c.kind == CategoryKind.SAVINGS
        }
        return if (expanded) pool else suggested.mapNotNull { id -> pool.firstOrNull { it.id.value == id } }
    }

    private companion object {
        val EXPENSE_SUGGESTIONS = listOf("overforing", "mat", "restaurang", "shopping", "transport", "noje", "halsa", "hem_bygg", "ovrigt_rorligt")
        val INCOME_SUGGESTIONS = listOf("overforing", "inkomst_ovrig", "lon", "utdelning", "ersattning", "barnbidrag")
    }
}
