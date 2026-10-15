package app.cashflow.feature.importer.ui.receipt

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cashflow.core.domain.datasource.AccountLocalDataSource
import app.cashflow.core.domain.datasource.ImportBatchLocalDataSource
import app.cashflow.core.domain.datasource.TransactionLocalDataSource
import app.cashflow.core.domain.model.TransactionStatus
import app.cashflow.core.domain.model.TransferKind
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

@Stable
data class ImportReceiptState(
    val files: String = "",
    val accounts: String = "",
    val newCount: Int = 0,
    val duplicateCount: Int = 0,
    val transferCount: Int = 0,
    val reviewCount: Int = 0,
    val autoCount: Int = 0,
    val warningCount: Int = 0,
    val isLoading: Boolean = true,
)

sealed interface ImportReceiptAction {
    data object OnDoneClick : ImportReceiptAction
    data object OnReviewClick : ImportReceiptAction
}

sealed interface ImportReceiptEvent {
    data object Dismiss : ImportReceiptEvent
    data object OpenReview : ImportReceiptEvent
}

/** The receipt is read back from storage by import id, so it survives process death. */
class ImportReceiptViewModel(
    importIds: List<String>,
    batches: ImportBatchLocalDataSource,
    transactions: TransactionLocalDataSource,
    accounts: AccountLocalDataSource,
) : ViewModel() {

    private val _state = MutableStateFlow(ImportReceiptState())
    val state: StateFlow<ImportReceiptState> = _state.asStateFlow()

    private val _events = Channel<ImportReceiptEvent>()
    val events = _events.receiveAsFlow()

    init {
        val ids = importIds.toSet()
        combine(batches.observeBatches(), transactions.observeAll(), accounts.observeAccounts()) { all, txs, accs ->
            val mine = all.filter { it.id.value in ids }
            val rows = txs.filter { it.importId.value in ids }
            ImportReceiptState(
                files = mine.joinToString(", ") { it.fileName },
                accounts = mine.mapNotNull { b -> accs.firstOrNull { it.id == b.accountId }?.name }.distinct().joinToString(", "),
                newCount = mine.sumOf { it.newCount },
                duplicateCount = mine.sumOf { it.duplicateCount },
                transferCount = rows.count { it.transferKind != TransferKind.NONE && it.status != TransactionStatus.TO_REVIEW },
                reviewCount = rows.count { it.status == TransactionStatus.TO_REVIEW },
                autoCount = rows.count { it.status == TransactionStatus.AUTO && it.transferKind == TransferKind.NONE },
                warningCount = mine.sumOf { it.warnings.size },
                isLoading = false,
            )
        }.onEach { s -> _state.update { s } }.launchIn(viewModelScope)
    }

    fun onAction(action: ImportReceiptAction) {
        viewModelScope.launch {
            _events.send(
                when (action) {
                    ImportReceiptAction.OnDoneClick -> ImportReceiptEvent.Dismiss
                    ImportReceiptAction.OnReviewClick -> ImportReceiptEvent.OpenReview
                },
            )
        }
    }
}
