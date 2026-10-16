package app.cashflow.core.domain.model

import kotlin.time.Instant

/** "Kom ihåg": every row whose normalized payee equals [payeeKey] gets [categoryId]. */
data class MerchantMemory(
    val payeeKey: String,
    val categoryId: CategoryId,
    val createdAt: Instant? = null,
)

enum class RuleMatch {
    CONTAINS,
    STARTS_WITH,
    EQUALS,
}

/** Where a user rule came from, shown in Kategoriregler. */
enum class RuleOrigin {
    CREATED,

    /** "Ändra för mig" on a built-in rule. */
    OVERRIDE,
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
    /** Only rows on this account; null = every account. */
    val accountId: AccountId? = null,
    /** Only amounts whose size is at least / at most this; null = no limit. */
    val minAmount: Money? = null,
    val maxAmount: Money? = null,
    val origin: RuleOrigin = RuleOrigin.CREATED,
    val createdAt: Instant? = null,
)
