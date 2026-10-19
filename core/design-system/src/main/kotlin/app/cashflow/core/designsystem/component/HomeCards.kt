package app.cashflow.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.remember
import app.cashflow.core.designsystem.icon.CfIcons
import app.cashflow.core.designsystem.theme.CashFlowTheme
import app.cashflow.core.designsystem.theme.Radius
import app.cashflow.core.designsystem.theme.Spacing

/** Direction of a change and whether it is good, so colour says good/bad and the arrow says up/down. */
enum class TrendDirection { Up, Down, Flat }

/**
 * Hem's key figure (cf-kpi): label with an info mark, a big value with a trend arrow, and an
 * optional goal bar with "Mål nått". The whole card is one button.
 */
@Composable
fun KpiTile(
    label: String,
    value: String,
    accessibilityText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trend: TrendDirection? = null,
    trendGood: Boolean? = null,
    goal: Float? = null,
    goalColor: Color = CashFlowTheme.colors.accentHomeInk,
    goalReachedLabel: String? = null,
    sub: String? = null,
) {
    val c = CashFlowTheme.colors
    Column(
        modifier
            .heightIn(min = 146.dp)
            .clip(Radius.sm)
            .background(c.surfaceCard)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = accessibilityText }
            .padding(horizontal = Spacing.s4, vertical = Spacing.s3),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = CashFlowTheme.typography.label, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(CfIcons.Info, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.Bottom) {
            FitText(value, CashFlowTheme.typography.headline.copy(fontSize = 26.sp, fontFeatureSettings = "tnum"), c.textPrimary, Modifier.weight(1f))
            if (trend != null) {
                val color = when {
                    trend == TrendDirection.Flat || trendGood == null -> c.textSecondary
                    trendGood -> c.textPositive
                    else -> c.accentGoalsInk
                }
                val angle by animateFloatAsState(
                    when (trend) {
                        TrendDirection.Up -> -45f
                        TrendDirection.Down -> 45f
                        TrendDirection.Flat -> 0f
                    },
                    tween(300),
                    label = "trend",
                )
                Icon(CfIcons.ArrowRight, contentDescription = null, tint = color, modifier = Modifier.size(24.dp).rotate(angle))
            }
        }
        if (goal != null) {
            GoalBar(goal, goalColor, Modifier.padding(top = Spacing.s2))
            if (goalReachedLabel != null) {
                Row(
                    Modifier.clip(Radius.pill).background(c.surfaceStatus).padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(CfIcons.Check, contentDescription = null, tint = c.textOnStatus, modifier = Modifier.size(12.dp))
                    Text(goalReachedLabel, style = CashFlowTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold), color = c.textOnStatus, maxLines = 1)
                }
            } else {
                // Keeps the tiles in a row the same height whether or not a goal is reached.
                Spacer(Modifier.heightIn(min = 22.dp))
            }
        } else {
            // A figure without a goal can carry one line of context instead of the bar.
            Box(Modifier.heightIn(min = 36.dp), contentAlignment = Alignment.BottomStart) {
                if (sub != null) Text(sub, style = CashFlowTheme.typography.caption, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A small line of the last months that draws in from the left, ending in a dot. */
@Composable
fun Sparkline(values: List<Float>, color: Color, modifier: Modifier = Modifier) {
    val inspecting = LocalInspectionMode.current
    val draw = remember { Animatable(if (inspecting) 1f else 0f) }
    LaunchedEffect(Unit) { draw.animateTo(1f, tween(700)) }
    Canvas(modifier.size(96.dp, 32.dp)) {
        if (values.size < 2) return@Canvas
        val min = values.min()
        val max = values.max()
        val range = (max - min).takeIf { it > 0f } ?: 1f
        val pad = 4.dp.toPx()
        fun x(i: Int) = i * (size.width - pad) / (values.size - 1)
        fun y(v: Float) = size.height - pad - (v - min) / range * (size.height - 2 * pad)
        val path = Path().apply { values.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) } }
        val measure = PathMeasure().apply { setPath(path, false) }
        val part = Path()
        measure.getSegment(0f, measure.length * draw.value, part, true)
        drawPath(part, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        if (draw.value >= 1f) drawCircle(color, 3.dp.toPx(), Offset(x(values.lastIndex), y(values.last())))
    }
}

/**
 * A Trender row (cf-trow): name and latest value, a sparkline, and the change against the
 * average. At 1.5× text the sparkline steps aside so the words keep their room.
 */
@Composable
fun TrendRow(
    label: String,
    value: String,
    points: List<Float>,
    color: Color,
    change: String,
    changeGood: Boolean?,
    changeCaption: String,
    accessibilityText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = CashFlowTheme.colors
    val showSpark = LocalDensity.current.fontScale < 1.5f
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = accessibilityText }
            .padding(horizontal = Spacing.s4, vertical = Spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = CashFlowTheme.typography.bodyStrong, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = CashFlowTheme.typography.label.copy(fontFeatureSettings = "tnum"), color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (showSpark) Sparkline(points, color)
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(IntrinsicMinEnd)) {
            Text(
                change,
                style = CashFlowTheme.typography.label.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                color = when (changeGood) {
                    true -> c.textPositive
                    false -> c.accentGoalsInk
                    null -> c.textSecondary
                },
                maxLines = 1,
            )
            Text(changeCaption, style = CashFlowTheme.typography.label, color = c.textSecondary, maxLines = 1)
        }
    }
}

private val IntrinsicMinEnd = 84.dp

/** An Att göra card (cf-flag): round icon, eyebrow, title, text and an underlined action. */
@Composable
fun TodoCard(
    icon: ImageVector,
    iconBackground: Color,
    iconTint: Color,
    eyebrow: String,
    title: String,
    text: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = CashFlowTheme.colors
    Row(
        modifier.fillMaxWidth().clip(Radius.sm).background(c.surfaceCard).padding(Spacing.s4),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Box(Modifier.size(40.dp).clip(Radius.pill).background(iconBackground), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(eyebrow, style = CashFlowTheme.typography.caption, color = c.textSecondary)
            Text(title, style = CashFlowTheme.typography.bodyStrong, color = c.textPrimary)
            Text(text, style = CashFlowTheme.typography.body, color = c.textSecondary)
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .clip(Radius.xs)
                    .clickable(role = Role.Button, onClick = onAction),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    action,
                    style = CashFlowTheme.typography.bodyStrong.copy(textDecoration = TextDecoration.Underline),
                    color = c.textLink,
                )
            }
        }
    }
}
