package app.cashflow.feature.importer.data.profile

import app.cashflow.core.domain.model.ImportSource
import app.cashflow.core.domain.model.Money
import app.cashflow.core.domain.model.RowWarning
import app.cashflow.core.domain.model.RowWarningReason
import app.cashflow.core.domain.model.StatementRow
import app.cashflow.feature.importer.data.xls.Cell
import kotlinx.datetime.LocalDate

/** The rows a profile understood, plus the rows it could not use. */
data class ProfileResult(
    val rows: List<StatementRow>,
    val warnings: List<RowWarning>,
)

/** A bank export format, recognised by the header fingerprint within the first rows. */
interface StatementProfile {
    val source: ImportSource

    /** Index of the header row in [grid], or null when this profile does not match. */
    fun findHeader(grid: List<List<Cell?>>): Int?
    fun parse(grid: List<List<Cell?>>, headerRow: Int): ProfileResult
}

internal fun Cell?.text(): String = when (this) {
    is Cell.Text -> value.trim()
    is Cell.Number -> value.toString()
    null -> ""
}

/** Normalises header text for fingerprinting: lower case, no spaces or punctuation. */
internal fun headerKey(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

/** Numbers come as numeric cells (BIFF) or Swedish text: "-2 388,02", "1.234,50", "−35 000". */
internal fun Cell?.money(): Money? = when (this) {
    is Cell.Number -> Money.ofDouble(value)
    is Cell.Text -> parseSwedishAmount(value)
    null -> null
}

internal fun parseSwedishAmount(raw: String): Money? {
    var s = raw.trim().replace('−', '-').replace(" ", "").replace(" ", "").removeSuffix("kr").removeSuffix("SEK")
    if (s.isEmpty()) return null
    if (s.contains(',')) s = s.replace(".", "").replace(',', '.')
    return s.toBigDecimalOrNull()?.movePointRight(2)?.setScale(0, java.math.RoundingMode.HALF_UP)?.toLong()?.let(::Money)
}

/** Dates are ISO text in LF files; Excel serial numbers are accepted too. */
internal fun Cell?.date(): LocalDate? = when (this) {
    is Cell.Text -> parseDate(value)
    is Cell.Number -> excelSerialToDate(value)
    null -> null
}

internal fun parseDate(raw: String): LocalDate? {
    val s = raw.trim().take(10)
    return runCatching { LocalDate.parse(s) }.getOrNull()
}

internal fun excelSerialToDate(serial: Double): LocalDate? {
    if (serial < 1 || serial > 2_958_465) return null
    // Excel's day 0 is 1899-12-30 once its 1900 leap-year bug is accounted for.
    return LocalDate.fromEpochDays(serial.toLong() - 25_569L)
}

internal fun warning(row: Int, reason: RowWarningReason) = RowWarning(rowNumber = row + 1, reason = reason)
