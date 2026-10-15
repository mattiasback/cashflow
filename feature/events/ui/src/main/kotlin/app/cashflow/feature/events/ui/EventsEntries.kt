package app.cashflow.feature.events.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.cashflow.feature.events.ui.detail.TransactionDetailRoot
import app.cashflow.feature.events.ui.list.EventsRoot
import app.cashflow.feature.events.ui.review.ReviewRoot
import kotlinx.coroutines.flow.Flow

/**
 * The Händelser feature's destinations. It only takes lambdas: it never sees the app's
 * Navigator or another feature's routes. [sheetMetadata] marks the entries :app shows as
 * bottom sheets.
 */
fun EntryProviderScope<NavKey>.eventsEntries(
    contentPadding: PaddingValues,
    sheetMetadata: Map<String, Any>,
    scrollToTop: Flow<Unit>,
    onOpenDetail: (String) -> Unit,
    onOpenImport: () -> Unit,
    onOpenReview: () -> Unit,
    onBack: () -> Unit,
) {
    entry<EventsRoute> {
        EventsRoot(
            contentPadding = contentPadding,
            onOpenDetail = onOpenDetail,
            onOpenImport = onOpenImport,
            onOpenReview = onOpenReview,
            scrollToTop = scrollToTop,
        )
    }
    entry<CategoryEventsRoute> { key ->
        EventsRoot(
            contentPadding = contentPadding,
            onOpenDetail = onOpenDetail,
            onOpenImport = onOpenImport,
            onOpenReview = onOpenReview,
            initialCategory = key.categoryId,
            initialPeriod = key.period,
        )
    }
    entry<ReviewRoute> {
        ReviewRoot(contentPadding = contentPadding, onBack = onBack)
    }
    entry<TransactionDetailRoute>(metadata = sheetMetadata) { key ->
        TransactionDetailRoot(txId = key.txId, onDismiss = onBack)
    }
}
