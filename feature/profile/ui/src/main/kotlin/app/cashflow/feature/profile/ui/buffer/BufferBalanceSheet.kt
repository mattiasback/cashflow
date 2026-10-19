package app.cashflow.feature.profile.ui.buffer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cashflow.core.designsystem.component.CfButton
import app.cashflow.core.designsystem.component.CfButtonVariant
import app.cashflow.core.designsystem.component.CfTextField
import app.cashflow.core.designsystem.component.SheetHeader
import app.cashflow.core.designsystem.theme.CashFlowTheme
import app.cashflow.core.designsystem.theme.Spacing
import app.cashflow.core.ui.ObserveAsEvents
import app.cashflow.core.ui.UiText
import app.cashflow.feature.profile.ui.R
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun BufferBalanceSheetRoot(
    onDismiss: () -> Unit,
    viewModel: BufferBalanceViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            BufferBalanceEvent.Dismiss -> onDismiss()
        }
    }
    BufferBalanceSheet(state, viewModel::onAction)
}

@Composable
fun BufferBalanceSheet(state: BufferBalanceState, onAction: (BufferBalanceAction) -> Unit) {
    val c = CashFlowTheme.colors
    Column(
        Modifier.fillMaxWidth().imePadding().padding(start = Spacing.s4, end = Spacing.s4, bottom = Spacing.s6),
        verticalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) {
        SheetHeader(title = stringResource(R.string.buffer_balance_title), eyebrow = stringResource(R.string.profile_group_goals))
        Text(stringResource(R.string.buffer_balance_body), style = CashFlowTheme.typography.body, color = c.textSecondary)
        CfTextField(
            label = stringResource(R.string.buffer_balance_label),
            value = state.amount,
            onValueChange = { onAction(BufferBalanceAction.OnAmountChange(it)) },
            placeholder = "50 000",
            numeric = true,
            suffix = "kr",
        )
        state.stored?.let { Text(it.asString(), style = CashFlowTheme.typography.label, color = c.textSecondary) }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s3)) {
            if (state.canClear) {
                CfButton(stringResource(R.string.buffer_balance_clear), { onAction(BufferBalanceAction.OnClearClick) }, Modifier.weight(1f), variant = CfButtonVariant.Outline)
            }
            CfButton(stringResource(R.string.buffer_balance_save), { onAction(BufferBalanceAction.OnSaveClick) }, Modifier.weight(1f), enabled = state.canSave)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BufferBalanceSheetPreview() {
    CashFlowTheme {
        BufferBalanceSheet(
            BufferBalanceState(amount = "50000", stored = UiText.DynamicString("Angivet 2026-10-01 · i dag 54 000 kr"), canSave = true, canClear = true),
            onAction = {},
        )
    }
}
