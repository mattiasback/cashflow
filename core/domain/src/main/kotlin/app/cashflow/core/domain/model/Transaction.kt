package app.cashflow.core.domain.model

import kotlinx.datetime.LocalDate

/** sha256 of the raw ledger row; stable even when the normalizer improves. */
@JvmInline
value class TxId(val value: String)

@JvmInline
value class ImportId(val value: String)

enum class TransferKind {
    NONE,

    /** Between two of your own transaction accounts: neutral. */
    INTERNAL,

    /** Money moved to a savings or investment account: positive savings. */
    TO_SAVINGS,

    /** Money moved from a savings or investment account: negative savings. */
    FROM_SAVINGS,

    /** A transfer whose other leg is never imported, e.g. a partner's account. */
    LONE_LEG,
}

/** Why a row has its category. Shown as "Så kategoriserades den". */
enum class CategorizedBy {
    TRANSFER,
    MEMORY,
    USER_RULE,
    BUILT_IN_RULE,
    MANUAL,

    /** A row inside a savings or investment account, categorized by its movement type. */
    SAVINGS_EXPORT,
    NONE,
}

enum class TransactionStatus {
    AUTO,
    TO_REVIEW,
    MANUAL,
}

data class Transaction(
    val id: TxId,
    val accountId: AccountId,
    val importId: ImportId,
    val bookingDate: LocalDate,
    val transactionDate: LocalDate,
    /** Signed from the account's point of view. */
    val amount: Money,
    val rawText: String,
    val normalizedText: String,
    val balanceAfter: Money?,
    val categoryId: CategoryId,
    val categorizedBy: CategorizedBy,
    val transferKind: TransferKind,
    /** The matched other leg of a transfer, when found. */
    val transferPartnerId: TxId? = null,
    /** Set when the user marked a lone leg as a household transfer. */
    val loneLegConfirmed: Boolean = false,
    /** Position in the source file, used to keep same-day order stable. */
    val fileOrder: Int = 0,
    /** Copied from the account at import time so totals need no join. */
    val countsInCashFlow: Boolean = true,
    /** What a savings or investment row is; null for bank rows. */
    val movement: Movement? = null,
    /** "Dölj": kept and listed, but left out of every total. */
    val hidden: Boolean = false,
    /** "Möjlig dubblett": an earlier row this one looks like, for sources without a balance. */
    val duplicateOf: TxId? = null,
    /** The user said it is not a duplicate, so it is never flagged again. */
    val duplicateChecked: Boolean = false,
) {
    val status: TransactionStatus
        get() = when {
            categorizedBy == CategorizedBy.MANUAL -> TransactionStatus.MANUAL
            duplicateOf != null && !duplicateChecked -> TransactionStatus.TO_REVIEW
            // Rows inside savings never count in cash flow, so they never wait for a decision.
            !countsInCashFlow -> TransactionStatus.AUTO
            transferKind == TransferKind.LONE_LEG && !loneLegConfirmed -> TransactionStatus.TO_REVIEW
            // The user marked it as a household transfer, so it was set by hand.
            transferKind == TransferKind.LONE_LEG -> TransactionStatus.MANUAL
            categorizedBy == CategorizedBy.NONE -> TransactionStatus.TO_REVIEW
            else -> TransactionStatus.AUTO
        }

    /** True when the row is a possible duplicate the user has not decided on yet. */
    val isPossibleDuplicate: Boolean get() = duplicateOf != null && !duplicateChecked

    /** Periods use the date you spent the money. */
    val period: Period get() = Period.of(transactionDate)
}
