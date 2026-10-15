package app.cashflow.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.cashflow.core.designsystem.icon.CfIcons
import app.cashflow.core.designsystem.theme.CashFlowTheme
import app.cashflow.core.designsystem.theme.Radius
import app.cashflow.core.designsystem.theme.Sizes
import app.cashflow.core.designsystem.theme.Spacing

/** Pill-shaped search field (cf-search). The text lives in the ViewModel. */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val c = CashFlowTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.buttonHeight)
            .clip(Radius.pill)
            .background(c.surfaceCard)
            .border(1.dp, c.borderSubtle, Radius.pill)
            .padding(horizontal = Spacing.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(CfIcons.Search, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Spacing.s2))
        Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
            if (value.isEmpty()) Text(placeholder, style = CashFlowTheme.typography.body, color = c.textSecondary, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = CashFlowTheme.typography.body.copy(color = c.textPrimary),
                cursorBrush = SolidColor(c.brand),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder },
            )
        }
    }
}

/** Filter button with a small label above the current value (cf-filterbtn). */
@Composable
fun FilterButton(
    label: String,
    value: String,
    active: Boolean,
    accessibilityLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = CashFlowTheme.colors
    Row(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(Radius.sm)
            .background(c.surfaceCard)
            .border(if (active) 2.5.dp else 1.5.dp, if (active) c.accentEventsInk else c.borderChip, Radius.sm)
            .clickable(role = Role.Button, onClickLabel = accessibilityLabel, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = accessibilityLabel }
            .padding(start = Spacing.s4, end = Spacing.s3, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = CashFlowTheme.typography.label, color = c.textSecondary)
            Text(value, style = CashFlowTheme.typography.bodyStrong, color = c.textPrimary, maxLines = 1)
        }
        Icon(CfIcons.ChevronDown, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
    }
}

/** Sheet heading: eyebrow, title, optional figure. Used inside bottom-sheet destinations. */
@Composable
fun SheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    figure: String? = null,
) {
    val c = CashFlowTheme.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (eyebrow != null) Text(eyebrow.uppercase(), style = CashFlowTheme.typography.overline, color = c.textSecondary)
        Text(title, style = CashFlowTheme.typography.title, color = c.textPrimary)
        if (figure != null) Text(figure, style = CashFlowTheme.typography.headline.copy(fontFeatureSettings = "tnum"), color = c.textPrimary)
    }
}

/** A check mark that draws itself (receipt signature animation). */
@Composable
fun AnimatedCheck(modifier: Modifier = Modifier, animate: Boolean = true) {
    val c = CashFlowTheme.colors
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(500, delayMillis = 150)) }
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(Radius.pill)
            .background(c.accentEventsTint)
            .drawBehind {
                val s = size.minDimension / 24f
                val path = Path().apply {
                    moveTo(8f * s + (size.width - 24f * s) / 2, 12.5f * s)
                    lineTo(11f * s + (size.width - 24f * s) / 2, 15.5f * s)
                    lineTo(16.5f * s + (size.width - 24f * s) / 2, 9f * s)
                }
                val measure = PathMeasure().apply { setPath(path, false) }
                val segment = Path()
                measure.getSegment(0f, measure.length * progress.value, segment, true)
                drawPath(segment, c.textOnEventsTint, style = Stroke(width = 2.2f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
            },
    )
}

/** Integer that rolls up from zero to [target] (receipt counters). */
@Composable
fun CountUpText(
    target: Int,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
) {
    val count = remember { Animatable(if (animate) 0f else target.toFloat()) }
    LaunchedEffect(target) { count.animateTo(target.toFloat(), tween(if (animate) 700 else 0)) }
    Text(
        text = count.value.toInt().toString(),
        style = CashFlowTheme.typography.titleL.copy(fontFeatureSettings = "tnum"),
        color = CashFlowTheme.colors.textPrimary,
        modifier = modifier.semantics { contentDescription = target.toString() },
    )
}
