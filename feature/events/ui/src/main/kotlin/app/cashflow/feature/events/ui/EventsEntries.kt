package app.cashflow.feature.events.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.cashflow.feature.events.ui.detail.DetailPane
import app.cashflow.feature.events.ui.detail.TransactionDetailRoot
import app.cashflow.feature.events.ui.list.EventsRoot
import app.cashflow.feature.events.ui.review.ReviewRoot
import kotlinx.coroutines.flow.Flow

/**
 * The Händelser feature's destinations. It only takes lambdas: it never sees the app's
 * Navigator or another feature's routes. [sheetMetadata] marks the entries :app shows as
 * bottom sheets. On wide windows :app passes list-detail metadata instead: the list gets
 * [listMetadata] and a transaction opens in the pane beside it ([detailMetadata], [detailInPane]).
 */
fun EntryProviderScope<NavKey>.eventsEntries(
    contentPadding: PaddingValues,
    sheetMetadata: Map<String, Any>,
    listMetadata: Map<String, Any> = emptyMap(),
    detailMetadata: Map<String, Any> = sheetMetadata,
    detailInPane: Boolean = false,
    scrollToTop: Flow<Unit>,
    onOpenDetail: (String) -> Unit,
    onOpenImport: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenProfile: () -> Unit,
    onBack: () -> Unit,
) {
    entry<EventsRoute>(metadata = listMetadata) {
        EventsRoot(
            contentPadding = contentPadding,
            onOpenDetail = onOpenDetail,
            onOpenImport = onOpenImport,
            onOpenReview = onOpenReview,
            onOpenProfile = onOpenProfile,
            scrollToTop = scrollToTop,
        )
    }
    entry<CategoryEventsRoute>(metadata = listMetadata) { key ->
        EventsRoot(
            contentPadding = contentPadding,
            onOpenDetail = onOpenDetail,
            onOpenImport = onOpenImport,
            onOpenReview = onOpenReview,
            onOpenProfile = onOpenProfile,
            initialCategory = key.categoryId,
            initialPeriod = key.period,
        )
    }
    entry<ReviewRoute> {
        ReviewRoot(contentPadding = contentPadding, onBack = onBack)
    }
    entry<TransactionDetailRoute>(metadata = detailMetadata) { key ->
        if (detailInPane) {
            DetailPane { TransactionDetailRoot(txId = key.txId, onDismiss = onBack) }
        } else {
            TransactionDetailRoot(txId = key.txId, onDismiss = onBack)
        }
    }
}
