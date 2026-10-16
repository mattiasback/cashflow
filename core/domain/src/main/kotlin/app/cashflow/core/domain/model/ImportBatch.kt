package app.cashflow.core.domain.model

import kotlin.time.Instant

data class ImportBatch(
    val id: ImportId,
    val source: ImportSource,
    val fileName: String,
    /** The one account of a bank statement; null when the file named several accounts. */
    val accountId: AccountId?,
    val importedAt: Instant,
    val rowCount: Int,
    val newCount: Int,
    val duplicateCount: Int,
    val warnings: List<RowWarning>,
    /** Set when the whole import was undone; its rows are kept aside so it can be redone. */
    val undoneAt: Instant? = null,
)

/** Result of a batch-atomic insert. */
data class InsertSummary(
    val newCount: Int,
    val duplicateCount: Int,
)
