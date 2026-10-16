package app.cashflow.feature.importer.data

import app.cashflow.core.domain.ImportError
import app.cashflow.core.domain.Result
import app.cashflow.core.domain.datasource.CsvProfileLocalDataSource
import app.cashflow.core.domain.model.ParsedStatement
import app.cashflow.feature.importer.data.csv.CsvGridReader
import app.cashflow.feature.importer.data.mapping.DefaultColumnMapper
import app.cashflow.feature.importer.data.profile.StatementProfile
import app.cashflow.feature.importer.data.profile.headerKey
import app.cashflow.feature.importer.data.profile.text
import app.cashflow.feature.importer.data.xls.Biff8Workbook
import app.cashflow.feature.importer.data.xls.Cell
import app.cashflow.feature.importer.data.xls.XlsFormatException
import app.cashflow.feature.importer.domain.PickedFile
import app.cashflow.feature.importer.domain.StatementReader

/**
 * Picks the container by its magic bytes (OLE2 `.xls` or text CSV), turns it into a cell
 * grid and lets the first profile whose header fingerprint matches parse it: the bank
 * profiles first, then column mappings the user saved for formats the app did not know.
 */
class ProfileStatementReader(
    private val profiles: List<StatementProfile>,
    private val saved: CsvProfileLocalDataSource? = null,
    private val mapper: DefaultColumnMapper = DefaultColumnMapper(),
) : StatementReader {

    override suspend fun read(file: PickedFile): Result<ParsedStatement, ImportError> {
        val grid = when (val g = Grids.read(file.bytes)) {
            is Result.Error -> return g
            is Result.Success -> g.data
        }
        for (profile in profiles) {
            val header = profile.findHeader(grid) ?: continue
            val parsed = profile.parse(grid, header)
            if (parsed.rows.isEmpty() && parsed.warnings.isEmpty()) return Result.Error(ImportError.EMPTY_FILE)
            return Result.Success(ParsedStatement(fileName = file.name, source = profile.source, rows = parsed.rows, warnings = parsed.warnings))
        }
        val mappings = (saved?.getProfiles() as? Result.Success)?.data.orEmpty()
        val mapping = mappings.firstOrNull { p -> grid.getOrNull(p.headerRow)?.let(Grids::fingerprint) == p.fingerprint }
        if (mapping != null) return mapper.parseGrid(file.name, grid, mapping)
        return Result.Error(ImportError.UNSUPPORTED_FORMAT)
    }
}

/** Shared by the bank profiles and the column mapper. */
object Grids {

    fun read(bytes: ByteArray): Result<List<List<Cell?>>, ImportError> {
        if (bytes.isEmpty()) return Result.Error(ImportError.EMPTY_FILE)
        val grid: List<List<Cell?>> = try {
            when {
                isOle2(bytes) -> Biff8Workbook.readFirstSheet(bytes).rows
                isZip(bytes) -> return Result.Error(ImportError.UNSUPPORTED_FORMAT) // .xlsx arrives later
                else -> CsvGridReader.read(bytes)
            }
        } catch (_: XlsFormatException) {
            return Result.Error(ImportError.UNREADABLE_FILE)
        } catch (_: RuntimeException) {
            return Result.Error(ImportError.UNREADABLE_FILE)
        }
        return if (grid.isEmpty()) Result.Error(ImportError.EMPTY_FILE) else Result.Success(grid)
    }

    /** Header cells, lower case without spaces or punctuation, joined by '|'. */
    fun fingerprint(header: List<Cell?>): String = header.map { headerKey(it.text()) }.dropLastWhile { it.isEmpty() }.joinToString("|")

    /** The CSV separator, or null for a spreadsheet. */
    fun separatorOf(bytes: ByteArray): Char? = if (isOle2(bytes)) null else CsvGridReader.separatorOf(bytes)

    private fun isOle2(b: ByteArray) = b.size >= 8 && b[0] == 0xD0.toByte() && b[1] == 0xCF.toByte() && b[2] == 0x11.toByte() && b[3] == 0xE0.toByte()
    private fun isZip(b: ByteArray) = b.size >= 4 && b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte()
}
