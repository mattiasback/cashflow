package app.cashflow.feature.importer.ui.mapping

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cashflow.core.domain.ImportError
import app.cashflow.core.domain.Result
import app.cashflow.core.domain.datasource.AccountLocalDataSource
import app.cashflow.core.domain.datasource.CsvProfileLocalDataSource
import app.cashflow.core.domain.model.Account
import app.cashflow.core.domain.model.AccountId
import app.cashflow.core.domain.model.AccountType
import app.cashflow.core.domain.model.CsvProfile
import app.cashflow.core.domain.model.ParsedStatement
import app.cashflow.core.importengine.AccountAnswer
import app.cashflow.core.importengine.AccountGuess
import app.cashflow.core.ui.UiText
import app.cashflow.core.ui.format.AppLocale
import app.cashflow.core.ui.format.MoneyFormatter
import app.cashflow.core.ui.format.PeriodFormatter
import app.cashflow.core.ui.toUiText
import app.cashflow.feature.importer.domain.ColumnMapper
import app.cashflow.feature.importer.domain.ColumnRole
import app.cashflow.feature.importer.domain.ImportFilesUseCase
import app.cashflow.feature.importer.domain.MappingDraft
import app.cashflow.feature.importer.domain.PickedFile
import app.cashflow.feature.importer.domain.StatementFileSource
import app.cashflow.feature.importer.ui.R
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Immutable
data class MapColumnUi(val index: Int, val header: String, val sample: String, val role: ColumnRole, val sure: Boolean)

@Immutable
data class PreviewRowUi(val text: String, val date: String, val amount: String, val isIncome: Boolean)

@Immutable
data class MapAccountUi(val id: String?, val label: UiText, val selected: Boolean)

@Stable
data class ColumnMappingState(
    val fileName: String = "",
    val meta: UiText? = null,
    val formatChips: List<UiText> = emptyList(),
    val columns: List<MapColumnUi> = emptyList(),
    val negate: Boolean = false,
    val preview: List<PreviewRowUi> = emptyList(),
    val warning: UiText? = null,
    val summary: UiText? = null,
    val accounts: List<MapAccountUi> = emptyList(),
    val newAccountName: String = "",
    val profileName: String = "",
    val canImport: Boolean = false,
    val isWorking: Boolean = false,
    val error: UiText? = null,
    val isLoading: Boolean = true,
)

sealed interface ColumnMappingAction {
    data class OnRoleSelect(val index: Int, val role: ColumnRole) : ColumnMappingAction
    data class OnNegateToggle(val negate: Boolean) : ColumnMappingAction
    data class OnAccountSelect(val id: String?) : ColumnMappingAction
    data class OnNewAccountNameChange(val name: String) : ColumnMappingAction
    data class OnProfileNameChange(val name: String) : ColumnMappingAction
    data object OnImportClick : ColumnMappingAction
    data object OnCloseClick : ColumnMappingAction
}

sealed interface ColumnMappingEvent {
    data class ShowReceipt(val importIds: List<String>) : ColumnMappingEvent
    data object Close : ColumnMappingEvent
}

/**
 * "Mappa kolumner" for a file no bank profile knows: confirm the guessed columns with a live
 * preview, pick the account, and save the mapping as a profile so the next file with the same
 * columns imports directly.
 */
@OptIn(ExperimentalUuidApi::class)
class ColumnMappingViewModel(
    private val uri: String,
    private val fileSource: StatementFileSource,
    private val mapper: ColumnMapper,
    private val csvProfiles: CsvProfileLocalDataSource,
    private val accounts: AccountLocalDataSource,
    private val importFiles: ImportFilesUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(ColumnMappingState())
    val state: StateFlow<ColumnMappingState> = _state.asStateFlow()

    private val _events = Channel<ColumnMappingEvent>()
    val events = _events.receiveAsFlow()

    private val locale = AppLocale.current()
    private var file: PickedFile? = null
    private var draft: MappingDraft? = null
    private var roles: MutableMap<Int, ColumnRole> = HashMap()
    private var negate = false
    private var known: List<Account> = emptyList()
    private var accountId: String? = null
    private var newName = ""
    private var profileName = ""

    init {
        viewModelScope.launch {
            known = (accounts.getAccounts() as? Result.Success)?.data.orEmpty().filter { it.type.countsInCashFlow }
            val f = when (val r = fileSource.open(uri)) {
                is Result.Error -> return@launch fail(r.error)
                is Result.Success -> r.data
            }
            file = f
            when (val r = mapper.inspect(f)) {
                is Result.Error -> fail(r.error)
                is Result.Success -> {
                    val d = r.data
                    draft = d
                    roles = d.columns.associate { it.index to it.role }.toMutableMap()
                    negate = d.looksUnsigned
                    newName = "Konto ${known.size + 1}"
                    profileName = f.name.substringBeforeLast('.').take(40)
                    refresh()
                }
            }
        }
    }

    fun onAction(action: ColumnMappingAction) {
        when (action) {
            is ColumnMappingAction.OnRoleSelect -> {
                // A role belongs to one column: picking it elsewhere frees the old column.
                if (action.role != ColumnRole.IGNORE) roles.entries.filter { it.value == action.role }.forEach { it.setValue(ColumnRole.IGNORE) }
                roles[action.index] = action.role
                refresh()
            }
            is ColumnMappingAction.OnNegateToggle -> {
                negate = action.negate
                refresh()
            }
            is ColumnMappingAction.OnAccountSelect -> {
                accountId = action.id
                refresh()
            }
            is ColumnMappingAction.OnNewAccountNameChange -> {
                newName = action.name
                refresh()
            }
            is ColumnMappingAction.OnProfileNameChange -> {
                profileName = action.name
                refresh()
            }
            ColumnMappingAction.OnImportClick -> import()
            ColumnMappingAction.OnCloseClick -> viewModelScope.launch { _events.send(ColumnMappingEvent.Close) }
        }
    }

    private fun profile(): CsvProfile? {
        val d = draft ?: return null
        fun col(role: ColumnRole) = roles.entries.firstOrNull { it.value == role }?.key
        val date = col(ColumnRole.DATE) ?: return null
        val text = col(ColumnRole.TEXT) ?: return null
        val amount = col(ColumnRole.AMOUNT)
        val inCol = col(ColumnRole.AMOUNT_IN)
        val outCol = col(ColumnRole.AMOUNT_OUT)
        if (amount == null && inCol == null && outCol == null) return null
        return CsvProfile(
            id = Uuid.random().toString(),
            name = profileName.trim().ifEmpty { d.fileName },
            fingerprint = d.fingerprint,
            headerRow = d.headerRow,
            dateColumn = date,
            textColumn = text,
            amountColumn = amount,
            inColumn = if (amount == null) inCol else null,
            outColumn = if (amount == null) outCol else null,
            transactionDateColumn = col(ColumnRole.TRANSACTION_DATE),
            balanceColumn = col(ColumnRole.BALANCE),
            negateAmounts = negate && amount != null,
            createdAt = Clock.System.now(),
        )
    }

    private fun refresh() {
        val d = draft ?: return
        val f = file ?: return
        val p = profile()
        val parsed: ParsedStatement? = p?.let { (mapper.parse(f, it) as? Result.Success)?.data }
        val rows = parsed?.rows.orEmpty()
        val incomes = rows.count { it.amount.isPositive }
        val warning = when {
            p == null -> UiText.StringResource(R.string.mapping_warn_missing)
            rows.isEmpty() -> UiText.StringResource(R.string.mapping_warn_nothing)
            // Almost everything as money in usually means the file lists expenses without a sign.
            rows.size >= 5 && incomes * 10 >= rows.size * 9 -> UiText.StringResource(R.string.mapping_warn_unsigned)
            else -> null
        }
        val first = rows.minOfOrNull { it.transactionDate }
        val last = rows.maxOfOrNull { it.transactionDate }
        _state.value = ColumnMappingState(
            fileName = d.fileName,
            meta = if (first != null && last != null) {
                UiText.StringResource(R.string.mapping_meta, arrayOf(d.rowCount, PeriodFormatter.iso(first, locale), PeriodFormatter.iso(last, locale)))
            } else {
                UiText.PluralResource(R.plurals.mapping_rows, d.rowCount)
            },
            formatChips = listOfNotNull(
                d.separator?.let { UiText.StringResource(R.string.mapping_chip_separator, arrayOf(if (it == '\t') "tab" else it.toString())) },
                UiText.StringResource(if (d.decimalComma) R.string.mapping_chip_decimal_comma else R.string.mapping_chip_decimal_point),
            ),
            columns = d.columns.map { MapColumnUi(it.index, it.header, it.sample, roles[it.index] ?: ColumnRole.IGNORE, it.sure && roles[it.index] == it.role) },
            negate = negate,
            preview = rows.take(3).map { r ->
                PreviewRowUi(r.rawText, PeriodFormatter.iso(r.transactionDate, locale), MoneyFormatter.format(r.amount, signed = true, locale = locale), r.amount.isPositive)
            },
            warning = warning,
            summary = if (rows.isEmpty()) null else UiText.StringResource(R.string.mapping_summary, arrayOf(rows.size, rows.size - incomes, incomes)),
            accounts = known.map { MapAccountUi(it.id.value, UiText.DynamicString(it.name), it.id.value == accountId) } +
                MapAccountUi(null, UiText.StringResource(R.string.import_new_account), accountId == null),
            newAccountName = newName,
            profileName = profileName,
            canImport = p != null && rows.isNotEmpty() && (accountId != null || newName.isNotBlank()),
            isWorking = _state.value.isWorking,
            isLoading = false,
        )
    }

    private fun import() {
        val p = profile() ?: return
        _state.update { it.copy(isWorking = true, error = null) }
        viewModelScope.launch {
            if (csvProfiles.upsertProfile(p) is Result.Error) return@launch fail(ImportError.STORAGE)
            val prepared = when (val r = importFiles.prepare(listOf(uri))) {
                is Result.Error -> return@launch fail(r.error)
                is Result.Success -> r.data
            }
            val plan = prepared.plan ?: return@launch fail(prepared.failures.firstOrNull()?.error ?: ImportError.UNSUPPORTED_FORMAT)
            val chosen = accountId?.let(::AccountId)
            val newAccount = Account(AccountId(Uuid.random().toString()), newName.trim(), AccountType.TRANSACTION)
            // The account picked here answers every "Vilket konto?" the plan has.
            val answers = plan.files.mapNotNull { it.guess as? AccountGuess.Ask }.map { it.group }.distinct().associateWith { _ ->
                if (chosen != null) AccountAnswer.Existing(chosen) else AccountAnswer.New(newAccount)
            }
            when (val r = importFiles.execute(plan, answers)) {
                is Result.Error -> fail(r.error)
                is Result.Success -> _events.send(ColumnMappingEvent.ShowReceipt(r.data.importIds.map { it.value }))
            }
        }
    }

    private fun fail(error: ImportError) {
        _state.update { it.copy(isWorking = false, isLoading = false, error = error.toUiText()) }
    }
}
