package app.cashflow.core.importengine

import app.cashflow.core.categorization.CategorizationContext
import app.cashflow.core.categorization.CategorizationInput
import app.cashflow.core.categorization.Categorizer
import app.cashflow.core.domain.DataError
import app.cashflow.core.domain.EmptyResult
import app.cashflow.core.domain.Result
import app.cashflow.core.domain.datasource.AccountLocalDataSource
import app.cashflow.core.domain.datasource.Classification
import app.cashflow.core.domain.datasource.MerchantMemoryLocalDataSource
import app.cashflow.core.domain.datasource.RuleCatalog
import app.cashflow.core.domain.datasource.RuleLocalDataSource
import app.cashflow.core.domain.datasource.TransactionLocalDataSource
import app.cashflow.core.domain.model.AccountType
import app.cashflow.core.domain.model.CategorizedBy
import app.cashflow.core.domain.model.CategoryId
import app.cashflow.core.domain.model.Transaction
import app.cashflow.core.domain.model.TransferKind

/**
 * Re-runs transfer matching and categorization over every stored row. Called after each
 * import (the other leg of a transfer may arrive later) and after "Kom ihåg". Rows the user
 * categorized by hand keep their category.
 */
class ReclassifyUseCase(
    private val transactions: TransactionLocalDataSource,
    private val accounts: AccountLocalDataSource,
    private val memories: MerchantMemoryLocalDataSource,
    private val rules: RuleLocalDataSource,
    private val catalog: RuleCatalog,
    private val matcher: TransferMatcher,
    private val categorizer: Categorizer,
) {

    suspend operator fun invoke(): EmptyResult<DataError.Local> {
        val all = when (val r = transactions.getAll()) {
            is Result.Error -> return r
            is Result.Success -> r.data
        }
        val accountList = when (val r = accounts.getAccounts()) {
            is Result.Error -> return r
            is Result.Success -> r.data
        }
        val memoryList = when (val r = memories.getAll()) {
            is Result.Error -> return r
            is Result.Success -> r.data
        }
        val userRules = when (val r = rules.getUserRules()) {
            is Result.Error -> return r
            is Result.Success -> r.data
        }
        val context = CategorizationContext(memoryList, userRules, catalog.builtInRules())
        val transfers = matcher.match(all, accountList)
        val byId = all.associateBy { it.id }
        val typeOf = accountList.associate { it.id to it.type }

        val updates = all.mapNotNull { tx ->
            val partnerType = transfers[tx.id]?.partner?.let { byId[it] }?.let { typeOf[it.accountId] }
            val next = classify(tx, transfers[tx.id], savingsCategoryOf(partnerType), context)
            if (next == tx.toClassification()) null else next
        }
        if (updates.isEmpty()) return Result.Success(Unit)
        return transactions.updateClassifications(updates)
    }

    private fun classify(tx: Transaction, transfer: TransferResult?, savingsCategory: CategoryId?, context: CategorizationContext): Classification {
        if (tx.categorizedBy == CategorizedBy.MANUAL) return tx.toClassification()
        val kind = transfer?.kind ?: TransferKind.NONE
        val decision = categorizer.categorize(
            CategorizationInput(
                rawText = tx.rawText,
                amount = tx.amount,
                transferKind = kind,
                loneLegConfirmed = tx.loneLegConfirmed,
                accountId = tx.accountId,
                countsInCashFlow = tx.countsInCashFlow,
                movement = tx.movement,
                savingsCategory = savingsCategory,
            ),
            context,
        )
        return Classification(
            id = tx.id,
            categoryId = decision.categoryId,
            categorizedBy = decision.categorizedBy,
            transferKind = kind,
            transferPartnerId = transfer?.partner,
            loneLegConfirmed = tx.loneLegConfirmed,
        )
    }
}

/** The category a savings account stands for, so its bank leg lands in the right savings category. */
internal fun savingsCategoryOf(type: AccountType?): CategoryId? = when (type) {
    AccountType.SAVINGS -> CategoryId.BUFFER
    AccountType.ISK, AccountType.KF -> CategoryId.FUNDS
    AccountType.PENSION -> CategoryId.PENSION
    else -> null
}

fun Transaction.toClassification() = Classification(
    id = id,
    categoryId = categoryId,
    categorizedBy = categorizedBy,
    transferKind = transferKind,
    transferPartnerId = transferPartnerId,
    loneLegConfirmed = loneLegConfirmed,
)
