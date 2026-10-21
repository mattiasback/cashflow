package app.cashflow.core.domain.model

import kotlinx.datetime.LocalDate

/** Where a statement came from. Bank sync (step 6) will be another source. */
enum class ImportSource {
    LANSFORSAKRINGAR_XLS,
    CSV,
    LYSA_XLS,
    AVANZA_CSV,

    /** An unknown file read through a column mapping the user saved. */
    CSV_MAPPED,

    /** Rows fetched from the bank (step 8), not read from a file. */
    BANK_SYNC,
}

/**
 * The account a row belongs to, when the file names it. Savings and investment exports
 * (Lysa, Avanza) hold several accounts in one file; bank statements hold one unnamed account
 * that is inferred from the balance chain instead.
 */
data class StatementAccount(
    /** Stable within the institution, e.g. "lysa:<account id>" or "avanza:ISK Exempel". */
    val key: String,
    val name: String,
    val institution: String,
)

/**
 * What a row on a savings or investment account is. Only money crossing the boundary between
 * the bank and the savings account ([DEPOSIT], [WITHDRAWAL]) touches cash flow, through its
 * bank leg. Everything else stays inside savings: moves between savings accounts, trades,
 * return (interest, dividends) and the cost of saving (fees, taxes).
 */
enum class Movement {
    DEPOSIT,
    WITHDRAWAL,
    INTERNAL_IN,
    INTERNAL_OUT,
    TRADE,
    RETURN,
    COST;

    val isBoundary: Boolean get() = this == DEPOSIT || this == WITHDRAWAL
    val isInternal: Boolean get() = this == INTERNAL_IN || this == INTERNAL_OUT
}

/** One booked row as the bank wrote it, before any account or category is known. */
data class StatementRow(
    val bookingDate: LocalDate,
    /** Falls back to [bookingDate] when the bank leaves it empty. */
    val transactionDate: LocalDate,
    val rawText: String,
    val amount: Money,
    val balanceAfter: Money?,
    /** Set by multi-account exports; null means "the statement's one account". */
    val account: StatementAccount? = null,
    /** Set for rows on savings and investment accounts. */
    val movement: Movement? = null,
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
    UNKNOWN_TYPE,
}

/** A file parsed by a known profile. Rows are always oldest first. */
data class ParsedStatement(
    val fileName: String,
    val source: ImportSource,
    val rows: List<StatementRow>,
    val warnings: List<RowWarning>,
) {
    /** True when the file names its accounts, so no balance-chain inference is needed. */
    val hasNamedAccounts: Boolean get() = rows.isNotEmpty() && rows.all { it.account != null }
}
