package app.cashflow.feature.rules.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cashflow.core.designsystem.component.CfButton
import app.cashflow.core.designsystem.component.CfButtonVariant
import app.cashflow.core.designsystem.component.CfChip
import app.cashflow.core.designsystem.component.CfSwitchRow
import app.cashflow.core.designsystem.component.CfTextField
import app.cashflow.core.designsystem.component.SheetHeader
import app.cashflow.core.designsystem.theme.CashFlowTheme
import app.cashflow.core.designsystem.theme.Radius
import app.cashflow.core.designsystem.theme.Spacing
import app.cashflow.core.ui.FlowTone
import app.cashflow.core.ui.ObserveAsEvents
import app.cashflow.core.ui.UiText
import app.cashflow.core.ui.colors
import app.cashflow.feature.rules.ui.R
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun RuleEditorRoot(
    ruleId: String?,
    payeeKey: String?,
    builtInCategory: String?,
    onDismiss: () -> Unit,
    viewModel: RuleEditorViewModel = koinViewModel(key = "rule-$ruleId-$payeeKey-$builtInCategory") {
        parametersOf(ruleId, payeeKey, builtInCategory)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            RuleEditorEvent.Dismiss -> onDismiss()
        }
    }
    RuleEditorSheet(state, viewModel::onAction)
}

/** Content of the rule sheet (the sheet itself is a Nav3 scene in :app). */
@Composable
fun RuleEditorSheet(
    state: RuleEditorState,
    onAction: (RuleEditorAction) -> Unit,
) {
    val c = CashFlowTheme.colors
    val panel = if (c.isDark) c.surfaceCard else c.surfaceGroup
    LazyColumn(
        modifier = Modifier.fillMaxWidth().imePadding(),
        contentPadding = PaddingValues(start = Spacing.s4, end = Spacing.s4, bottom = Spacing.s6),
        verticalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) {
        item {
            SheetHeader(
                title = when (state.mode) {
                    EditorMode.NEW_RULE -> stringResource(R.string.editor_title_new)
                    else -> state.title
                },
                eyebrow = stringResource(
                    when (state.mode) {
                        EditorMode.NEW_RULE -> R.string.editor_eyebrow_new
                        EditorMode.EDIT_RULE -> R.string.editor_eyebrow_edit
                        EditorMode.MEMORY -> R.string.editor_eyebrow_memory
                        EditorMode.BUILT_IN -> R.string.editor_eyebrow_builtin
                    },
                ),
            )
        }
        when (state.mode) {
            EditorMode.BUILT_IN -> {
                item {
                    Column(
                        Modifier.fillMaxWidth().clip(Radius.sm).background(panel).padding(Spacing.s4),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
                    ) {
                        Text(stringResource(R.string.editor_builtin_caption), style = CashFlowTheme.typography.caption, color = c.textSecondary)
                        Text(state.builtInText.orEmpty(), style = CashFlowTheme.typography.body, color = c.textPrimary)
                        state.reach?.let { Text(it.asString(), style = CashFlowTheme.typography.label, color = c.textSecondary) }
                        Text(stringResource(R.string.editor_builtin_note), style = CashFlowTheme.typography.label, color = c.textSecondary)
                    }
                }
                item { CfButton(stringResource(R.string.editor_override), { onAction(RuleEditorAction.OnOverrideClick) }, Modifier.fillMaxWidth()) }
            }
            EditorMode.MEMORY -> {
                item { Text(stringResource(R.string.editor_memory_body), style = CashFlowTheme.typography.body, color = c.textSecondary) }
                item { CategoryPicker(state.categories, onAction) }
                state.reach?.let { r -> item { Text(r.asString(), style = CashFlowTheme.typography.label, color = c.textSecondary) } }
                item { Buttons(state, onAction, deleteLabel = R.string.editor_forget) }
            }
            EditorMode.NEW_RULE, EditorMode.EDIT_RULE -> {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                        Text(stringResource(R.string.editor_if_text), style = CashFlowTheme.typography.label, color = c.textSecondary)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                            state.matchOptions.forEach { o -> CfChip(o.label.asString(), o.selected, { onAction(RuleEditorAction.OnMatchSelect(o.key)) }) }
                        }
                        CfTextField(
                            label = stringResource(R.string.editor_text),
                            value = state.text,
                            onValueChange = { onAction(RuleEditorAction.OnTextChange(it)) },
                            placeholder = stringResource(R.string.editor_text_hint),
                        )
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                        Text(stringResource(R.string.editor_account), style = CashFlowTheme.typography.label, color = c.textSecondary)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                            state.accountOptions.forEach { o -> CfChip(o.label.asString(), o.selected, { onAction(RuleEditorAction.OnAccountSelect(o.key)) }) }
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                        CfTextField(
                            stringResource(R.string.editor_over), state.over, { onAction(RuleEditorAction.OnOverChange(it)) },
                            Modifier.weight(1f), placeholder = stringResource(R.string.editor_optional), numeric = true, suffix = "kr",
                        )
                        CfTextField(
                            stringResource(R.string.editor_under), state.under, { onAction(RuleEditorAction.OnUnderChange(it)) },
                            Modifier.weight(1f), placeholder = stringResource(R.string.editor_optional), numeric = true, suffix = "kr",
                        )
                    }
                }
                item { CategoryPicker(state.categories, onAction) }
                item {
                    Column(
                        Modifier.fillMaxWidth().clip(Radius.sm).background(panel).padding(Spacing.s4),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s1),
                    ) {
                        Text(
                            state.reach?.asString() ?: stringResource(R.string.editor_incomplete),
                            style = CashFlowTheme.typography.bodyStrong,
                            color = c.textPrimary,
                        )
                        Text(stringResource(R.string.editor_order_note), style = CashFlowTheme.typography.label, color = c.textSecondary)
                    }
                }
                state.applyLabel?.let { label ->
                    item { CfSwitchRow(label.asString(), state.applyToPast, { onAction(RuleEditorAction.OnApplyToggle(it)) }) }
                }
                item { Buttons(state, onAction, deleteLabel = R.string.editor_delete) }
            }
        }
        state.error?.let { e -> item { Text(e.asString(), style = CashFlowTheme.typography.label, color = c.dangerStrong) } }
    }
}

@Composable
private fun CategoryPicker(categories: List<EditorCategoryUi>, onAction: (RuleEditorAction) -> Unit) {
    val c = CashFlowTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        Text(stringResource(R.string.editor_category), style = CashFlowTheme.typography.label, color = c.textSecondary)
        categories.groupBy { it.group }.forEach { (group, list) ->
            Text(group.asString().uppercase(), style = CashFlowTheme.typography.overline, color = c.textSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                list.forEach { cat ->
                    CfChip(cat.name.asString(), cat.selected, { onAction(RuleEditorAction.OnCategorySelect(cat.id)) }, leadingColor = cat.tone.colors().strong)
                }
            }
        }
    }
}

@Composable
private fun Buttons(state: RuleEditorState, onAction: (RuleEditorAction) -> Unit, deleteLabel: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s3)) {
        if (state.canDelete) {
            CfButton(stringResource(deleteLabel), { onAction(RuleEditorAction.OnDeleteClick) }, Modifier.weight(1f), variant = CfButtonVariant.Danger)
        }
        CfButton(stringResource(R.string.editor_save), { onAction(RuleEditorAction.OnSaveClick) }, Modifier.weight(1f), enabled = state.canSave)
    }
}

@Preview(showBackground = true)
@Composable
private fun RuleEditorPreview() {
    CashFlowTheme {
        RuleEditorSheet(
            RuleEditorState(
                mode = EditorMode.NEW_RULE,
                text = "Bolån",
                matchOptions = listOf(EditorOptionUi("STARTS_WITH", UiText.DynamicString("börjar med"), true)),
                accountOptions = listOf(EditorOptionUi("", UiText.DynamicString("Alla konton"), true)),
                categories = listOf(EditorCategoryUi("boende", UiText.DynamicString("Boende"), UiText.DynamicString("Fasta"), FlowTone.Fixed, true)),
                reach = UiText.DynamicString("Matchar 8 transaktioner"),
                applyLabel = UiText.DynamicString("Använd på 2 tidigare köp också"),
                canSave = true,
                isLoading = false,
            ),
            onAction = {},
        )
    }
}
