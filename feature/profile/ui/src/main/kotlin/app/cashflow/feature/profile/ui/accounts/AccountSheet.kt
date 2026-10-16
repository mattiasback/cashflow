package app.cashflow.feature.profile.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cashflow.core.designsystem.component.CfButton
import app.cashflow.core.designsystem.component.CfChip
import app.cashflow.core.designsystem.component.CfTextField
import app.cashflow.core.designsystem.component.SheetHeader
import app.cashflow.core.designsystem.theme.CashFlowTheme
import app.cashflow.core.designsystem.theme.Radius
import app.cashflow.core.designsystem.theme.Spacing
import app.cashflow.core.domain.model.AccountType
import app.cashflow.core.ui.ObserveAsEvents
import app.cashflow.core.ui.UiText
import app.cashflow.core.ui.label
import app.cashflow.feature.profile.ui.R
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun AccountSheetRoot(
    accountId: String,
    onDismiss: () -> Unit,
    viewModel: AccountSheetViewModel = koinViewModel(key = accountId) { parametersOf(accountId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            AccountSheetEvent.Dismiss -> onDismiss()
        }
    }
    AccountSheet(state, viewModel::onAction)
}

private val SavingsTypes = listOf(AccountType.SAVINGS, AccountType.ISK, AccountType.KF, AccountType.PENSION, AccountType.OTHER)

/** Content of the account sheet (the sheet itself is a Nav3 scene in :app). */
@Composable
fun AccountSheet(
    state: AccountSheetState,
    onAction: (AccountSheetAction) -> Unit,
) {
    val c = CashFlowTheme.colors
    LazyColumn(
        modifier = Modifier.fillMaxWidth().imePadding(),
        contentPadding = PaddingValues(start = Spacing.s4, end = Spacing.s4, bottom = Spacing.s6),
        verticalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) {
        item { SheetHeader(title = state.title, eyebrow = state.institution ?: stringResource(R.string.account_eyebrow)) }
        item {
            CfTextField(
                label = stringResource(R.string.account_name),
                value = state.name,
                onValueChange = { onAction(AccountSheetAction.OnNameChange(it)) },
            )
        }
        if (state.typeEditable) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                    Text(stringResource(R.string.account_type), style = CashFlowTheme.typography.label, color = c.textSecondary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                        SavingsTypes.forEach { type ->
                            CfChip(type.label().asString(), state.type == type, { onAction(AccountSheetAction.OnTypeSelect(type)) })
                        }
                    }
                }
            }
        }
        if (state.showsMarketValue) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
                    CfTextField(
                        label = stringResource(R.string.account_market_value),
                        value = state.marketValue,
                        onValueChange = { onAction(AccountSheetAction.OnMarketValueChange(it)) },
                        placeholder = stringResource(R.string.account_market_value_hint),
                        numeric = true,
                        suffix = "kr",
                    )
                    Text(
                        (state.marketValueNote ?: UiText.StringResource(R.string.account_market_value_why)).asString(),
                        style = CashFlowTheme.typography.label,
                        color = c.textSecondary,
                    )
                }
            }
        }
        if (state.figures.isNotEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().clip(Radius.sm).background(if (c.isDark) c.surfaceCard else c.surfaceGroup).padding(Spacing.s4),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s2),
                ) {
                    state.figures.forEach { f ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(f.label.asString(), style = CashFlowTheme.typography.body, color = c.textSecondary, modifier = Modifier.weight(1f))
                            Text(f.value, style = CashFlowTheme.typography.bodyStrong.copy(fontFeatureSettings = "tnum"), color = c.textPrimary)
                        }
                    }
                }
            }
        }
        state.error?.let { e -> item { Text(e.asString(), style = CashFlowTheme.typography.label, color = c.dangerStrong) } }
        item {
            CfButton(stringResource(R.string.account_save), { onAction(AccountSheetAction.OnSaveClick) }, Modifier.fillMaxWidth(), enabled = state.canSave)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AccountSheetPreview() {
    CashFlowTheme {
        AccountSheet(
            AccountSheetState(
                title = "ISK Exempel", institution = "Avanza", name = "ISK Exempel", type = AccountType.ISK,
                typeEditable = true, showsMarketValue = true, marketValue = "128300", canSave = true, isLoading = false,
                figures = listOf(AccountFigureUi(UiText.DynamicString("Avkastning 2026"), "1 240 kr")),
            ),
            onAction = {},
        )
    }
}
