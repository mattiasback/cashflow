package app.cashflow.core.domain.model

@JvmInline
value class CategoryId(val value: String) {
    companion object {
        val UNCATEGORIZED = CategoryId("okat")
        val TRANSFER = CategoryId("overforing")
        val SAVINGS_TRANSFER = CategoryId("sparande")
    }
}

/** How a category behaves in the cash-flow calculation. */
enum class CategoryKind {
    INCOME,
    FIXED,
    VARIABLE,
    SAVINGS,
    TRANSFER,
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
