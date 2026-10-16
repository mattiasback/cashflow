package app.cashflow.feature.importer.ui.history

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cashflow.core.domain.Result
import app.cashflow.core.domain.datasource.AccountLocalDataSource
import app.cashflow.core.domain.datasource.ImportBatchLocalDataSource
import app.cashflow.core.domain.datasource.TransactionLocalDataSource
import app.cashflow.core.domain.model.ImportBatch
import app.cashflow.core.domain.model.ImportId
import app.cashflow.core.domain.model.ImportSource
import app.cashflow.core.domain.model.Period
import app.cashflow.core.domain.model.TransactionStatus
import app.cashflow.core.importengine.UndoImportUseCase
import app.cashflow.core.ui.UiText
import app.cashflow.core.ui.format.AppLocale
import app.cashflow.core.ui.format.PeriodFormatter
import app.cashflow.core.ui.toUiText
import app.cashflow.feature.importer.ui.R
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

enum class ImportKind { BANK_FILE, SAVINGS_FILE, MAPPED_FILE }

@Immutable
data class ImportRowUi(
    val id: String,
    val title: String,
    val kind: ImportKind,
    val line: UiText,
    val undone: Boolean,
)

@Immutable
data class ImportMonthUi(val heading: String, val rows: List<ImportRowUi>)

/** The import sheet: what one import added and the two-step undo. */
@Immutable
data class ImportSheetUi(
    val id: String,
    val title: String,
    val eyebrow: UiText,
    val importedAt: String,
    val added: Int,
    val duplicates: Int,
    val review: Int,
    val edited: Int,
    val accounts: String,
    val range: String,
    val shortId: String,
    val undoneAt: String?,
    val confirming: Boolean,
    val confirmText: UiText,
)

@Stable
data class ImportHistoryState(
    val months: List<ImportMonthUi> = emptyList(),
    val sheet: ImportSheetUi? = null,
    val isWorking: Boolean = false,
    val error: UiText? = null,
    val isLoading: Boolean = true,
)

sealed interface ImportHistoryAction {
    data class OnImportClick(val id: String) : ImportHistoryAction
    data object OnUndoClick : ImportHistoryAction
    data object OnRedoClick : ImportHistoryAction
    data object OnSheetDismiss : ImportHistoryAction
    data object OnBackClick : ImportHistoryAction
}

sealed interface ImportHistoryEvent {
    data object NavigateBack : ImportHistoryEvent
}

/**
 * Importhistorik: every import grouped by month. An import can be undone long after, in two
 * steps, with a warning when you changed some of its rows; an undone import can be redone.
 */
class ImportHistoryViewModel(
    batches: ImportBatchLocalDataSource,
    transactions: TransactionLocalDataSource,
    accounts: AccountLocalDataSource,
    private val undoImport: UndoImportUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(ImportHistoryState())
    val state: StateFlow<ImportHistoryState> = _state.asStateFlow()

    private val _events = Channel<ImportHistoryEvent>()
    val events = _events.receiveAsFlow()

    private val locale = AppLocale.current()
    private val open = MutableStateFlow<String?>(null)
    private val confirming = MutableStateFlow(false)

    init {
        combine(batches.observeBatches(), transactions.observeAll(), accounts.observeAccounts(), open, confirming) { all, txs, accs, openId, confirm ->
            val byImport = txs.groupBy { it.importId }
            val tz = TimeZone.currentSystemDefault()
            fun local(b: ImportBatch) = b.importedAt.toLocalDateTime(tz)
            val months = all.sortedByDescending { it.importedAt }
                .groupBy { Period(local(it).year, local(it).month.ordinal + 1) }
                .map { (period, list) ->
                    ImportMonthUi(
                        heading = PeriodFormatter.monthLong(period, locale),
                        rows = list.map { b ->
                            val rows = byImport[b.id].orEmpty()
                            ImportRowUi(
                                id = b.id.value,
                                title = b.fileName,
                                kind = kindOf(b.source),
                                line = if (b.undoneAt != null) {
                                    UiText.StringResource(R.string.history_line_undone, arrayOf(b.newCount))
                                } else {
                                    UiText.StringResource(
                                        R.string.history_line,
                                        arrayOf(stamp(b), b.newCount, b.duplicateCount, rows.count { it.status == TransactionStatus.TO_REVIEW }),
                                    )
                                },
                                undone = b.undoneAt != null,
                            )
                        },
                    )
                }
            val sheet = all.firstOrNull { it.id.value == openId }?.let { b ->
                val rows = byImport[b.id].orEmpty()
                val edited = undoImport.editedRows(rows, b.id)
                val accountNames = rows.map { it.accountId }.distinct().mapNotNull { id -> accs.firstOrNull { it.id == id }?.name }
                val first = rows.minOfOrNull { it.transactionDate }
                val last = rows.maxOfOrNull { it.transactionDate }
                ImportSheetUi(
                    id = b.id.value,
                    title = b.fileName,
                    eyebrow = UiText.StringResource(
                        when (kindOf(b.source)) {
                            ImportKind.BANK_FILE -> R.string.history_kind_bank
                            ImportKind.SAVINGS_FILE -> R.string.history_kind_savings
                            ImportKind.MAPPED_FILE -> R.string.history_kind_mapped
                        },
                    ),
                    importedAt = stamp(b),
                    added = b.newCount,
                    duplicates = b.duplicateCount,
                    review = rows.count { it.status == TransactionStatus.TO_REVIEW },
                    edited = edited,
                    accounts = accountNames.joinToString(", ").ifEmpty { "–" },
                    range = if (first != null && last != null) "${PeriodFormatter.iso(first, locale)} – ${PeriodFormatter.iso(last, locale)}" else "–",
                    shortId = "#" + b.id.value.take(8),
                    undoneAt = b.undoneAt?.let { stampOf(it.toLocalDateTime(tz)) },
                    confirming = confirm,
                    confirmText = if (edited > 0) {
                        UiText.StringResource(R.string.history_confirm_edited, arrayOf(b.newCount, edited))
                    } else {
                        UiText.StringResource(R.string.history_confirm, arrayOf(b.newCount))
                    },
                )
            }
            ImportHistoryState(months = months, sheet = sheet, isWorking = _state.value.isWorking, error = _state.value.error, isLoading = false)
        }.onEach { s -> _state.update { s } }.launchIn(viewModelScope)
    }

    fun onAction(action: ImportHistoryAction) {
        when (action) {
            is ImportHistoryAction.OnImportClick -> {
                confirming.value = false
                open.value = action.id
            }
            ImportHistoryAction.OnSheetDismiss -> {
                confirming.value = false
                open.value = null
            }
            ImportHistoryAction.OnUndoClick -> {
                // Two steps: the first tap explains what will happen, the second undoes.
                if (!confirming.value) confirming.value = true else run(undo = true)
            }
            ImportHistoryAction.OnRedoClick -> run(undo = false)
            ImportHistoryAction.OnBackClick -> viewModelScope.launch { _events.send(ImportHistoryEvent.NavigateBack) }
        }
    }

    private fun run(undo: Boolean) {
        val id = open.value?.let(::ImportId) ?: return
        _state.update { it.copy(isWorking = true, error = null) }
        viewModelScope.launch {
            val r = if (undo) undoImport.undo(listOf(id)) else undoImport.redo(id)
            confirming.value = false
            _state.update { it.copy(isWorking = false, error = (r as? Result.Error)?.error?.toUiText()) }
        }
    }

    private fun stamp(b: ImportBatch) = stampOf(b.importedAt.toLocalDateTime(TimeZone.currentSystemDefault()))

    private fun stampOf(t: kotlinx.datetime.LocalDateTime) =
        "${PeriodFormatter.iso(t.date, locale)} ${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"

    private fun kindOf(source: ImportSource) = when (source) {
        ImportSource.LANSFORSAKRINGAR_XLS, ImportSource.CSV -> ImportKind.BANK_FILE
        ImportSource.LYSA_XLS, ImportSource.AVANZA_CSV -> ImportKind.SAVINGS_FILE
        ImportSource.CSV_MAPPED -> ImportKind.MAPPED_FILE
    }
}
