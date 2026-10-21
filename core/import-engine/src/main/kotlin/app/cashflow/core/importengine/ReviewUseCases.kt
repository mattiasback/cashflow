package app.cashflow.core.importengine

import app.cashflow.core.categorization.PayeeNormalizer
import app.cashflow.core.domain.DataError
import app.cashflow.core.domain.EmptyResult
import app.cashflow.core.domain.Result
import app.cashflow.core.domain.datasource.Classification
import app.cashflow.core.domain.datasource.MerchantMemoryLocalDataSource
import app.cashflow.core.domain.datasource.TransactionLocalDataSource
import app.cashflow.core.domain.model.CategorizedBy
import app.cashflow.core.domain.model.CategoryId
import app.cashflow.core.domain.model.MerchantMemory
import app.cashflow.core.domain.model.Transaction
import app.cashflow.core.domain.model.TransferKind

/**
 * What a review decision changed, so "Ångra" can put it back exactly: the classification of
 * every row before the change, and each touched payee's remembered category (null = none).
 */
class CategorizeUndo internal constructor(
    internal val before: List<Classification>,
    internal val memoriesBefore: Map<String, CategoryId?>,
)

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

    suspend fun categorize(rows: List<Transaction>, categoryId: CategoryId, remember: Boolean): Result<CategorizeUndo, DataError.Local> {
        val undo = snapshot(if (remember) rows.map { normalizer.key(it.rawText) }.toSet() else emptySet())
        if (undo is Result.Error) return undo
        val token = (undo as Result.Success).data
        if (rows.isEmpty()) return Result.Success(token)
        val result = if (remember) {
            rememberPayees(rows, categoryId)
        } else {
            transactions.updateClassifications(
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
        return when (result) {
            is Result.Error -> result
            is Result.Success -> Result.Success(token)
        }
    }

    /** Marks lone legs (transfers to a partner's account) as neutral household transfers. */
    suspend fun confirmHouseholdTransfer(rows: List<Transaction>): Result<CategorizeUndo, DataError.Local> {
        val undo = snapshot(emptySet())
        if (undo is Result.Error) return undo
        val r = transactions.updateClassifications(
            rows.map {
                it.toClassification().copy(
                    categoryId = CategoryId.TRANSFER,
                    categorizedBy = CategorizedBy.TRANSFER,
                    transferKind = TransferKind.LONE_LEG,
                    loneLegConfirmed = true,
                )
            },
        )
        return if (r is Result.Error) r else undo
    }

    /** Puts rows and remembered payees back exactly as they were before the decision. */
    suspend fun undo(token: CategorizeUndo): EmptyResult<DataError.Local> {
        for ((key, previous) in token.memoriesBefore) {
            val r = if (previous == null) memories.forget(key) else memories.remember(MerchantMemory(key, previous))
            if (r is Result.Error) return r
        }
        val current = when (val r = transactions.getAll()) {
            is Result.Error -> return r
            is Result.Success -> r.data.associate { it.id to it.toClassification() }
        }
        val changed = token.before.filter { current[it.id] != null && current[it.id] != it }
        return if (changed.isEmpty()) Result.Success(Unit) else transactions.updateClassifications(changed)
    }

    /** Payee key for grouping the review queue, shared with merchant memory. */
    fun payeeKey(tx: Transaction): String = normalizer.key(tx.rawText)

    private suspend fun rememberPayees(rows: List<Transaction>, categoryId: CategoryId): EmptyResult<DataError.Local> {
        val keys = rows.map { normalizer.key(it.rawText) }.toSet()
        keys.forEach { key ->
            val r = memories.remember(MerchantMemory(key, categoryId, kotlin.time.Clock.System.now()))
            if (r is Result.Error) return r
        }
        // Earlier decisions for this payee follow the new one too, on every row, not only the
        // chosen ones: rows set by hand, and rows marked as a household transfer (a confirmed
        // lone leg ignores rules, so without this it kept "Överföring" after choosing e.g. Lån).
        val all = when (val r = transactions.getAll()) {
            is Result.Error -> return r
            is Result.Success -> r.data
        }
        val toTransfer = categoryId == CategoryId.TRANSFER
        val earlier = all.filter { tx ->
            normalizer.key(tx.rawText) in keys &&
                (tx.categorizedBy == CategorizedBy.MANUAL || (tx.transferKind == TransferKind.LONE_LEG && tx.loneLegConfirmed))
        }
        if (earlier.isNotEmpty()) {
            val updates = earlier.map { tx ->
                val loneLeg = tx.transferKind == TransferKind.LONE_LEG
                tx.toClassification().copy(
                    categoryId = categoryId,
                    categorizedBy = CategorizedBy.MEMORY,
                    transferKind = if (loneLeg && !toTransfer) TransferKind.NONE else tx.transferKind,
                    loneLegConfirmed = loneLeg && toTransfer,
                )
            }
            val r = transactions.updateClassifications(updates)
            if (r is Result.Error) return r
        }
        return reclassify()
    }

    private suspend fun snapshot(payeeKeys: Set<String>): Result<CategorizeUndo, DataError.Local> {
        val all = when (val r = transactions.getAll()) {
            is Result.Error -> return r
            is Result.Success -> r.data
        }
        val remembered = when (val r = memories.getAll()) {
            is Result.Error -> return r
            is Result.Success -> r.data.associate { it.payeeKey to it.categoryId }
        }
        return Result.Success(
            CategorizeUndo(
                before = all.map { it.toClassification() },
                memoriesBefore = payeeKeys.associateWith { remembered[it] },
            ),
        )
    }
}
