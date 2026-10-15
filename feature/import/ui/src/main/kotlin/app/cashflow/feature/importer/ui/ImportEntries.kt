package app.cashflow.feature.importer.ui

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.cashflow.feature.importer.ui.entry.ImportRoot
import app.cashflow.feature.importer.ui.entry.ImportViewModel
import app.cashflow.feature.importer.ui.receipt.ImportReceiptRoot
import app.cashflow.feature.importer.ui.receipt.ImportReceiptViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Import destinations; navigation happens only through the lambdas. */
fun EntryProviderScope<NavKey>.importEntries(
    sheetMetadata: Map<String, Any>,
    onShowReceipt: (List<String>) -> Unit,
    onOpenReview: () -> Unit,
    onBack: () -> Unit,
) {
    entry<ImportRoute> { key ->
        ImportRoot(sharedUris = key.uris, onShowReceipt = onShowReceipt, onClose = onBack)
    }
    entry<ImportReceiptRoute>(metadata = sheetMetadata) { key ->
        ImportReceiptRoot(importIds = key.importIds, onDismiss = onBack, onOpenReview = onOpenReview)
    }
}

val importUiModule = module {
    // Lambda forms: both take navigation arguments as parameters.
    viewModel { params -> ImportViewModel(params.get(), get(), get()) }
    viewModel { params -> ImportReceiptViewModel(params.get(), get(), get(), get()) }
}
