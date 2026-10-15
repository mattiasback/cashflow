package app.cashflow.core.importengine

import app.cashflow.core.categorization.PayeeNormalizer
import app.cashflow.core.domain.DataError
import app.cashflow.core.domain.EmptyResult
import app.cashflow.core.domain.Result
import app.cashflow.core.domain.datasource.MerchantMemoryLocalDataSource
import app.cashflow.core.domain.datasource.TransactionLocalDataSource
import app.cashflow.core.domain.model.CategorizedBy
import app.cashflow.core.domain.model.CategoryId
import app.cashflow.core.domain.model.MerchantMemory
import app.cashflow.core.domain.model.Transaction
import app.cashflow.core.domain.model.TransferKind

/**
 * Sets a category on rows. With "Kom ihåg" the payee is remembered, so every matching row
 * (now and in future imports) gets it; without it only the chosen rows change, as MANUAL.
 */
class CategorizeUseCase(
    private val transactions: TransactionLocalDataSource,
    private val memories: MerchantMemoryLocalDataSource,
    private val normalizer: PayeeNormalizer,
    private val reclassify: ReclassifyUseCase,
) {

    suspend fun categorize(rows: List<Transaction>, categoryId: CategoryId, remember: Boolean): EmptyResult<DataError.Local> {
        if (rows.isEmpty()) return Result.Success(Unit)
        if (remember) {
            rows.map { normalizer.key(it.rawText) }.distinct().forEach { key ->
                val r = memories.remember(MerchantMemory(key, categoryId))
                if (r is Result.Error) return r
            }
            // Rows the user had set by hand for this payee follow the new decision too.
            val manual = rows.filter { it.categorizedBy == CategorizedBy.MANUAL }
            if (manual.isNotEmpty()) {
                val r = transactions.updateClassifications(manual.map { it.toClassification().copy(categoryId = categoryId, categorizedBy = CategorizedBy.MEMORY) })
                if (r is Result.Error) return r
            }
            return reclassify()
        }
        return transactions.updateClassifications(
            rows.map {
                it.toClassification().copy(
                    categoryId = categoryId,
                    categorizedBy = CategorizedBy.MANUAL,
                    // Choosing a real category means the row is not a household transfer.
                    transferKind = if (it.transferKind == TransferKind.LONE_LEG) TransferKind.NONE else it.transferKind,
                )
            },
        )
    }

    /** Marks lone legs (transfers to a partner's account) as neutral household transfers. */
    suspend fun confirmHouseholdTransfer(rows: List<Transaction>): EmptyResult<DataError.Local> =
        transactions.updateClassifications(
            rows.map {
                it.toClassification().copy(
                    categoryId = CategoryId.TRANSFER,
                    categorizedBy = CategorizedBy.TRANSFER,
                    transferKind = TransferKind.LONE_LEG,
                    loneLegConfirmed = true,
                )
            },
        )

    /** Payee key for grouping the review queue, shared with merchant memory. */
    fun payeeKey(tx: Transaction): String = normalizer.key(tx.rawText)
}
