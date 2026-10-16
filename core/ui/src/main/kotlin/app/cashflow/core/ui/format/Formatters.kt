package app.cashflow.core.ui.format

import app.cashflow.core.domain.model.Money
import app.cashflow.core.domain.model.Period
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** Swedish is the primary locale; English (US) is secondary. */
enum class AppLocale { SV, EN;
    companion object {
        fun current(locale: Locale = Locale.getDefault()): AppLocale = if (locale.language == "en") EN else SV
    }
}

private const val NBSP = ' '
private const val MINUS = '−'

private fun group(value: Long, separator: Char): String {
    val digits = abs(value).toString()
    val sb = StringBuilder()
    digits.forEachIndexed { i, ch ->
        if (i > 0 && (digits.length - i) % 3 == 0) sb.append(separator)
        sb.append(ch)
    }
    return sb.toString()
}

/**
 * Money: `12 450 kr` / `SEK 12,450`. Groups with a no-break space, the unit stays on the
 * same line, negatives use the real minus sign (U+2212), never a hyphen.
 */
object MoneyFormatter {

    fun format(money: Money, signed: Boolean = false, decimals: Boolean = false, locale: AppLocale = AppLocale.current()): String {
        val negative = money.ore < 0
        val sign = when {
            negative -> MINUS.toString()
            signed && money.ore > 0 -> "+"
            else -> ""
        }
        val body = if (decimals) {
            val ore = abs(money.ore)
            val whole = group(ore / 100, if (locale == AppLocale.SV) NBSP else ',')
            val frac = (ore % 100).toString().padStart(2, '0')
            if (locale == AppLocale.SV) "$whole,$frac" else "$whole.$frac"
        } else {
            group(abs(money.kronor), if (locale == AppLocale.SV) NBSP else ',')
        }
        return if (locale == AppLocale.SV) "$sign$body${NBSP}kr" else "${sign}SEK$NBSP$body"
    }

    /** For TalkBack: "18 500 kronor", "minus 1 200 kronor". */
    fun spoken(money: Money, locale: AppLocale = AppLocale.current()): String {
        val amount = group(abs(money.kronor), ' ')
        return when (locale) {
            AppLocale.SV -> (if (money.isNegative) "minus " else "") + "$amount kronor"
            AppLocale.EN -> (if (money.isNegative) "minus " else "") + "$amount kronor"
        }
    }
}

/** Percent: `12,5 %` / `12.5%`, one decimal by default. */
object PercentFormatter {
    fun format(value: Double, decimals: Int = 1, signed: Boolean = false, locale: AppLocale = AppLocale.current()): String {
        val factor = Math.pow(10.0, decimals.toDouble())
        val rounded = (abs(value) * factor).roundToLong() / factor
        val zero = rounded == 0.0
        var number = if (decimals == 0) rounded.roundToLong().toString() else String.format(Locale.ROOT, "%.${decimals}f", rounded)
        if (locale == AppLocale.SV) number = number.replace('.', ',')
        val sign = when {
            value < 0 && !zero -> MINUS.toString()
            signed && value > 0 && !zero -> "+"
            else -> ""
        }
        return if (locale == AppLocale.SV) "$sign$number$NBSP%" else "$sign$number%"
    }

    /** Share of a whole; tiny non-zero shares read "<1 %" instead of "0 %". */
    fun share(part: Long, whole: Long, locale: AppLocale = AppLocale.current()): String {
        if (whole <= 0) return format(0.0, 0, locale = locale)
        val pct = part.toDouble() / whole * 100
        if (pct > 0 && pct < 0.5) return if (locale == AppLocale.SV) "<1$NBSP%" else "<1%"
        return format(pct, 0, locale = locale)
    }
}

/** Dates: `2026-10-27`, `okt 2026`, `27 okt.` (sv) / `Oct 27, 2026`, `Oct 2026` (en). */
object PeriodFormatter {
    private val svMonths = listOf("jan", "feb", "mar", "apr", "maj", "jun", "jul", "aug", "sep", "okt", "nov", "dec")
    private val svMonthsLong = listOf("januari", "februari", "mars", "april", "maj", "juni", "juli", "augusti", "september", "oktober", "november", "december")
    private val enMonths = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val enMonthsLong = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

    fun month(period: Period, locale: AppLocale = AppLocale.current()): String =
        if (locale == AppLocale.SV) "${svMonths[period.month - 1]} ${period.year}" else "${enMonths[period.month - 1]} ${period.year}"

    /** Spoken month: "oktober 2026". */
    fun monthLong(period: Period, locale: AppLocale = AppLocale.current()): String =
        if (locale == AppLocale.SV) "${svMonthsLong[period.month - 1]} ${period.year}" else "${enMonthsLong[period.month - 1]} ${period.year}"

    fun iso(date: LocalDate, locale: AppLocale = AppLocale.current()): String =
        if (locale == AppLocale.SV) date.toString() else "${enMonths[date.month.number - 1]} ${date.day}, ${date.year}"

    /** Day heading in lists: "tis 13 okt" / "Tue, Oct 13". */
    fun dayHeading(date: LocalDate, today: LocalDate? = null, locale: AppLocale = AppLocale.current()): String {
        val svDays = listOf("mån", "tis", "ons", "tors", "fre", "lör", "sön")
        val enDays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val dow = date.dayOfWeek.ordinal
        val sameYear = today == null || today.year == date.year
        return if (locale == AppLocale.SV) {
            "${svDays[dow]} ${date.day} ${svMonths[date.month.number - 1]}" + if (sameYear) "" else " ${date.year}"
        } else {
            "${enDays[dow]}, ${enMonths[date.month.number - 1]} ${date.day}" + if (sameYear) "" else ", ${date.year}"
        }
    }
}

/** Reads an amount typed by the user: "2 500", "2500,50", "−1 200 kr". Null when it is not a number. */
object MoneyParser {
    fun parse(text: String): Money? {
        var s = text.trim().replace(MINUS, '-').replace(NBSP.toString(), "").replace(" ", "").removeSuffix("kr").removeSuffix("SEK")
        if (s.isEmpty()) return null
        if (s.contains(',')) s = s.replace(".", "").replace(',', '.')
        return s.toBigDecimalOrNull()?.movePointRight(2)?.setScale(0, java.math.RoundingMode.HALF_UP)?.toLong()?.let(::Money)
    }

    /** The plain editable form of an amount: "2500" or "2500,50". */
    fun editable(money: Money): String {
        val whole = money.ore / 100
        val ore = kotlin.math.abs(money.ore % 100)
        return if (ore == 0L) whole.toString() else "$whole,${ore.toString().padStart(2, '0')}"
    }
}
