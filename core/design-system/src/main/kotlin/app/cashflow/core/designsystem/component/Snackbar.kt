package app.cashflow.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cashflow.core.designsystem.theme.CashFlowTheme
import app.cashflow.core.designsystem.theme.Radius
import app.cashflow.core.designsystem.theme.Spacing

/**
 * The app's snackbar ("Sparat · Ångra"): a flat emphasis card in the design system's radius and
 * type, not Material's default. Place it at the bottom of the screen, above the nav pill.
 */
@Composable
fun CfSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    val c = CashFlowTheme.colors
    // In dark the emphasis surface is close to the canvas, so the raised control colour is used.
    val bg = if (c.isDark) c.surfaceControl else c.surfaceEmphasis
    val fg = if (c.isDark) c.textPrimary else c.textOnEmphasis
    val action = if (c.isDark) c.textLink else c.textOnEmphasis
    SnackbarHost(state, modifier) { data ->
        Row(
            Modifier
                .padding(horizontal = Spacing.gutter)
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(Radius.sm)
                .background(bg)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .padding(start = Spacing.s4, end = Spacing.s1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                data.visuals.message,
                style = CashFlowTheme.typography.body,
                color = fg,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(vertical = Spacing.s3),
            )
            data.visuals.actionLabel?.let { label ->
                Box(
                    Modifier
                        .heightIn(min = 48.dp)
                        .clip(Radius.xs)
                        .clickable(role = Role.Button, onClickLabel = label) { data.performAction() }
                        .padding(horizontal = Spacing.s3),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = CashFlowTheme.typography.bodyStrong, color = action, maxLines = 1)
                }
            }
        }
    }
}
