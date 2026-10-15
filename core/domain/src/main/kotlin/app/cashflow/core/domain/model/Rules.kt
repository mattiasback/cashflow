package app.cashflow.core.domain.model

/** "Kom ihåg": every row whose normalized payee equals [payeeKey] gets [categoryId]. */
data class MerchantMemory(
    val payeeKey: String,
    val categoryId: CategoryId,
)

enum class RuleMatch {
    CONTAINS,
    STARTS_WITH,
    EQUALS,
}

/** A text rule. Built-in rules ship as JSON; user rules live in Room. */
data class CategoryRule(
    val id: String,
    val pattern: String,
    val match: RuleMatch,
    val categoryId: CategoryId,
    /** Only apply to outflows (negative) or inflows (positive); null = both. */
    val sign: Int? = null,
    val isBuiltIn: Boolean,
)
