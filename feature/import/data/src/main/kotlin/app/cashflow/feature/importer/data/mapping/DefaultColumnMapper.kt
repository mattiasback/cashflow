package app.cashflow.feature.importer.data.mapping

import app.cashflow.core.domain.ImportError
import app.cashflow.core.domain.Result
import app.cashflow.core.domain.model.CsvProfile
import app.cashflow.core.domain.model.ImportSource
import app.cashflow.core.domain.model.Money
import app.cashflow.core.domain.model.ParsedStatement
import app.cashflow.core.domain.model.RowWarning
import app.cashflow.core.domain.model.RowWarningReason
import app.cashflow.core.domain.model.StatementRow
import app.cashflow.feature.importer.data.Grids
import app.cashflow.feature.importer.data.profile.excelSerialToDate
import app.cashflow.feature.importer.data.profile.headerKey
import app.cashflow.feature.importer.data.profile.money
import app.cashflow.feature.importer.data.profile.text
import app.cashflow.feature.importer.data.profile.warning
import app.cashflow.feature.importer.data.xls.Cell
import app.cashflow.feature.importer.domain.ColumnGuess
import app.cashflow.feature.importer.domain.ColumnMapper
import app.cashflow.feature.importer.domain.ColumnRole
import app.cashflow.feature.importer.domain.MappingDraft
import app.cashflow.feature.importer.domain.PickedFile
import kotlinx.datetime.LocalDate

/**
 * "Mappa kolumner" for files no bank profile knows: finds the header row, guesses each
 * column's role from its header (sure) or its values (unsure), and parses the file with the
 * mapping the user confirms. A saved mapping is matched again by the header fingerprint.
 */
class DefaultColumnMapper : ColumnMapper {

    override fun inspect(file: PickedFile): Result<MappingDraft, ImportError> {
        val grid = when (val g = Grids.read(file.bytes)) {
            is Result.Error -> return g
            is Result.Success -> g.data
        }
        val headerRow = findHeader(grid) ?: return Result.Error(ImportError.UNSUPPORTED_FORMAT)
        val header = grid[headerRow]
        val body = grid.drop(headerRow + 1).filter { r -> r.any { it.text().isNotEmpty() } }
        if (body.isEmpty()) return Result.Error(ImportError.EMPTY_FILE)
        val width = (listOf(header) + body.take(20)).maxOf { it.size }

        val taken = HashSet<ColumnRole>()
        val guesses = (0 until width).map { i ->
            val head = header.getOrNull(i).text()
            val values = body.take(30).map { it.getOrNull(i) }
            val byName = roleByHeader(headerKey(head), taken)
            val role = byName ?: roleByValues(values, taken)
            if (role != ColumnRole.IGNORE) taken += role
            ColumnGuess(
                index = i,
                header = head.ifEmpty { "Kolumn ${i + 1}" },
                sample = values.firstOrNull { it.text().isNotEmpty() }.text(),
                role = role,
                sure = byName != null,
            )
        }
        val amountIndex = guesses.firstOrNull { it.role == ColumnRole.AMOUNT }?.index
        val amounts = amountIndex?.let { idx -> body.mapNotNull { it.getOrNull(idx).money() } }.orEmpty()
        return Result.Success(
            MappingDraft(
                fileName = file.name,
                fingerprint = Grids.fingerprint(header),
                headerRow = headerRow,
                columns = guesses,
                rowCount = body.size,
                separator = Grids.separatorOf(file.bytes),
                decimalComma = body.take(30).any { r -> r.any { c -> c is Cell.Text && Regex("""\d,\d{1,2}$""").containsMatchIn(c.value.trim()) } },
                looksUnsigned = amounts.isNotEmpty() && amounts.count { it.isPositive } * 10 >= amounts.size * 9,
            ),
        )
    }

    override fun parse(file: PickedFile, profile: CsvProfile): Result<ParsedStatement, ImportError> {
        val grid = when (val g = Grids.read(file.bytes)) {
            is Result.Error -> return g
            is Result.Success -> g.data
        }
        return parseGrid(file.name, grid, profile)
    }

    fun parseGrid(fileName: String, grid: List<List<Cell?>>, profile: CsvProfile): Result<ParsedStatement, ImportError> {
        val rows = ArrayList<StatementRow>()
        val warnings = ArrayList<RowWarning>()
        for (i in profile.headerRow + 1 until grid.size) {
            val r = grid[i]
            if (r.all { it == null || it.text().isEmpty() }) continue
            val dateCell = r.getOrNull(profile.dateColumn)
            val date = flexibleDate(dateCell)
            if (date == null) {
                warnings += warning(i, if (dateCell.text().isEmpty()) RowWarningReason.MISSING_DATE else RowWarningReason.UNREADABLE_DATE)
                continue
            }
            val amount = amountOf(r, profile)
            if (amount == null) {
                warnings += warning(i, RowWarningReason.UNREADABLE_AMOUNT)
                continue
            }
            rows += StatementRow(
                bookingDate = date,
                transactionDate = profile.transactionDateColumn?.let { flexibleDate(r.getOrNull(it)) } ?: date,
                rawText = r.getOrNull(profile.textColumn).text(),
                amount = if (profile.negateAmounts) -amount else amount,
                balanceAfter = profile.balanceColumn?.let { r.getOrNull(it).money() },
            )
        }
        if (rows.isEmpty() && warnings.isEmpty()) return Result.Error(ImportError.EMPTY_FILE)
        return Result.Success(ParsedStatement(fileName, ImportSource.CSV_MAPPED, rows, warnings))
    }

    private fun amountOf(r: List<Cell?>, p: CsvProfile): Money? {
        p.amountColumn?.let { return r.getOrNull(it).money() }
        val inn = p.inColumn?.let { r.getOrNull(it).money() }?.abs()
        val out = p.outColumn?.let { r.getOrNull(it).money() }?.abs()
        if (inn == null && out == null) return null
        return (inn ?: Money.ZERO) - (out ?: Money.ZERO)
    }

    /** The header is the first row with at least two text cells whose next row holds a date. */
    private fun findHeader(grid: List<List<Cell?>>): Int? =
        (0 until minOf(grid.size - 1, HEADER_SEARCH_ROWS)).firstOrNull { i ->
            val texts = grid[i].count { it is Cell.Text && it.value.isNotBlank() && flexibleDate(it) == null && it.money() == null }
            texts >= 2 && grid.drop(i + 1).take(3).any { r -> r.any { flexibleDate(it) != null } }
        }

    private fun roleByHeader(key: String, taken: Set<ColumnRole>): ColumnRole? {
        fun free(role: ColumnRole) = role.takeIf { it !in taken }
        return when {
            key.isEmpty() -> null
            key in TRANSACTION_DATE_NAMES -> free(ColumnRole.TRANSACTION_DATE) ?: free(ColumnRole.DATE)
            DATE_NAMES.any { key.contains(it) } -> free(ColumnRole.DATE) ?: free(ColumnRole.TRANSACTION_DATE)
            BALANCE_NAMES.any { key.contains(it) } -> free(ColumnRole.BALANCE)
            key in IN_NAMES -> free(ColumnRole.AMOUNT_IN)
            key in OUT_NAMES -> free(ColumnRole.AMOUNT_OUT)
            AMOUNT_NAMES.any { key.contains(it) } -> free(ColumnRole.AMOUNT)
            TEXT_NAMES.any { key.contains(it) } -> free(ColumnRole.TEXT)
            else -> null
        }
    }

    private fun roleByValues(values: List<Cell?>, taken: Set<ColumnRole>): ColumnRole {
        val filled = values.filter { it.text().isNotEmpty() }
        if (filled.isEmpty()) return ColumnRole.IGNORE
        return when {
            filled.all { flexibleDate(it) != null } && ColumnRole.DATE !in taken -> ColumnRole.DATE
            filled.all { it.money() != null } && ColumnRole.AMOUNT !in taken && ColumnRole.AMOUNT_IN !in taken -> ColumnRole.AMOUNT
            filled.any { c -> c.text().any(Char::isLetter) } && ColumnRole.TEXT !in taken -> ColumnRole.TEXT
            else -> ColumnRole.IGNORE
        }
    }

    private companion object {
        const val HEADER_SEARCH_ROWS = 10
        val DATE_NAMES = listOf("datum", "date", "bokforingsdag", "bokföringsdag", "bokford", "bokförd", "valutadag")
        val TRANSACTION_DATE_NAMES = listOf("transaktionsdatum", "transaktionsdag", "transactiondate", "köpdatum", "kopdatum")
        val TEXT_NAMES = listOf("text", "beskrivning", "mottagare", "description", "meddelande", "rubrik", "specifikation", "referens", "transaktion", "händelse", "namn")
        val AMOUNT_NAMES = listOf("belopp", "amount", "summa", "insättninguttag", "insattninguttag")
        val IN_NAMES = listOf("insättning", "insattning", "in", "kredit", "credit", "inbetalning")
        val OUT_NAMES = listOf("uttag", "ut", "debet", "debit", "utbetalning")
        val BALANCE_NAMES = listOf("saldo", "behållning", "behallning", "balance")
    }
}

/** Dates in the formats Swedish banks use: ISO, yyyy/MM/dd, dd.MM.yyyy, dd/MM/yyyy, yyyyMMdd, Excel serials. */
internal fun flexibleDate(cell: Cell?): LocalDate? = when (cell) {
    is Cell.Number -> excelSerialToDate(cell.value)
    is Cell.Text -> parseFlexibleDate(cell.value)
    null -> null
}

internal fun parseFlexibleDate(raw: String): LocalDate? {
    val s = raw.trim().take(10)
    Regex("""^(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})$""").find(s)?.let { m ->
        val (y, mo, d) = m.destructured
        return runCatching { LocalDate(y.toInt(), mo.toInt(), d.toInt()) }.getOrNull()
    }
    Regex("""^(\d{1,2})[-/.](\d{1,2})[-/.](\d{4})$""").find(s)?.let { m ->
        val (d, mo, y) = m.destructured
        return runCatching { LocalDate(y.toInt(), mo.toInt(), d.toInt()) }.getOrNull()
    }
    Regex("""^(\d{4})(\d{2})(\d{2})$""").find(s)?.let { m ->
        val (y, mo, d) = m.destructured
        return runCatching { LocalDate(y.toInt(), mo.toInt(), d.toInt()) }.getOrNull()?.takeIf { it.year in 1990..2100 }
    }
    return null
}
