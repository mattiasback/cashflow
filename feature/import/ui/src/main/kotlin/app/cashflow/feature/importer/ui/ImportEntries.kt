package app.cashflow.feature.importer.ui

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.cashflow.feature.importer.ui.entry.ImportRoot
import app.cashflow.feature.importer.ui.history.ImportHistoryRoot
import app.cashflow.feature.importer.ui.mapping.ColumnMappingRoot
import app.cashflow.feature.importer.ui.mapping.ColumnMappingViewModel
import app.cashflow.feature.importer.ui.history.ImportHistoryViewModel
import app.cashflow.feature.importer.ui.entry.ImportViewModel
import app.cashflow.feature.importer.ui.receipt.ImportReceiptRoot
import app.cashflow.feature.importer.ui.receipt.ImportReceiptViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Import destinations; navigation happens only through the lambdas. */
fun EntryProviderScope<NavKey>.importEntries(
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    sheetMetadata: Map<String, Any>,
    onShowReceipt: (List<String>) -> Unit,
    onOpenReview: () -> Unit,
    onOpenMapping: (String) -> Unit,
    onBack: () -> Unit,
) {
    entry<ImportRoute> { key ->
        ImportRoot(sharedUris = key.uris, onShowReceipt = onShowReceipt, onClose = onBack, onOpenMapping = onOpenMapping)
    }
    entry<ColumnMappingRoute> { key ->
        ColumnMappingRoot(uri = key.uri, onShowReceipt = onShowReceipt, onClose = onBack)
    }
    entry<ImportReceiptRoute>(metadata = sheetMetadata) { key ->
        ImportReceiptRoot(importIds = key.importIds, onDismiss = onBack, onOpenReview = onOpenReview)
    }
    entry<ImportHistoryRoute> {
        ImportHistoryRoot(contentPadding = contentPadding, onBack = onBack)
    }
}

val importUiModule = module {
    // Lambda forms: both take navigation arguments as parameters.
    viewModel { params -> ImportViewModel(params.get(), get(), get()) }
    viewModel { params -> ImportReceiptViewModel(params.get(), get(), get(), get(), get()) }
    viewModelOf(::ImportHistoryViewModel)
    viewModel { params -> ColumnMappingViewModel(params.get(), get(), get(), get(), get(), get()) }
}
