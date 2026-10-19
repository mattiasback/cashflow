package app.cashflow.core.designsystem.component

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.cashflow.core.designsystem.theme.CashFlowTheme
import app.cashflow.core.designsystem.theme.Radius
import app.cashflow.core.designsystem.theme.Spacing

/**
 * A placeholder block for section skeletons. A soft highlight sweeps across it while loading
 * (design FlowLoading); with "Remove animations" on, or in previews, it stays still. The sweep is
 * read in the draw phase, so it never recomposes.
 */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier) {
    val c = CashFlowTheme.colors
    val sweep = rememberSweep()
    val highlight = if (c.isDark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.55f)
    Box(
        modifier
            .clip(Radius.sm)
            .background(c.surfaceControl)
            .fillMaxHeight()
            .clearAndSetSemantics {}
            .drawWithContent {
                drawContent()
                val p = sweep.value
                if (p >= 0f) {
                    val w = size.width
                    val x = -w + p * 3 * w
                    drawRect(Brush.linearGradient(listOf(Color.Transparent, highlight, Color.Transparent), start = Offset(x, 0f), end = Offset(x + w, size.height)))
                }
            },
    )
}

/** -1 when animations are off; otherwise 0..1 repeating. */
@Composable
private fun rememberSweep(): State<Float> {
    val context = LocalContext.current
    val off = LocalInspectionMode.current || remember(context) { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    if (off) return remember { mutableFloatStateOf(-1f) }
    val transition = rememberInfiniteTransition(label = "skeleton")
    return transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "sweep")
}

/**
 * A section that could not be read, with a way to try again. It announces itself to TalkBack.
 */
@Composable
fun ErrorCard(title: String, text: String, retryLabel: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val c = CashFlowTheme.colors
    CfCard(modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
            Text(title, style = CashFlowTheme.typography.title, color = c.textPrimary)
            Text(text, style = CashFlowTheme.typography.body, color = c.textSecondary)
            CfButton(retryLabel, onRetry, Modifier.fillMaxWidth(), variant = CfButtonVariant.Outline)
        }
    }
}

/** A list-row skeleton: a round icon, two lines and an amount. */
@Composable
fun SkeletonRows(count: Int, modifier: Modifier = Modifier) {
    CfCard(modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s4)) {
            repeat(count) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                    SkeletonBlock(Modifier.size(36.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SkeletonBlock(Modifier.fillMaxWidth(0.6f).height(14.dp))
                        SkeletonBlock(Modifier.fillMaxWidth(0.8f).height(12.dp))
                    }
                    SkeletonBlock(Modifier.fillMaxWidth(0.2f).height(14.dp))
                }
            }
        }
    }
}
