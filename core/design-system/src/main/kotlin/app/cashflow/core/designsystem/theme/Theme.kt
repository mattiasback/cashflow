package app.cashflow.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * CashFlow theme. The token set rides on MaterialTheme's colour scheme (no custom
 * CompositionLocals): [CashFlowTheme.colors] picks the light or dark tokens from the
 * scheme that is in effect, so previews and forced themes stay consistent.
 */
@Composable
fun CashFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val c = if (darkTheme) DarkCashFlowColors else LightCashFlowColors
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = c.brand, onPrimary = c.textOnAccent,
            background = c.bgCanvas, onBackground = c.textPrimary,
            surface = c.surfaceCard, onSurface = c.textPrimary,
            surfaceVariant = c.surfaceControl, onSurfaceVariant = c.textSecondary,
            surfaceContainerLow = c.surfaceSheet, surfaceContainer = c.surfaceSheet,
            surfaceContainerHigh = c.surfaceCard, outline = c.borderChip, outlineVariant = c.divider,
            error = c.dangerStrong, scrim = Color.Black,
        )
    } else {
        lightColorScheme(
            primary = c.brand, onPrimary = c.textOnAccent,
            background = c.bgCanvas, onBackground = c.textPrimary,
            surface = c.surfaceCard, onSurface = c.textPrimary,
            surfaceVariant = c.surfaceControl, onSurfaceVariant = c.textSecondary,
            surfaceContainerLow = c.surfaceSheet, surfaceContainer = c.surfaceSheet,
            surfaceContainerHigh = c.surfaceCard, outline = c.borderChip, outlineVariant = c.divider,
            error = c.dangerStrong, scrim = Color.Black,
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

object CashFlowTheme {
    val colors: CashFlowColors
        @Composable
        @ReadOnlyComposable
        get() = if (MaterialTheme.colorScheme.background == DarkCashFlowColors.bgCanvas) DarkCashFlowColors else LightCashFlowColors

    val typography: CashFlowTypography
        get() = CashFlowTypographyDefault
}

/** The accent a top-level tab owns: band colour plus its theme-tuned ink. */
enum class Accent { Home, Cashflow, Savings, Goals, Settings, Events, Insights }

@Composable
@ReadOnlyComposable
fun Accent.band(): Color = with(CashFlowTheme.colors) {
    when (this@band) {
        Accent.Home -> accentHome
        Accent.Cashflow -> accentCashflow
        Accent.Savings -> accentSavings
        Accent.Goals -> accentGoals
        Accent.Settings -> accentSettings
        Accent.Events -> accentEvents
        Accent.Insights -> accentInsights
    }
}

@Composable
@ReadOnlyComposable
fun Accent.ink(): Color = with(CashFlowTheme.colors) {
    when (this@ink) {
        Accent.Home -> accentHomeInk
        Accent.Cashflow -> accentCashflowInk
        Accent.Savings -> accentSavingsInk
        Accent.Goals -> accentGoalsInk
        Accent.Settings -> accentSettingsInk
        Accent.Events -> accentEventsInk
        Accent.Insights -> accentInsightsInk
    }
}
