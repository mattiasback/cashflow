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
) {
    val status: TransactionStatus
        get() = when {
            transferKind == TransferKind.LONE_LEG && !loneLegConfirmed -> TransactionStatus.TO_REVIEW
            // The user marked it as a household transfer, so it was set by hand.
            transferKind == TransferKind.LONE_LEG -> TransactionStatus.MANUAL
            categorizedBy == CategorizedBy.NONE -> TransactionStatus.TO_REVIEW
            categorizedBy == CategorizedBy.MANUAL -> TransactionStatus.MANUAL
            else -> TransactionStatus.AUTO
        }

    /** Periods use the date you spent the money. */
    val period: Period get() = Period.of(transactionDate)
}
