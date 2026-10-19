package app.cashflow.core.categorization

import app.cashflow.core.domain.model.AccountId
import app.cashflow.core.domain.model.CategorizedBy
import app.cashflow.core.domain.model.CategoryId
import app.cashflow.core.domain.model.CategoryRule
import app.cashflow.core.domain.model.MerchantMemory
import app.cashflow.core.domain.model.Money
import app.cashflow.core.domain.model.Movement
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
    /** Savings categories (kind SAVINGS), so a rule can refine a savings transfer. */
    internal val savingsCategories: Set<CategoryId> = DEFAULT_SAVINGS,
) {
    internal val memory: Map<String, CategoryId> = memories.associate { it.payeeKey to it.categoryId }
    internal val user = RuleEngine(userRules)
    internal val builtIn = RuleEngine(builtInRules)

    private companion object {
        val DEFAULT_SAVINGS = setOf(
            CategoryId.FUNDS, CategoryId.BUFFER, CategoryId.PENSION, CategoryId.AMORTIZATION, CategoryId.SAVINGS_TRANSFER, CategoryId("barnspar"),
        )
    }
}

/** One row as the categorizer sees it. */
data class CategorizationInput(
    val rawText: String,
    val amount: Money,
    val transferKind: TransferKind,
    val loneLegConfirmed: Boolean,
    val accountId: AccountId? = null,
    /** False for rows inside savings and investment accounts. */
    val countsInCashFlow: Boolean = true,
    val movement: Movement? = null,
    /** For a paired savings transfer: the category its savings account stands for. */
    val savingsCategory: CategoryId? = null,
)

/**
 * Pipeline, first hit wins: rows inside savings by their movement → transfer kind →
 * remembered payee ("Kom ihåg") → user rule → built-in rule → "Ej kategoriserat". Each
 * decision records why, for "Så kategoriserades den".
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
    ): CategoryDecision = categorize(CategorizationInput(rawText, amount, transferKind, loneLegConfirmed), context)

    fun categorize(input: CategorizationInput, context: CategorizationContext): CategoryDecision {
        if (!input.countsInCashFlow && input.movement != null) return insideSavings(input.movement)
        val key = normalizer.key(input.rawText)
        val upper = input.rawText.uppercase()
        when (input.transferKind) {
            TransferKind.INTERNAL -> return CategoryDecision(CategoryId.TRANSFER, CategorizedBy.TRANSFER)
            TransferKind.TO_SAVINGS, TransferKind.FROM_SAVINGS -> {
                // A choice the user made for this payee ("Kom ihåg", e.g. Överföring) wins over the account type.
                context.memory[key]?.let { return CategoryDecision(it, CategorizedBy.MEMORY) }
                // The savings account says what kind of saving it is; else a savings rule may; else "Övrigt sparande".
                input.savingsCategory?.let { return CategoryDecision(it, CategorizedBy.TRANSFER) }
                val rule = context.user.firstMatch(key, upper, input.amount, input.accountId)
                    ?: context.builtIn.firstMatch(key, upper, input.amount, input.accountId)
                val category = rule?.categoryId?.takeIf { it in context.savingsCategories } ?: CategoryId.SAVINGS_TRANSFER
                return CategoryDecision(category, CategorizedBy.TRANSFER)
            }
            TransferKind.LONE_LEG -> if (input.loneLegConfirmed) return CategoryDecision(CategoryId.TRANSFER, CategorizedBy.TRANSFER)
            TransferKind.NONE -> Unit
        }
        context.memory[key]?.let { return CategoryDecision(it, CategorizedBy.MEMORY) }
        context.user.firstMatch(key, upper, input.amount, input.accountId)?.let { return CategoryDecision(it.categoryId, CategorizedBy.USER_RULE) }
        context.builtIn.firstMatch(key, upper, input.amount, input.accountId)?.let { return CategoryDecision(it.categoryId, CategorizedBy.BUILT_IN_RULE) }
        return CategoryDecision(CategoryId.UNCATEGORIZED, CategorizedBy.NONE)
    }

    private fun insideSavings(movement: Movement): CategoryDecision = when (movement) {
        Movement.DEPOSIT, Movement.WITHDRAWAL, Movement.INTERNAL_IN, Movement.INTERNAL_OUT ->
            CategoryDecision(CategoryId.TRANSFER, CategorizedBy.SAVINGS_EXPORT)
        Movement.TRADE -> CategoryDecision(CategoryId.TRADES, CategorizedBy.SAVINGS_EXPORT)
        Movement.RETURN -> CategoryDecision(CategoryId.SAVINGS_RETURN, CategorizedBy.SAVINGS_EXPORT)
        Movement.COST -> CategoryDecision(CategoryId.SAVINGS_COST, CategorizedBy.SAVINGS_EXPORT)
    }
}
