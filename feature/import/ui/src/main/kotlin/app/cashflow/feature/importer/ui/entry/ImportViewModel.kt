package app.cashflow.feature.importer.ui.entry

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cashflow.core.domain.Result
import app.cashflow.core.domain.datasource.AccountLocalDataSource
import app.cashflow.core.domain.model.Account
import app.cashflow.core.domain.model.AccountId
import app.cashflow.core.domain.model.AccountType
import app.cashflow.core.importengine.AccountAnswer
import app.cashflow.core.importengine.ImportPlan
import app.cashflow.core.ui.UiText
import app.cashflow.core.ui.toUiText
import app.cashflow.feature.importer.domain.ImportFilesUseCase
import app.cashflow.feature.importer.ui.R
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

enum class ImportPhase { PICKING, READING, QUESTIONS, IMPORTING, FAILED }

@Immutable
data class AccountChoiceUi(val id: String?, val label: UiText, val selected: Boolean)

@Immutable
data class AccountQuestionUi(
    val group: Int,
    val files: String,
    val choices: List<AccountChoiceUi>,
    val isNew: Boolean,
    val newName: String,
)

@Immutable
data class FileFailureUi(val fileName: String, val reason: UiText)

@Stable
data class ImportState(
    val phase: ImportPhase = ImportPhase.PICKING,
    val fileCount: Int = 0,
    val questions: List<AccountQuestionUi> = emptyList(),
    val failures: List<FileFailureUi> = emptyList(),
    val canImport: Boolean = false,
    val error: UiText? = null,
)

sealed interface ImportAction {
    data class OnFilesPicked(val uris: List<String>) : ImportAction
    data object OnPickFilesClick : ImportAction
    data class OnAccountChoice(val group: Int, val accountId: String?) : ImportAction
    data class OnNewNameChange(val group: Int, val name: String) : ImportAction
    data object OnImportClick : ImportAction
    data object OnCloseClick : ImportAction
}

sealed interface ImportEvent {
    data object LaunchPicker : ImportEvent
    data class ShowReceipt(val importIds: List<String>) : ImportEvent
    data object Close : ImportEvent
}

/**
 * Picks files (or takes shared ones), reads them, lets the engine infer accounts and asks
 * "Vilket konto?" once per chain of files only when it is unsure. Then imports and opens the
 * receipt.
 */
class ImportViewModel(
    sharedUris: List<String>,
    private val importFiles: ImportFilesUseCase,
    private val accounts: AccountLocalDataSource,
) : ViewModel() {

    private val _state = MutableStateFlow(ImportState())
    val state: StateFlow<ImportState> = _state.asStateFlow()

    private val _events = Channel<ImportEvent>()
    val events = _events.receiveAsFlow()

    private var plan: ImportPlan? = null
    private var known: List<Account> = emptyList()

    /** Answers per question group: an existing account id, or null for a new account. */
    private val answers = HashMap<Int, String?>()
    private val newNames = HashMap<Int, String>()

    init {
        if (sharedUris.isNotEmpty()) prepare(sharedUris) else send(ImportEvent.LaunchPicker)
    }

    fun onAction(action: ImportAction) {
        when (action) {
            is ImportAction.OnFilesPicked -> if (action.uris.isEmpty()) send(ImportEvent.Close) else prepare(action.uris)
            ImportAction.OnPickFilesClick -> send(ImportEvent.LaunchPicker)
            is ImportAction.OnAccountChoice -> {
                answers[action.group] = action.accountId
                refreshQuestions()
            }
            is ImportAction.OnNewNameChange -> {
                newNames[action.group] = action.name
                refreshQuestions()
            }
            ImportAction.OnImportClick -> execute()
            ImportAction.OnCloseClick -> send(ImportEvent.Close)
        }
    }

    private fun send(event: ImportEvent) {
        viewModelScope.launch { _events.send(event) }
    }

    private fun prepare(uris: List<String>) {
        _state.update { it.copy(phase = ImportPhase.READING, fileCount = uris.size, error = null, failures = emptyList()) }
        viewModelScope.launch {
            known = (accounts.getAccounts() as? Result.Success)?.data.orEmpty()
            when (val r = importFiles.prepare(uris)) {
                is Result.Error -> _state.update { it.copy(phase = ImportPhase.FAILED, error = r.error.toUiText()) }
                is Result.Success -> {
                    val prepared = r.data
                    val failures = prepared.failures.map { FileFailureUi(it.fileName, it.error.toUiText()) }
                    plan = prepared.plan
                    val questions = prepared.plan?.questions.orEmpty()
                    when {
                        prepared.plan == null -> _state.update {
                            it.copy(phase = ImportPhase.FAILED, failures = failures, error = UiText.StringResource(R.string.import_nothing_readable))
                        }
                        questions.isEmpty() -> {
                            _state.update { it.copy(failures = failures) }
                            execute()
                        }
                        else -> {
                            answers.clear()
                            newNames.clear()
                            questions.forEachIndexed { i, q ->
                                answers[q.group] = q.candidates.firstOrNull()?.value
                                newNames[q.group] = defaultName(known.size + i + 1)
                            }
                            _state.update { it.copy(phase = ImportPhase.QUESTIONS, failures = failures) }
                            refreshQuestions()
                        }
                    }
                }
            }
        }
    }

    private fun defaultName(n: Int) = "Konto $n"

    private fun refreshQuestions() {
        val questions = plan?.questions.orEmpty().map { q ->
            val chosen = answers[q.group]
            // Offer tied candidates first, then every other known account, then a new one.
            val ordered = known.sortedBy { a -> if (q.candidates.contains(a.id)) 0 else 1 }
            AccountQuestionUi(
                group = q.group,
                files = q.fileNames.joinToString(", "),
                choices = ordered.map { AccountChoiceUi(it.id.value, UiText.DynamicString(it.name), it.id.value == chosen) } +
                    AccountChoiceUi(null, UiText.StringResource(R.string.import_new_account), chosen == null),
                isNew = chosen == null,
                newName = newNames[q.group].orEmpty(),
            )
        }
        val ready = questions.all { !it.isNew || it.newName.isNotBlank() }
        _state.update { it.copy(questions = questions, canImport = ready) }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun execute() {
        val p = plan ?: return
        val accountAnswers = p.questions.associate { q ->
            val chosen = answers[q.group]
            q.group to if (chosen != null) {
                AccountAnswer.Existing(AccountId(chosen))
            } else {
                AccountAnswer.New(Account(AccountId(Uuid.random().toString()), newNames[q.group]!!.trim(), AccountType.TRANSACTION))
            }
        }
        _state.update { it.copy(phase = ImportPhase.IMPORTING) }
        viewModelScope.launch {
            when (val r = importFiles.execute(p, accountAnswers)) {
                is Result.Error -> _state.update { it.copy(phase = ImportPhase.FAILED, error = r.error.toUiText()) }
                is Result.Success -> _events.send(ImportEvent.ShowReceipt(r.data.importIds.map { it.value }))
            }
        }
    }
}
