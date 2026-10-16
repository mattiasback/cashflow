package app.cashflow.feature.importer.ui.entry

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.layout.WindowInsetsRulers
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fitInside
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cashflow.core.designsystem.component.AccentHeader
import app.cashflow.core.designsystem.component.BandIconButton
import app.cashflow.core.designsystem.component.CfButton
import app.cashflow.core.designsystem.component.CfButtonVariant
import app.cashflow.core.designsystem.component.CfCard
import app.cashflow.core.designsystem.component.CfChip
import app.cashflow.core.designsystem.icon.CfIcons
import app.cashflow.core.designsystem.theme.Accent
import app.cashflow.core.designsystem.theme.CashFlowTheme
import app.cashflow.core.designsystem.theme.Radius
import app.cashflow.core.designsystem.theme.Spacing
import app.cashflow.core.ui.ObserveAsEvents
import app.cashflow.core.ui.UiText
import app.cashflow.feature.importer.ui.R
import app.cashflow.feature.importer.ui.StatementMimeTypes
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ImportRoot(
    sharedUris: List<String>,
    onShowReceipt: (List<String>) -> Unit,
    onClose: () -> Unit,
    viewModel: ImportViewModel = koinViewModel { parametersOf(sharedUris) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.onAction(ImportAction.OnFilesPicked(uris.map { it.toString() }))
    }
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            ImportEvent.LaunchPicker -> picker.launch(StatementMimeTypes)
            is ImportEvent.ShowReceipt -> onShowReceipt(event.importIds)
            ImportEvent.Close -> onClose()
        }
    }
    ImportScreen(state, viewModel::onAction)
}

@Composable
fun ImportScreen(
    state: ImportState,
    onAction: (ImportAction) -> Unit,
) {
    val c = CashFlowTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(c.bgCanvas)
            // The new-account name field must stay above the keyboard.
            .fitInside(WindowInsetsRulers.Ime.current)
            .verticalScroll(rememberScrollState())
            .padding(bottom = Spacing.s12),
    ) {
        AccentHeader(
            accent = Accent.Events,
            title = stringResource(R.string.import_title),
            actions = { BandIconButton(CfIcons.Close, stringResource(R.string.cd_cancel_import), { onAction(ImportAction.OnCloseClick) }) },
        ) {
            CfCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                    when (state.phase) {
                        ImportPhase.PICKING -> {
                            Text(stringResource(R.string.import_pick_title), style = CashFlowTheme.typography.title, color = c.textPrimary)
                            Text(stringResource(R.string.import_pick_body), style = CashFlowTheme.typography.body, color = c.textSecondary)
                            CfButton(stringResource(R.string.import_pick_button), { onAction(ImportAction.OnPickFilesClick) }, Modifier.fillMaxWidth(), icon = CfIcons.File)
                        }
                        ImportPhase.READING, ImportPhase.IMPORTING -> {
                            CircularProgressIndicator(color = c.accentEventsInk, modifier = Modifier.align(Alignment.CenterHorizontally))
                            Text(
                                if (state.phase == ImportPhase.READING) UiText.PluralResource(R.plurals.import_reading, state.fileCount).asString()
                                else stringResource(R.string.import_importing),
                                style = CashFlowTheme.typography.body,
                                color = c.textPrimary,
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            )
                        }
                        ImportPhase.QUESTIONS -> {
                            val onlyTypes = state.questions.isEmpty()
                            Text(
                                stringResource(if (onlyTypes) R.string.import_type_title else R.string.import_questions_title),
                                style = CashFlowTheme.typography.title,
                                color = c.textPrimary,
                            )
                            Text(
                                stringResource(if (onlyTypes) R.string.import_type_body else R.string.import_questions_body),
                                style = CashFlowTheme.typography.body,
                                color = c.textSecondary,
                            )
                            if (state.typeQuestions.isNotEmpty()) {
                                Text(stringResource(R.string.import_type_hint), style = CashFlowTheme.typography.label, color = c.textSecondary)
                            }
                        }
                        ImportPhase.FAILED -> {
                            Text(stringResource(R.string.import_failed_title), style = CashFlowTheme.typography.title, color = c.textPrimary)
                            state.error?.let { Text(it.asString(), style = CashFlowTheme.typography.body, color = c.dangerStrong) }
                            CfButton(stringResource(R.string.import_pick_again), { onAction(ImportAction.OnPickFilesClick) }, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }

        Column(Modifier.padding(horizontal = Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
            state.questions.forEach { q -> QuestionCard(q, onAction) }
            state.typeQuestions.forEach { q -> TypeQuestionCard(q, onAction) }
            if (state.failures.isNotEmpty()) {
                Text(stringResource(R.string.import_skipped_files), style = CashFlowTheme.typography.overline, color = c.textSecondary, modifier = Modifier.padding(top = Spacing.s4))
                state.failures.forEach { f ->
                    CfCard(Modifier.fillMaxWidth()) {
                        Column {
                            Text(f.fileName, style = CashFlowTheme.typography.bodyStrong, color = c.textPrimary)
                            Text(f.reason.asString(), style = CashFlowTheme.typography.label, color = c.textSecondary)
                        }
                    }
                }
            }
            if (state.phase == ImportPhase.QUESTIONS) {
                Spacer(Modifier.height(Spacing.s3))
                CfButton(
                    stringResource(R.string.import_button_go),
                    { onAction(ImportAction.OnImportClick) },
                    Modifier.fillMaxWidth(),
                    variant = CfButtonVariant.Accent,
                    accent = c.actionEvents,
                    enabled = state.canImport,
                )
            }
        }
    }
}

@Composable
private fun QuestionCard(q: AccountQuestionUi, onAction: (ImportAction) -> Unit) {
    val c = CashFlowTheme.colors
    CfCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
            Text(stringResource(R.string.import_which_account), style = CashFlowTheme.typography.title, color = c.textPrimary, modifier = Modifier.semantics { heading() })
            Text(q.files, style = CashFlowTheme.typography.label, color = c.textSecondary, maxLines = 3)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                q.choices.forEach { choice ->
                    CfChip(
                        label = choice.label.asString(),
                        selected = choice.selected,
                        onClick = { onAction(ImportAction.OnAccountChoice(q.group, choice.id)) },
                    )
                }
            }
            if (q.isNew) {
                val label = stringResource(R.string.import_account_name)
                Text(label, style = CashFlowTheme.typography.label, color = c.textSecondary)
                BasicTextField(
                    value = q.newName,
                    onValueChange = { onAction(ImportAction.OnNewNameChange(q.group, it)) },
                    singleLine = true,
                    textStyle = CashFlowTheme.typography.body.copy(color = c.textPrimary),
                    cursorBrush = SolidColor(c.brand),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(Radius.xs)
                        .border(1.dp, c.borderSubtle, Radius.xs)
                        .padding(horizontal = Spacing.s3, vertical = Spacing.s3)
                        .semantics { contentDescription = label },
                )
            }
            Text(stringResource(R.string.import_remember_hint), style = CashFlowTheme.typography.label, color = c.textSecondary)
        }
    }
}

@Composable
private fun TypeQuestionCard(q: AccountTypeQuestionUi, onAction: (ImportAction) -> Unit) {
    val c = CashFlowTheme.colors
    CfCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
            Text(
                stringResource(R.string.import_type_question, q.name),
                style = CashFlowTheme.typography.title,
                color = c.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Text(q.institution, style = CashFlowTheme.typography.label, color = c.textSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                q.options.forEach { option ->
                    CfChip(
                        label = option.label.asString(),
                        selected = option.selected,
                        onClick = { onAction(ImportAction.OnAccountType(q.key, option.type)) },
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ImportScreenPreview() {
    app.cashflow.core.designsystem.theme.CashFlowTheme {
        ImportScreen(
            ImportState(
                phase = ImportPhase.QUESTIONS,
                fileCount = 3,
                canImport = true,
                questions = listOf(
                    AccountQuestionUi(
                        0, "Kontoutdrag.xls, Kontoutdrag (1).xls",
                        listOf(AccountChoiceUi(null, UiText.DynamicString("Nytt konto"), true)),
                        isNew = true, newName = "Konto 1",
                    ),
                ),
            ),
            onAction = {},
        )
    }
}
