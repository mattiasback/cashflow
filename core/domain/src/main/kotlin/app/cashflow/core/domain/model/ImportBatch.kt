package app.cashflow.core.domain.model

import kotlin.time.Instant

data class ImportBatch(
    val id: ImportId,
    val source: ImportSource,
    val fileName: String,
    val accountId: AccountId?,
    val importedAt: Instant,
    val rowCount: Int,
    val newCount: Int,
    val duplicateCount: Int,
    val warnings: List<RowWarning>,
)

/** Result of a batch-atomic insert. */
data class InsertSummary(
    val newCount: Int,
    val duplicateCount: Int,
)
