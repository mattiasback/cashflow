package app.cashflow.core.importengine

import app.cashflow.core.categorization.PayeeNormalizer
import app.cashflow.core.categorization.RuleEngine
import app.cashflow.core.domain.DataError
import app.cashflow.core.domain.EmptyResult
import app.cashflow.core.domain.Result
import app.cashflow.core.domain.datasource.MerchantMemoryLocalDataSource
import app.cashflow.core.domain.datasource.RuleLocalDataSource
import app.cashflow.core.domain.datasource.TransactionLocalDataSource
import app.cashflow.core.domain.model.CategorizedBy
import app.cashflow.core.domain.model.CategoryId
import app.cashflow.core.domain.model.CategoryRule
import app.cashflow.core.domain.model.Transaction

/** How a rule relates to the rows already imported. */
data class RuleReach(
    /** Rows the rule matches. */
    val matches: Int,
    /** Of those, rows that have another category because you or "Kom ihåg" chose it. */
    val pastWithOtherCategory: Int,
)

/**
 * Kategoriregler: your own rules go before the built-in ones. Saving or deleting a rule
 * re-runs categorization, so every row that is not set by hand follows the rules at once.
 * "Använd på tidigare köp" also moves rows you or "Kom ihåg" had set to something else.
 */
class RuleUseCases(
    private val rules: RuleLocalDataSource,
    private val memories: MerchantMemoryLocalDataSource,
    private val transactions: TransactionLocalDataSource,
    private val normalizer: PayeeNormalizer,
    private val reclassify: ReclassifyUseCase,
) {

    fun reach(rule: CategoryRule, rows: List<Transaction>): RuleReach {
        val matching = matching(rule, rows)
        return RuleReach(
            matches = matching.size,
            pastWithOtherCategory = matching.count { it.categoryId != rule.categoryId && it.categorizedBy.isPersonalChoice() },
        )
    }

    /** How many rows each payee key has; feeds "använd N gånger" for remembered payees. */
    fun usesOfPayee(payeeKey: String, rows: List<Transaction>): Int = rows.count { normalizer.key(it.rawText) == payeeKey }

    suspend fun save(rule: CategoryRule, applyToPast: Boolean): EmptyResult<DataError.Local> {
        val r = rules.upsertRule(rule)
        if (r is Result.Error) return r
        if (applyToPast) {
            val all = when (val a = transactions.getAll()) {
                is Result.Error -> return a
                is Result.Success -> a.data
            }
            val moved = matching(rule, all).filter { it.categorizedBy.isPersonalChoice() && it.categoryId != rule.categoryId }
            if (moved.isNotEmpty()) {
                val u = transactions.updateClassifications(moved.map { it.toClassification().copy(categoryId = rule.categoryId, categorizedBy = CategorizedBy.USER_RULE) })
                if (u is Result.Error) return u
                // Their remembered payees would otherwise win over the rule again.
                moved.map { normalizer.key(it.rawText) }.distinct().forEach { key ->
                    val f = memories.forget(key)
                    if (f is Result.Error) return f
                }
            }
        }
        return reclassify()
    }

    suspend fun delete(id: String): EmptyResult<DataError.Local> {
        val r = rules.deleteRule(id)
        if (r is Result.Error) return r
        return reclassify()
    }

    /** Changes or forgets a remembered payee ("Kom ihåg"). */
    suspend fun updateMemory(payeeKey: String, categoryId: CategoryId?): EmptyResult<DataError.Local> {
        val r = if (categoryId == null) {
            memories.forget(payeeKey)
        } else {
            memories.remember(app.cashflow.core.domain.model.MerchantMemory(payeeKey, categoryId, kotlin.time.Clock.System.now()))
        }
        if (r is Result.Error) return r
        return reclassify()
    }

    private fun matching(rule: CategoryRule, rows: List<Transaction>): List<Transaction> {
        val engine = RuleEngine(listOf(rule))
        return rows.filter { tx ->
            tx.countsInCashFlow && engine.matches(rule, normalizer.key(tx.rawText), tx.rawText.uppercase(), tx.amount, tx.accountId)
        }
    }

    private fun CategorizedBy.isPersonalChoice() = this == CategorizedBy.MANUAL || this == CategorizedBy.MEMORY
}
