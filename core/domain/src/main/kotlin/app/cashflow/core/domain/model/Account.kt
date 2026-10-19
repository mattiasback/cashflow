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

    /** Everything that is not an everyday account: savings, investments, pension and other. */
    val holdsSavings: Boolean get() = this != TRANSACTION

    /** Pension is paid by the employer, so it never counts as the household's own savings. */
    val countsAsHouseholdSavings: Boolean get() = isSavingsLike

    /** Investment accounts have a market value the exports do not carry; you enter it. */
    val hasMarketValue: Boolean get() = this == ISK || this == KF

    companion object {
        /**
         * The type from the account's name in a savings export: "ISK …" → ISK, "KF …" →
         * kapitalförsäkring, "Sparkonto …" → savings account, "… Tjänstepension" → pension.
         * Null when the name says nothing ("Burken"), so the user is asked once.
         */
        fun fromName(name: String): AccountType? {
            val n = name.trim().lowercase()
            return when {
                n.startsWith("isk") || n.contains(" isk") || n.contains("investeringssparkonto") -> ISK
                n.startsWith("kf ") || n == "kf" || n.contains("kapitalförsäkring") || n.contains("kapitalforsakring") -> KF
                n.contains("pension") -> PENSION
                n.startsWith("sparkonto") || n.contains("sparkonto") || n.contains("buffert") -> SAVINGS
                else -> null
            }
        }
    }
}

data class Account(
    val id: AccountId,
    val name: String,
    val type: AccountType,
    /** Clearing number prefix, e.g. "9550" for Avanza; null when unknown. */
    val clearingPrefix: String? = null,
    /** Account number with dots stripped, learned from transfer texts; never logged. */
    val accountNumber: String? = null,
    /** "Lysa", "Avanza" … for accounts named by a savings export; null for bank accounts. */
    val institution: String? = null,
    /** The export's own key for the account ([StatementAccount.key]); null for bank accounts. */
    val externalKey: String? = null,
    /** Market value you entered for an ISK or KF, since the exports have none. */
    val marketValue: Money? = null,
    val marketValueDate: LocalDate? = null,
)

/** A known balance of an account after a booked row. Feeds account inference. */
data class BalanceState(
    val accountId: AccountId,
    val date: LocalDate,
    val balance: Money,
)
