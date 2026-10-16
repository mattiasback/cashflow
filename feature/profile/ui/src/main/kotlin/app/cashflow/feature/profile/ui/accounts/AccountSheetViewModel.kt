package app.cashflow.feature.profile.ui.accounts

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cashflow.core.domain.Result
import app.cashflow.core.domain.cashflow.SavingsCalculator
import app.cashflow.core.domain.datasource.AccountLocalDataSource
import app.cashflow.core.domain.datasource.TransactionLocalDataSource
import app.cashflow.core.domain.model.Account
import app.cashflow.core.domain.model.AccountType
import app.cashflow.core.importengine.ReclassifyUseCase
import app.cashflow.core.ui.UiText
import app.cashflow.core.ui.format.AppLocale
import app.cashflow.core.ui.format.MoneyFormatter
import app.cashflow.core.ui.format.MoneyParser
import app.cashflow.core.ui.format.PeriodFormatter
import app.cashflow.core.ui.toUiText
import app.cashflow.feature.profile.ui.R
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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

@Immutable
data class AccountFigureUi(val label: UiText, val value: String)

@Stable
data class AccountSheetState(
    val title: String = "",
    val institution: String? = null,
    val name: String = "",
    val type: AccountType = AccountType.TRANSACTION,
    /** Bank accounts keep their type; savings accounts can be corrected. */
    val typeEditable: Boolean = false,
    val showsMarketValue: Boolean = false,
    val marketValue: String = "",
    val marketValueNote: UiText? = null,
    val figures: List<AccountFigureUi> = emptyList(),
    val canSave: Boolean = false,
    val error: UiText? = null,
    val isLoading: Boolean = true,
)

sealed interface AccountSheetAction {
    data class OnNameChange(val name: String) : AccountSheetAction
    data class OnTypeSelect(val type: AccountType) : AccountSheetAction
    data class OnMarketValueChange(val text: String) : AccountSheetAction
    data object OnSaveClick : AccountSheetAction
    data object OnDismiss : AccountSheetAction
}

sealed interface AccountSheetEvent {
    data object Dismiss : AccountSheetEvent
}

/** One account: rename it, correct a savings account's type, enter an ISK/KF value, see return and cost. */
class AccountSheetViewModel(
    private val accountId: String,
    private val accounts: AccountLocalDataSource,
    transactions: TransactionLocalDataSource,
    private val reclassify: ReclassifyUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(AccountSheetState())
    val state: StateFlow<AccountSheetState> = _state.asStateFlow()

    private val _events = Channel<AccountSheetEvent>()
    val events = _events.receiveAsFlow()

    private val locale = AppLocale.current()
    private var account: Account? = null
    private var edited = false

    init {
        combine(accounts.observeAccounts(), transactions.observeAll()) { accs, txs -> accs to txs }
            .onEach { (accs, txs) ->
                val a = accs.firstOrNull { it.id.value == accountId } ?: return@onEach
                account = a
                val year = Clock.System.todayIn(TimeZone.currentSystemDefault()).year
                val summary = SavingsCalculator().summarize(listOf(a), txs, year).firstOrNull()
                val fmt = { m: app.cashflow.core.domain.model.Money -> MoneyFormatter.format(m, locale = locale) }
                val figures = if (summary == null) {
                    listOf(AccountFigureUi(UiText.StringResource(R.string.account_rows), txs.count { it.accountId == a.id }.toString()))
                } else {
                    listOf(
                        AccountFigureUi(UiText.StringResource(R.string.account_balance), fmt(summary.balance)),
                        AccountFigureUi(UiText.StringResource(R.string.account_deposits), fmt(summary.deposits)),
                        AccountFigureUi(UiText.StringResource(R.string.account_withdrawals), fmt(summary.withdrawals)),
                        AccountFigureUi(UiText.StringResource(R.string.account_return, arrayOf(year)), fmt(summary.returnInYear)),
                        AccountFigureUi(UiText.StringResource(R.string.account_cost, arrayOf(year)), fmt(summary.costInYear)),
                    )
                }
                _state.update { s ->
                    // The user's unsaved edits win over the stored values.
                    val base = if (edited) s else s.copy(
                        name = a.name,
                        type = a.type,
                        marketValue = a.marketValue?.let(MoneyParser::editable).orEmpty(),
                    )
                    base.copy(
                        title = a.name,
                        institution = a.institution,
                        typeEditable = a.type.holdsSavings,
                        showsMarketValue = base.type.hasMarketValue,
                        marketValueNote = a.marketValueDate?.let { UiText.StringResource(R.string.account_value_updated, arrayOf(PeriodFormatter.iso(it, locale))) },
                        figures = figures,
                        canSave = canSave(base),
                        isLoading = false,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun onAction(action: AccountSheetAction) {
        when (action) {
            is AccountSheetAction.OnNameChange -> edit { it.copy(name = action.name) }
            is AccountSheetAction.OnTypeSelect -> edit { it.copy(type = action.type, showsMarketValue = action.type.hasMarketValue) }
            is AccountSheetAction.OnMarketValueChange -> edit { it.copy(marketValue = action.text) }
            AccountSheetAction.OnSaveClick -> save()
            AccountSheetAction.OnDismiss -> viewModelScope.launch { _events.send(AccountSheetEvent.Dismiss) }
        }
    }

    private fun edit(change: (AccountSheetState) -> AccountSheetState) {
        edited = true
        _state.update { s -> change(s).let { it.copy(canSave = canSave(it), error = null) } }
    }

    private fun canSave(s: AccountSheetState): Boolean =
        s.name.isNotBlank() && (!s.showsMarketValue || s.marketValue.isBlank() || MoneyParser.parse(s.marketValue) != null)

    private fun save() {
        val a = account ?: return
        val s = _state.value
        if (!canSave(s)) return
        val value = if (s.type.hasMarketValue) MoneyParser.parse(s.marketValue) else null
        val updated = a.copy(
            name = s.name.trim(),
            type = if (a.type.holdsSavings) s.type else a.type,
            marketValue = value,
            marketValueDate = when {
                value == null -> null
                value == a.marketValue -> a.marketValueDate
                else -> Clock.System.todayIn(TimeZone.currentSystemDefault())
            },
        )
        viewModelScope.launch {
            when (val r = accounts.upsertAccount(updated)) {
                is Result.Error -> _state.update { it.copy(error = r.error.toUiText()) }
                is Result.Success -> {
                    // The type decides how its transfers pair and which savings category the bank leg gets.
                    if (updated.type != a.type) reclassify()
                    _events.send(AccountSheetEvent.Dismiss)
                }
            }
        }
    }
}
