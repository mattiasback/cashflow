package app.cashflow.feature.events.ui.detail

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cashflow.core.domain.Result
import app.cashflow.core.domain.datasource.AccountLocalDataSource
import app.cashflow.core.domain.datasource.CategoryLocalDataSource
import app.cashflow.core.domain.datasource.TransactionLocalDataSource
import app.cashflow.core.domain.model.CategorizedBy
import app.cashflow.core.domain.model.CategoryId
import app.cashflow.core.domain.model.CategoryKind
import app.cashflow.core.domain.model.Transaction
import app.cashflow.core.domain.model.TransferKind
import app.cashflow.core.domain.model.TxId
import app.cashflow.core.importengine.CategorizeUseCase
import app.cashflow.core.ui.FlowTone
import app.cashflow.core.ui.UiText
import app.cashflow.core.ui.categoryName
import app.cashflow.core.ui.format.AppLocale
import app.cashflow.core.ui.format.MoneyFormatter
import app.cashflow.core.ui.format.PeriodFormatter
import app.cashflow.core.ui.groupName
import app.cashflow.core.ui.toUiText
import app.cashflow.core.ui.tone
import app.cashflow.feature.events.ui.R
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
data class PickOptionUi(val id: String, val name: UiText, val group: UiText, val tone: FlowTone, val selected: Boolean)

@Stable
data class TransactionDetailState(
    val title: String = "",
    val amount: String = "",
    val isIncome: Boolean = false,
    val category: UiText? = null,
    val transactionDate: String = "",
    val bookingDate: String = "",
    val account: String = "",
    val howLabel: UiText? = null,
    val howText: UiText? = null,
    val partner: String? = null,
    val rawText: String = "",
    val shortId: String = "",
    val canMarkHousehold: Boolean = false,
    val picking: Boolean = false,
    val options: List<PickOptionUi> = emptyList(),
    val remember: Boolean = false,
    val canSave: Boolean = false,
    val isLoading: Boolean = true,
    val error: UiText? = null,
)

sealed interface TransactionDetailAction {
    data object OnChangeCategoryClick : TransactionDetailAction
    data class OnCategoryPick(val id: String) : TransactionDetailAction
    data class OnRememberToggle(val remember: Boolean) : TransactionDetailAction
    data object OnSaveClick : TransactionDetailAction
    data object OnCancelClick : TransactionDetailAction
    data object OnMarkHouseholdClick : TransactionDetailAction
}

sealed interface TransactionDetailEvent {
    data object Dismiss : TransactionDetailEvent
}

class TransactionDetailViewModel(
    txId: String,
    transactions: TransactionLocalDataSource,
    categories: CategoryLocalDataSource,
    accounts: AccountLocalDataSource,
    private val categorize: CategorizeUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(TransactionDetailState())
    val state: StateFlow<TransactionDetailState> = _state.asStateFlow()

    private val _events = Channel<TransactionDetailEvent>()
    val events = _events.receiveAsFlow()

    /** The category picker is a small in-sheet state flag, not a destination. */
    private data class Picker(val open: Boolean = false, val picked: CategoryId? = null, val remember: Boolean = false)

    private val picker = MutableStateFlow(Picker())
    private var current: Transaction? = null
    private val locale = AppLocale.current()

    init {
        combine(transactions.observe(TxId(txId)), transactions.observeAll(), categories.observeCategories(), accounts.observeAccounts(), picker) { tx, all, cats, accs, p ->
            current = tx
            if (tx == null) return@combine TransactionDetailState(isLoading = false)
            val byId = cats.associateBy { it.id }
            val partner = tx.transferPartnerId?.let { pid -> all.firstOrNull { it.id == pid } }
            val (howLabel, howText) = how(tx)
            val selected = p.picked ?: tx.categoryId
            TransactionDetailState(
                title = tx.normalizedText.ifBlank { tx.rawText },
                amount = MoneyFormatter.format(tx.amount, signed = true, decimals = true, locale = locale),
                isIncome = tx.amount.isPositive,
                category = categoryName(tx.categoryId, byId[tx.categoryId]?.name ?: tx.categoryId.value),
                transactionDate = PeriodFormatter.iso(tx.transactionDate, locale),
                bookingDate = PeriodFormatter.iso(tx.bookingDate, locale),
                account = accs.firstOrNull { it.id == tx.accountId }?.name.orEmpty(),
                howLabel = howLabel,
                howText = howText,
                partner = partner?.let { pt -> accs.firstOrNull { it.id == pt.accountId }?.name },
                rawText = tx.rawText,
                shortId = "#" + tx.id.value.take(8),
                canMarkHousehold = tx.transferKind == TransferKind.LONE_LEG && !tx.loneLegConfirmed,
                picking = p.open,
                options = cats
                    .filter { it.kind != CategoryKind.UNCATEGORIZED }
                    .sortedWith(compareBy({ it.kind.ordinal }, { it.sortOrder }))
                    .map { PickOptionUi(it.id.value, categoryName(it.id, it.name), it.kind.groupName(), it.kind.tone(), it.id == selected) },
                remember = p.remember,
                canSave = p.picked != null && p.picked != tx.categoryId,
                isLoading = false,
            )
        }.onEach { s -> _state.update { s } }.launchIn(viewModelScope)
    }

    fun onAction(action: TransactionDetailAction) {
        when (action) {
            TransactionDetailAction.OnChangeCategoryClick -> picker.update { it.copy(open = true) }
            is TransactionDetailAction.OnCategoryPick -> picker.update { it.copy(picked = CategoryId(action.id)) }
            is TransactionDetailAction.OnRememberToggle -> picker.update { it.copy(remember = action.remember) }
            TransactionDetailAction.OnCancelClick -> picker.update { Picker() }
            TransactionDetailAction.OnSaveClick -> save()
            TransactionDetailAction.OnMarkHouseholdClick -> markHousehold()
        }
    }

    private fun save() {
        val tx = current ?: return
        val p = picker.value
        val category = p.picked ?: return
        viewModelScope.launch {
            when (val r = categorize.categorize(listOf(tx), category, p.remember)) {
                is Result.Error -> _state.update { it.copy(error = r.error.toUiText()) }
                is Result.Success -> {
                    picker.update { Picker() }
                    _events.send(TransactionDetailEvent.Dismiss)
                }
            }
        }
    }

    private fun markHousehold() {
        val tx = current ?: return
        viewModelScope.launch {
            when (val r = categorize.confirmHouseholdTransfer(listOf(tx))) {
                is Result.Error -> _state.update { it.copy(error = r.error.toUiText()) }
                is Result.Success -> _events.send(TransactionDetailEvent.Dismiss)
            }
        }
    }

    private fun how(tx: Transaction): Pair<UiText, UiText> {
        fun s(id: Int) = UiText.StringResource(id)
        return when {
            tx.transferKind == TransferKind.INTERNAL -> s(R.string.detail_how_transfer) to s(R.string.detail_how_transfer_text)
            tx.transferKind == TransferKind.TO_SAVINGS || tx.transferKind == TransferKind.FROM_SAVINGS ->
                s(R.string.detail_how_transfer) to s(R.string.detail_how_savings_text)
            tx.transferKind == TransferKind.LONE_LEG && tx.loneLegConfirmed -> s(R.string.detail_how_transfer) to s(R.string.detail_how_household_text)
            tx.transferKind == TransferKind.LONE_LEG && tx.categorizedBy == CategorizedBy.NONE -> s(R.string.detail_how_lone) to s(R.string.detail_how_lone_text)
            else -> when (tx.categorizedBy) {
                CategorizedBy.MEMORY -> s(R.string.detail_how_memory) to s(R.string.detail_how_memory_text)
                CategorizedBy.USER_RULE -> s(R.string.detail_how_rule) to s(R.string.detail_how_rule_text)
                CategorizedBy.BUILT_IN_RULE -> s(R.string.detail_how_builtin) to s(R.string.detail_how_builtin_text)
                CategorizedBy.MANUAL -> s(R.string.detail_how_manual) to s(R.string.detail_how_manual_text)
                CategorizedBy.TRANSFER -> s(R.string.detail_how_transfer) to s(R.string.detail_how_transfer_text)
                CategorizedBy.NONE -> s(R.string.detail_how_none) to s(R.string.detail_how_none_text)
            }
        }
    }
}
