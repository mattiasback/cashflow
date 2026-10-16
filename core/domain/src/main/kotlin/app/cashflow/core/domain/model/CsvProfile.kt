package app.cashflow.core.domain.model

import kotlin.time.Instant

/**
 * A column mapping the user saved for a file format the app did not know. It is matched by
 * the header fingerprint, so the next file from the same bank imports without asking.
 * Columns are zero-based indexes into the header row.
 */
data class CsvProfile(
    val id: String,
    val name: String,
    /** Lower-case header cells with spaces and punctuation removed, joined by '|'. */
    val fingerprint: String,
    val headerRow: Int,
    val dateColumn: Int,
    val textColumn: Int,
    /** One signed amount column, or separate in/out columns. */
    val amountColumn: Int?,
    val inColumn: Int?,
    val outColumn: Int?,
    val transactionDateColumn: Int? = null,
    val balanceColumn: Int? = null,
    val createdAt: Instant,
    val lastUsedAt: Instant? = null,
)
