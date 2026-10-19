package app.cashflow.feature.insights.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Insikter tab root. */
@Serializable
data object InsightsRoute : NavKey

fun EntryProviderScope<NavKey>.insightsEntries(
    contentPadding: PaddingValues,
    scrollToTop: Flow<Unit>,
    onOpenSavings: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenImport: () -> Unit,
) {
    entry<InsightsRoute> {
        InsightsRoot(
            contentPadding = contentPadding,
            onOpenSavings = onOpenSavings,
            onOpenProfile = onOpenProfile,
            onOpenImport = onOpenImport,
            scrollToTop = scrollToTop,
        )
    }
}

val insightsUiModule = module {
    viewModel { InsightsViewModel(get(), get(), get(), get()) }
}
