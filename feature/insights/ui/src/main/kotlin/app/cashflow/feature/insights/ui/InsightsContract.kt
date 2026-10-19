package app.cashflow.feature.insights.ui

import androidx.compose.runtime.Immutable
import app.cashflow.core.ui.UiText

enum class OpportunityIcon { Subscriptions, SmallCharges, Category }

@Immutable
data class OpportunityUi(
    val id: String,
    val icon: OpportunityIcon,
    val title: UiText,
    val sub: UiText,
    val gain: String,
    val effort: UiText,
    val done: Boolean,
    val spoken: UiText,
)

@Immutable
data class WhatIfUi(
    /** Extra per month, in steps of 500 kr from 0 to 10 000 kr. */
    val extraKronor: Int,
    val extraText: String,
    /** "Bufferten full"; null when no buffer balance is entered. */
    val bufferFull: UiText?,
    val bufferFullSub: UiText?,
    val rate: String,
    val rateNow: UiText,
    val funds: String,
    val fundsSub: UiText,
    val note: UiText,
    val spoken: UiText,
)

@Immutable
data class BenchmarkUi(val label: UiText, val value: String, val fraction: Float, val tone: BenchTone, val note: UiText)

enum class BenchTone { Savings, Fixed }

/** The explanation sheet for a suggestion or the hero figure. */
@Immutable
data class InsightSheetUi(
    val eyebrow: UiText,
    val title: UiText,
    val figure: String?,
    val description: UiText?,
    val why: UiText,
    val assume: UiText?,
    val how: UiText,
    /** The main button; null when there is nothing to mark (the hero's sheet only closes). */
    val done: UiText?,
)

sealed interface InsightSheet {
    data object About : InsightSheet
    data class Suggestion(val id: String) : InsightSheet
}

@Immutable
data class InsightsState(
    val isLoading: Boolean = true,
    val hasData: Boolean = false,
    /** At least three months: averages and recurring charges need some history. */
    val enoughData: Boolean = false,
    val gain: String = "",
    val gainDelta: UiText? = null,
    val gainSpoken: UiText? = null,
    val opportunities: List<OpportunityUi> = emptyList(),
    val whatIf: WhatIfUi? = null,
    val benchmarks: List<BenchmarkUi> = emptyList(),
    /** The anonymous summary for "Fråga en AI": no names, account numbers or payees. */
    val prompt: UiText? = null,
    val copied: Boolean = false,
    val sheet: InsightSheetUi? = null,
)

sealed interface InsightsAction {
    data class OnOpportunityClick(val id: String) : InsightsAction
    data object OnAboutClick : InsightsAction
    data object OnSheetDone : InsightsAction
    data object OnSheetDismiss : InsightsAction
    data class OnExtraChange(val kronor: Int) : InsightsAction
    data class OnCopy(val text: String) : InsightsAction
    data class OnShare(val text: String) : InsightsAction
    data object OnProfileClick : InsightsAction
    data object OnImportClick : InsightsAction
}

sealed interface InsightsEvent {
    data class Copy(val text: String) : InsightsEvent
    data class Share(val text: String) : InsightsEvent
    data object OpenProfile : InsightsEvent
    data object OpenImport : InsightsEvent
}
