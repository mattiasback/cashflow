package app.cashflow.feature.profile.ui.mortgage

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cashflow.core.domain.Result
import app.cashflow.core.domain.datasource.SettingsDataSource
import app.cashflow.core.domain.datasource.TransactionLocalDataSource
import app.cashflow.core.domain.model.Money
import app.cashflow.core.domain.model.MortgageSplit
import app.cashflow.core.domain.model.MortgageSplitMode
import app.cashflow.core.domain.model.Transaction
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Stable
data class MortgageState(
    val enabled: Boolean = false,
    val mode: MortgageSplitMode = MortgageSplitMode.AMORTIZATION_AMOUNT,
    val amount: String = "",
    val share: String = "",
    val pattern: String = "BOLÅN",
    /** "6 betalningar matchar · senaste 2026-09-28: 9 000 kr → ränta 6 500 kr, amortering 2 500 kr". */
    val preview: UiText? = null,
    val canSave: Boolean = false,
    val error: UiText? = null,
    val isLoading: Boolean = true,
)

sealed interface MortgageAction {
    data class OnEnabledToggle(val enabled: Boolean) : MortgageAction
    data class OnModeSelect(val mode: MortgageSplitMode) : MortgageAction
    data class OnAmountChange(val text: String) : MortgageAction
    data class OnShareChange(val text: String) : MortgageAction
    data class OnPatternChange(val text: String) : MortgageAction
    data object OnSaveClick : MortgageAction
}

sealed interface MortgageEvent {
    data object Dismiss : MortgageEvent
}

/**
 * "Räkna amortering som sparande": enter the monthly amortization or the interest share, and
 * see how the latest matching payment splits before saving.
 */
class MortgageViewModel(
    private val settings: SettingsDataSource,
    private val transactions: TransactionLocalDataSource,
) : ViewModel() {

    private val _state = MutableStateFlow(MortgageState())
    val state: StateFlow<MortgageState> = _state.asStateFlow()

    private val _events = Channel<MortgageEvent>()
    val events = _events.receiveAsFlow()

    private val locale = AppLocale.current()
    private var rows: List<Transaction> = emptyList()

    init {
        viewModelScope.launch {
            val m = settings.settings.first().mortgage
            rows = (transactions.getAll() as? Result.Success)?.data.orEmpty().filter { it.countsInCashFlow && it.amount.isNegative }
            refresh(
                MortgageState(
                    enabled = m.enabled,
                    mode = m.mode,
                    amount = if (m.amortization == Money.ZERO) "" else MoneyParser.editable(m.amortization),
                    share = if (m.interestSharePercent == 0) "" else m.interestSharePercent.toString(),
                    pattern = m.pattern,
                    isLoading = false,
                ),
            )
        }
    }

    fun onAction(action: MortgageAction) {
        when (action) {
            is MortgageAction.OnEnabledToggle -> refresh(_state.value.copy(enabled = action.enabled))
            is MortgageAction.OnModeSelect -> refresh(_state.value.copy(mode = action.mode))
            is MortgageAction.OnAmountChange -> refresh(_state.value.copy(amount = action.text))
            is MortgageAction.OnShareChange -> refresh(_state.value.copy(share = action.text.filter(Char::isDigit).take(3)))
            is MortgageAction.OnPatternChange -> refresh(_state.value.copy(pattern = action.text))
            MortgageAction.OnSaveClick -> save()
        }
    }

    private fun split(s: MortgageState): MortgageSplit? {
        val pattern = s.pattern.trim().uppercase()
        if (pattern.isEmpty()) return null
        return when (s.mode) {
            MortgageSplitMode.AMORTIZATION_AMOUNT -> MoneyParser.parse(s.amount)?.takeIf { !it.isNegative }
                ?.let { MortgageSplit(enabled = s.enabled, mode = s.mode, amortization = it, pattern = pattern) }
            MortgageSplitMode.INTEREST_SHARE -> s.share.toIntOrNull()?.takeIf { it in 0..100 }
                ?.let { MortgageSplit(enabled = s.enabled, mode = s.mode, interestSharePercent = it, pattern = pattern) }
        }
    }

    private fun refresh(s: MortgageState) {
        val split = split(s)
        val matching = split?.let { m -> rows.filter { m.matches(it.rawText) } }.orEmpty()
        val latest = matching.maxByOrNull { it.transactionDate }
        val preview = when {
            split == null -> null
            latest == null -> UiText.StringResource(R.string.mortgage_no_match)
            else -> {
                val paid = latest.amount.abs()
                val amort = split.copy(enabled = true).amortizationOf(paid)
                UiText.StringResource(
                    R.string.mortgage_preview,
                    arrayOf(
                        matching.size,
                        PeriodFormatter.iso(latest.transactionDate, locale),
                        MoneyFormatter.format(paid, locale = locale),
                        MoneyFormatter.format(paid - amort, locale = locale),
                        MoneyFormatter.format(amort, locale = locale),
                    ),
                )
            }
        }
        _state.value = s.copy(preview = preview, canSave = !s.enabled || split != null, error = null)
    }

    private fun save() {
        val s = _state.value
        val split = split(s) ?: if (!s.enabled) null else return
        viewModelScope.launch {
            val r = settings.update { current -> current.copy(mortgage = split ?: current.mortgage.copy(enabled = false)) }
            when (r) {
                is Result.Error -> _state.update { it.copy(error = r.error.toUiText()) }
                is Result.Success -> _events.send(MortgageEvent.Dismiss)
            }
        }
    }
}
