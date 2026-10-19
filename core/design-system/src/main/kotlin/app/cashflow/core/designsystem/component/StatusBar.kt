package app.cashflow.core.designsystem.component

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import app.cashflow.core.designsystem.theme.CashFlowTheme
import app.cashflow.core.designsystem.theme.Sizes
import app.cashflow.core.designsystem.theme.Spacing

/**
 * A fully transparent status bar over a screen whose band scrolls away (every tab but Flöde).
 * While the band is behind it the icons are light; once content is, they follow the theme
 * (dark icons on the light canvas), so the clock and battery stay readable without a strip.
 */
@Composable
fun BandStatusBar(onBand: Boolean) {
    val view = LocalView.current
    val dark = CashFlowTheme.colors.isDark
    if (view.isInEditMode) return
    DisposableEffect(onBand, dark) {
        view.context.findActivity()?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !onBand && !dark
        }
        // Nothing to undo: the next screen sets its own icons, and resetting here would race it.
        onDispose { }
    }
}

/** How far the list scrolls before the band's colour has left the status bar. */
private val BandScroll = Sizes.controlHeight + Spacing.s4 * 2 + Spacing.s8

/** True while the band (the list's first item) is still behind the status bar. */
@Composable
fun rememberOnBand(state: LazyListState): Boolean {
    val limit = with(LocalDensity.current) { BandScroll.roundToPx() }
    val onBand by remember(state, limit) { derivedStateOf { state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset < limit } }
    return onBand
}

/** True while the band is still behind the status bar, for screens in a scrolling column. */
@Composable
fun rememberOnBand(state: ScrollState): Boolean {
    val limit = with(LocalDensity.current) { BandScroll.roundToPx() }
    val onBand by remember(state, limit) { derivedStateOf { state.value < limit } }
    return onBand
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
