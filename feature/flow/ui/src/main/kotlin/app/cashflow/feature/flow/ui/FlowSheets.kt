package app.cashflow.feature.flow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cashflow.core.designsystem.component.CfBottomSheet
import app.cashflow.core.designsystem.component.CfButton
import app.cashflow.core.designsystem.component.CfButtonVariant
import app.cashflow.core.designsystem.component.CfChip
import app.cashflow.core.designsystem.component.GoalBar
import app.cashflow.core.designsystem.component.Segment
import app.cashflow.core.designsystem.component.SegmentedControl
import app.cashflow.core.designsystem.component.SheetHeader
import app.cashflow.core.designsystem.theme.CashFlowTheme
import app.cashflow.core.designsystem.theme.Radius
import app.cashflow.core.designsystem.theme.Spacing
import app.cashflow.core.domain.cashflow.PeriodMode
import app.cashflow.core.ui.DetailSheetContent

/** The period picker and the node detail sheet; both live in screen state, not the back stack. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FlowSheets(state: FlowState, onAction: (FlowAction) -> Unit) {
    val c = CashFlowTheme.colors
    state.picker?.let { picker ->
        CfBottomSheet(onDismissRequest = { onAction(FlowAction.OnPickerClose) }) { PickerContent(state, picker, onAction) }
    }
    state.sheet?.let { sheet ->
        CfBottomSheet(onDismissRequest = { onAction(FlowAction.OnSheetDismiss) }) { DetailSheetContent(sheet.toDetail()) { onAction(FlowAction.OnSheetCta) } }
    }
}

@Composable
private fun PickerContent(state: FlowState, picker: PickerUi, onAction: (FlowAction) -> Unit) {
    val c = CashFlowTheme.colors
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.s4).padding(bottom = Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) {
        SheetHeader(
            eyebrow = stringResource(R.string.picker_eyebrow),
            title = stringResource(
                when (state.mode) {
                    PeriodMode.MONTH -> R.string.picker_title_month
                    PeriodMode.YEAR -> R.string.picker_title_year
                    PeriodMode.ALL -> R.string.picker_title_all
                },
            ),
        )
        SegmentedControl(
            segments = listOf(
                Segment(PeriodMode.MONTH.name, stringResource(R.string.mode_month)),
                Segment(PeriodMode.YEAR.name, stringResource(R.string.mode_year)),
                Segment(PeriodMode.ALL.name, stringResource(R.string.mode_all)),
            ),
            selectedKey = state.mode.name,
            onSelect = { onAction(FlowAction.OnModeSelect(PeriodMode.valueOf(it))) },
        )
        if (picker.years.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
            ) {
                picker.years.forEach { y ->
                    CfChip(y.label, y.selected, { onAction(FlowAction.OnPickerYear(y.key.toInt())) }, Modifier.weight(1f))
                }
            }
        }
        if (picker.months.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                picker.months.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                        row.forEach { m ->
                            CfChip(
                                m.label, m.selected, { onAction(FlowAction.OnPeriodSelect(m.key)) },
                                Modifier.weight(1f).semantics { contentDescription = m.spoken },
                                enabled = m.enabled,
                            )
                        }
                    }
                }
            }
        }
        picker.allText?.let { Text(it.asString(), style = CashFlowTheme.typography.body, color = c.textSecondary) }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s3)) {
            CfButton(stringResource(R.string.picker_latest), { onAction(FlowAction.OnPickerLatest) }, Modifier.weight(1f), variant = CfButtonVariant.Outline)
            CfButton(stringResource(R.string.picker_done), { onAction(FlowAction.OnPickerClose) }, Modifier.weight(1f))
        }
    }
}

