package app.cashflow.core.importengine

import app.cashflow.core.domain.DataError
import app.cashflow.core.domain.EmptyResult
import app.cashflow.core.domain.Result
import app.cashflow.core.domain.datasource.TransactionLocalDataSource
import app.cashflow.core.domain.model.CategorizedBy
import app.cashflow.core.domain.model.ImportId
import app.cashflow.core.domain.model.Transaction
import app.cashflow.core.domain.model.TxId
import kotlin.time.Clock

/**
 * Undo and redo of a whole import. The rows are set aside in one Room transaction by import
 * id, then transfers are matched again, so totals and pairs are exactly as before the import.
 */
class UndoImportUseCase(
    private val transactions: TransactionLocalDataSource,
    private val reclassify: ReclassifyUseCase,
    private val clock: Clock = Clock.System,
) {

    /** Rows of the import the user categorized or marked by hand; their choices go with the undo. */
    fun editedRows(rows: List<Transaction>, importId: ImportId): Int =
        rows.count { it.importId == importId && (it.categorizedBy == CategorizedBy.MANUAL || it.loneLegConfirmed || it.hidden) }

    suspend fun undo(ids: Collection<ImportId>): EmptyResult<DataError.Local> {
        val at = clock.now()
        for (id in ids) {
            val r = transactions.undoImport(id, at)
            if (r is Result.Error) return r
        }
        return reclassify()
    }

    suspend fun redo(id: ImportId): EmptyResult<DataError.Local> {
        val r = transactions.redoImport(id)
        if (r is Result.Error) return r
        return reclassify()
    }
}

/**
 * "Dölj" keeps a row but leaves it out of every total. "Ta bort" deletes it and remembers its
 * id, so a re-import of the same file does not bring it back; its transfer partner is matched
 * again.
 */
class HideDeleteUseCase(
    private val transactions: TransactionLocalDataSource,
    private val reclassify: ReclassifyUseCase,
    private val clock: Clock = Clock.System,
) {
    suspend fun setHidden(id: TxId, hidden: Boolean): EmptyResult<DataError.Local> = transactions.setHidden(listOf(id), hidden)

    suspend fun delete(id: TxId): EmptyResult<DataError.Local> {
        val r = transactions.delete(listOf(id), clock.now())
        if (r is Result.Error) return r
        return reclassify()
    }
}
