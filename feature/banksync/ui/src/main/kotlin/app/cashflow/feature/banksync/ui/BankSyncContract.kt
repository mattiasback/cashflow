package app.cashflow.feature.banksync.ui

import androidx.compose.runtime.Immutable
import app.cashflow.core.ui.UiText

/** A bank account in the consent you just gave, waiting for "Vilket konto?". */
@Immutable
data class MappingRowUi(
    val stableKey: String,
    val title: String,
    val kind: app.cashflow.feature.banksync.domain.BankAccountKind,
    /** Key of the chosen option: an account id, [NEW] or [SKIP]. */
    val selected: String,
) {
    companion object {
        const val NEW = "new"
        const val SKIP = "skip"
    }
}

@Immutable
data class AccountOptionUi(val key: String, val label: UiText)

@Immutable
data class LinkedAccountUi(val title: String, val into: String, val synced: String?)

/** One consent: the bank, until when it lasts, and its accounts. */
@Immutable
data class ConsentUi(
    val sessionId: String,
    val institution: String,
    val validity: UiText,
    val expired: Boolean,
    val accounts: List<LinkedAccountUi>,
)

@Immutable
data class BankUi(val name: String)

data class BankSyncState(
    val isLoading: Boolean = true,
    val hasKey: Boolean = false,
    /** The last characters of the saved application id, so you can tell which app it is. */
    val keyHint: String? = null,
    val applicationId: String = BuildConfig.ENABLE_BANKING_APP_ID,
    val redirectUrl: String = DEFAULT_REDIRECT,
    val keyPicked: Boolean = false,
    val isSavingKey: Boolean = false,
    val consents: List<ConsentUi> = emptyList(),
    /** Non-null while the bank list is open. */
    val banks: List<BankUi>? = null,
    val isLoadingBanks: Boolean = false,
    /** The bank whose BankID page is open; its answer is awaited. */
    val awaitingBank: String? = null,
    val pastedAddress: String = "",
    val mapping: List<MappingRowUi>? = null,
    val accountOptions: List<AccountOptionUi> = emptyList(),
    val isWorking: Boolean = false,
    val result: UiText? = null,
    /** One line per account after a sync: what the bank delivered, no row contents. */
    val reports: List<UiText> = emptyList(),
    val error: UiText? = null,
) {
    val canSaveKey: Boolean get() = applicationId.isNotBlank() && redirectUrl.isNotBlank() && keyPicked && !isSavingKey
    val canSync: Boolean get() = consents.any { !it.expired } && !isWorking

    companion object {
        /** Enable Banking needs https: this page (docs/bank-callback) forwards to cashflow://bank-callback. */
        const val DEFAULT_REDIRECT = "https://mattiasback.github.io/cashflow-callback"
    }
}

sealed interface BankSyncAction {
    data object OnBackClick : BankSyncAction
    data class OnApplicationIdChange(val text: String) : BankSyncAction
    data class OnRedirectChange(val text: String) : BankSyncAction
    data class OnKeyFilePicked(val uri: String) : BankSyncAction
    data object OnSaveKeyClick : BankSyncAction
    data object OnRemoveKeyClick : BankSyncAction
    data object OnConnectClick : BankSyncAction
    data object OnBanksDismiss : BankSyncAction
    data class OnBankClick(val name: String) : BankSyncAction
    data class OnPastedAddressChange(val text: String) : BankSyncAction
    data object OnPastedAddressSubmit : BankSyncAction
    data object OnCancelWaitingClick : BankSyncAction
    data class OnMappingSelect(val stableKey: String, val key: String) : BankSyncAction
    data object OnMappingConfirm : BankSyncAction
    data object OnSyncClick : BankSyncAction
    data object OnResyncClick : BankSyncAction
    data class OnDisconnectClick(val sessionId: String) : BankSyncAction
}

sealed interface BankSyncEvent {
    data object NavigateBack : BankSyncEvent

    /** The bank's BankID page, opened in the browser. */
    data class OpenBrowser(val url: String) : BankSyncEvent
}
