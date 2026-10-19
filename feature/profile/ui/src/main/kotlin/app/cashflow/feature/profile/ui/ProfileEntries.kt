package app.cashflow.feature.profile.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.cashflow.feature.profile.ui.accounts.AccountSheetRoot
import app.cashflow.feature.profile.ui.accounts.AccountSheetViewModel
import app.cashflow.feature.profile.ui.accounts.AccountsRoot
import app.cashflow.feature.profile.ui.accounts.AccountsViewModel
import app.cashflow.feature.profile.ui.buffer.BufferBalanceSheetRoot
import app.cashflow.feature.profile.ui.buffer.BufferBalanceViewModel
import app.cashflow.feature.profile.ui.mortgage.MortgageSheetRoot
import app.cashflow.feature.profile.ui.mortgage.MortgageViewModel
import app.cashflow.feature.profile.ui.settings.ProfileRoot
import app.cashflow.feature.profile.ui.settings.ProfileViewModel
import kotlinx.serialization.Serializable
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Profil: settings, accounts and the way into import history and rules. */
@Serializable
data object ProfileRoute : NavKey

/** Every account: bank, savings and investments. */
@Serializable
data object AccountsRoute : NavKey

/** One account as a bottom sheet: name, type, value, return and cost. */
@Serializable
data class AccountRoute(val accountId: String) : NavKey

/** "Räkna amortering som sparande": how a mortgage payment splits. */
@Serializable
data object MortgageRoute : NavKey

/** The buffer's balance, entered by hand: Insikter follows the buffer from it. */
@Serializable
data object BufferBalanceRoute : NavKey

/** Profile destinations. They only take lambdas; :app maps them to its Navigator. */
fun EntryProviderScope<NavKey>.profileEntries(
    contentPadding: PaddingValues,
    sheetMetadata: Map<String, Any>,
    onOpenAccounts: () -> Unit,
    onOpenAccount: (String) -> Unit,
    onOpenMortgage: () -> Unit,
    onOpenBufferBalance: () -> Unit,
    onOpenImportHistory: () -> Unit,
    onOpenRules: () -> Unit,
    onBack: () -> Unit,
) {
    entry<ProfileRoute> {
        ProfileRoot(
            contentPadding = contentPadding,
            onOpenAccounts = onOpenAccounts,
            onOpenMortgage = onOpenMortgage,
            onOpenBufferBalance = onOpenBufferBalance,
            onOpenImportHistory = onOpenImportHistory,
            onOpenRules = onOpenRules,
            onBack = onBack,
        )
    }
    entry<AccountsRoute> {
        AccountsRoot(contentPadding = contentPadding, onOpenAccount = onOpenAccount, onBack = onBack)
    }
    entry<AccountRoute>(metadata = sheetMetadata) { key ->
        AccountSheetRoot(accountId = key.accountId, onDismiss = onBack)
    }
    entry<MortgageRoute>(metadata = sheetMetadata) {
        MortgageSheetRoot(onDismiss = onBack)
    }
    entry<BufferBalanceRoute>(metadata = sheetMetadata) {
        BufferBalanceSheetRoot(onDismiss = onBack)
    }
}

val profileUiModule = module {
    viewModelOf(::ProfileViewModel)
    viewModelOf(::AccountsViewModel)
    viewModelOf(::MortgageViewModel)
    viewModel { BufferBalanceViewModel(get(), get()) }
    // Lambda form: the account id is a navigation argument.
    viewModel { params -> AccountSheetViewModel(params.get(), get(), get(), get()) }
}
