package app.cashflow.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.animation.core.animate
import androidx.compose.foundation.layout.offset
import kotlin.math.roundToInt

/**
 * A pinned bar that slides away while you scroll down and comes back as soon as you scroll up
 * (like Gmail's). Put [connection] on the scrolling parent with `nestedScroll` and [modifier]
 * on the bar; the offset is read in the layout phase, so scrolling never recomposes the screen.
 */
@Stable
class ScrollAwayState {
    private var offset by mutableFloatStateOf(0f)
    private var height by mutableIntStateOf(0)
    private var up = true

    /** True once at least half the bar is out of sight. */
    // Derived, so a screen reading it recomposes only when it flips, not on every scrolled pixel.
    val hidden: Boolean by derivedStateOf { height > 0 && offset <= -height / 2f }

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y != 0f) up = available.y > 0f
            offset = (offset + available.y).coerceIn(-height.toFloat(), 0f)
            return Offset.Zero
        }

        // When the finger lifts the bar settles fully in or out, never half under the status bar:
        // in after any scroll up, out after a scroll down.
        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            val target = if (up) 0f else -height.toFloat()
            if (offset != target) animate(offset, target) { value, _ -> offset = value }
            return Velocity.Zero
        }
    }

    val modifier: Modifier = Modifier
        .onSizeChanged { height = it.height }
        .offset { IntOffset(0, offset.roundToInt()) }

    fun show() {
        offset = 0f
    }
}

@Composable
fun rememberScrollAwayState(): ScrollAwayState = remember { ScrollAwayState() }
