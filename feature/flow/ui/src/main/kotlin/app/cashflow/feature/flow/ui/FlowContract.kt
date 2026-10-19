package app.cashflow.feature.flow.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import app.cashflow.core.domain.cashflow.PeriodMode
import app.cashflow.core.ui.DetailSheetUi
import app.cashflow.core.ui.FlowTone
import app.cashflow.core.ui.SheetRowUi
import app.cashflow.core.ui.UiText

/** The four Flöde sub-tabs. [key] is what is stored in settings. */
enum class FlowTab(val key: String) {
    CASHFLOW("cashflow"), INCOME("income"), EXPENSE("expense"), SAVINGS("savings");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: CASHFLOW
    }
}

@Immutable
data class TileUi(
    val label: UiText,
    val value: UiText,
    val sub: UiText?,
    /** 0..1 fill of the goal bar, or null for no bar. */
    val goal: Float? = null,
)

/** One value in the chart head: "Inkomst 48 200 kr". [tone] null = no dot. */
@Immutable
data class ChartKeyUi(val label: UiText, val amount: String, val tone: FlowTone?, val negative: Boolean = false)

@Immutable
data class BarSegmentUi(val value: Float, val tone: FlowTone)

@Immutable
data class BarGroupUi(
    val key: String,
    val label: String,
    val mark: String?,
    val bars: List<List<BarSegmentUi>>,
    val deficit: Boolean,
    val spoken: UiText,
)

@Immutable
data class LineUi(
    val series: List<Pair<FlowTone, List<Float>>>,
    val selectedIndex: Int,
    val ticks: List<Pair<Int, String>>,
    val overspend: Boolean,
    val area: Boolean,
)

@Immutable
data class DonutSegmentUi(
    val id: String,
    val label: UiText,
    val amount: String,
    val share: String,
    val value: Float,
    val tone: FlowTone,
    /** Shade tier inside one colour family: 1, .78, .6 … */
    val alpha: Float,
    val leaf: Boolean,
    val spoken: UiText,
)

@Immutable
data class DonutUi(
    val segments: List<DonutSegmentUi>,
    val centerLabel: UiText,
    val centerValue: String,
    /** The label of the level shown, null at the top level. */
    val parentLabel: UiText?,
    val parentId: String?,
    /** "Sparat mer än kassaflödet": shown when the period took money from the account. */
    val deficitText: UiText?,
)

@Immutable
data class SankeyNodeUiModel(val id: String, val label: UiText, val amount: String, val tone: FlowTone, val column: Int)

@Immutable
data class SankeyUi(
    val nodes: List<SankeyNodeUiModel>,
    val links: List<Triple<String, String, Float>>,
    val spoken: UiText,
)

@Immutable
data class CategoryBarUi(
    val id: String,
    val name: UiText,
    val amount: String,
    val share: String,
    val fraction: Float,
    val tone: FlowTone,
    val spokenAmount: String,
    /** "↑ 12 %" against the 12-month average, null when it barely moved. */
    val trend: String? = null,
    val trendBetter: Boolean = true,
)

@Immutable
data class LatestTxUi(val id: String, val title: String, val sub: UiText, val amount: String, val tone: FlowTone)

/** Target of a node sheet's button. */
sealed interface SheetCta {
    data class Tab(val tab: FlowTab) : SheetCta
    data class Events(val categoryId: String?) : SheetCta
    data object Close : SheetCta
}

@Immutable
data class NodeSheetUi(
    val eyebrow: UiText,
    val title: UiText,
    val figure: String,
    val description: UiText?,
    val shareALabel: UiText,
    val shareA: String,
    val shareBLabel: UiText,
    val shareB: String,
    val bar: Float,
    val barTone: FlowTone,
    val rows: List<SheetRowUi>,
    val cta: UiText,
    val action: SheetCta,
) {
    fun toDetail() = DetailSheetUi(eyebrow, title, figure, description, shareALabel, shareA, shareBLabel, shareB, bar, barTone, rows, cta)
}

@Immutable
data class PickerChipUi(val key: String, val label: String, val selected: Boolean, val enabled: Boolean = true, val spoken: String = label)

@Immutable
data class PickerUi(
    val years: List<PickerChipUi>,
    val months: List<PickerChipUi>,
    val allText: UiText?,
)

@Stable
data class FlowState(
    val tab: FlowTab = FlowTab.CASHFLOW,
    val mode: PeriodMode = PeriodMode.MONTH,
    /** The period pill on the band; null in Alla (the pill is hidden). */
    val periodPill: String? = null,
    val summaryLabel: UiText = UiText.DynamicString(""),
    val summaryValue: String = "",
    val summarySpoken: String = "",
    val delta: UiText? = null,
    val deltaPositive: Boolean = true,
    val tiles: List<TileUi> = emptyList(),
    val chartTitle: String = "",
    val chartKeys: List<ChartKeyUi> = emptyList(),
    val barGroups: List<BarGroupUi> = emptyList(),
    val selectedBar: String? = null,
    val average: Float? = null,
    val line: LineUi? = null,
    val lineSpoken: String = "",
    val donut: DonutUi? = null,
    val sankey: SankeyUi? = null,
    val categoryBars: List<CategoryBarUi> = emptyList(),
    val barsTotal: String = "",
    val latest: List<LatestTxUi> = emptyList(),
    val picker: PickerUi? = null,
    val sheet: NodeSheetUi? = null,
    val hasData: Boolean = false,
    val isLoading: Boolean = true,
    /** The data could not be read; the screen offers "Försök igen". */
    val loadError: Boolean = false,
)

sealed interface FlowAction {
    data object OnRetry : FlowAction
    data class OnTabSelect(val tab: FlowTab) : FlowAction
    data class OnModeSelect(val mode: PeriodMode) : FlowAction
    /** A bar group (month or year key) or a picker chip. */
    data class OnPeriodSelect(val key: String) : FlowAction
    /** Scrub in the Alla line chart. */
    data class OnScrub(val index: Int) : FlowAction
    data object OnPreviousPeriod : FlowAction
    data object OnNextPeriod : FlowAction
    data object OnPickerOpen : FlowAction
    data class OnPickerYear(val year: Int) : FlowAction
    data object OnPickerLatest : FlowAction
    data object OnPickerClose : FlowAction
    data object OnInfoClick : FlowAction
    data class OnDonutSegment(val id: String) : FlowAction
    data object OnDonutBack : FlowAction
    data class OnNodeClick(val id: String) : FlowAction
    data class OnCategoryClick(val categoryId: String) : FlowAction
    data object OnSheetCta : FlowAction
    data object OnSheetDismiss : FlowAction
    data object OnShowAllEvents : FlowAction
    /** A row under Senaste transaktioner: opens the transaction, where it can be edited. */
    data class OnTransactionClick(val id: String) : FlowAction
    data object OnImportClick : FlowAction
    data object OnProfileClick : FlowAction
}

sealed interface FlowEvent {
    data class OpenEvents(val categoryId: String?, val period: String?) : FlowEvent
    data object OpenImport : FlowEvent
    data class OpenTransaction(val id: String) : FlowEvent
    data object OpenProfile : FlowEvent
}
