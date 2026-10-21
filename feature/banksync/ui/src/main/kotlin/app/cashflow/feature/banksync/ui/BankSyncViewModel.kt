package app.cashflow.feature.banksync.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cashflow.core.domain.Result
import app.cashflow.core.domain.datasource.AccountLocalDataSource
import app.cashflow.core.domain.model.AccountId
import app.cashflow.core.domain.model.AccountType
import app.cashflow.core.ui.UiText
import app.cashflow.core.ui.format.AppLocale
import app.cashflow.core.ui.format.PeriodFormatter
import app.cashflow.feature.banksync.domain.BankAccountKind
import app.cashflow.feature.banksync.domain.BankConsent
import app.cashflow.feature.banksync.domain.BankCredentialStore
import app.cashflow.feature.banksync.domain.BankCredentials
import app.cashflow.feature.banksync.domain.BankError
import app.cashflow.feature.banksync.domain.BankInstitution
import app.cashflow.feature.banksync.domain.BankKeyUseCase
import app.cashflow.feature.banksync.domain.BankLinkLocalDataSource
import app.cashflow.feature.banksync.domain.ConnectBankUseCase
import app.cashflow.feature.banksync.domain.DisconnectBankUseCase
import app.cashflow.feature.banksync.domain.LinkAccountsUseCase
import app.cashflow.feature.banksync.domain.LinkTarget
import app.cashflow.feature.banksync.domain.SyncBankUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * Profil → Bankkoppling. The key is read once into memory and handed encrypted to the store;
 * it is never put in saved state. The bank's answer arrives through [BankCallbacks] (the app's
 * link) or as an address you paste.
 */
class BankSyncViewModel(
    private val key: BankKeyUseCase,
    private val connect: ConnectBankUseCase,
    private val linkAccounts: LinkAccountsUseCase,
    private val sync: SyncBankUseCase,
    private val disconnect: DisconnectBankUseCase,
    private val credentials: BankCredentialStore,
    links: BankLinkLocalDataSource,
    private val accounts: AccountLocalDataSource,
    private val callbacks: BankCallbacks,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private val locale = AppLocale.current()
    private val _state = MutableStateFlow(BankSyncState())
    val state: StateFlow<BankSyncState> = _state.asStateFlow()

    private val _events = Channel<BankSyncEvent>()
    val events = _events.receiveAsFlow()

    private var pickedKey: String? = null
    private var institutions: List<BankInstitution> = emptyList()
    private var consent: BankConsent? = null

    init {
        credentials.hasCredentials.onEach { has ->
            val hint = if (has) credentials.load()?.applicationId?.takeLast(HINT_CHARS) else null
            val waiting = if (has) credentials.pending()?.institution else null
            _state.update { it.copy(isLoading = false, hasKey = has, keyHint = hint, awaitingBank = it.awaitingBank ?: waiting) }
        }.launchIn(viewModelScope)

        combine(links.observeLinks(), accounts.observeAccounts()) { all, known ->
            val now = clock.now()
            all.groupBy { it.sessionId }.map { (sessionId, group) ->
                val first = group.first()
                val until = PeriodFormatter.iso(first.validUntil.toLocalDateTime(TimeZone.currentSystemDefault()).date, locale)
                val expired = first.validUntil <= now
                ConsentUi(
                    sessionId = sessionId,
                    institution = first.institution,
                    validity = UiText.StringResource(if (expired) R.string.banksync_consent_expired else R.string.banksync_consent_until, arrayOf(until)),
                    expired = expired,
                    accounts = group.map { link ->
                        val target = known.firstOrNull { it.id == link.accountId }?.name.orEmpty()
                        LinkedAccountUi(
                            title = listOfNotNull(link.displayName, link.maskedNumber?.let { "··$it" }).joinToString(" "),
                            into = target,
                            synced = link.lastSynced?.let { PeriodFormatter.iso(it, locale) },
                        )
                    },
                )
            }
        }.onEach { consents -> _state.update { it.copy(consents = consents) } }.launchIn(viewModelScope)

        callbacks.urls.filterNotNull().onEach { url ->
            callbacks.consume()
            complete(url)
        }.launchIn(viewModelScope)
    }

    fun onAction(action: BankSyncAction) {
        when (action) {
            BankSyncAction.OnBackClick -> viewModelScope.launch { _events.send(BankSyncEvent.NavigateBack) }
            is BankSyncAction.OnApplicationIdChange -> _state.update { it.copy(applicationId = action.text, error = null) }
            is BankSyncAction.OnRedirectChange -> _state.update { it.copy(redirectUrl = action.text, error = null) }
            is BankSyncAction.OnKeyFilePicked -> viewModelScope.launch {
                pickedKey = key.readKey(action.uri)
                _state.update { it.copy(keyPicked = pickedKey != null, error = if (pickedKey == null) BankError.INVALID_KEY.toUiText() else null) }
            }
            BankSyncAction.OnSaveKeyClick -> saveKey()
            BankSyncAction.OnRemoveKeyClick -> viewModelScope.launch {
                key.remove()
                pickedKey = null
                _state.update { it.copy(keyPicked = false, applicationId = "", awaitingBank = null, mapping = null, result = null, error = null) }
            }
            BankSyncAction.OnConnectClick -> loadBanks()
            BankSyncAction.OnBanksDismiss -> _state.update { it.copy(banks = null) }
            is BankSyncAction.OnBankClick -> startAt(action.name)
            is BankSyncAction.OnPastedAddressChange -> _state.update { it.copy(pastedAddress = action.text, error = null) }
            BankSyncAction.OnPastedAddressSubmit -> complete(_state.value.pastedAddress)
            BankSyncAction.OnCancelWaitingClick -> viewModelScope.launch {
                credentials.setPending(null)
                _state.update { it.copy(awaitingBank = null, pastedAddress = "") }
            }
            is BankSyncAction.OnMappingSelect -> _state.update { s ->
                s.copy(mapping = s.mapping?.map { if (it.stableKey == action.stableKey) it.copy(selected = action.key) else it })
            }
            BankSyncAction.OnMappingConfirm -> confirmMapping()
            BankSyncAction.OnSyncClick -> runSync()
            is BankSyncAction.OnDisconnectClick -> working {
                disconnect(action.sessionId).onError { setError(it) }
            }
        }
    }

    private fun saveKey() {
        val pem = pickedKey ?: return
        val s = _state.value
        _state.update { it.copy(isSavingKey = true, error = null) }
        viewModelScope.launch {
            val result = key.save(BankCredentials(s.applicationId, pem, s.redirectUrl))
            if (result is Result.Success) pickedKey = null
            _state.update {
                it.copy(isSavingKey = false, keyPicked = result !is Result.Success && it.keyPicked, error = (result as? Result.Error)?.error?.toUiText())
            }
        }
    }

    private fun loadBanks() {
        _state.update { it.copy(isLoadingBanks = true, banks = emptyList(), error = null) }
        viewModelScope.launch {
            when (val r = connect.institutions()) {
                is Result.Error -> _state.update { it.copy(isLoadingBanks = false, banks = null, error = r.error.toUiText()) }
                is Result.Success -> {
                    institutions = r.data
                    _state.update { it.copy(isLoadingBanks = false, banks = r.data.map { b -> BankUi(b.name) }) }
                }
            }
        }
    }

    private fun startAt(name: String) {
        val institution = institutions.firstOrNull { it.name == name } ?: return
        _state.update { it.copy(banks = null) }
        working {
            when (val r = connect.start(institution)) {
                is Result.Error -> setError(r.error)
                is Result.Success -> {
                    _state.update { it.copy(awaitingBank = institution.name, pastedAddress = "", result = null) }
                    _events.send(BankSyncEvent.OpenBrowser(r.data.url))
                }
            }
        }
    }

    private fun complete(address: String) {
        if (address.isBlank()) return
        working {
            when (val r = connect.complete(address)) {
                is Result.Error -> {
                    // A refused or stopped authorization is over: start again from "Koppla bank".
                    if (r.error == BankError.CANCELLED) _state.update { it.copy(awaitingBank = null) }
                    setError(r.error)
                }
                is Result.Success -> showMapping(r.data)
            }
        }
    }

    private suspend fun showMapping(received: BankConsent) {
        consent = received
        val suggested = (linkAccounts.suggest(received) as? Result.Success)?.data.orEmpty()
        val known = (accounts.getAccounts() as? Result.Success)?.data.orEmpty()
        val options = known.sortedBy { it.name.lowercase() }
            .map { AccountOptionUi(it.id.value, UiText.DynamicString(it.name)) } +
            AccountOptionUi(MappingRowUi.NEW, UiText.StringResource(R.string.banksync_map_new)) +
            AccountOptionUi(MappingRowUi.SKIP, UiText.StringResource(R.string.banksync_map_skip))
        val rows = linkAccounts.syncable(received).map { bank ->
            MappingRowUi(
                stableKey = bank.stableKey,
                title = listOfNotNull(bank.name ?: received.institution, bank.number?.takeLast(4)?.let { "··$it" }).joinToString(" "),
                kind = bank.kind,
                selected = when (val t = suggested[bank.stableKey]) {
                    is LinkTarget.Existing -> t.accountId.value
                    LinkTarget.Skip -> MappingRowUi.SKIP
                    else -> MappingRowUi.NEW
                },
            )
        }
        _state.update { it.copy(awaitingBank = null, pastedAddress = "", mapping = rows, accountOptions = options) }
    }

    private fun confirmMapping() {
        val received = consent ?: return
        val rows = _state.value.mapping ?: return
        working {
            val choices = rows.associate { row ->
                val bank = received.accounts.first { it.stableKey == row.stableKey }
                row.stableKey to when (row.selected) {
                    MappingRowUi.SKIP -> LinkTarget.Skip
                    MappingRowUi.NEW -> LinkTarget.New(
                        bank.name ?: received.institution,
                        if (bank.kind == BankAccountKind.SAVINGS) AccountType.SAVINGS else AccountType.TRANSACTION,
                    )
                    else -> LinkTarget.Existing(AccountId(row.selected))
                }
            }
            when (val r = linkAccounts.link(received, choices)) {
                is Result.Error -> setError(r.error)
                is Result.Success -> {
                    consent = null
                    _state.update { it.copy(mapping = null) }
                    syncNow()
                }
            }
        }
    }

    private fun runSync() = working { syncNow() }

    private suspend fun syncNow() {
        val today = clock.todayIn(TimeZone.currentSystemDefault())
        when (val r = sync(today)) {
            is Result.Error -> setError(r.error)
            is Result.Success -> {
                val o = r.data
                _state.update {
                    it.copy(
                        result = UiText.StringResource(
                            R.string.banksync_result,
                            arrayOf(o.newCount, o.matchedCount, o.unclearCount),
                        ),
                        error = if (o.expiredCount > 0) BankError.CONSENT_EXPIRED.toUiText() else null,
                    )
                }
            }
        }
    }

    private fun working(block: suspend () -> Unit) {
        if (_state.value.isWorking) return
        _state.update { it.copy(isWorking = true, error = null) }
        viewModelScope.launch {
            try {
                block()
            } finally {
                _state.update { it.copy(isWorking = false) }
            }
        }
    }

    private fun setError(error: BankError) = _state.update { it.copy(error = error.toUiText()) }

    private inline fun <T> Result<T, BankError>.onError(action: (BankError) -> Unit) {
        if (this is Result.Error) action(error)
    }

    private companion object {
        const val HINT_CHARS = 4
    }
}

internal fun BankError.toUiText(): UiText = UiText.StringResource(
    when (this) {
        BankError.NO_CREDENTIALS -> R.string.banksync_error_no_key
        BankError.INVALID_KEY -> R.string.banksync_error_invalid_key
        BankError.UNAUTHORIZED -> R.string.banksync_error_unauthorized
        BankError.REDIRECT_NOT_REGISTERED -> R.string.banksync_error_redirect
        BankError.APP_INACTIVE -> R.string.banksync_error_inactive
        BankError.CONSENT_EXPIRED -> R.string.banksync_error_expired
        BankError.STATE_MISMATCH -> R.string.banksync_error_state
        BankError.CANCELLED -> R.string.banksync_error_cancelled
        BankError.RATE_LIMITED -> R.string.banksync_error_rate
        BankError.BANK_UNAVAILABLE -> R.string.banksync_error_bank
        BankError.NETWORK -> R.string.banksync_error_network
        BankError.INVALID_RESPONSE, BankError.UNKNOWN -> R.string.banksync_error_unknown
        BankError.STORAGE -> R.string.banksync_error_storage
    },
)
