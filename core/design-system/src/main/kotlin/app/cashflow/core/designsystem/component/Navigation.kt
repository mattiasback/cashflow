package app.cashflow.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.cashflow.core.designsystem.theme.Accent
import app.cashflow.core.designsystem.theme.CashFlowTheme
import app.cashflow.core.designsystem.theme.Radius
import app.cashflow.core.designsystem.theme.Sizes
import app.cashflow.core.designsystem.theme.Spacing
import app.cashflow.core.designsystem.theme.ink

data class NavItem(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val accent: Accent,
)

/**
 * The floating nav pill. The active tab takes its accent's ink and shows Lysa's dot instead
 * of its label (the label stays for TalkBack). It sits on the navigation-bar inset over a
 * fade so content scrolls behind it.
 */
@Composable
fun BottomNavPill(
    items: List<NavItem>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = CashFlowTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, c.bgCanvas.copy(alpha = 0.92f))))
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(top = Spacing.s4, bottom = Spacing.s2),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minWidth = Sizes.navWidth, minHeight = Sizes.navHeight)
                .shadow(16.dp, Radius.pill, ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.12f))
                .clip(Radius.pill)
                .background(c.surfaceNav)
                .padding(horizontal = Spacing.s2, vertical = Spacing.s1)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            items.forEach { item ->
                val selected = item.key == selectedKey
                val tint by animateColorAsState(if (selected) item.accent.ink() else c.textPrimary, tween(200), label = "navTint")
                Column(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 72.dp, minHeight = Sizes.touchMin)
                        .clip(Radius.pill)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(item.key) })
                        .padding(horizontal = Spacing.s2, vertical = Spacing.s1),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(Sizes.iconSize))
                    if (selected) {
                        Box(Modifier.padding(vertical = 4.5.dp).size(5.dp).clip(Radius.pill).background(tint))
                        // The label stays in the tree for screen readers.
                        Text(item.label, style = CashFlowTheme.typography.navLabel, color = Color.Transparent, modifier = Modifier.size(1.dp))
                    } else {
                        Text(item.label, style = CashFlowTheme.typography.navLabel, color = c.textSecondary, maxLines = 1)
                    }
                }
            }
        }
    }
}
