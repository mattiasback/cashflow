package app.cashflow.feature.profile.ui.accounts

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cashflow.core.domain.cashflow.SavingsCalculator
import app.cashflow.core.domain.datasource.AccountLocalDataSource
import app.cashflow.core.domain.datasource.TransactionLocalDataSource
import app.cashflow.core.domain.model.Account
import app.cashflow.core.domain.model.AccountType
import app.cashflow.core.domain.model.Transaction
import app.cashflow.core.ui.UiText
import app.cashflow.core.ui.format.AppLocale
import app.cashflow.core.ui.format.MoneyFormatter
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
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

@Immutable
data class AccountRowUi(
    val id: String,
    val name: String,
    val type: AccountType,
    val institution: String?,
    val rowCount: Int,
    val value: String?,
)

@Stable
data class AccountsState(
    val savingsValue: String = "",
    val savingsNote: UiText? = null,
    val bankAccounts: List<AccountRowUi> = emptyList(),
    val savingsAccounts: List<AccountRowUi> = emptyList(),
    val pensionAccounts: List<AccountRowUi> = emptyList(),
    val isLoading: Boolean = true,
)

sealed interface AccountsAction {
    data class OnAccountClick(val id: String) : AccountsAction
    data object OnBackClick : AccountsAction
}

sealed interface AccountsEvent {
    data class OpenAccount(val id: String) : AccountsEvent
    data object NavigateBack : AccountsEvent
}

/** Every account, with what each savings account holds; the household's own savings on top. */
class AccountsViewModel(
    accounts: AccountLocalDataSource,
    transactions: TransactionLocalDataSource,
) : ViewModel() {

    private val _state = MutableStateFlow(AccountsState())
    val state: StateFlow<AccountsState> = _state.asStateFlow()

    private val _events = Channel<AccountsEvent>()
    val events = _events.receiveAsFlow()

    private val locale = AppLocale.current()
    private val calculator = SavingsCalculator()

    init {
        combine(accounts.observeAccounts(), transactions.observeAll()) { accs, txs -> build(accs, txs) }
            .onEach { s -> _state.update { s } }
            .launchIn(viewModelScope)
    }

    fun onAction(action: AccountsAction) {
        when (action) {
            is AccountsAction.OnAccountClick -> send(AccountsEvent.OpenAccount(action.id))
            AccountsAction.OnBackClick -> send(AccountsEvent.NavigateBack)
        }
    }

    private fun send(event: AccountsEvent) {
        viewModelScope.launch { _events.send(event) }
    }

    private fun build(accs: List<Account>, txs: List<Transaction>): AccountsState {
        val year = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year
        val summaries = calculator.summarize(accs, txs, year).associateBy { it.account.id }
        val rowsByAccount = txs.groupingBy { it.accountId }.eachCount()
        val latestBalance = txs.filter { it.balanceAfter != null }.groupBy { it.accountId }
            .mapValues { (_, rows) -> rows.maxWith(compareBy<Transaction>({ it.bookingDate }, { it.fileOrder })).balanceAfter!! }
        fun row(a: Account): AccountRowUi {
            val s = summaries[a.id]
            return AccountRowUi(
                id = a.id.value,
                name = a.name,
                type = a.type,
                institution = a.institution,
                rowCount = rowsByAccount[a.id] ?: 0,
                // Bank accounts show the balance after their latest booked row.
                value = (s?.value ?: latestBalance[a.id])?.let { MoneyFormatter.format(it, locale = locale) },
            )
        }
        val sorted = accs.sortedBy { it.name.lowercase() }
        val household = calculator.householdValue(summaries.values.toList())
        val missingValue = accs.count { it.type.hasMarketValue && it.marketValue == null }
        return AccountsState(
            savingsValue = MoneyFormatter.format(household, locale = locale),
            savingsNote = if (missingValue > 0) UiText.PluralResource(R.plurals.accounts_missing_value, missingValue) else null,
            bankAccounts = sorted.filter { !it.type.holdsSavings }.map(::row),
            savingsAccounts = sorted.filter { it.type.holdsSavings && it.type != AccountType.PENSION }.map(::row),
            pensionAccounts = sorted.filter { it.type == AccountType.PENSION }.map(::row),
            isLoading = false,
        )
    }
}
