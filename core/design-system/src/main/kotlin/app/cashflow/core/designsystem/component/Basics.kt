package app.cashflow.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cashflow.core.designsystem.theme.CashFlowTheme
import app.cashflow.core.designsystem.theme.Radius
import app.cashflow.core.designsystem.theme.Sizes
import app.cashflow.core.designsystem.theme.Spacing

/** Flat card on the canvas: separation comes from tone, never a shadow. */
@Composable
fun CfCard(
    modifier: Modifier = Modifier,
    color: Color = CashFlowTheme.colors.surfaceCard,
    contentPadding: PaddingValues = PaddingValues(Spacing.s4),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(Radius.sm)
            .background(color)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
    ) { content() }
}

/** Section title row ("Att granska", "Transaktioner") with an optional trailing slot. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = Spacing.s6, bottom = Spacing.s4)
            .heightIn(min = Sizes.buttonHeightCompact),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = CashFlowTheme.typography.titleL, color = CashFlowTheme.colors.textPrimary, modifier = Modifier.weight(1f))
        trailing()
    }
}

enum class CfButtonVariant { Primary, Outline, Danger, Accent }

/** Nearly square Lysa-style button (radius-xs), 48dp tall, label in body-strong. */
@Composable
fun CfButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: CfButtonVariant = CfButtonVariant.Primary,
    accent: Color? = null,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val c = CashFlowTheme.colors
    val (bg, fg, border) = when (variant) {
        CfButtonVariant.Primary -> Triple(c.brand, c.textOnAccent, null)
        // Accent fills are dark enough for a white label in both themes (e.g. actionEvents).
        CfButtonVariant.Accent -> Triple(accent ?: c.brand, Color.White, null)
        CfButtonVariant.Outline -> Triple(Color.Transparent, c.textPrimary, BorderStroke(1.5.dp, c.borderStrong))
        CfButtonVariant.Danger -> Triple(Color.Transparent, c.textDanger, BorderStroke(1.5.dp, c.textDanger))
    }
    Row(
        modifier = modifier
            .heightIn(min = Sizes.buttonHeight)
            // Disabled is the whole control at opacity-disabled, never a recoloured label.
            .alpha(if (enabled) 1f else 0.38f)
            .clip(Radius.xs)
            .background(bg)
            .then(if (border != null) Modifier.border(border, Radius.xs) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.s5),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Spacing.s2))
        }
        Text(text, style = CashFlowTheme.typography.bodyStrong, color = fg, maxLines = 2)
    }
}

/** Outline chip (cf-chip2); selected fills with text-primary. 200 ms colour change. */
@Composable
fun CfChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingColor: Color? = null,
    enabled: Boolean = true,
) {
    val c = CashFlowTheme.colors
    val bg by animateColorAsState(if (selected) c.textPrimary else Color.Transparent, tween(200), label = "chipBg")
    val fg by animateColorAsState(if (selected) c.bgCanvas else c.textPrimary, tween(200), label = "chipFg")
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = Sizes.controlHeight)
            .clip(Radius.pill)
            .background(bg)
            .border(1.5.dp, if (selected) c.textPrimary else c.borderChip, Radius.pill)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = Spacing.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingColor != null) {
            Box(Modifier.size(10.dp).clip(Radius.xs).background(leadingColor))
            Spacer(Modifier.width(Spacing.s2))
        }
        Text(label, style = CashFlowTheme.typography.bodyStrong, color = if (enabled) fg else fg.copy(alpha = 0.35f), maxLines = 1)
    }
}

enum class BadgeKind { Auto, Manual, Review, Transfer }

/** Small status pill on transaction rows: Automatisk / Manuell / Att granska / Överföring. */
@Composable
fun StatusBadge(text: String, kind: BadgeKind, modifier: Modifier = Modifier) {
    val c = CashFlowTheme.colors
    val (bg, fg) = when (kind) {
        BadgeKind.Auto -> c.accentEventsTint to c.textOnEventsTint
        BadgeKind.Manual -> c.surfaceControl to c.textPrimary
        BadgeKind.Review -> c.surfaceCelebrate to c.textOnCelebrate
        BadgeKind.Transfer -> c.surfaceTintBrand to c.textOnTintBrand
    }
    Text(
        text = text,
        style = CashFlowTheme.typography.caption,
        color = fg,
        maxLines = 1,
        modifier = modifier
            .clip(Radius.pill)
            .background(bg)
            .padding(horizontal = Spacing.s2, vertical = 2.dp),
    )
}

/** Brand switch, 52×32 track; the thumb grows from 20 to 24dp when on. */
@Composable
fun CfSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = CashFlowTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touchMin)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = CashFlowTheme.typography.body, color = c.textPrimary, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(Spacing.s3))
        val track by animateColorAsState(if (checked) c.brand else c.surfaceControl, tween(200), label = "track")
        Box(
            modifier = Modifier
                .size(width = 52.dp, height = 32.dp)
                .clip(Radius.pill)
                .background(track)
                .border(2.dp, if (checked) c.brand else c.borderChip, Radius.pill),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .padding(horizontal = if (checked) 2.dp else 4.dp)
                    .size(if (checked) 24.dp else 20.dp)
                    .clip(Radius.pill)
                    .background(if (checked) c.textOnAccent else c.borderChip),
            )
        }
    }
}

/** A list group card whose rows are separated by hairlines. */
@Composable
fun ListGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(Radius.sm)
            .background(CashFlowTheme.colors.surfaceCard),
    ) { content() }
}

/** Two-line row: optional leading slot, title/subtitle, trailing value and badge. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    value: String? = null,
    valueColor: Color = CashFlowTheme.colors.textPrimary,
    trailing: (@Composable () -> Unit)? = null,
    showDivider: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val c = CashFlowTheme.colors
    Column(modifier) {
        if (showDivider) Box(Modifier.fillMaxWidth().padding(start = Spacing.s4).heightIn(min = 1.dp).background(c.divider))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 70.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = Spacing.s4, vertical = Spacing.s3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(Spacing.s3))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = CashFlowTheme.typography.bodyStrong, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(subtitle, style = CashFlowTheme.typography.label, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (value != null || trailing != null) {
                Spacer(Modifier.width(Spacing.s3))
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (value != null) {
                        Text(value, style = CashFlowTheme.typography.bodyStrong.copy(fontFeatureSettings = "tnum"), color = valueColor, maxLines = 1, softWrap = false)
                    }
                    trailing?.invoke()
                }
            }
        }
    }
}

/** Round 32dp category dot: the category's fill colour with its strong colour square. */
@Composable
fun CategoryDot(color: Color, fill: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(Sizes.badgeSize).clip(Radius.pill).background(fill),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(10.dp).clip(Radius.xs).background(color))
    }
}
