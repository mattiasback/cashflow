package app.cashflow.feature.events.ui.list

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import app.cashflow.core.ui.FlowTone
import app.cashflow.core.ui.UiText
import app.cashflow.feature.events.ui.common.TxDayUi

enum class StatusFilter { ALL, REVIEW, AUTO, MANUAL }

enum class EventsSheet { CATEGORY, PERIOD }

@Immutable
data class CategoryOptionUi(
    /** null = all categories. */
    val id: String?,
    val name: UiText,
    val group: UiText?,
    val tone: FlowTone?,
    val count: Int,
    val selected: Boolean,
)

@Immutable
data class PeriodOptionUi(
    /** null = all time. */
    val key: String?,
    val label: UiText,
    val selected: Boolean,
)

@Immutable
data class LastImportUi(
    val fileName: String,
    val meta: UiText,
)

@Stable
data class EventsState(
    val days: List<TxDayUi> = emptyList(),
    val query: String = "",
    val statusFilter: StatusFilter = StatusFilter.ALL,
    val categoryLabel: UiText? = null,
    val categoryActive: Boolean = false,
    val periodLabel: UiText? = null,
    val periodActive: Boolean = false,
    val countText: UiText? = null,
    val reviewCount: Int = 0,
    val reviewPayees: Int = 0,
    val lastImport: LastImportUi? = null,
    val hasTransactions: Boolean = false,
    val sheet: EventsSheet? = null,
    val categoryOptions: List<CategoryOptionUi> = emptyList(),
    val periodOptions: List<PeriodOptionUi> = emptyList(),
    val isLoading: Boolean = true,
    val error: UiText? = null,
)

sealed interface EventsAction {
    data class OnQueryChange(val query: String) : EventsAction
    data class OnStatusChipClick(val filter: StatusFilter) : EventsAction
    data object OnCategoryFilterClick : EventsAction
    data object OnPeriodFilterClick : EventsAction
    data class OnCategoryPicked(val id: String?) : EventsAction
    data class OnPeriodPicked(val key: String?) : EventsAction
    data object OnSheetDismiss : EventsAction
    data class OnTransactionClick(val id: String) : EventsAction
    data object OnImportClick : EventsAction
    data object OnReviewClick : EventsAction
    data object OnProfileClick : EventsAction
}

sealed interface EventsEvent {
    data class NavigateToDetail(val txId: String) : EventsEvent
    data object OpenImport : EventsEvent
    data object OpenReview : EventsEvent
    data object OpenProfile : EventsEvent
}
