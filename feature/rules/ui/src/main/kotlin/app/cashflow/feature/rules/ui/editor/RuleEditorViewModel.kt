package app.cashflow.feature.rules.ui.editor

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cashflow.core.domain.Result
import app.cashflow.core.domain.datasource.AccountLocalDataSource
import app.cashflow.core.domain.datasource.CategoryLocalDataSource
import app.cashflow.core.domain.datasource.MerchantMemoryLocalDataSource
import app.cashflow.core.domain.datasource.RuleCatalog
import app.cashflow.core.domain.datasource.RuleLocalDataSource
import app.cashflow.core.domain.datasource.TransactionLocalDataSource
import app.cashflow.core.domain.model.AccountId
import app.cashflow.core.domain.model.CategoryId
import app.cashflow.core.domain.model.CategoryKind
import app.cashflow.core.domain.model.CategoryRule
import app.cashflow.core.domain.model.RuleMatch
import app.cashflow.core.domain.model.RuleOrigin
import app.cashflow.core.domain.model.Transaction
import app.cashflow.core.importengine.RuleUseCases
import app.cashflow.core.ui.FlowTone
import app.cashflow.core.ui.UiText
import app.cashflow.core.ui.categoryName
import app.cashflow.core.ui.format.MoneyParser
import app.cashflow.core.ui.groupName
import app.cashflow.core.ui.toUiText
import app.cashflow.core.ui.tone
import app.cashflow.feature.rules.ui.R
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

enum class EditorMode { NEW_RULE, EDIT_RULE, MEMORY, BUILT_IN }

@Immutable
data class EditorOptionUi(val key: String, val label: UiText, val selected: Boolean)

@Immutable
data class EditorCategoryUi(val id: String, val name: UiText, val group: UiText, val tone: FlowTone, val selected: Boolean)

@Stable
data class RuleEditorState(
    val mode: EditorMode = EditorMode.NEW_RULE,
    val title: String = "",
    /** Built-in group: every name it matches and how often it is used here. */
    val builtInText: String? = null,
    val matchOptions: List<EditorOptionUi> = emptyList(),
    val text: String = "",
    val accountOptions: List<EditorOptionUi> = emptyList(),
    val over: String = "",
    val under: String = "",
    val categories: List<EditorCategoryUi> = emptyList(),
    val reach: UiText? = null,
    val applyLabel: UiText? = null,
    val applyToPast: Boolean = true,
    val canSave: Boolean = false,
    val canDelete: Boolean = false,
    val error: UiText? = null,
    val isLoading: Boolean = true,
)

sealed interface RuleEditorAction {
    data class OnMatchSelect(val key: String) : RuleEditorAction
    data class OnTextChange(val text: String) : RuleEditorAction
    data class OnAccountSelect(val key: String) : RuleEditorAction
    data class OnOverChange(val text: String) : RuleEditorAction
    data class OnUnderChange(val text: String) : RuleEditorAction
    data class OnCategorySelect(val id: String) : RuleEditorAction
    data class OnApplyToggle(val apply: Boolean) : RuleEditorAction
    data object OnOverrideClick : RuleEditorAction
    data object OnSaveClick : RuleEditorAction
    data object OnDeleteClick : RuleEditorAction
}

sealed interface RuleEditorEvent {
    data object Dismiss : RuleEditorEvent
}

/**
 * The rule editor sheet. Fields live in [SavedStateHandle], so a half-written rule survives
 * process death. "Matchar N transaktioner" updates as you type.
 */
@OptIn(ExperimentalUuidApi::class)
class RuleEditorViewModel(
    private val ruleId: String?,
    private val payeeKey: String?,
    private val builtInCategory: String?,
    private val handle: SavedStateHandle,
    private val rules: RuleLocalDataSource,
    memories: MerchantMemoryLocalDataSource,
    categories: CategoryLocalDataSource,
    accounts: AccountLocalDataSource,
    transactions: TransactionLocalDataSource,
    private val catalog: RuleCatalog,
    private val ruleUseCases: RuleUseCases,
) : ViewModel() {

    private val _state = MutableStateFlow(RuleEditorState())
    val state: StateFlow<RuleEditorState> = _state.asStateFlow()

    private val _events = Channel<RuleEditorEvent>()
    val events = _events.receiveAsFlow()

    private val tick = MutableStateFlow(0)
    private var existing: CategoryRule? = null
    private var rows: List<Transaction> = emptyList()

    private var mode: EditorMode
        get() = handle.get<String>(K_MODE)?.let(EditorMode::valueOf) ?: EditorMode.NEW_RULE
        set(value) { handle[K_MODE] = value.name }

    private fun field(key: String) = handle.get<String>(key).orEmpty()
    private fun set(key: String, value: String?) {
        handle[key] = value
        tick.update { it + 1 }
    }

    init {
        viewModelScope.launch {
            if (handle.get<String>(K_MODE) == null) seed(memories)
            combine(categories.observeCategories(), accounts.observeAccounts(), transactions.observeAll(), tick) { cats, accs, txs, _ ->
                rows = txs
                build(cats, accs)
            }.onEach { s -> _state.update { s } }.launchIn(viewModelScope)
        }
    }

    /** The first time only: fill the fields from the rule, payee or built-in group being opened. */
    private suspend fun seed(memories: MerchantMemoryLocalDataSource) {
        when {
            ruleId != null -> {
                val r = (rules.getUserRules() as? Result.Success)?.data?.firstOrNull { it.id == ruleId }
                existing = r
                mode = EditorMode.EDIT_RULE
                if (r != null) {
                    handle[K_MATCH] = r.match.name
                    handle[K_TEXT] = r.pattern
                    handle[K_ACCOUNT] = r.accountId?.value
                    handle[K_OVER] = r.minAmount?.let(MoneyParser::editable)
                    handle[K_UNDER] = r.maxAmount?.let(MoneyParser::editable)
                    handle[K_CATEGORY] = r.categoryId.value
                }
            }
            payeeKey != null -> {
                mode = EditorMode.MEMORY
                handle[K_MATCH] = RuleMatch.EQUALS.name
                handle[K_TEXT] = payeeKey
                handle[K_CATEGORY] = (memories.getAll() as? Result.Success)?.data?.firstOrNull { it.payeeKey == payeeKey }?.categoryId?.value
            }
            builtInCategory != null -> {
                mode = EditorMode.BUILT_IN
                handle[K_CATEGORY] = builtInCategory
                handle[K_MATCH] = RuleMatch.CONTAINS.name
            }
            else -> {
                mode = EditorMode.NEW_RULE
                handle[K_MATCH] = RuleMatch.CONTAINS.name
            }
        }
        handle[K_APPLY] = "true"
    }

    private suspend fun build(cats: List<app.cashflow.core.domain.model.Category>, accs: List<app.cashflow.core.domain.model.Account>): RuleEditorState {
        if (ruleId != null && existing == null) existing = (rules.getUserRules() as? Result.Success)?.data?.firstOrNull { it.id == ruleId }
        val m = mode
        val selectedCategory = field(K_CATEGORY)
        val draft = draft()
        val reach = draft?.let { ruleUseCases.reach(it, rows) }
        val byId = cats.associateBy { it.id }
        val builtInText = if (m == EditorMode.BUILT_IN) {
            val patterns = catalog.builtInRules().filter { it.categoryId.value == builtInCategory }.map { it.pattern.trim() }.distinctBy { it.uppercase() }
            patterns.joinToString(", ")
        } else {
            null
        }
        return RuleEditorState(
            mode = m,
            title = when (m) {
                EditorMode.BUILT_IN, EditorMode.MEMORY -> field(K_TEXT).ifEmpty { builtInCategory?.let { id -> byId[CategoryId(id)]?.name } ?: "" }
                else -> field(K_TEXT)
            },
            builtInText = builtInText,
            matchOptions = listOf(
                EditorOptionUi(RuleMatch.STARTS_WITH.name, UiText.StringResource(R.string.editor_match_starts), field(K_MATCH) == RuleMatch.STARTS_WITH.name),
                EditorOptionUi(RuleMatch.CONTAINS.name, UiText.StringResource(R.string.editor_match_contains), field(K_MATCH) == RuleMatch.CONTAINS.name),
                EditorOptionUi(RuleMatch.EQUALS.name, UiText.StringResource(R.string.editor_match_equals), field(K_MATCH) == RuleMatch.EQUALS.name),
            ),
            text = field(K_TEXT),
            accountOptions = listOf(EditorOptionUi("", UiText.StringResource(R.string.editor_all_accounts), field(K_ACCOUNT).isEmpty())) +
                accs.filter { it.type.countsInCashFlow }.sortedBy { it.name }.map { EditorOptionUi(it.id.value, UiText.DynamicString(it.name), field(K_ACCOUNT) == it.id.value) },
            over = field(K_OVER),
            under = field(K_UNDER),
            categories = cats
                .filter { it.kind != CategoryKind.UNCATEGORIZED && it.kind != CategoryKind.INVESTMENT }
                .sortedWith(compareBy({ it.kind.ordinal }, { it.sortOrder }))
                .map { EditorCategoryUi(it.id.value, categoryName(it.id, it.name), it.kind.groupName(), it.kind.tone(), it.id.value == selectedCategory) },
            reach = when {
                m == EditorMode.BUILT_IN -> UiText.PluralResource(
                    R.plurals.editor_builtin_uses,
                    rows.count { it.categorizedBy == app.cashflow.core.domain.model.CategorizedBy.BUILT_IN_RULE && it.categoryId.value == builtInCategory },
                )
                m == EditorMode.MEMORY -> UiText.PluralResource(R.plurals.editor_memory_uses, ruleUseCases.usesOfPayee(field(K_TEXT), rows))
                reach == null -> null
                else -> UiText.PluralResource(R.plurals.editor_matches, reach.matches)
            },
            applyLabel = reach?.takeIf { m == EditorMode.NEW_RULE || m == EditorMode.EDIT_RULE }?.let { r ->
                if (r.pastWithOtherCategory > 0) UiText.PluralResource(R.plurals.editor_apply_count, r.pastWithOtherCategory) else UiText.StringResource(R.string.editor_apply)
            },
            applyToPast = field(K_APPLY) != "false",
            canSave = when (m) {
                EditorMode.BUILT_IN -> false
                EditorMode.MEMORY -> selectedCategory.isNotEmpty()
                else -> draft != null
            },
            canDelete = m == EditorMode.EDIT_RULE || m == EditorMode.MEMORY,
            error = _state.value.error,
            isLoading = false,
        )
    }

    /** The rule as typed so far, or null when it is not complete. */
    private fun draft(): CategoryRule? {
        val text = field(K_TEXT).trim()
        val category = field(K_CATEGORY)
        if (text.isEmpty() || category.isEmpty()) return null
        val over = field(K_OVER).takeIf { it.isNotBlank() }?.let { MoneyParser.parse(it) ?: return null }
        val under = field(K_UNDER).takeIf { it.isNotBlank() }?.let { MoneyParser.parse(it) ?: return null }
        return CategoryRule(
            id = existing?.id ?: field(K_NEW_ID).ifEmpty { Uuid.random().toString().also { handle[K_NEW_ID] = it } },
            pattern = text,
            match = RuleMatch.valueOf(field(K_MATCH).ifEmpty { RuleMatch.CONTAINS.name }),
            categoryId = CategoryId(category),
            isBuiltIn = false,
            accountId = field(K_ACCOUNT).takeIf { it.isNotEmpty() }?.let(::AccountId),
            minAmount = over?.abs(),
            maxAmount = under?.abs(),
            origin = existing?.origin ?: if (handle.get<String>(K_OVERRIDE) == "true") RuleOrigin.OVERRIDE else RuleOrigin.CREATED,
            createdAt = existing?.createdAt ?: Clock.System.now(),
        )
    }

    fun onAction(action: RuleEditorAction) {
        when (action) {
            is RuleEditorAction.OnMatchSelect -> set(K_MATCH, action.key)
            is RuleEditorAction.OnTextChange -> set(K_TEXT, action.text)
            is RuleEditorAction.OnAccountSelect -> set(K_ACCOUNT, action.key.ifEmpty { null })
            is RuleEditorAction.OnOverChange -> set(K_OVER, action.text)
            is RuleEditorAction.OnUnderChange -> set(K_UNDER, action.text)
            is RuleEditorAction.OnCategorySelect -> set(K_CATEGORY, action.id)
            is RuleEditorAction.OnApplyToggle -> set(K_APPLY, action.apply.toString())
            RuleEditorAction.OnOverrideClick -> {
                // "Ändra för mig": a rule of your own that goes before the built-in one.
                handle[K_OVERRIDE] = "true"
                handle[K_TEXT] = ""
                mode = EditorMode.NEW_RULE
                viewModelScope.launch {
                    val first = catalog.builtInRules().firstOrNull { it.categoryId.value == builtInCategory }
                    if (first != null) {
                        handle[K_TEXT] = first.pattern
                        handle[K_MATCH] = first.match.name
                    }
                    tick.update { it + 1 }
                }
            }
            RuleEditorAction.OnSaveClick -> save()
            RuleEditorAction.OnDeleteClick -> delete()
        }
    }

    private fun save() {
        viewModelScope.launch {
            val r = when (mode) {
                EditorMode.MEMORY -> ruleUseCases.updateMemory(field(K_TEXT), CategoryId(field(K_CATEGORY)))
                EditorMode.NEW_RULE, EditorMode.EDIT_RULE -> {
                    val rule = draft() ?: return@launch
                    ruleUseCases.save(rule, applyToPast = state.first().applyToPast)
                }
                EditorMode.BUILT_IN -> return@launch
            }
            finish(r)
        }
    }

    private fun delete() {
        viewModelScope.launch {
            val r = when (mode) {
                EditorMode.MEMORY -> ruleUseCases.updateMemory(field(K_TEXT), null)
                EditorMode.EDIT_RULE -> ruleUseCases.delete(existing?.id ?: ruleId ?: return@launch)
                else -> return@launch
            }
            finish(r)
        }
    }

    private suspend fun finish(r: app.cashflow.core.domain.EmptyResult<app.cashflow.core.domain.DataError.Local>) {
        when (r) {
            is Result.Error -> _state.update { it.copy(error = r.error.toUiText()) }
            is Result.Success -> _events.send(RuleEditorEvent.Dismiss)
        }
    }

    private companion object {
        const val K_MODE = "mode"
        const val K_MATCH = "match"
        const val K_TEXT = "text"
        const val K_ACCOUNT = "account"
        const val K_OVER = "over"
        const val K_UNDER = "under"
        const val K_CATEGORY = "category"
        const val K_APPLY = "apply"
        const val K_NEW_ID = "newId"
        const val K_OVERRIDE = "override"
    }
}
