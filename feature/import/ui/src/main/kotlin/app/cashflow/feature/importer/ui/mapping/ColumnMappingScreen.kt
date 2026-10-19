package app.cashflow.feature.importer.ui.mapping

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cashflow.core.designsystem.component.AccentHeader
import app.cashflow.core.designsystem.component.BandStatusBar
import app.cashflow.core.designsystem.component.rememberOnBand
import app.cashflow.core.designsystem.component.BandIconButton
import app.cashflow.core.designsystem.component.CfButton
import app.cashflow.core.designsystem.component.CfButtonVariant
import app.cashflow.core.designsystem.component.CfCard
import app.cashflow.core.designsystem.component.CfChip
import app.cashflow.core.designsystem.component.CfSwitchRow
import app.cashflow.core.designsystem.component.CfTextField
import app.cashflow.core.designsystem.component.GroupHeading
import app.cashflow.core.designsystem.icon.CfIcons
import app.cashflow.core.designsystem.theme.Accent
import app.cashflow.core.designsystem.theme.CashFlowTheme
import app.cashflow.core.designsystem.theme.Radius
import app.cashflow.core.designsystem.theme.Spacing
import app.cashflow.core.ui.ObserveAsEvents
import app.cashflow.core.ui.UiText
import app.cashflow.feature.importer.domain.ColumnRole
import app.cashflow.feature.importer.ui.R
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ColumnMappingRoot(
    uri: String,
    onShowReceipt: (List<String>) -> Unit,
    onClose: () -> Unit,
    viewModel: ColumnMappingViewModel = koinViewModel(key = "map-$uri") { parametersOf(uri) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is ColumnMappingEvent.ShowReceipt -> onShowReceipt(event.importIds)
            ColumnMappingEvent.Close -> onClose()
        }
    }
    ColumnMappingScreen(state, viewModel::onAction)
}

private fun ColumnRole.label(): Int = when (this) {
    ColumnRole.DATE -> R.string.mapping_role_date
    ColumnRole.TRANSACTION_DATE -> R.string.mapping_role_tx_date
    ColumnRole.TEXT -> R.string.mapping_role_text
    ColumnRole.AMOUNT -> R.string.mapping_role_amount
    ColumnRole.AMOUNT_IN -> R.string.mapping_role_in
    ColumnRole.AMOUNT_OUT -> R.string.mapping_role_out
    ColumnRole.BALANCE -> R.string.mapping_role_balance
    ColumnRole.IGNORE -> R.string.mapping_role_ignore
}

@Composable
fun ColumnMappingScreen(
    state: ColumnMappingState,
    onAction: (ColumnMappingAction) -> Unit,
) {
    val c = CashFlowTheme.colors
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(c.bgCanvas)
                .imePadding()
                .verticalScroll(scroll)
                .padding(bottom = Spacing.s12),
        ) {
            AccentHeader(accent = Accent.Events, title = stringResource(R.string.import_title), leading = { BandIconButton(CfIcons.Close, stringResource(R.string.cd_cancel_import), { onAction(ColumnMappingAction.OnCloseClick) }) }) {
                CfCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                        Text(state.fileName, style = CashFlowTheme.typography.bodyStrong, color = c.textPrimary)
                        state.meta?.let { Text(it.asString(), style = CashFlowTheme.typography.label, color = c.textSecondary) }
                        Text(stringResource(R.string.mapping_intro), style = CashFlowTheme.typography.body, color = c.textPrimary)
                        if (state.formatChips.isNotEmpty()) {
                            Text(state.formatChips.map { it.asString() }.joinToString(" · "), style = CashFlowTheme.typography.label, color = c.textSecondary)
                        }
                        if (state.isLoading) CircularProgressIndicator(color = c.accentEventsInk, modifier = Modifier.align(Alignment.CenterHorizontally))
                        state.error?.let { Text(it.asString(), style = CashFlowTheme.typography.body, color = c.dangerStrong) }
                    }
                }
            }
            if (state.columns.isEmpty()) return@Column
            Column(Modifier.padding(horizontal = Spacing.gutter)) {
                GroupHeading(stringResource(R.string.mapping_step_columns))
                CfCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                        state.columns.forEach { col -> ColumnRow(col, onAction) }
                        CfSwitchRow(stringResource(R.string.mapping_negate), state.negate, { onAction(ColumnMappingAction.OnNegateToggle(it)) })
                    }
                }

                GroupHeading(stringResource(R.string.mapping_step_preview))
                state.warning?.let { w ->
                    Column(
                        Modifier.fillMaxWidth().clip(Radius.sm).background(c.vizFixedFill).padding(Spacing.s4),
                    ) { Text(w.asString(), style = CashFlowTheme.typography.body, color = c.textPrimary) }
                }
                if (state.preview.isNotEmpty()) {
                    CfCard(Modifier.fillMaxWidth().padding(top = Spacing.s2)) {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                            state.preview.forEach { p ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(p.text, style = CashFlowTheme.typography.bodyStrong, color = c.textPrimary, maxLines = 1)
                                        Text(p.date, style = CashFlowTheme.typography.label, color = c.textSecondary)
                                    }
                                    Text(p.amount, style = CashFlowTheme.typography.bodyStrong.copy(fontFeatureSettings = "tnum"), color = c.textPrimary)
                                }
                            }
                        }
                    }
                }
                state.summary?.let { Text(it.asString(), style = CashFlowTheme.typography.label, color = c.textSecondary, modifier = Modifier.padding(top = Spacing.s2, start = Spacing.s1)) }

                GroupHeading(stringResource(R.string.mapping_step_account))
                CfCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                        Text(stringResource(R.string.mapping_account_question), style = CashFlowTheme.typography.body, color = c.textPrimary)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                            state.accounts.forEach { a -> CfChip(a.label.asString(), a.selected, { onAction(ColumnMappingAction.OnAccountSelect(a.id)) }) }
                        }
                        if (state.accounts.lastOrNull()?.selected == true) {
                            CfTextField(stringResource(R.string.import_account_name), state.newAccountName, { onAction(ColumnMappingAction.OnNewAccountNameChange(it)) })
                        }
                    }
                }

                GroupHeading(stringResource(R.string.mapping_step_save))
                CfCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                        CfTextField(stringResource(R.string.mapping_profile_name), state.profileName, { onAction(ColumnMappingAction.OnProfileNameChange(it)) })
                        Text(stringResource(R.string.mapping_profile_hint), style = CashFlowTheme.typography.label, color = c.textSecondary)
                    }
                }
                CfButton(
                    stringResource(R.string.mapping_import),
                    { onAction(ColumnMappingAction.OnImportClick) },
                    Modifier.fillMaxWidth().padding(top = Spacing.s6),
                    variant = CfButtonVariant.Accent,
                    accent = c.actionEvents,
                    enabled = state.canImport && !state.isWorking,
                )
            }
        }
        BandStatusBar(rememberOnBand(scroll))
    }
}

@Composable
private fun ColumnRow(col: MapColumnUi, onAction: (ColumnMappingAction) -> Unit) {
    val c = CashFlowTheme.colors
    var open by remember { mutableStateOf(false) }
    val roleLabel = stringResource(col.role.label())
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(col.header, style = CashFlowTheme.typography.bodyStrong, color = c.textPrimary, maxLines = 1)
            Text(stringResource(R.string.mapping_sample, col.sample), style = CashFlowTheme.typography.label, color = c.textSecondary, maxLines = 1)
        }
        Box(Modifier.padding(start = Spacing.s3).width(150.dp)) {
            Column(horizontalAlignment = Alignment.End) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp)
                        .clip(Radius.xs)
                        .border(1.dp, c.borderSubtle, Radius.xs)
                        .clickable(role = Role.DropdownList) { open = true }
                        .semantics { contentDescription = "${col.header}: $roleLabel" }
                        .padding(horizontal = Spacing.s3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(roleLabel, style = CashFlowTheme.typography.body, color = c.textPrimary, modifier = Modifier.weight(1f), maxLines = 1)
                    Icon(CfIcons.ChevronDown, contentDescription = null, tint = c.textSecondary)
                }
                Text(
                    stringResource(if (col.sure) R.string.mapping_sure else R.string.mapping_guess),
                    style = CashFlowTheme.typography.caption,
                    color = if (col.sure) c.textOnEventsTint else c.textSecondary,
                )
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = c.surfaceSheet) {
                ColumnRole.entries.forEach { role ->
                    DropdownMenuItem(
                        text = { Text(stringResource(role.label()), style = CashFlowTheme.typography.body, color = c.textPrimary) },
                        onClick = {
                            open = false
                            onAction(ColumnMappingAction.OnRoleSelect(col.index, role))
                        },
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ColumnMappingPreview() {
    CashFlowTheme {
        ColumnMappingScreen(
            ColumnMappingState(
                fileName = "Kontoutdrag_2026-10.csv",
                meta = UiText.DynamicString("47 rader · 2026-09-01 – 2026-10-13"),
                columns = listOf(
                    MapColumnUi(0, "Datum", "2026-10-13", ColumnRole.DATE, true),
                    MapColumnUi(1, "Beskrivning", "MATBUTIK EXEMPEL", ColumnRole.TEXT, true),
                    MapColumnUi(2, "Belopp", "-412,50", ColumnRole.AMOUNT, false),
                ),
                preview = listOf(PreviewRowUi("MATBUTIK EXEMPEL", "2026-10-13", "−413 kr", false)),
                accounts = listOf(MapAccountUi(null, UiText.DynamicString("+ Nytt konto"), true)),
                newAccountName = "Konto 4",
                profileName = "Kontoutdrag",
                canImport = true,
                isLoading = false,
            ),
            onAction = {},
        )
    }
}
