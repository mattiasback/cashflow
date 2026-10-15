package app.cashflow.feature.events.ui

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Händelser tab root. */
@Serializable
data object EventsRoute : NavKey

/** Händelser filtered on one category and month, opened from Flöde's category bars. */
@Serializable
data class CategoryEventsRoute(val categoryId: String, val period: String?) : NavKey

/** Review by payee: one decision clears many rows. */
@Serializable
data object ReviewRoute : NavKey

/** Transaction detail, shown as a bottom sheet. */
@Serializable
data class TransactionDetailRoute(val txId: String) : NavKey
