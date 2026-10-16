package app.cashflow.feature.importer.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cashflow.core.designsystem.component.AccentHeader
import app.cashflow.core.designsystem.component.BandIconButton
import app.cashflow.core.designsystem.component.CfButton
import app.cashflow.core.designsystem.component.CfButtonVariant
import app.cashflow.core.designsystem.component.CfCard
import app.cashflow.core.designsystem.component.GroupHeading
import app.cashflow.core.designsystem.component.ListGroup
import app.cashflow.core.designsystem.component.ListRow
import app.cashflow.core.designsystem.component.SheetHeader
import app.cashflow.core.designsystem.icon.CfIcons
import app.cashflow.core.designsystem.theme.Accent
import app.cashflow.core.designsystem.theme.CashFlowTheme
import app.cashflow.core.designsystem.theme.Radius
import app.cashflow.core.designsystem.theme.Sizes
import app.cashflow.core.designsystem.theme.Spacing
import app.cashflow.core.ui.ObserveAsEvents
import app.cashflow.core.ui.UiText
import app.cashflow.feature.importer.ui.R
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ImportHistoryRoot(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    viewModel: ImportHistoryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            ImportHistoryEvent.NavigateBack -> onBack()
        }
    }
    ImportHistoryScreen(state, viewModel::onAction, contentPadding)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportHistoryScreen(
    state: ImportHistoryState,
    onAction: (ImportHistoryAction) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val c = CashFlowTheme.colors
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(c.bgCanvas),
        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + Spacing.s4),
    ) {
        item(key = "header") {
            AccentHeader(
                accent = Accent.Settings,
                title = stringResource(R.string.history_title),
                actions = { BandIconButton(CfIcons.ChevronLeft, stringResource(R.string.cd_back), { onAction(ImportHistoryAction.OnBackClick) }) },
            ) {
                CfCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
                        Text(stringResource(R.string.history_intro_title), style = CashFlowTheme.typography.bodyStrong, color = c.textPrimary)
                        Text(stringResource(R.string.history_intro_body), style = CashFlowTheme.typography.label, color = c.textSecondary)
                    }
                }
            }
        }
        state.months.forEach { month ->
            item(key = month.heading) {
                Column(Modifier.padding(horizontal = Spacing.gutter)) {
                    GroupHeading(month.heading)
                    ListGroup {
                        month.rows.forEachIndexed { i, row ->
                            ListRow(
                                title = row.title,
                                subtitle = row.line.asString(),
                                leading = { KindIcon(row.kind, row.undone) },
                                showDivider = i > 0,
                                onClick = { onAction(ImportHistoryAction.OnImportClick(row.id)) },
                                trailing = { Icon(CfIcons.ChevronRight, contentDescription = null, tint = c.textSecondary) },
                            )
                        }
                    }
                }
            }
        }
        item(key = "footer") {
            Text(
                stringResource(R.string.history_footer),
                style = CashFlowTheme.typography.label,
                color = c.textSecondary,
                modifier = Modifier.padding(horizontal = Spacing.gutter + Spacing.s1, vertical = Spacing.s4),
            )
        }
    }

    state.sheet?.let { sheet ->
        ModalBottomSheet(onDismissRequest = { onAction(ImportHistoryAction.OnSheetDismiss) }, containerColor = c.surfaceSheet) {
            ImportSheetContent(sheet, state.isWorking, state.error, onAction)
        }
    }
}

@Composable
private fun KindIcon(kind: ImportKind, undone: Boolean) {
    val c = CashFlowTheme.colors
    val (bg, fg, icon) = when (kind) {
        ImportKind.BANK_FILE -> Triple(c.accentEventsTint, c.textOnEventsTint, CfIcons.File)
        ImportKind.SAVINGS_FILE -> Triple(c.vizSavingsFill, c.vizSavings, CfIcons.Savings)
        ImportKind.MAPPED_FILE -> Triple(c.surfaceControl, c.textPrimary, CfIcons.Sliders)
    }
    Box(
        Modifier.size(Sizes.badgeSize + 8.dp).clip(Radius.pill).background(if (undone) c.surfaceControl else bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon as ImageVector, contentDescription = null, tint = if (undone) c.textSecondary else fg, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ImportSheetContent(sheet: ImportSheetUi, working: Boolean, error: UiText?, onAction: (ImportHistoryAction) -> Unit) {
    val c = CashFlowTheme.colors
    val panel = if (c.isDark) c.surfaceCard else c.surfaceGroup
    Column(
        Modifier.fillMaxWidth().padding(start = Spacing.s4, end = Spacing.s4, bottom = Spacing.s6),
        verticalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) {
        SheetHeader(title = sheet.title, eyebrow = sheet.eyebrow.asString())
        Text(sheet.importedAt, style = CashFlowTheme.typography.label, color = c.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s3)) {
            Stat(sheet.added.toString(), stringResource(R.string.history_stat_added), Modifier.weight(1f))
            Stat(sheet.duplicates.toString(), stringResource(R.string.history_stat_dups), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s3)) {
            Stat(sheet.review.toString(), stringResource(R.string.history_stat_review), Modifier.weight(1f))
            Stat(sheet.edited.toString(), stringResource(R.string.history_stat_edited), Modifier.weight(1f))
        }
        Column(
            Modifier.fillMaxWidth().clip(Radius.sm).background(panel).padding(Spacing.s4),
            verticalArrangement = Arrangement.spacedBy(Spacing.s1),
        ) {
            Text(stringResource(R.string.history_details), style = CashFlowTheme.typography.caption, color = c.textSecondary)
            Text(sheet.accounts, style = CashFlowTheme.typography.body, color = c.textPrimary)
            Text(stringResource(R.string.history_details_line, sheet.shortId, sheet.range), style = CashFlowTheme.typography.label, color = c.textSecondary)
        }
        if (sheet.undoneAt == null) {
            if (sheet.confirming) {
                Column(
                    Modifier.fillMaxWidth().clip(Radius.sm).background(c.vizFixedFill).padding(Spacing.s4).semantics { liveRegion = LiveRegionMode.Polite },
                    verticalArrangement = Arrangement.spacedBy(Spacing.s1),
                ) {
                    Text(stringResource(R.string.history_confirm_title), style = CashFlowTheme.typography.bodyStrong, color = c.textPrimary)
                    Text(sheet.confirmText.asString(), style = CashFlowTheme.typography.body, color = c.textPrimary)
                }
            }
            CfButton(
                stringResource(if (sheet.confirming) R.string.history_undo_confirm else R.string.history_undo),
                { onAction(ImportHistoryAction.OnUndoClick) },
                Modifier.fillMaxWidth(),
                variant = CfButtonVariant.Danger,
                enabled = !working,
            )
        } else {
            Column(
                Modifier.fillMaxWidth().clip(Radius.sm).background(panel).padding(Spacing.s4),
                verticalArrangement = Arrangement.spacedBy(Spacing.s1),
            ) {
                Text(stringResource(R.string.history_undone_title, sheet.undoneAt), style = CashFlowTheme.typography.bodyStrong, color = c.textPrimary)
                Text(stringResource(R.string.history_undone_body), style = CashFlowTheme.typography.body, color = c.textPrimary)
            }
            CfButton(stringResource(R.string.history_redo), { onAction(ImportHistoryAction.OnRedoClick) }, Modifier.fillMaxWidth(), variant = CfButtonVariant.Outline, enabled = !working)
        }
        error?.let { Text(it.asString(), style = CashFlowTheme.typography.label, color = c.dangerStrong) }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    val c = CashFlowTheme.colors
    Column(modifier.clip(Radius.sm).background(if (c.isDark) c.surfaceCard else c.surfaceGroup).padding(Spacing.s4)) {
        Text(value, style = CashFlowTheme.typography.titleL.copy(fontFeatureSettings = "tnum"), color = c.textPrimary)
        Text(label, style = CashFlowTheme.typography.label, color = c.textSecondary)
    }
}

@Preview(showBackground = true)
@Composable
private fun ImportHistoryPreview() {
    CashFlowTheme {
        ImportHistoryScreen(
            ImportHistoryState(
                months = listOf(
                    ImportMonthUi(
                        "oktober 2026",
                        listOf(
                            ImportRowUi("1", "Kontoutdrag.xls", ImportKind.BANK_FILE, UiText.DynamicString("2026-10-16 10:11 · 38 nya · 6 dubbletter · 3 att granska"), false),
                            ImportRowUi("2", "transaktioner.csv", ImportKind.SAVINGS_FILE, UiText.DynamicString("Ångrad · 645 borttagna"), true),
                        ),
                    ),
                ),
                isLoading = false,
            ),
            onAction = {},
        )
    }
}
