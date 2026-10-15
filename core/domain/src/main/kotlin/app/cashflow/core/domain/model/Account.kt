package app.cashflow.core.domain.model

import kotlinx.datetime.LocalDate

@JvmInline
value class AccountId(val value: String)

enum class AccountType {
    TRANSACTION,
    SAVINGS,
    ISK,
    KF,
    PENSION,
    OTHER;

    /** Only money on everyday accounts counts in cash flow. */
    val countsInCashFlow: Boolean get() = this == TRANSACTION
    val isSavingsLike: Boolean get() = this == SAVINGS || this == ISK || this == KF
}

data class Account(
    val id: AccountId,
    val name: String,
    val type: AccountType,
    /** Clearing number prefix, e.g. "9550" for Avanza; null when unknown. */
    val clearingPrefix: String? = null,
    /** Account number with dots stripped, learned from transfer texts; never logged. */
    val accountNumber: String? = null,
)

/** A known balance of an account after a booked row. Feeds account inference. */
data class BalanceState(
    val accountId: AccountId,
    val date: LocalDate,
    val balance: Money,
)
