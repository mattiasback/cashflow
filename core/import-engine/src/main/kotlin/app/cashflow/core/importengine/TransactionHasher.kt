package app.cashflow.core.importengine

import app.cashflow.core.domain.model.AccountId
import app.cashflow.core.domain.model.StatementRow
import app.cashflow.core.domain.model.TxId
import java.security.MessageDigest

/**
 * Ids hash the raw ledger row: `sha256(account|bookingDate|öre|rawText.trim()|balanceAfter)`.
 * The balance after the row makes two identical same-day rows unique and keeps ids stable
 * across overlapping exports. Normalized text is never part of the id, so improving the
 * normalizer never changes ids. Banks without a balance column use the row's position
 * among identical rows that day ([sameDaySeq]) instead.
 */
class TransactionHasher {

    fun hash(accountId: AccountId, row: StatementRow, sameDaySeq: Int = 0): TxId {
        val balancePart = row.balanceAfter?.ore?.toString() ?: "seq$sameDaySeq"
        val key = listOf(
            accountId.value,
            row.bookingDate.toString(),
            row.amount.ore.toString(),
            row.rawText.trim(),
            balancePart,
        ).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        return TxId(digest.joinToString("") { "%02x".format(it) })
    }

    /** Hashes every row of a statement in file order, numbering identical rows without a balance. */
    fun hashAll(accountId: AccountId, rows: List<StatementRow>): List<TxId> {
        val seen = HashMap<String, Int>()
        return rows.map { row ->
            val seq = if (row.balanceAfter == null) {
                val k = "${row.bookingDate}|${row.amount.ore}|${row.rawText.trim()}"
                seen.merge(k, 1, Int::plus)!! - 1
            } else {
                0
            }
            hash(accountId, row, seq)
        }
    }
}
