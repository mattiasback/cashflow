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
 * Turns booked bank rows into [Transaction]s with stable ids, so a repeated sync skips them:
 * `sha256(bank|provider|account key|reference)`. A bank that gives no reference falls back to
 * date, amount, text and a number among identical rows. A bank may also reuse one reference for
 * several rows; only the first keeps the plain reference id, the others add date, amount, text
 * and a number, so they are not silently merged into one.
 */
class BankRowMapper(private val normalizer: PayeeNormalizer) {

    fun map(rows: List<BankTransaction>, link: BankLink, account: Account, importId: ImportId): List<Transaction> {
        val booked = oldestFirst(rows.filter { it.booked && it.currency == SEK })
        val firstWithReference = HashSet<String>()
        val seen = HashMap<String, Int>()
        return booked.mapIndexed { index, row ->
            val text = row.text.trim()
            val reference = row.reference?.takeIf { it.isNotBlank() }
            val same = "${row.bookingDate}|${row.amount.ore}|$text"
            val idKey = when {
                reference == null -> "row|$same|${next(seen, same)}"
                firstWithReference.add(reference) -> "ref|$reference"
                else -> "ref|$reference|again|$same|${next(seen, "$reference|$same")}"
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
        }
    }

    private fun next(seen: HashMap<String, Int>, key: String): Int = seen.merge(key, 1, Int::plus)!! - 1

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
