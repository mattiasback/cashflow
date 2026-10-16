package app.cashflow.core.domain.model

@JvmInline
value class CategoryId(val value: String) {
    companion object {
        val UNCATEGORIZED = CategoryId("okat")
        val TRANSFER = CategoryId("overforing")
        val SAVINGS_TRANSFER = CategoryId("sparande")
        val BUFFER = CategoryId("buffert")
        val FUNDS = CategoryId("fonder")
        val PENSION = CategoryId("pension")
        val AMORTIZATION = CategoryId("amortering")
        val HOUSING = CategoryId("boende")

        /** Rows inside savings and investment accounts. */
        val TRADES = CategoryId("vardepapper")
        val SAVINGS_RETURN = CategoryId("avkastning")
        val SAVINGS_COST = CategoryId("sparkostnad")
    }
}

/** How a category behaves in the cash-flow calculation. */
enum class CategoryKind {
    INCOME,
    FIXED,
    VARIABLE,
    SAVINGS,
    TRANSFER,

    /** Inside savings: trades, return and cost. Never part of cash flow or the pickers. */
    INVESTMENT,
    UNCATEGORIZED;

    val isExpense: Boolean get() = this == FIXED || this == VARIABLE
}

data class Category(
    val id: CategoryId,
    /** Swedish display name; the UI localizes built-in ids through resources. */
    val name: String,
    val kind: CategoryKind,
    val sortOrder: Int = 0,
)
