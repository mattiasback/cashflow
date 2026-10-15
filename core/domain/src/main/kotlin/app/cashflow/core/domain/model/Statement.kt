package app.cashflow.core.domain.model

import kotlinx.datetime.LocalDate

/** Where a statement came from. Bank sync (step 6) will be another source. */
enum class ImportSource {
    LANSFORSAKRINGAR_XLS,
    CSV,
}

/** One booked row as the bank wrote it, before any account or category is known. */
data class StatementRow(
    val bookingDate: LocalDate,
    /** Falls back to [bookingDate] when the bank leaves it empty. */
    val transactionDate: LocalDate,
    val rawText: String,
    val amount: Money,
    val balanceAfter: Money?,
)

/** A row the reader could not use. It becomes a warning on the receipt, not a failed file. */
data class RowWarning(
    val rowNumber: Int,
    val reason: RowWarningReason,
)

enum class RowWarningReason {
    MISSING_DATE,
    MISSING_AMOUNT,
    UNREADABLE_DATE,
    UNREADABLE_AMOUNT,
    PENDING_RESERVATION,
}

/** A file parsed by a known profile. Rows are always oldest first. */
data class ParsedStatement(
    val fileName: String,
    val source: ImportSource,
    val rows: List<StatementRow>,
    val warnings: List<RowWarning>,
)
