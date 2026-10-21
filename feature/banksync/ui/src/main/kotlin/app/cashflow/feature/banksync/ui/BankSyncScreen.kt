package app.cashflow.feature.banksync.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cashflow.core.designsystem.component.AccentHeader
import app.cashflow.core.designsystem.component.BandIconButton
import app.cashflow.core.designsystem.component.BandStatusBar
import app.cashflow.core.designsystem.component.CfBottomSheet
import app.cashflow.core.designsystem.component.CfButton
import app.cashflow.core.designsystem.component.CfButtonVariant
import app.cashflow.core.designsystem.component.CfCard
import app.cashflow.core.designsystem.component.CfChip
import app.cashflow.core.designsystem.component.CfTextField
import app.cashflow.core.designsystem.component.GroupHeading
import app.cashflow.core.designsystem.component.ListGroup
import app.cashflow.core.designsystem.component.ListRow
import app.cashflow.core.designsystem.component.LocalNavRailInset
import app.cashflow.core.designsystem.component.SheetHeader
import app.cashflow.core.designsystem.component.SkeletonRows
import app.cashflow.core.designsystem.component.rememberOnBand
import app.cashflow.core.designsystem.icon.CfIcons
import app.cashflow.core.designsystem.theme.Accent
import app.cashflow.core.designsystem.theme.CashFlowTheme
import app.cashflow.core.designsystem.theme.Spacing
import app.cashflow.core.ui.ObserveAsEvents
import app.cashflow.core.ui.UiText
import app.cashflow.feature.banksync.domain.BankAccountKind
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun BankSyncRoot(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    viewModel: BankSyncViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            BankSyncEvent.NavigateBack -> onBack()
            is BankSyncEvent.OpenBrowser -> runCatching { uriHandler.openUri(event.url) }
        }
    }
    BankSyncScreen(state, viewModel::onAction, contentPadding)
}

@Composable
fun BankSyncScreen(
    state: BankSyncState,
    onAction: (BankSyncAction) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val c = CashFlowTheme.colors
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(c.bgCanvas)
                .verticalScroll(scroll)
                .padding(start = LocalNavRailInset.current)
                .padding(contentPadding),
        ) {
            AccentHeader(
                accent = Accent.Settings,
                title = stringResource(R.string.banksync_title),
                leading = { BandIconButton(CfIcons.ChevronLeft, stringResource(R.string.banksync_cd_back), { onAction(BankSyncAction.OnBackClick) }) },
            ) {
                CfCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.banksync_intro), style = CashFlowTheme.typography.body, color = c.textPrimary)
                }
            }
            Column(Modifier.padding(horizontal = Spacing.gutter)) {
                if (state.isLoading) {
                    SkeletonRows(3, Modifier.padding(top = Spacing.s6))
                } else {
                    KeySection(state, onAction)
                    if (state.hasKey) {
                        WaitingSection(state, onAction)
                        MappingSection(state, onAction)
                        BanksSection(state, onAction)
                    }
                }
                Feedback(state.result, state.error)
                Text(
                    stringResource(R.string.banksync_footer),
                    style = CashFlowTheme.typography.label,
                    color = c.textSecondary,
                    modifier = Modifier.padding(top = Spacing.s4, bottom = Spacing.s6, start = Spacing.s1, end = Spacing.s1),
                )
            }
        }
        BandStatusBar(rememberOnBand(scroll))
    }
    state.banks?.let { banks -> BankPicker(banks, state.isLoadingBanks, onAction) }
}

// Temporary (step 9): the key import is a stand-in until a backend holds the key (docs/bank-sync.md).
@Composable
private fun KeySection(state: BankSyncState, onAction: (BankSyncAction) -> Unit) {
    val c = CashFlowTheme.colors
    GroupHeading(stringResource(R.string.banksync_group_key))
    if (state.hasKey) {
        ListGroup {
            ListRow(
                title = stringResource(R.string.banksync_key_saved),
                subtitle = state.keyHint?.let { stringResource(R.string.banksync_key_hint, it) },
            )
        }
        CfButton(
            text = stringResource(R.string.banksync_key_remove),
            onClick = { onAction(BankSyncAction.OnRemoveKeyClick) },
            variant = CfButtonVariant.Danger,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.s3),
        )
        return
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onAction(BankSyncAction.OnKeyFilePicked(uri.toString()))
    }
    CfCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
            Text(stringResource(R.string.banksync_key_how), style = CashFlowTheme.typography.label, color = c.textSecondary)
            CfTextField(
                label = stringResource(R.string.banksync_app_id),
                value = state.applicationId,
                onValueChange = { onAction(BankSyncAction.OnApplicationIdChange(it)) },
            )
            CfTextField(
                label = stringResource(R.string.banksync_redirect),
                value = state.redirectUrl,
                onValueChange = { onAction(BankSyncAction.OnRedirectChange(it)) },
            )
            CfButton(
                text = stringResource(if (state.keyPicked) R.string.banksync_key_picked else R.string.banksync_key_pick),
                onClick = { picker.launch(arrayOf("application/x-pem-file", "application/x-x509-ca-cert", "text/plain", "application/octet-stream")) },
                variant = CfButtonVariant.Outline,
                icon = if (state.keyPicked) CfIcons.Check else CfIcons.File,
                modifier = Modifier.fillMaxWidth(),
            )
            CfButton(
                text = stringResource(if (state.isSavingKey) R.string.banksync_key_checking else R.string.banksync_key_save),
                onClick = { onAction(BankSyncAction.OnSaveKeyClick) },
                enabled = state.canSaveKey,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun WaitingSection(state: BankSyncState, onAction: (BankSyncAction) -> Unit) {
    val bank = state.awaitingBank ?: return
    val c = CashFlowTheme.colors
    GroupHeading(stringResource(R.string.banksync_group_waiting))
    CfCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
            Text(stringResource(R.string.banksync_waiting, bank), style = CashFlowTheme.typography.body, color = c.textPrimary)
            Text(stringResource(R.string.banksync_paste_hint), style = CashFlowTheme.typography.label, color = c.textSecondary)
            CfTextField(
                label = stringResource(R.string.banksync_paste_label),
                value = state.pastedAddress,
                onValueChange = { onAction(BankSyncAction.OnPastedAddressChange(it)) },
                placeholder = "https://…?code=…&state=…",
            )
            CfButton(
                text = stringResource(R.string.banksync_paste_submit),
                onClick = { onAction(BankSyncAction.OnPastedAddressSubmit) },
                enabled = state.pastedAddress.isNotBlank() && !state.isWorking,
                modifier = Modifier.fillMaxWidth(),
            )
            CfButton(
                text = stringResource(R.string.banksync_waiting_cancel),
                onClick = { onAction(BankSyncAction.OnCancelWaitingClick) },
                variant = CfButtonVariant.Outline,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun MappingSection(state: BankSyncState, onAction: (BankSyncAction) -> Unit) {
    val rows = state.mapping ?: return
    val c = CashFlowTheme.colors
    GroupHeading(stringResource(R.string.banksync_group_mapping))
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
        rows.forEach { row ->
            CfCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                    Text(row.title, style = CashFlowTheme.typography.bodyStrong, color = c.textPrimary)
                    Text(stringResource(row.kind.label()), style = CashFlowTheme.typography.label, color = c.textSecondary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                        state.accountOptions.forEach { option ->
                            CfChip(
                                label = option.label.asString(),
                                selected = row.selected == option.key,
                                onClick = { onAction(BankSyncAction.OnMappingSelect(row.stableKey, option.key)) },
                            )
                        }
                    }
                }
            }
        }
        CfButton(
            text = stringResource(R.string.banksync_map_confirm),
            onClick = { onAction(BankSyncAction.OnMappingConfirm) },
            enabled = !state.isWorking,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BanksSection(state: BankSyncState, onAction: (BankSyncAction) -> Unit) {
    val c = CashFlowTheme.colors
    GroupHeading(stringResource(R.string.banksync_group_banks))
    state.consents.forEach { consent ->
        ListGroup(Modifier.padding(bottom = Spacing.s3)) {
            ListRow(
                title = consent.institution,
                subtitle = consent.validity.asString(),
                valueColor = if (consent.expired) c.textDanger else c.textPrimary,
            )
            consent.accounts.forEach { account ->
                ListRow(
                    title = account.title,
                    subtitle = listOfNotNull(
                        stringResource(R.string.banksync_link_into, account.into),
                        account.synced?.let { stringResource(R.string.banksync_link_synced, it) },
                    ).joinToString(" · "),
                    showDivider = true,
                )
            }
        }
        CfButton(
            text = stringResource(R.string.banksync_disconnect, consent.institution),
            onClick = { onAction(BankSyncAction.OnDisconnectClick(consent.sessionId)) },
            variant = CfButtonVariant.Outline,
            enabled = !state.isWorking,
            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.s4),
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
        if (state.consents.isNotEmpty()) {
            CfButton(
                text = stringResource(if (state.isWorking) R.string.banksync_syncing else R.string.banksync_sync),
                onClick = { onAction(BankSyncAction.OnSyncClick) },
                enabled = state.canSync,
                icon = CfIcons.Sync,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        CfButton(
            text = stringResource(R.string.banksync_connect),
            onClick = { onAction(BankSyncAction.OnConnectClick) },
            variant = if (state.consents.isEmpty()) CfButtonVariant.Primary else CfButtonVariant.Outline,
            enabled = !state.isWorking && state.awaitingBank == null && state.mapping == null,
            icon = CfIcons.Bank,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Feedback(result: UiText?, error: UiText?) {
    val c = CashFlowTheme.colors
    result?.let { Text(it.asString(), style = CashFlowTheme.typography.body, color = c.textPrimary, modifier = Modifier.padding(top = Spacing.s4)) }
    error?.let { Text(it.asString(), style = CashFlowTheme.typography.body, color = c.textDanger, modifier = Modifier.padding(top = Spacing.s4)) }
}

@Composable
private fun BankPicker(banks: List<BankUi>, loading: Boolean, onAction: (BankSyncAction) -> Unit) {
    CfBottomSheet(onDismissRequest = { onAction(BankSyncAction.OnBanksDismiss) }) {
        SheetHeader(stringResource(R.string.banksync_pick_bank), Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.s3))
        if (loading) {
            SkeletonRows(5, Modifier.padding(horizontal = Spacing.gutter))
        } else {
            LazyColumn(Modifier.heightIn(max = 480.dp).padding(horizontal = Spacing.gutter)) {
                itemsIndexed(banks, key = { _, b -> b.name }) { i, bank ->
                    ListRow(title = bank.name, showDivider = i > 0, onClick = { onAction(BankSyncAction.OnBankClick(bank.name)) })
                }
            }
        }
    }
}

private fun BankAccountKind.label() = when (this) {
    BankAccountKind.CURRENT -> R.string.banksync_kind_current
    BankAccountKind.SAVINGS -> R.string.banksync_kind_savings
    BankAccountKind.OTHER -> R.string.banksync_kind_other
}

@Preview(showBackground = true)
@Composable
private fun BankSyncKeyPreview() {
    CashFlowTheme {
        BankSyncScreen(BankSyncState(isLoading = false, applicationId = "aa11bb22-0000", keyPicked = true), {})
    }
}

@Preview(showBackground = true)
@Composable
private fun BankSyncLinkedPreview() {
    CashFlowTheme {
        BankSyncScreen(
            BankSyncState(
                isLoading = false,
                hasKey = true,
                keyHint = "0000",
                consents = listOf(
                    ConsentUi(
                        "s1", "Exempelbanken", UiText.DynamicString("Gäller till 2027-04-19"), false,
                        listOf(LinkedAccountUi("Lönekonto ··1234", "Lönekonto", "2026-10-21")),
                    ),
                ),
                result = UiText.DynamicString("12 nya · 48 fanns redan från fil · 0 att granska"),
            ),
            {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BankSyncMappingPreview() {
    CashFlowTheme {
        BankSyncScreen(
            BankSyncState(
                isLoading = false,
                hasKey = true,
                mapping = listOf(
                    MappingRowUi("h1", "Lönekonto ··1234", BankAccountKind.CURRENT, "a1"),
                    MappingRowUi("h2", "Sparkonto ··9876", BankAccountKind.SAVINGS, MappingRowUi.NEW),
                ),
                accountOptions = listOf(
                    AccountOptionUi("a1", UiText.DynamicString("Lönekonto")),
                    AccountOptionUi(MappingRowUi.NEW, UiText.DynamicString("Nytt konto")),
                    AccountOptionUi(MappingRowUi.SKIP, UiText.DynamicString("Hoppa över")),
                ),
            ),
            {},
        )
    }
}
