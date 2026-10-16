package app.cashflow.feature.rules.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.cashflow.feature.rules.ui.editor.RuleEditorRoot
import app.cashflow.feature.rules.ui.editor.RuleEditorViewModel
import app.cashflow.feature.rules.ui.list.RulesRoot
import app.cashflow.feature.rules.ui.list.RulesViewModel
import kotlinx.serialization.Serializable
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Kategoriregler: Mina regler and Inbyggda. */
@Serializable
data object RulesRoute : NavKey

/**
 * The rule sheet. One of the ids is set: a user rule ([ruleId]), a remembered payee
 * ([payeeKey]) or a built-in category group ([builtInCategory]); none = a new rule.
 */
@Serializable
data class RuleEditorRoute(
    val ruleId: String? = null,
    val payeeKey: String? = null,
    val builtInCategory: String? = null,
) : NavKey

fun EntryProviderScope<NavKey>.rulesEntries(
    contentPadding: PaddingValues,
    sheetMetadata: Map<String, Any>,
    onOpenEditor: (RuleEditorRoute) -> Unit,
    onBack: () -> Unit,
) {
    entry<RulesRoute> {
        RulesRoot(
            contentPadding = contentPadding,
            onOpenRule = { onOpenEditor(RuleEditorRoute(ruleId = it)) },
            onOpenMemory = { onOpenEditor(RuleEditorRoute(payeeKey = it)) },
            onOpenBuiltIn = { onOpenEditor(RuleEditorRoute(builtInCategory = it)) },
            onBack = onBack,
        )
    }
    entry<RuleEditorRoute>(metadata = sheetMetadata) { key ->
        RuleEditorRoot(ruleId = key.ruleId, payeeKey = key.payeeKey, builtInCategory = key.builtInCategory, onDismiss = onBack)
    }
}

val rulesUiModule = module {
    viewModelOf(::RulesViewModel)
    // Lambda form: the ids are navigation arguments, and the fields survive process death.
    viewModel { params ->
        RuleEditorViewModel(
            ruleId = params.values.getOrNull(0) as String?,
            payeeKey = params.values.getOrNull(1) as String?,
            builtInCategory = params.values.getOrNull(2) as String?,
            handle = get(),
            rules = get(),
            memories = get(),
            categories = get(),
            accounts = get(),
            transactions = get(),
            catalog = get(),
            ruleUseCases = get(),
        )
    }
}
