package app.cashflow.core.designsystem.icon

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The design system's 24dp line icons (1.75 stroke, round caps and joins). Icons are tinted
 * by the Icon composable, so the stroke colour here is a placeholder.
 */
object CfIcons {
    val Home = line("M4 10.5 12 4l8 6.5V20a1 1 0 0 1-1 1h-4.5v-6h-5v6H5a1 1 0 0 1-1-1z")
    val Cashflow = line("M3 17l5.5-5.5 4 4L21 7M15 7h6v6")
    val Transfer = line("M5 8.5h14M15.5 5 19 8.5 15.5 12M19 15.5H5M8.5 12 5 15.5 8.5 19")
    val Bulb = line("M9 18h6M10 21h4M12 3a6 6 0 0 0-3.5 10.9c.6.5 1 1.2 1 2.1h5c0-.9.4-1.6 1-2.1A6 6 0 0 0 12 3z")
    val ChevronRight = line("M9.5 6l6 6-6 6")
    val ChevronLeft = line("M14.5 6l-6 6 6 6")
    val ChevronDown = line("M6 9.5l6 6 6-6")
    val User = line("M12 12a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7zM5.5 20a6.5 6.5 0 0 1 13 0")
    val Sliders = line("M4 7h9M17 7h3M4 17h3M11 17h9M15 5v4M9 15v4")
    val Info = line("M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 11v6M12 7.5h.01")
    val Calendar = line("M4.5 6.5h15v13h-15zM4.5 10.5h15M8.5 4v4M15.5 4v4")
    val Close = line("M6 6l12 12M18 6 6 18")
    val Check = line("M5 12.5l4.5 4.5L19 7.5")
    val ArrowRight = line("M5 12h14M13 6l6 6-6 6")
    val Tag = line("M3 12l9-9h7v7l-9 9-7-7zM15.5 8.5h.01")
    val Copy = line("M9 9h10v10H9zM5 15V5h10")
    val Plus = line("M12 5v14M5 12h14")
    val Search = line("M11 17.5a6.5 6.5 0 1 0 0-13 6.5 6.5 0 0 0 0 13zM20 20l-4.2-4.2")
    val File = line("M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8zM14 3v5h5M9 14h6M9 17h4")
    val Sparkle = line("M12 3l1.8 4.7 4.7 1.8-4.7 1.8L12 16l-1.8-4.7-4.7-1.8 4.7-1.8zM19 15l.8 2.2 2.2.8-2.2.8L19 21l-.8-2.2-2.2-.8 2.2-.8z")
    val Savings = line("M5 11a7 6 0 0 1 13-2.5h1.5a1.5 1.5 0 0 1 1.5 1.5v2.5l-2 .8A7 6 0 0 1 16 16.5V20h-3v-2h-3v2H7v-3.6A6 6 0 0 1 5 11zM10 7.2h4M15.5 11h.01")

    private fun line(d: String): ImageVector = ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = PathParser().parsePathString(d).toNodes(),
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.75f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ).build()
}
