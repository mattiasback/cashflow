package app.cashflow.feature.home.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import app.cashflow.core.designsystem.component.TrendDirection
import app.cashflow.core.ui.DetailSheetUi
import app.cashflow.core.ui.UiText

/** Hem's four key figures, also the keys of their sheets. */
enum class KpiId { INCOME, EXPENSES, SAVINGS_RATE, BUFFER }

@Immutable
data class KpiUi(
    val id: KpiId,
    val label: UiText,
    val value: String,
    val spoken: UiText,
    val trend: TrendDirection? = null,
    val trendGood: Boolean? = null,
    /** 0..1 progress toward the goal; null for figures without a goal. */
    val goal: Float? = null,
    val goalReached: Boolean = false,
    /** A line of context under the value, for figures without a goal. */
    val sub: UiText? = null,
)

/** Which Flöde sub-tab a trend row opens (the Flöde tab keys). */
enum class TrendId(val flowTab: String) { NET("cashflow"), INCOME("income"), EXPENSES("expense"), SAVINGS_RATE("savings") }

@Immutable
data class TrendUi(
    val id: TrendId,
    val label: UiText,
    val value: UiText,
    val points: List<Float>,
    val tone: TrendTone,
    val change: String,
    val good: Boolean?,
    val spoken: UiText,
)

/** Colour of a trend line: Nettokassaflöde uses the Flöde accent, the rest their flow colour. */
enum class TrendTone { Cashflow, Income, Expenses, Savings }

enum class TodoKind { REVIEW, DUPLICATES, IMPORT, ALL_DONE }

@Immutable
data class TodoUi(val kind: TodoKind, val eyebrow: UiText, val title: UiText, val text: UiText, val action: UiText)

/** Where a sheet's button goes. */
sealed interface HomeTarget {
    data class Flow(val tab: TrendId) : HomeTarget
    data object Accounts : HomeTarget
    data object Review : HomeTarget
}

@Immutable
data class HomeSheetUi(val detail: DetailSheetUi, val target: HomeTarget)

enum class HomeSheet { NET, INCOME, EXPENSES, SAVINGS_RATE, BUFFER }

@Stable
data class HomeState(
    val summaryLabel: UiText = UiText.DynamicString(""),
    val summaryValue: String = "",
    val summarySpoken: String = "",
    val delta: UiText? = null,
    val deltaPositive: Boolean = true,
    val kpis: List<KpiUi> = emptyList(),
    val trends: List<TrendUi> = emptyList(),
    val todo: List<TodoUi> = emptyList(),
    val footer: UiText? = null,
    val sheet: HomeSheetUi? = null,
    val hasData: Boolean = false,
    val isLoading: Boolean = true,
    /** A note when the demo household or the empty app is shown, not your data. */
    val modeNote: UiText? = null,
    /** The data could not be read; the screen offers "Försök igen". */
    val loadError: Boolean = false,
)

sealed interface HomeAction {
    data object OnRetry : HomeAction
    /** First start: try the app on the demo household instead of importing. */
    data object OnTryDemo : HomeAction
    data object OnInfoClick : HomeAction
    data class OnKpiClick(val id: KpiId) : HomeAction
    data class OnTrendClick(val id: TrendId) : HomeAction
    data class OnTodoClick(val kind: TodoKind) : HomeAction
    data object OnSheetCta : HomeAction
    data object OnSheetDismiss : HomeAction
    data object OnScopePrivate : HomeAction
    data object OnProfileClick : HomeAction
    data object OnImportClick : HomeAction
}

sealed interface HomeEvent {
    /** Flöde opens on this sub-tab and the latest month (stored in settings first). */
    data object OpenFlow : HomeEvent
    data object OpenReview : HomeEvent
    data object OpenImport : HomeEvent
    data object OpenProfile : HomeEvent
    data object OpenAccounts : HomeEvent
    data class ShowMessage(val text: UiText) : HomeEvent
}

