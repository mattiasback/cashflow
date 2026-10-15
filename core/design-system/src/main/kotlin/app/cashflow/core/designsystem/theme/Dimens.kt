package app.cashflow.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** 4dp grid, radii and sizes from tokens.json. */
object Spacing {
    val s1 = 4.dp
    val s2 = 8.dp
    val s3 = 12.dp
    val s4 = 16.dp
    val s5 = 20.dp
    val s6 = 24.dp
    val s8 = 32.dp
    val s12 = 48.dp

    /** Bottom inset so the floating nav never covers the last row. */
    val s24 = 96.dp
    val gutter = s4
}

object Radius {
    val xs = RoundedCornerShape(4.dp)
    val sm = RoundedCornerShape(8.dp)
    val lg = RoundedCornerShape(16.dp)
    val pill = RoundedCornerShape(percent = 50)
    val sheetTop = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
}

object Sizes {
    val touchMin = 48.dp
    val controlHeight = 40.dp
    val thumbSize = 32.dp
    val buttonHeight = 48.dp
    val buttonHeightCompact = 36.dp
    val navHeight = 70.dp
    val navWidth = 258.dp
    val badgeSize = 32.dp
    val iconSize = 24.dp
    val accentBandFade = 96.dp
    val contentMax = 600.dp
}
