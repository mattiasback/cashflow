package app.cashflow.feature.home.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Hem tab root, the start destination. */
@Serializable
data object HomeRoute : NavKey

fun EntryProviderScope<NavKey>.homeEntries(
    contentPadding: PaddingValues,
    scrollToTop: Flow<Unit>,
    onOpenFlow: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenAccounts: () -> Unit,
) {
    entry<HomeRoute> {
        HomeRoot(
            contentPadding = contentPadding,
            onOpenFlow = onOpenFlow,
            onOpenReview = onOpenReview,
            onOpenImport = onOpenImport,
            onOpenProfile = onOpenProfile,
            onOpenAccounts = onOpenAccounts,
            scrollToTop = scrollToTop,
        )
    }
}

val homeUiModule = module {
    viewModel { HomeViewModel(get(), get(), get(), get(), get(), get()) }
}
