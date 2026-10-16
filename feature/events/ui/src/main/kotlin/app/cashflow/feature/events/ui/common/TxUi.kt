package app.cashflow.feature.events.ui.common

import androidx.compose.runtime.Immutable
import app.cashflow.core.domain.model.Category
import app.cashflow.core.domain.model.CategoryId
import app.cashflow.core.domain.model.CategoryKind
import app.cashflow.core.domain.model.Transaction
import app.cashflow.core.domain.model.TransactionStatus
import app.cashflow.core.domain.model.TransferKind
import app.cashflow.core.ui.FlowTone
import app.cashflow.core.ui.UiText
import app.cashflow.core.ui.categoryName
import app.cashflow.core.ui.format.AppLocale
import app.cashflow.core.ui.format.MoneyFormatter
import app.cashflow.core.ui.format.PeriodFormatter
import app.cashflow.core.ui.tone
import app.cashflow.feature.events.ui.R
import app.cashflow.core.designsystem.component.BadgeKind

/** One transaction row, pre-formatted: the UI only lays it out. */
@Immutable
data class TxRowUi(
    val id: String,
    val title: String,
    val date: String,
    val category: UiText,
    val amount: String,
    val isIncome: Boolean,
    val tone: FlowTone,
    val badge: UiText,
    val badgeKind: BadgeKind,
    val spokenAmount: String,
)

@Immutable
data class TxDayUi(
    val key: String,
    val heading: String,
    val rows: List<TxRowUi>,
)

internal fun Transaction.toRowUi(categories: Map<CategoryId, Category>, locale: AppLocale): TxRowUi {
    val category = categories[categoryId]
    val kind = category?.kind ?: CategoryKind.UNCATEGORIZED
    return TxRowUi(
        id = id.value,
        title = normalizedText.ifBlank { rawText },
        date = PeriodFormatter.iso(transactionDate, locale),
        category = categoryName(categoryId, category?.name ?: categoryId.value),
        amount = MoneyFormatter.format(amount, signed = true, locale = locale),
        // Only categorized income is shown as a gain. Transfers and money still to review stay neutral.
        isIncome = amount.isPositive && kind == CategoryKind.INCOME && badgeKind() != BadgeKind.Transfer,
        tone = kind.tone(),
        badge = statusText(),
        badgeKind = badgeKind(),
        spokenAmount = MoneyFormatter.spoken(amount, locale),
    )
}

internal fun Transaction.statusText(): UiText = when {
    transferKind == TransferKind.INTERNAL || (transferKind == TransferKind.LONE_LEG && loneLegConfirmed) || categoryId == CategoryId.TRANSFER ->
        UiText.StringResource(R.string.status_transfer)
    transferKind == TransferKind.TO_SAVINGS || transferKind == TransferKind.FROM_SAVINGS -> UiText.StringResource(R.string.status_savings)
    status == TransactionStatus.TO_REVIEW -> UiText.StringResource(R.string.status_review)
    status == TransactionStatus.MANUAL -> UiText.StringResource(R.string.status_manual)
    else -> UiText.StringResource(R.string.status_auto)
}

internal fun Transaction.badgeKind(): BadgeKind = when {
    transferKind == TransferKind.INTERNAL || (transferKind == TransferKind.LONE_LEG && loneLegConfirmed) || categoryId == CategoryId.TRANSFER -> BadgeKind.Transfer
    transferKind == TransferKind.TO_SAVINGS || transferKind == TransferKind.FROM_SAVINGS -> BadgeKind.Transfer
    status == TransactionStatus.TO_REVIEW -> BadgeKind.Review
    status == TransactionStatus.MANUAL -> BadgeKind.Manual
    else -> BadgeKind.Auto
}
