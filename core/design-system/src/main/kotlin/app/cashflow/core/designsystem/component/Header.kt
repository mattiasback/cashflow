package app.cashflow.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.cashflow.core.designsystem.theme.Accent
import app.cashflow.core.designsystem.theme.CashFlowTheme
import app.cashflow.core.designsystem.theme.Radius
import app.cashflow.core.designsystem.theme.Sizes
import app.cashflow.core.designsystem.theme.Spacing
import app.cashflow.core.designsystem.theme.band

private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * The thematic band at the top of a tab. It draws behind the status bar, fades into the
 * canvas behind the hero card, and hosts round controls on an `overlay-on-accent` track.
 * Body text never sits on the band itself; the hero content is a card.
 */
@Composable
fun AccentHeader(
    accent: Accent,
    modifier: Modifier = Modifier,
    title: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    hero: @Composable ColumnScope.() -> Unit = {},
) {
    val c = CashFlowTheme.colors
    val bandColor by animateColorAsState(accent.band(), tween(300, easing = Emphasized), label = "band")
    Box(modifier.fillMaxWidth().background(c.bgCanvas)) {
        Column(Modifier.fillMaxWidth()) {
            Spacer(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(bandColor))
            Box(Modifier.fillMaxWidth().height(Sizes.controlHeight + Spacing.s4 * 2 + Spacing.s8).background(bandColor))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(Sizes.accentBandFade)
                    .background(Brush.verticalGradient(listOf(bandColor, c.bgCanvas))),
            )
        }
        Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Sizes.controlHeight + Spacing.s4 * 2)
                    .padding(horizontal = Spacing.s4),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
            ) {
                if (title != null) {
                    // Titles on the band are white and large enough for the 3:1 icon-only accents.
                    Text(title, style = CashFlowTheme.typography.title, color = c.textOnAccent, modifier = Modifier.weight(1f))
                } else {
                    Spacer(Modifier.weight(1f))
                }
                actions()
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.s4), content = hero)
        }
    }
}

/** Round 40dp control on the band (profile, filter). */
@Composable
fun BandIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = CashFlowTheme.colors
    Box(
        modifier = modifier
            .size(Sizes.touchMin)
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(Sizes.controlHeight).clip(Radius.pill).background(c.overlayOnAccent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = c.textOnAccent, modifier = Modifier.size(22.dp))
        }
    }
}

/** Period pill on the band: "‹  okt 2026  ›" with steppers either side. */
@Composable
fun BandPeriodStepper(
    label: String,
    previousLabel: String,
    nextLabel: String,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = CashFlowTheme.colors
    Row(
        modifier = modifier
            .height(Sizes.controlHeight)
            .clip(Radius.pill)
            .background(c.overlayOnAccent),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperArrow(app.cashflow.core.designsystem.icon.CfIcons.ChevronLeft, previousLabel, canGoBack, onPrevious, Color.White)
        Text(label, style = CashFlowTheme.typography.bodyStrong, color = c.textOnAccent, modifier = Modifier.padding(horizontal = Spacing.s1))
        StepperArrow(app.cashflow.core.designsystem.icon.CfIcons.ChevronRight, nextLabel, canGoForward, onNext, Color.White)
    }
}

@Composable
private fun StepperArrow(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit, tint: Color) {
    Box(
        Modifier
            .size(Sizes.controlHeight)
            .clip(Radius.pill)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) tint else tint.copy(alpha = 0.38f), modifier = Modifier.size(20.dp))
    }
}

/** Period dropdown on the band (cf-dropdown): "okt 2026 ⌄", opens the period sheet. */
@Composable
fun BandDropdown(label: String, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = CashFlowTheme.colors
    Row(
        modifier = modifier
            .heightIn(min = Sizes.touchMin)
            .clip(Radius.pill)
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick)
            .semantics(mergeDescendants = true) { this.contentDescription = contentDescription },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .height(Sizes.controlHeight)
                .clip(Radius.pill)
                .background(c.overlayOnAccent)
                .padding(start = Spacing.s4, end = Spacing.s3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
        ) {
            Text(label, style = CashFlowTheme.typography.bodyStrong, color = c.textOnAccent, maxLines = 1)
            Icon(app.cashflow.core.designsystem.icon.CfIcons.ChevronDown, contentDescription = null, tint = c.textOnAccent, modifier = Modifier.size(18.dp))
        }
    }
}

/** Compact thumb switch on the band (cf-seg-thumb-track on overlay): the white thumb marks the choice. */
@Composable
fun BandSegmented(
    segments: List<Segment>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val c = CashFlowTheme.colors
    Row(
        modifier = modifier
            .height(Sizes.controlHeight)
            .clip(Radius.pill)
            .background(c.overlayOnAccent)
            .padding(4.dp)
            .semantics { this.contentDescription = contentDescription }
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        segments.forEach { s ->
            val selected = s.key == selectedKey
            val bg by animateColorAsState(if (selected) c.surfaceControlThumb else Color.Transparent, tween(200, easing = Emphasized), label = "bandSeg")
            Box(
                Modifier
                    .fillMaxHeight()
                    .clip(Radius.pill)
                    .background(bg)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(s.key) })
                    .padding(horizontal = Spacing.s3),
                contentAlignment = Alignment.Center,
            ) {
                Text(s.label, style = CashFlowTheme.typography.bodyStrong, color = if (selected) c.textOnThumb else c.textOnAccent, maxLines = 1)
            }
        }
    }
}
