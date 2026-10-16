package app.cashflow.feature.importer.domain

import app.cashflow.core.domain.ImportError
import app.cashflow.core.domain.Result
import app.cashflow.core.domain.model.CsvProfile
import app.cashflow.core.domain.model.ParsedStatement

/** What a column of an unknown file holds. */
enum class ColumnRole {
    DATE,
    TRANSACTION_DATE,
    TEXT,
    AMOUNT,
    AMOUNT_IN,
    AMOUNT_OUT,
    BALANCE,
    IGNORE,
}

/** One column as found in the file, with the app's guess and how sure it is. */
data class ColumnGuess(
    val index: Int,
    val header: String,
    val sample: String,
    val role: ColumnRole,
    /** True when the header named the role; false when only the values suggested it. */
    val sure: Boolean,
)

/** An unknown file opened for "Mappa kolumner": its shape and the app's first guess. */
data class MappingDraft(
    val fileName: String,
    val fingerprint: String,
    val headerRow: Int,
    val columns: List<ColumnGuess>,
    val rowCount: Int,
    val separator: Char?,
    val decimalComma: Boolean,
    /** True when most amounts are positive, as when a bank lists money out without a sign. */
    val looksUnsigned: Boolean,
)

/** Reads unknown files with a user-made column mapping. Implemented in :feature:import:data. */
interface ColumnMapper {
    fun inspect(file: PickedFile): Result<MappingDraft, ImportError>

    /** Parses the file with [profile]; used for the live preview and by the saved profile. */
    fun parse(file: PickedFile, profile: CsvProfile): Result<ParsedStatement, ImportError>
}
