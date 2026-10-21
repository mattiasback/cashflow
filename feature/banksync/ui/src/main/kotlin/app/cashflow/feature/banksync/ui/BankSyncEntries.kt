package app.cashflow.feature.banksync.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Profil → Bankkoppling (step 8, optional). */
@Serializable
data object BankSyncRoute : NavKey

/**
 * The bank's answer (`cashflow://bank-callback?code=…&state=…`), handed over by the activity.
 * Untrusted until [app.cashflow.feature.banksync.domain.ConnectBankUseCase] has matched its state.
 */
class BankCallbacks {
    private val _urls = MutableStateFlow<String?>(null)
    val urls: StateFlow<String?> = _urls.asStateFlow()

    fun deliver(url: String) {
        _urls.value = url
    }

    fun consume() {
        _urls.value = null
    }

    companion object {
        const val SCHEME = "cashflow"
        const val HOST = "bank-callback"
    }
}

fun EntryProviderScope<NavKey>.bankSyncEntries(contentPadding: PaddingValues, onBack: () -> Unit) {
    entry<BankSyncRoute> {
        BankSyncRoot(contentPadding = contentPadding, onBack = onBack)
    }
}

val bankSyncUiModule = module {
    single { BankCallbacks() }
    // Lambda form: the clock keeps its default.
    viewModel { BankSyncViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
}
