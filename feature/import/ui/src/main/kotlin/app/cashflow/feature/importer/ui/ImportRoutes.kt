package app.cashflow.feature.importer.ui

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Import entry. [uris] is set when files arrive through a validated share intent. */
@Serializable
data class ImportRoute(val uris: List<String> = emptyList()) : NavKey

/** The receipt of one import run, shown as a bottom sheet. */
@Serializable
data class ImportReceiptRoute(val importIds: List<String>) : NavKey

/** "Mappa kolumner" for a file whose format the app does not know. */
@Serializable
data class ColumnMappingRoute(val uri: String) : NavKey

/** Importhistorik: every import, with undo and redo. */
@Serializable
data object ImportHistoryRoute : NavKey

/** MIME types the picker offers: Excel 97–2003, .xlsx (rejected with a clear error for now) and CSV. */
val StatementMimeTypes = arrayOf(
    "application/vnd.ms-excel",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "text/csv",
    "text/comma-separated-values",
    "application/octet-stream",
)
