package app.cashflow.feature.profile.ui.buffer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cashflow.core.domain.cashflow.Buffer
import app.cashflow.core.domain.datasource.SettingsDataSource
import app.cashflow.core.domain.datasource.TransactionLocalDataSource
import app.cashflow.core.domain.model.BufferBalance
import app.cashflow.core.ui.UiText
import app.cashflow.core.ui.format.AppLocale
import app.cashflow.core.ui.format.MoneyFormatter
import app.cashflow.core.ui.format.MoneyParser
import app.cashflow.core.ui.format.PeriodFormatter
import app.cashflow.feature.profile.ui.R
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

data class BufferBalanceState(
    val amount: String = "",
    /** "Angivet 2026-10-19 · i dag 128 000 kr" for a stored balance; null when none is stored. */
    val stored: UiText? = null,
    val canSave: Boolean = false,
    val canClear: Boolean = false,
)

sealed interface BufferBalanceAction {
    data class OnAmountChange(val text: String) : BufferBalanceAction
    data object OnSaveClick : BufferBalanceAction
    data object OnClearClick : BufferBalanceAction
}

sealed interface BufferBalanceEvent {
    data object Dismiss : BufferBalanceEvent
}

/**
 * The buffer balance, entered by hand since the exports only show flows. It is saved as today's
 * balance; Insikter then adds what moves into and out of Buffert after today.
 */
class BufferBalanceViewModel(
    private val settings: SettingsDataSource,
    transactions: TransactionLocalDataSource,
    private val today: () -> LocalDate = { Clock.System.todayIn(TimeZone.currentSystemDefault()) },
) : ViewModel() {

    private val locale = AppLocale.current()
    private val _state = MutableStateFlow(BufferBalanceState())
    val state: StateFlow<BufferBalanceState> = _state.asStateFlow()

    private val _events = Channel<BufferBalanceEvent>()
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val stored = settings.settings.first().bufferBalance
            _state.update { it.copy(amount = stored?.amount?.let(MoneyParser::editable).orEmpty(), canSave = stored != null) }
        }
        combine(settings.settings, transactions.observeAll()) { prefs, txs ->
            prefs.bufferBalance?.let { b ->
                UiText.StringResource(
                    R.string.buffer_balance_stored,
                    arrayOf(PeriodFormatter.iso(b.date, locale), MoneyFormatter.format(Buffer.estimate(b, txs) ?: b.amount, locale = locale)),
                )
            }
        }.onEach { line -> _state.update { it.copy(stored = line, canClear = line != null) } }.launchIn(viewModelScope)
    }

    fun onAction(action: BufferBalanceAction) {
        when (action) {
            is BufferBalanceAction.OnAmountChange -> _state.update {
                it.copy(amount = action.text, canSave = MoneyParser.parse(action.text)?.let { m -> m.ore >= 0 } == true)
            }
            BufferBalanceAction.OnSaveClick -> {
                val amount = MoneyParser.parse(_state.value.amount)?.takeIf { it.ore >= 0 } ?: return
                save(BufferBalance(amount, today()))
            }
            BufferBalanceAction.OnClearClick -> save(null)
        }
    }

    private fun save(balance: BufferBalance?) {
        viewModelScope.launch {
            settings.update { it.copy(bufferBalance = balance) }
            _events.send(BufferBalanceEvent.Dismiss)
        }
    }
}
