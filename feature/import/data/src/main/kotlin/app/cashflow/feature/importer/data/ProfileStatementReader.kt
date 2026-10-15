package app.cashflow.feature.importer.data

import app.cashflow.core.domain.ImportError
import app.cashflow.core.domain.Result
import app.cashflow.core.domain.model.ParsedStatement
import app.cashflow.feature.importer.data.csv.CsvGridReader
import app.cashflow.feature.importer.data.profile.StatementProfile
import app.cashflow.feature.importer.data.xls.Biff8Workbook
import app.cashflow.feature.importer.data.xls.Cell
import app.cashflow.feature.importer.data.xls.XlsFormatException
import app.cashflow.feature.importer.domain.PickedFile
import app.cashflow.feature.importer.domain.StatementReader

/**
 * Picks the container by its magic bytes (OLE2 `.xls` or text CSV), turns it into a cell
 * grid and lets the first profile whose header fingerprint matches parse it.
 */
class ProfileStatementReader(
    private val profiles: List<StatementProfile>,
) : StatementReader {

    override fun read(file: PickedFile): Result<ParsedStatement, ImportError> {
        if (file.bytes.isEmpty()) return Result.Error(ImportError.EMPTY_FILE)
        val grid: List<List<Cell?>> = try {
            when {
                isOle2(file.bytes) -> Biff8Workbook.readFirstSheet(file.bytes).rows
                isZip(file.bytes) -> return Result.Error(ImportError.UNSUPPORTED_FORMAT) // .xlsx arrives later
                else -> CsvGridReader.read(file.bytes)
            }
        } catch (_: XlsFormatException) {
            return Result.Error(ImportError.UNREADABLE_FILE)
        } catch (_: RuntimeException) {
            return Result.Error(ImportError.UNREADABLE_FILE)
        }
        if (grid.isEmpty()) return Result.Error(ImportError.EMPTY_FILE)

        for (profile in profiles) {
            val header = profile.findHeader(grid) ?: continue
            val parsed = profile.parse(grid, header)
            if (parsed.rows.isEmpty() && parsed.warnings.isEmpty()) return Result.Error(ImportError.EMPTY_FILE)
            return Result.Success(
                ParsedStatement(
                    fileName = file.name,
                    source = profile.source,
                    rows = parsed.rows,
                    warnings = parsed.warnings,
                ),
            )
        }
        return Result.Error(ImportError.UNSUPPORTED_FORMAT)
    }

    private fun isOle2(b: ByteArray) = b.size >= 8 && b[0] == 0xD0.toByte() && b[1] == 0xCF.toByte() && b[2] == 0x11.toByte() && b[3] == 0xE0.toByte()
    private fun isZip(b: ByteArray) = b.size >= 4 && b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte()
}
