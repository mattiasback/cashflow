package app.cashflow.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Type scale from tokens.json, in sp so every style follows the system font scale. Roboto on Android. */
@Immutable
data class CashFlowTypography(
    val display: TextStyle,
    val headlineL: TextStyle,
    val headline: TextStyle,
    val titleL: TextStyle,
    val title: TextStyle,
    val bodyStrong: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    val overline: TextStyle,
    val navLabel: TextStyle,
)

/** Every monetary figure uses tabular figures so columns of amounts line up. */
private const val TABULAR = "tnum"

fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = TABULAR)

val CashFlowTypographyDefault = CashFlowTypography(
    display = TextStyle(fontFamily = FontFamily.Default, fontSize = 64.sp, lineHeight = 68.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp, fontFeatureSettings = TABULAR),
    headlineL = TextStyle(fontFamily = FontFamily.Default, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, fontFeatureSettings = TABULAR),
    headline = TextStyle(fontFamily = FontFamily.Default, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    titleL = TextStyle(fontFamily = FontFamily.Default, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
    title = TextStyle(fontFamily = FontFamily.Default, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyStrong = TextStyle(fontFamily = FontFamily.Default, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    body = TextStyle(fontFamily = FontFamily.Default, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    label = TextStyle(fontFamily = FontFamily.Default, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    caption = TextStyle(fontFamily = FontFamily.Default, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
    overline = TextStyle(fontFamily = FontFamily.Default, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.05.em),
    navLabel = TextStyle(fontFamily = FontFamily.Default, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium),
)
