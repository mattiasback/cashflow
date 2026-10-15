package app.cashflow.core.categorization

import app.cashflow.core.domain.model.CategorizedBy
import app.cashflow.core.domain.model.CategoryId
import app.cashflow.core.domain.model.CategoryRule
import app.cashflow.core.domain.model.MerchantMemory
import app.cashflow.core.domain.model.Money
import app.cashflow.core.domain.model.TransferKind

data class CategoryDecision(
    val categoryId: CategoryId,
    val categorizedBy: CategorizedBy,
)

/** Everything the categorizer knows for one run; build it once per import. */
class CategorizationContext(
    memories: List<MerchantMemory>,
    userRules: List<CategoryRule>,
    builtInRules: List<CategoryRule>,
) {
    internal val memory: Map<String, CategoryId> = memories.associate { it.payeeKey to it.categoryId }
    internal val user = RuleEngine(userRules)
    internal val builtIn = RuleEngine(builtInRules)
}

/**
 * Pipeline, first hit wins: transfer kind → remembered payee ("Kom ihåg") → user rule →
 * built-in rule → "Ej kategoriserat". Each decision records why, for "Så kategoriserades den".
 */
class Categorizer(
    private val normalizer: PayeeNormalizer,
) {

    fun categorize(
        rawText: String,
        amount: Money,
        transferKind: TransferKind,
        loneLegConfirmed: Boolean,
        context: CategorizationContext,
    ): CategoryDecision {
        when (transferKind) {
            TransferKind.INTERNAL -> return CategoryDecision(CategoryId.TRANSFER, CategorizedBy.TRANSFER)
            TransferKind.TO_SAVINGS, TransferKind.FROM_SAVINGS ->
                return CategoryDecision(CategoryId.SAVINGS_TRANSFER, CategorizedBy.TRANSFER)
            TransferKind.LONE_LEG -> if (loneLegConfirmed) return CategoryDecision(CategoryId.TRANSFER, CategorizedBy.TRANSFER)
            TransferKind.NONE -> Unit
        }
        val key = normalizer.key(rawText)
        context.memory[key]?.let { return CategoryDecision(it, CategorizedBy.MEMORY) }
        val upper = rawText.uppercase()
        context.user.firstMatch(key, upper, amount)?.let { return CategoryDecision(it.categoryId, CategorizedBy.USER_RULE) }
        context.builtIn.firstMatch(key, upper, amount)?.let { return CategoryDecision(it.categoryId, CategorizedBy.BUILT_IN_RULE) }
        return CategoryDecision(CategoryId.UNCATEGORIZED, CategorizedBy.NONE)
    }
}
