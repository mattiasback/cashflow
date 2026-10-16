package app.cashflow.feature.profile.ui.settings

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cashflow.core.domain.cashflow.SavingsCalculator
import app.cashflow.core.domain.datasource.AccountLocalDataSource
import app.cashflow.core.domain.datasource.ImportBatchLocalDataSource
import app.cashflow.core.domain.datasource.MerchantMemoryLocalDataSource
import app.cashflow.core.domain.datasource.RuleLocalDataSource
import app.cashflow.core.domain.datasource.SettingsDataSource
import app.cashflow.core.domain.datasource.TransactionLocalDataSource
import app.cashflow.core.domain.model.MortgageSplitMode
import app.cashflow.core.domain.model.ThemeMode
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
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Stable
data class ProfileState(
    val accountsLine: UiText = UiText.DynamicString(""),
    val importsLine: UiText = UiText.DynamicString(""),
    val rulesLine: UiText = UiText.DynamicString(""),
    val mortgageEnabled: Boolean = false,
    val mortgageLine: UiText? = null,
    val percentDecimals: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val isLoading: Boolean = true,
)

sealed interface ProfileAction {
    data object OnAccountsClick : ProfileAction
    data object OnImportHistoryClick : ProfileAction
    data object OnRulesClick : ProfileAction
    data class OnMortgageToggle(val enabled: Boolean) : ProfileAction
    data object OnMortgageClick : ProfileAction
    data class OnPercentDecimalsToggle(val enabled: Boolean) : ProfileAction
    data class OnThemeSelect(val theme: ThemeMode) : ProfileAction
    data object OnBackClick : ProfileAction
}

sealed interface ProfileEvent {
    data object OpenAccounts : ProfileEvent
    data object OpenImportHistory : ProfileEvent
    data object OpenRules : ProfileEvent
    data object OpenMortgage : ProfileEvent
    data object NavigateBack : ProfileEvent
}

/** Profil for step 2: accounts and imports, calculations, rules and display settings. */
class ProfileViewModel(
    private val settings: SettingsDataSource,
    accounts: AccountLocalDataSource,
    transactions: TransactionLocalDataSource,
    batches: ImportBatchLocalDataSource,
    rules: RuleLocalDataSource,
    memories: MerchantMemoryLocalDataSource,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    private val _events = Channel<ProfileEvent>()
    val events = _events.receiveAsFlow()

    private val locale = AppLocale.current()
    private val savings = SavingsCalculator()

    init {
        val counts = combine(batches.observeBatches(), rules.observeUserRules(), memories.observeAll()) { b, r, m -> Triple(b, r, m) }
        combine(settings.settings, accounts.observeAccounts(), transactions.observeAll(), counts) { prefs, accs, txs, (imports, userRules, remembered) ->
            val year = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year
            val household = savings.householdValue(savings.summarize(accs, txs, year))
            val mortgage = prefs.mortgage
            ProfileState(
                accountsLine = UiText.StringResource(R.string.profile_accounts_line, arrayOf(accs.size, MoneyFormatter.format(household, locale = locale))),
                importsLine = UiText.PluralResource(R.plurals.profile_imports_line, imports.count { it.undoneAt == null }),
                rulesLine = UiText.StringResource(R.string.profile_rules_line, arrayOf(userRules.size, remembered.size)),
                mortgageEnabled = mortgage.enabled,
                mortgageLine = if (!mortgage.enabled) null else when (mortgage.mode) {
                    MortgageSplitMode.AMORTIZATION_AMOUNT ->
                        UiText.StringResource(R.string.profile_mortgage_amount, arrayOf(MoneyFormatter.format(mortgage.amortization, locale = locale)))
                    MortgageSplitMode.INTEREST_SHARE -> UiText.StringResource(R.string.profile_mortgage_share, arrayOf(mortgage.interestSharePercent))
                },
                percentDecimals = prefs.percentDecimals,
                theme = prefs.theme,
                isLoading = false,
            )
        }.onEach { s -> _state.update { s } }.launchIn(viewModelScope)
    }

    fun onAction(action: ProfileAction) {
        when (action) {
            ProfileAction.OnAccountsClick -> send(ProfileEvent.OpenAccounts)
            ProfileAction.OnImportHistoryClick -> send(ProfileEvent.OpenImportHistory)
            ProfileAction.OnRulesClick -> send(ProfileEvent.OpenRules)
            ProfileAction.OnMortgageClick -> send(ProfileEvent.OpenMortgage)
            ProfileAction.OnBackClick -> send(ProfileEvent.NavigateBack)
            is ProfileAction.OnMortgageToggle -> {
                update { it.copy(mortgage = it.mortgage.copy(enabled = action.enabled)) }
                // Turning it on needs an amount or a share, so the sheet opens right away.
                if (action.enabled) send(ProfileEvent.OpenMortgage)
            }
            is ProfileAction.OnPercentDecimalsToggle -> update { it.copy(percentDecimals = action.enabled) }
            is ProfileAction.OnThemeSelect -> update { it.copy(theme = action.theme) }
        }
    }

    private fun update(transform: (app.cashflow.core.domain.model.AppSettings) -> app.cashflow.core.domain.model.AppSettings) {
        viewModelScope.launch { settings.update(transform) }
    }

    private fun send(event: ProfileEvent) {
        viewModelScope.launch { _events.send(event) }
    }
}
