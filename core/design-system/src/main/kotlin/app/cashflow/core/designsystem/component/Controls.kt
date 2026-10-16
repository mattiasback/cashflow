package app.cashflow.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.cashflow.core.designsystem.theme.CashFlowTheme
import app.cashflow.core.designsystem.theme.Radius
import app.cashflow.core.designsystem.theme.Sizes
import app.cashflow.core.designsystem.theme.Spacing

/** One option of a [SegmentedControl]. */
data class Segment(val key: String, val label: String)

/** Pill segmented control (cf-seg): a control track with a raised thumb on the selected option. */
@Composable
fun SegmentedControl(
    segments: List<Segment>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = CashFlowTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.controlHeight)
            .clip(Radius.pill)
            .background(c.surfaceControl)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        segments.forEach { s ->
            val selected = s.key == selectedKey
            val bg by animateColorAsState(if (selected) c.surfaceControlThumb else Color.Transparent, tween(200), label = "seg")
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Sizes.controlHeight - 8.dp)
                    .clip(Radius.pill)
                    .background(bg)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(s.key) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    s.label,
                    style = CashFlowTheme.typography.bodyStrong,
                    color = if (selected) c.textOnThumb else c.textSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = Spacing.s2),
                )
            }
        }
    }
}

/** Labelled text field (cf-field): small label above a bordered input. */
@Composable
fun CfTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    numeric: Boolean = false,
    suffix: String? = null,
) {
    val c = CashFlowTheme.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
        Text(label, style = CashFlowTheme.typography.label, color = c.textSecondary)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Sizes.buttonHeight)
                .clip(Radius.xs)
                .background(c.surfaceCard)
                .border(1.dp, c.borderSubtle, Radius.xs)
                .padding(horizontal = Spacing.s3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).padding(vertical = Spacing.s3)) {
                if (value.isEmpty() && placeholder != null) {
                    Text(placeholder, style = CashFlowTheme.typography.body, color = c.textSecondary, maxLines = 1)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = CashFlowTheme.typography.body.copy(color = c.textPrimary),
                    cursorBrush = SolidColor(c.brand),
                    keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
                )
            }
            if (suffix != null) Text(suffix, style = CashFlowTheme.typography.body, color = c.textSecondary)
        }
    }
}

/** Overline group heading above a list group (cf-prof-h). */
@Composable
fun GroupHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = CashFlowTheme.typography.overline,
        color = CashFlowTheme.colors.textSecondary,
        modifier = modifier.padding(start = Spacing.s1, top = Spacing.s6, bottom = Spacing.s2),
    )
}
