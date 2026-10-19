package app.cashflow.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import app.cashflow.core.designsystem.icon.CfIcons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cashflow.core.designsystem.theme.CashFlowTheme
import app.cashflow.core.designsystem.theme.Radius
import app.cashflow.core.designsystem.theme.Spacing

/**
 * Hero card on the accent band: label above the figure ("Nettokassaflöde · okt 2026"),
 * the figure in headline-l with tabular digits, and an optional signed delta.
 */
@Composable
fun SummaryCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    delta: String? = null,
    deltaPositive: Boolean = true,
    accessibilityText: String? = null,
    onInfo: (() -> Unit)? = null,
    infoLabel: String? = null,
    rollDigits: Boolean = false,
) {
    val c = CashFlowTheme.colors
    CfCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
            Column(
                Modifier.then(if (accessibilityText != null) Modifier.clearAndSetSemantics { contentDescription = accessibilityText } else Modifier),
                verticalArrangement = Arrangement.spacedBy(Spacing.s1),
            ) {
                Text(label, style = CashFlowTheme.typography.label, color = c.textSecondary)
                // The figure rolls like an odometer when it changes; at large font scales it shrinks to fit instead.
                if (rollDigits && LocalDensity.current.fontScale < 1.3f) {
                    OdometerText(value, CashFlowTheme.typography.headlineL.copy(fontFeatureSettings = "tnum"), c.textPrimary)
                } else {
                    // Hero figures shrink to fit one line rather than truncate (design: minimumScaleFactor 0.7).
                    FitText(value, CashFlowTheme.typography.headlineL, c.textPrimary)
                }
            }
            if (delta != null || onInfo != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                    if (delta != null) {
                        Text(
                            delta,
                            style = CashFlowTheme.typography.bodyStrong.copy(fontFeatureSettings = "tnum"),
                            color = if (deltaPositive) c.textPositive else c.textPrimary,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    if (onInfo != null) {
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(Radius.pill)
                                .clickable(role = Role.Button, onClickLabel = infoLabel, onClick = onInfo)
                                .semantics { contentDescription = infoLabel.orEmpty() },
                            contentAlignment = Alignment.Center,
                        ) { Icon(CfIcons.Info, contentDescription = null, tint = c.textPrimary, modifier = Modifier.size(20.dp)) }
                    }
                }
            }
        }
    }
}

/** Metric tile in the 2×2 grid: label, big figure, one supporting line. */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    valueColor: Color = CashFlowTheme.colors.textPrimary,
    accentDot: Color? = null,
    accessibilityText: String? = null,
    onClick: (() -> Unit)? = null,
    goal: Float? = null,
    goalColor: Color = CashFlowTheme.colors.textPrimary,
) {
    val c = CashFlowTheme.colors
    Column(
        modifier = modifier
            .heightIn(min = 118.dp)
            .clip(Radius.sm)
            .background(c.surfaceCard)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .then(if (accessibilityText != null) Modifier.clearAndSetSemantics { contentDescription = accessibilityText } else Modifier)
            .padding(horizontal = Spacing.s4, vertical = Spacing.s3),
        verticalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (accentDot != null) {
                Box(Modifier.width(10.dp).height(10.dp).clip(Radius.xs).background(accentDot))
                Spacer(Modifier.width(Spacing.s2))
            }
            Text(label, style = CashFlowTheme.typography.label, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        FitText(
            value,
            CashFlowTheme.typography.headline.copy(fontSize = CashFlowTheme.typography.titleL.fontSize * 1.1f, fontFeatureSettings = "tnum"),
            valueColor,
            Modifier.padding(top = 6.dp),
        )
        Spacer(Modifier.weight(1f))
        // The supporting line is always laid out (empty when absent) so tiles in a grid share one height.
        Text(sub.orEmpty(), style = CashFlowTheme.typography.label, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = Spacing.s2))
        if (goal != null) GoalBar(goal, goalColor, Modifier.padding(top = 6.dp))
    }
}

private val GrowEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * A category bar: name, amount and share, with a track whose fill grows from zero the
 * first time it is shown. The fill is drawn in the draw phase, so the animation never
 * recomposes the row.
 */
@Composable
fun CategoryBar(
    name: String,
    amount: String,
    share: String,
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    accessibilityText: String = "$name, $amount, $share",
    animate: Boolean = true,
    onClick: (() -> Unit)? = null,
    trend: String? = null,
    trendBetter: Boolean = true,
) {
    val c = CashFlowTheme.colors
    val inspecting = LocalInspectionMode.current
    val progress = remember { Animatable(if (animate && !inspecting) 0f else fraction) }
    LaunchedEffect(fraction) { progress.animateTo(fraction.coerceIn(0f, 1f), tween(900, easing = GrowEasing)) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(Radius.sm)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = name, onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = accessibilityText }
            .heightIn(min = 48.dp)
            .padding(Spacing.s2),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(name, style = CashFlowTheme.typography.bodyStrong, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (trend != null) {
                    Text(
                        trend,
                        style = CashFlowTheme.typography.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                        color = if (trendBetter) c.textOnTintBrand else c.textPrimary,
                        maxLines = 1,
                        modifier = Modifier.clip(Radius.pill).background(if (trendBetter) c.surfaceTintBrand else c.surfaceControl).padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(amount, style = CashFlowTheme.typography.body.copy(fontFeatureSettings = "tnum"), color = c.textPrimary, maxLines = 1, softWrap = false)
            Spacer(Modifier.width(10.dp))
            Text(share, style = CashFlowTheme.typography.label.copy(fontFeatureSettings = "tnum"), color = c.textSecondary, maxLines = 1, softWrap = false, modifier = Modifier.widthIn(min = 48.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(Radius.pill)
                .background(c.surfaceControl)
                .drawBehind {
                    val w = size.width * progress.value
                    if (w > 0f) drawRoundRect(color, size = Size(w, size.height), cornerRadius = CornerRadius(size.height / 2))
                },
        )
    }
}

/** One line of text that steps its size down to fit instead of being cut off. */
@Composable
fun FitText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    BasicText(
        text = text,
        style = style.copy(color = color),
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = style.fontSize * 0.5f, maxFontSize = style.fontSize),
        modifier = modifier,
    )
}

