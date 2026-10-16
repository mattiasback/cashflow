package app.cashflow.feature.rules.ui.list

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cashflow.core.domain.datasource.CategoryLocalDataSource
import app.cashflow.core.domain.datasource.MerchantMemoryLocalDataSource
import app.cashflow.core.domain.datasource.RuleCatalog
import app.cashflow.core.domain.datasource.RuleLocalDataSource
import app.cashflow.core.domain.datasource.TransactionLocalDataSource
import app.cashflow.core.domain.model.CategorizedBy
import app.cashflow.core.domain.model.CategoryId
import app.cashflow.core.domain.model.CategoryRule
import app.cashflow.core.domain.model.RuleOrigin
import app.cashflow.core.importengine.RuleUseCases
import app.cashflow.core.ui.FlowTone
import app.cashflow.core.ui.UiText
import app.cashflow.core.ui.categoryName
import app.cashflow.core.ui.format.AppLocale
import app.cashflow.core.ui.format.PeriodFormatter
import app.cashflow.core.ui.tone
import app.cashflow.feature.rules.ui.R
import app.cashflow.feature.rules.ui.RuleMatchUi
import app.cashflow.feature.rules.ui.toMatchUi
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
import kotlin.time.Instant

enum class RulesTab { MINE, BUILT_IN }

/** One row in Kategoriregler: "börjar med "ICA" → Mat", where it came from and how often it is used. */
@Immutable
data class RuleRowUi(
    val key: String,
    val match: RuleMatchUi,
    val category: UiText,
    val tone: FlowTone,
    val origin: UiText,
    val uses: UiText,
)

@Stable
data class RulesState(
    val tab: RulesTab = RulesTab.MINE,
    val rows: List<RuleRowUi> = emptyList(),
    val mineCount: Int = 0,
    val isLoading: Boolean = true,
)

sealed interface RulesAction {
    data class OnTabSelect(val tab: RulesTab) : RulesAction
    data class OnRowClick(val key: String) : RulesAction
    data object OnNewRuleClick : RulesAction
    data object OnBackClick : RulesAction
}

/** Where a row leads: a user rule, a remembered payee or a built-in category group. */
sealed interface RulesEvent {
    data class OpenRule(val ruleId: String?) : RulesEvent
    data class OpenMemory(val payeeKey: String) : RulesEvent
    data class OpenBuiltIn(val categoryId: String) : RulesEvent
    data object NavigateBack : RulesEvent
}

/**
 * Kategoriregler: "Mina regler" (your rules and remembered payees) and "Inbyggda" (the bundled
 * catalog, grouped per category, read only).
 */
class RulesViewModel(
    rules: RuleLocalDataSource,
    memories: MerchantMemoryLocalDataSource,
    categories: CategoryLocalDataSource,
    transactions: TransactionLocalDataSource,
    private val catalog: RuleCatalog,
    private val ruleUseCases: RuleUseCases,
) : ViewModel() {

    private val _state = MutableStateFlow(RulesState())
    val state: StateFlow<RulesState> = _state.asStateFlow()

    private val _events = Channel<RulesEvent>()
    val events = _events.receiveAsFlow()

    private val tab = MutableStateFlow(RulesTab.MINE)
    private val builtIn = MutableStateFlow<List<CategoryRule>>(emptyList())
    private val locale = AppLocale.current()

    init {
        viewModelScope.launch { builtIn.value = catalog.builtInRules() }
        val sources = combine(rules.observeUserRules(), memories.observeAll(), builtIn) { r, m, b -> Triple(r, m, b) }
        combine(sources, categories.observeCategories(), transactions.observeAll(), tab) { (userRules, remembered, builtInRules), cats, txs, t ->
            val byId = cats.associateBy { it.id }
            fun name(id: CategoryId) = categoryName(id, byId[id]?.name ?: id.value)
            fun tone(id: CategoryId) = (byId[id]?.kind ?: app.cashflow.core.domain.model.CategoryKind.UNCATEGORIZED).tone()
            val rows = when (t) {
                RulesTab.MINE -> {
                    userRules.sortedByDescending { it.createdAt }.map { r ->
                        RuleRowUi(
                            key = "rule:${r.id}",
                            match = r.toMatchUi(locale),
                            category = name(r.categoryId),
                            tone = tone(r.categoryId),
                            origin = origin(r.origin, r.createdAt),
                            uses = UiText.PluralResource(R.plurals.rules_uses, ruleUseCases.reach(r, txs).matches),
                        )
                    } + remembered.sortedByDescending { it.createdAt }.map { m ->
                        RuleRowUi(
                            key = "memory:${m.payeeKey}",
                            match = RuleMatchUi(app.cashflow.core.domain.model.RuleMatch.EQUALS, m.payeeKey),
                            category = name(m.categoryId),
                            tone = tone(m.categoryId),
                            origin = m.createdAt?.let { UiText.StringResource(R.string.rules_origin_memory_on, arrayOf(date(it))) }
                                ?: UiText.StringResource(R.string.rules_origin_memory),
                            uses = UiText.PluralResource(R.plurals.rules_uses, ruleUseCases.usesOfPayee(m.payeeKey, txs)),
                        )
                    }
                }
                RulesTab.BUILT_IN -> builtInRules.groupBy { it.categoryId }
                    .toSortedMap(compareBy<CategoryId>({ byId[it]?.kind?.ordinal ?: 99 }, { byId[it]?.sortOrder ?: 0 }))
                    .map { (categoryId, group) ->
                        val patterns = group.map { it.pattern.trim() }.distinctBy { it.uppercase() }
                        val shown = patterns.take(3).joinToString(", ") + if (patterns.size > 3) " …" else ""
                        RuleRowUi(
                            key = "builtin:${categoryId.value}",
                            match = RuleMatchUi(null, shown),
                            category = name(categoryId),
                            tone = tone(categoryId),
                            origin = UiText.PluralResource(R.plurals.rules_builtin_patterns, patterns.size),
                            uses = UiText.PluralResource(
                                R.plurals.rules_uses_here,
                                txs.count { it.categorizedBy == CategorizedBy.BUILT_IN_RULE && it.categoryId == categoryId },
                            ),
                        )
                    }
            }
            RulesState(tab = t, rows = rows, mineCount = userRules.size + remembered.size, isLoading = false)
        }.onEach { s -> _state.update { s } }.launchIn(viewModelScope)
    }

    fun onAction(action: RulesAction) {
        when (action) {
            is RulesAction.OnTabSelect -> tab.value = action.tab
            RulesAction.OnNewRuleClick -> send(RulesEvent.OpenRule(null))
            RulesAction.OnBackClick -> send(RulesEvent.NavigateBack)
            is RulesAction.OnRowClick -> {
                val (kind, id) = action.key.split(':', limit = 2)
                send(
                    when (kind) {
                        "rule" -> RulesEvent.OpenRule(id)
                        "memory" -> RulesEvent.OpenMemory(id)
                        else -> RulesEvent.OpenBuiltIn(id)
                    },
                )
            }
        }
    }

    private fun send(e: RulesEvent) {
        viewModelScope.launch { _events.send(e) }
    }

    private fun date(at: Instant) = PeriodFormatter.iso(at.toLocalDateTime(TimeZone.currentSystemDefault()).date, locale)

    private fun origin(origin: RuleOrigin, at: Instant?): UiText {
        val d = at?.let(::date) ?: ""
        return when (origin) {
            RuleOrigin.CREATED -> UiText.StringResource(R.string.rules_origin_created, arrayOf(d))
            RuleOrigin.OVERRIDE -> UiText.StringResource(R.string.rules_origin_override, arrayOf(d))
        }
    }
}
