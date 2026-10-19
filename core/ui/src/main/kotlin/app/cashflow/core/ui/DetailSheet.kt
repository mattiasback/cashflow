package app.cashflow.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cashflow.core.designsystem.component.CfButton
import app.cashflow.core.designsystem.component.CfButtonVariant
import app.cashflow.core.designsystem.component.GoalBar
import app.cashflow.core.designsystem.component.SheetHeader
import app.cashflow.core.designsystem.theme.CashFlowTheme
import app.cashflow.core.designsystem.theme.Radius
import app.cashflow.core.designsystem.theme.Spacing

@Immutable
data class SheetRowUi(val title: UiText, val sub: UiText?, val amount: String, val share: String?, val tone: FlowTone)

/**
 * A figure explained (FlowSheet board): eyebrow, title, figure, two key values, a share bar, the
 * rows behind it and one button. Used by Flöde's nodes and Hem's key figures.
 */
@Immutable
data class DetailSheetUi(
    val eyebrow: UiText,
    val title: UiText,
    val figure: String,
    val description: UiText?,
    val shareALabel: UiText,
    val shareA: String,
    val shareBLabel: UiText,
    val shareB: String,
    val bar: Float,
    val barTone: FlowTone,
    val rows: List<SheetRowUi>,
    val cta: UiText,
)

@Composable
fun DetailSheetContent(sheet: DetailSheetUi, onCta: () -> Unit) {
    val c = CashFlowTheme.colors
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.s4).padding(bottom = Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) {
        SheetHeader(eyebrow = sheet.eyebrow.asString(), title = sheet.title.asString(), figure = sheet.figure)
        sheet.description?.let { Text(it.asString(), style = CashFlowTheme.typography.body, color = c.textSecondary) }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s4)) {
            KeyValue(sheet.shareALabel.asString(), sheet.shareA, Modifier.weight(1f))
            KeyValue(sheet.shareBLabel.asString(), sheet.shareB, Modifier.weight(1f))
        }
        GoalBar(sheet.bar, sheet.barTone.colors().strong)
        if (sheet.rows.isNotEmpty()) {
            Column {
                sheet.rows.forEachIndexed { i, row ->
                    if (i > 0) Box(Modifier.fillMaxWidth().padding(start = 52.dp).height(1.dp).background(c.divider))
                    val title = row.title.asString()
                    val sub = row.sub?.asString()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .semantics(mergeDescendants = true) { contentDescription = listOfNotNull(title, sub, row.amount, row.share).joinToString(", ") }
                            .padding(vertical = Spacing.s2),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(40.dp).clip(Radius.pill).background(row.tone.colors().fill), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(10.dp).clip(Radius.xs).background(row.tone.colors().strong))
                        }
                        Spacer(Modifier.width(Spacing.s3))
                        Column(Modifier.weight(1f)) {
                            Text(title, style = CashFlowTheme.typography.bodyStrong, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (sub != null) Text(sub, style = CashFlowTheme.typography.body, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(row.amount, style = CashFlowTheme.typography.bodyStrong.copy(fontFeatureSettings = "tnum"), color = c.textPrimary, maxLines = 1)
                            if (row.share != null) Text(row.share, style = CashFlowTheme.typography.body.copy(fontFeatureSettings = "tnum"), color = c.textSecondary, maxLines = 1)
                        }
                    }
                }
            }
        }
        CfButton(sheet.cta.asString(), onCta, Modifier.fillMaxWidth(), variant = CfButtonVariant.Outline)
    }
}

@Composable
private fun KeyValue(label: String, value: String, modifier: Modifier) {
    val c = CashFlowTheme.colors
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Text(label, style = CashFlowTheme.typography.label, color = c.textSecondary)
        Text(value, style = CashFlowTheme.typography.titleL.copy(fontFeatureSettings = "tnum"), color = c.textPrimary, maxLines = 1)
    }
}
