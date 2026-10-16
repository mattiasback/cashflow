package app.cashflow.feature.profile.ui.mortgage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fitInside
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.WindowInsetsRulers
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cashflow.core.designsystem.component.CfButton
import app.cashflow.core.designsystem.component.CfSwitchRow
import app.cashflow.core.designsystem.component.CfTextField
import app.cashflow.core.designsystem.component.Segment
import app.cashflow.core.designsystem.component.SegmentedControl
import app.cashflow.core.designsystem.component.SheetHeader
import app.cashflow.core.designsystem.theme.CashFlowTheme
import app.cashflow.core.designsystem.theme.Radius
import app.cashflow.core.designsystem.theme.Spacing
import app.cashflow.core.domain.model.MortgageSplitMode
import app.cashflow.core.ui.ObserveAsEvents
import app.cashflow.core.ui.UiText
import app.cashflow.feature.profile.ui.R
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MortgageSheetRoot(
    onDismiss: () -> Unit,
    viewModel: MortgageViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            MortgageEvent.Dismiss -> onDismiss()
        }
    }
    MortgageSheet(state, viewModel::onAction)
}

@Composable
fun MortgageSheet(
    state: MortgageState,
    onAction: (MortgageAction) -> Unit,
) {
    val c = CashFlowTheme.colors
    LazyColumn(
        modifier = Modifier.fillMaxWidth().fitInside(WindowInsetsRulers.Ime.current),
        contentPadding = PaddingValues(start = Spacing.s4, end = Spacing.s4, bottom = Spacing.s6),
        verticalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) {
        item { SheetHeader(title = stringResource(R.string.mortgage_title), eyebrow = stringResource(R.string.mortgage_eyebrow)) }
        item {
            CfSwitchRow(stringResource(R.string.profile_mortgage_switch), state.enabled, { onAction(MortgageAction.OnEnabledToggle(it)) })
        }
        item { Text(stringResource(R.string.mortgage_body), style = CashFlowTheme.typography.body, color = c.textSecondary) }
        item {
            SegmentedControl(
                segments = listOf(
                    Segment(MortgageSplitMode.AMORTIZATION_AMOUNT.name, stringResource(R.string.mortgage_mode_amount)),
                    Segment(MortgageSplitMode.INTEREST_SHARE.name, stringResource(R.string.mortgage_mode_share)),
                ),
                selectedKey = state.mode.name,
                onSelect = { onAction(MortgageAction.OnModeSelect(MortgageSplitMode.valueOf(it))) },
            )
        }
        item {
            if (state.mode == MortgageSplitMode.AMORTIZATION_AMOUNT) {
                CfTextField(
                    label = stringResource(R.string.mortgage_amount),
                    value = state.amount,
                    onValueChange = { onAction(MortgageAction.OnAmountChange(it)) },
                    placeholder = "2 500",
                    numeric = true,
                    suffix = "kr",
                )
            } else {
                CfTextField(
                    label = stringResource(R.string.mortgage_share),
                    value = state.share,
                    onValueChange = { onAction(MortgageAction.OnShareChange(it)) },
                    placeholder = "40",
                    numeric = true,
                    suffix = "%",
                )
            }
        }
        item {
            CfTextField(
                label = stringResource(R.string.mortgage_pattern),
                value = state.pattern,
                onValueChange = { onAction(MortgageAction.OnPatternChange(it)) },
            )
        }
        state.preview?.let { p ->
            item {
                Text(
                    p.asString(),
                    style = CashFlowTheme.typography.body,
                    color = c.textPrimary,
                    modifier = Modifier.fillMaxWidth().clip(Radius.sm).background(if (c.isDark) c.surfaceCard else c.surfaceGroup).padding(Spacing.s4),
                )
            }
        }
        state.error?.let { e -> item { Text(e.asString(), style = CashFlowTheme.typography.label, color = c.dangerStrong) } }
        item {
            CfButton(stringResource(R.string.mortgage_save), { onAction(MortgageAction.OnSaveClick) }, Modifier.fillMaxWidth(), enabled = state.canSave)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MortgageSheetPreview() {
    CashFlowTheme {
        MortgageSheet(
            MortgageState(
                enabled = true, amount = "2500", canSave = true, isLoading = false,
                preview = UiText.DynamicString("6 betalningar matchar. Senaste 2026-09-28: 9 000 kr blir ränta 6 500 kr och amortering 2 500 kr."),
            ),
            onAction = {},
        )
    }
}
