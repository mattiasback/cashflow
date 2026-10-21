package app.cashflow.feature.profile.ui.settings

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cashflow.core.domain.cashflow.SavingsCalculator
import app.cashflow.core.domain.datasource.AccountLocalDataSource
import app.cashflow.core.domain.datasource.GoalLocalDataSource
import app.cashflow.core.domain.datasource.ImportBatchLocalDataSource
import app.cashflow.core.domain.datasource.MerchantMemoryLocalDataSource
import app.cashflow.core.domain.datasource.RuleLocalDataSource
import app.cashflow.core.domain.datasource.SettingsDataSource
import app.cashflow.core.domain.datasource.TransactionLocalDataSource
import app.cashflow.core.domain.model.AppMode
import app.cashflow.core.domain.model.AppModeController
import app.cashflow.core.domain.model.DebugFlags
import app.cashflow.core.domain.model.Goal
import app.cashflow.core.domain.model.GoalType
import app.cashflow.core.domain.model.Goals
import app.cashflow.core.domain.model.MortgageSplitMode
import app.cashflow.core.domain.model.ThemeMode
import app.cashflow.core.ui.UiText
import app.cashflow.core.ui.format.AppLocale
import app.cashflow.core.ui.format.MoneyFormatter
import app.cashflow.core.ui.format.PeriodFormatter
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
import kotlin.math.roundToInt

@Stable
data class ProfileState(
    val accountsLine: UiText = UiText.DynamicString(""),
    val importsLine: UiText = UiText.DynamicString(""),
    val rulesLine: UiText = UiText.DynamicString(""),
    val mortgageEnabled: Boolean = false,
    val mortgageLine: UiText? = null,
    val percentDecimals: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val bufferGoalMonths: Int = GoalType.BUFFER_MONTHS.defaultTarget.toInt(),
    val savingsGoalPercent: Int = GoalType.SAVINGS_RATE.defaultTarget.toInt(),
    /** "50 000 kr den 2026-10-01" or "Inte angivet". */
    val bufferBalanceLine: UiText = UiText.DynamicString(""),
    val isLoading: Boolean = true,
    /** Showing the made-up demo household instead of your data. */
    val demo: Boolean = false,
    /** Debug builds only: hold reads back, or fail them. */
    val debugSlow: Boolean = false,
    val debugFail: Boolean = false,
)

sealed interface ProfileAction {
    data object OnAccountsClick : ProfileAction
    data object OnImportHistoryClick : ProfileAction
    data object OnRulesClick : ProfileAction
    data class OnMortgageToggle(val enabled: Boolean) : ProfileAction
    data object OnMortgageClick : ProfileAction
    data object OnBufferBalanceClick : ProfileAction
    data class OnPercentDecimalsToggle(val enabled: Boolean) : ProfileAction
    data class OnThemeSelect(val theme: ThemeMode) : ProfileAction
    /** Restarts the app on the demo household or back on your data. */
    data class OnDemoToggle(val enabled: Boolean) : ProfileAction
    data class OnDebugSlowToggle(val enabled: Boolean) : ProfileAction
    data class OnDebugFailToggle(val enabled: Boolean) : ProfileAction
    /** One step up (+1) or down (−1) for a goal; months for Buffert, percent for Sparkvot. */
    data class OnGoalStep(val type: GoalType, val step: Int) : ProfileAction
    data object OnBackClick : ProfileAction
}

sealed interface ProfileEvent {
    data object OpenAccounts : ProfileEvent
    data object OpenImportHistory : ProfileEvent
    data object OpenRules : ProfileEvent
    data object OpenMortgage : ProfileEvent
    data object OpenBufferBalance : ProfileEvent
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
    private val goals: GoalLocalDataSource,
    private val mode: AppModeController,
    private val debug: DebugFlags,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    private val _events = Channel<ProfileEvent>()
    val events = _events.receiveAsFlow()

    private val locale = AppLocale.current()
    private val savings = SavingsCalculator()

    init {
        val counts = combine(batches.observeBatches(), rules.observeUserRules(), memories.observeAll(), goals.observeGoals()) { b, r, m, g -> Counts(b, r, m, Goals.of(g)) }
        combine(settings.settings, accounts.observeAccounts(), transactions.observeAll(), counts) { prefs, accs, txs, (imports, userRules, remembered, goalValues) ->
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
                bufferGoalMonths = goalValues.bufferMonths.roundToInt(),
                savingsGoalPercent = goalValues.savingsRatePercent.roundToInt(),
                bufferBalanceLine = prefs.bufferBalance?.let { b ->
                    UiText.StringResource(R.string.profile_buffer_balance_line, arrayOf(MoneyFormatter.format(b.amount, locale = locale), PeriodFormatter.iso(b.date, locale)))
                } ?: UiText.StringResource(R.string.profile_buffer_balance_none),
                isLoading = false,
                demo = mode.mode == AppMode.DEMO,
            )
        }.onEach { s -> _state.update { s.copy(debugSlow = it.debugSlow, debugFail = it.debugFail) } }.launchIn(viewModelScope)
        combine(debug.slowLoading, debug.failReads) { slow, fail -> slow to fail }
            .onEach { (slow, fail) -> _state.update { it.copy(debugSlow = slow, debugFail = fail) } }
            .launchIn(viewModelScope)
    }

    fun onAction(action: ProfileAction) {
        when (action) {
            ProfileAction.OnAccountsClick -> send(ProfileEvent.OpenAccounts)
            ProfileAction.OnImportHistoryClick -> send(ProfileEvent.OpenImportHistory)
            ProfileAction.OnRulesClick -> send(ProfileEvent.OpenRules)
            ProfileAction.OnMortgageClick -> send(ProfileEvent.OpenMortgage)
            ProfileAction.OnBufferBalanceClick -> send(ProfileEvent.OpenBufferBalance)
            ProfileAction.OnBackClick -> send(ProfileEvent.NavigateBack)
            is ProfileAction.OnMortgageToggle -> {
                update { it.copy(mortgage = it.mortgage.copy(enabled = action.enabled)) }
                // Turning it on needs an amount or a share, so the sheet opens right away.
                if (action.enabled) send(ProfileEvent.OpenMortgage)
            }
            is ProfileAction.OnPercentDecimalsToggle -> update { it.copy(percentDecimals = action.enabled) }
            is ProfileAction.OnThemeSelect -> update { it.copy(theme = action.theme) }
            is ProfileAction.OnDebugSlowToggle -> debug.setSlowLoading(action.enabled)
            is ProfileAction.OnDebugFailToggle -> debug.setFailReads(action.enabled)
            is ProfileAction.OnDemoToggle -> mode.switchTo(if (action.enabled) AppMode.DEMO else AppMode.PRIVATE)
            is ProfileAction.OnGoalStep -> {
                val current = when (action.type) {
                    GoalType.BUFFER_MONTHS -> _state.value.bufferGoalMonths
                    GoalType.SAVINGS_RATE -> _state.value.savingsGoalPercent
                }.toDouble()
                val next = (current + action.step).coerceIn(action.type.range)
                if (next != current) viewModelScope.launch { goals.setGoal(Goal(action.type, next)) }
            }
        }
    }

    private fun update(transform: (app.cashflow.core.domain.model.AppSettings) -> app.cashflow.core.domain.model.AppSettings) {
        viewModelScope.launch { settings.update(transform) }
    }

    private fun send(event: ProfileEvent) {
        viewModelScope.launch { _events.send(event) }
    }
}

private data class Counts(
    val imports: List<app.cashflow.core.domain.model.ImportBatch>,
    val rules: List<app.cashflow.core.domain.model.CategoryRule>,
    val memories: List<app.cashflow.core.domain.model.MerchantMemory>,
    val goals: Goals,
)
