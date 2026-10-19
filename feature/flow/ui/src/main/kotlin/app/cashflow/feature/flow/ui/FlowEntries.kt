package app.cashflow.feature.flow.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Flöde tab root. */
@Serializable
data object FlowRoute : NavKey

fun EntryProviderScope<NavKey>.flowEntries(
    contentPadding: PaddingValues,
    scrollToTop: Flow<Unit>,
    onOpenEvents: (categoryId: String?, period: String?) -> Unit,
    onOpenImport: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    entry<FlowRoute> {
        FlowRoot(
            contentPadding = contentPadding,
            onOpenEvents = onOpenEvents,
            onOpenImport = onOpenImport,
            onOpenProfile = onOpenProfile,
            scrollToTop = scrollToTop,
        )
    }
}

val flowUiModule = module {
    viewModel { FlowViewModel(get(), get(), get(), get(), get()) }
}
