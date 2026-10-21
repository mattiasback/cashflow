package app.cashflow.feature.banksync.domain

import app.cashflow.core.categorization.PayeeNormalizer
import app.cashflow.core.domain.model.Account
import app.cashflow.core.domain.model.CategorizedBy
import app.cashflow.core.domain.model.CategoryId
import app.cashflow.core.domain.model.ImportId
import app.cashflow.core.domain.model.Transaction
import app.cashflow.core.domain.model.TransactionOrigin
import app.cashflow.core.domain.model.TransferKind
import app.cashflow.core.domain.model.TxId
import java.security.MessageDigest

/**
 * Turns booked bank rows into [Transaction]s with stable ids:
 * `sha256(bank|provider|account key|reference)`, so a repeated sync skips them. A bank that
 * gives no reference falls back to date, amount, text and a sequence number among identical
 * rows that day; syncs always fetch whole days, so the number is stable.
 */
class BankRowMapper(private val normalizer: PayeeNormalizer) {

    fun map(rows: List<BankTransaction>, link: BankLink, account: Account, importId: ImportId): List<Transaction> {
        val booked = oldestFirst(rows.filter { it.booked && it.currency == SEK })
        val seen = HashMap<String, Int>()
        return booked.mapIndexed { index, row ->
            val text = row.text.trim()
            val idKey = row.reference?.takeIf { it.isNotBlank() }?.let { "ref|$it" } ?: run {
                val k = "${row.bookingDate}|${row.amount.ore}|$text"
                "row|$k|${seen.merge(k, 1, Int::plus)!! - 1}"
            }
            Transaction(
                id = hash("bank|${link.provider}|${link.stableKey}|$idKey"),
                accountId = account.id,
                importId = importId,
                bookingDate = row.bookingDate,
                transactionDate = row.transactionDate ?: row.bookingDate,
                amount = row.amount,
                rawText = text,
                normalizedText = normalizer.displayName(text),
                balanceAfter = row.balanceAfter,
                categoryId = CategoryId.UNCATEGORIZED,
                categorizedBy = CategorizedBy.NONE,
                transferKind = TransferKind.NONE,
                fileOrder = index,
                countsInCashFlow = account.type.countsInCashFlow,
                origin = TransactionOrigin.BANK,
            )
        }.distinctBy { it.id }
    }

    /** Banks list newest first or oldest first; same-day order is kept. */
    private fun oldestFirst(rows: List<BankTransaction>): List<BankTransaction> {
        val ordered = if (rows.size > 1 && rows.first().bookingDate > rows.last().bookingDate) rows.asReversed() else rows
        return ordered.sortedBy { it.bookingDate }
    }

    private fun hash(key: String): TxId {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        return TxId(digest.joinToString("") { "%02x".format(it) })
    }

    private companion object {
        const val SEK = "SEK"
    }
}
