package app.cashflow.feature.profile.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cashflow.core.designsystem.component.AccentHeader
import app.cashflow.core.designsystem.component.AccentTopBar
import app.cashflow.core.designsystem.component.BandIconButton
import app.cashflow.core.designsystem.component.CfCard
import app.cashflow.core.designsystem.component.GroupHeading
import app.cashflow.core.designsystem.component.ListGroup
import app.cashflow.core.designsystem.component.ListRow
import app.cashflow.core.designsystem.icon.CfIcons
import app.cashflow.core.designsystem.theme.Accent
import app.cashflow.core.designsystem.theme.CashFlowTheme
import app.cashflow.core.designsystem.theme.Spacing
import app.cashflow.core.domain.model.AccountType
import app.cashflow.core.ui.ObserveAsEvents
import app.cashflow.core.ui.label
import app.cashflow.feature.profile.ui.R
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AccountsRoot(
    contentPadding: PaddingValues,
    onOpenAccount: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: AccountsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is AccountsEvent.OpenAccount -> onOpenAccount(event.id)
            AccountsEvent.NavigateBack -> onBack()
        }
    }
    AccountsScreen(state, viewModel::onAction, contentPadding)
}

@Composable
fun AccountsScreen(
    state: AccountsState,
    onAction: (AccountsAction) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val c = CashFlowTheme.colors
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(c.bgCanvas)
                .verticalScroll(rememberScrollState())
                .padding(contentPadding),
        ) {
            AccentHeader(accent = Accent.Settings, pinnedBar = true) {
                CfCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
                        Text(stringResource(R.string.accounts_savings_title).uppercase(), style = CashFlowTheme.typography.overline, color = c.textSecondary)
                        Text(state.savingsValue, style = CashFlowTheme.typography.headline.copy(fontFeatureSettings = "tnum"), color = c.textPrimary)
                        Text(stringResource(R.string.accounts_savings_body), style = CashFlowTheme.typography.label, color = c.textSecondary)
                        state.savingsNote?.let { Text(it.asString(), style = CashFlowTheme.typography.label, color = c.textPrimary) }
                    }
                }
            }
            Column(Modifier.padding(horizontal = Spacing.gutter)) {
                Group(stringResource(R.string.accounts_group_bank), state.bankAccounts, onAction)
                Group(stringResource(R.string.accounts_group_savings), state.savingsAccounts, onAction)
                Group(stringResource(R.string.accounts_group_pension), state.pensionAccounts, onAction)
                Text(
                    stringResource(R.string.accounts_footer),
                    style = CashFlowTheme.typography.label,
                    color = c.textSecondary,
                    modifier = Modifier.padding(top = Spacing.s4, bottom = Spacing.s6, start = Spacing.s1, end = Spacing.s1),
                )
            }
        }
        // The band's controls stay at the top while the content scrolls under them.
        AccentTopBar(accent = Accent.Settings, title = stringResource(R.string.accounts_title), actions = { BandIconButton(CfIcons.ChevronLeft, stringResource(R.string.cd_back), { onAction(AccountsAction.OnBackClick) }) })
    }
}

@Composable
private fun Group(title: String, rows: List<AccountRowUi>, onAction: (AccountsAction) -> Unit) {
    if (rows.isEmpty()) return
    GroupHeading(title)
    ListGroup {
        rows.forEachIndexed { i, row ->
            val parts = listOfNotNull(row.type.label().asString(), row.institution)
            ListRow(
                title = row.name,
                subtitle = stringResource(R.string.accounts_row_sub, parts.joinToString(" · "), row.rowCount),
                value = row.value,
                showDivider = i > 0,
                onClick = { onAction(AccountsAction.OnAccountClick(row.id)) },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AccountsScreenPreview() {
    CashFlowTheme {
        AccountsScreen(
            AccountsState(
                savingsValue = "214 500 kr",
                bankAccounts = listOf(AccountRowUi("1", "Lönekonto", AccountType.TRANSACTION, null, 820, null)),
                savingsAccounts = listOf(
                    AccountRowUi("2", "Sparkonto Exempel", AccountType.SAVINGS, "Avanza", 130, "86 200 kr"),
                    AccountRowUi("3", "ISK Exempel", AccountType.ISK, "Avanza", 90, "128 300 kr"),
                ),
                isLoading = false,
            ),
            onAction = {},
        )
    }
}
