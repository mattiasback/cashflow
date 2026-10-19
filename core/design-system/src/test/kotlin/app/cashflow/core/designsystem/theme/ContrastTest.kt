package app.cashflow.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import assertk.assertThat
import assertk.assertions.isEmpty
import org.junit.jupiter.api.Test

/**
 * WCAG contrast of the colour pairs the screens actually use, in both themes: 4.5:1 for text,
 * 3:1 for large text, icons and the white controls on the accent bands.
 */
class ContrastTest {

    private fun ratio(fg: Color, bg: Color): Double {
        val a = fg.compositeOver(bg).luminance() + 0.05
        val b = bg.luminance() + 0.05
        return maxOf(a, b) / minOf(a, b)
    }

    private data class Pair(val name: String, val fg: Color, val bg: Color, val min: Double)

    private fun pairs(c: CashFlowColors): List<Pair> = buildList {
        for ((bgName, bg) in listOf("canvas" to c.bgCanvas, "card" to c.surfaceCard, "group" to c.surfaceGroup, "sheet" to c.surfaceSheet)) {
            add(Pair("textPrimary on $bgName", c.textPrimary, bg, 4.5))
            add(Pair("textSecondary on $bgName", c.textSecondary, bg, 4.5))
            add(Pair("textLink on $bgName", c.textLink, bg, 4.5))
        }
        add(Pair("textPositive on card", c.textPositive, c.surfaceCard, 4.5))
        add(Pair("dangerStrong on card", c.dangerStrong, c.surfaceCard, 4.5))
        add(Pair("textOnStatus on status", c.textOnStatus, c.surfaceStatus, 4.5))
        add(Pair("textOnCelebrate on celebrate", c.textOnCelebrate, c.surfaceCelebrate, 4.5))
        add(Pair("textOnEventsTint on tint", c.textOnEventsTint, c.accentEventsTint, 4.5))
        add(Pair("textOnInsightsTint on tint", c.textOnInsightsTint, c.accentInsightsTint, 4.5))
        add(Pair("textSecondary on control", c.textSecondary, c.surfaceControl, 3.0))
        val inks = listOf(
            "home" to c.accentHomeInk, "cashflow" to c.accentCashflowInk, "savings" to c.accentSavingsInk, "goals" to c.accentGoalsInk,
            "settings" to c.accentSettingsInk, "events" to c.accentEventsInk, "insights" to c.accentInsightsInk,
        )
        for ((n, ink) in inks) add(Pair("$n ink on card", ink, c.surfaceCard, 4.5))
        val bands = listOf(
            "home" to c.accentHome, "cashflow" to c.accentCashflow, "savings" to c.accentSavings, "goals" to c.accentGoals,
            "settings" to c.accentSettings, "events" to c.accentEvents, "insights" to c.accentInsights,
        )
        for ((n, band) in bands) add(Pair("white on $n band", c.textOnAccent, band, 3.0))
    }

    private fun failures(c: CashFlowColors) = pairs(c).mapNotNull { p ->
        val r = ratio(p.fg, p.bg)
        if (r < p.min) "${p.name}: %.2f < %.1f".format(r, p.min) else null
    }

    @Test
    fun `light theme pairs meet WCAG AA`() {
        assertThat(failures(LightCashFlowColors)).isEmpty()
    }

    @Test
    fun `dark theme pairs meet WCAG AA`() {
        assertThat(failures(DarkCashFlowColors)).isEmpty()
    }
}
