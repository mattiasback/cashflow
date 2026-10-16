package app.cashflow.feature.importer.data.profile

import app.cashflow.core.domain.model.ImportSource
import app.cashflow.core.domain.model.Money
import app.cashflow.core.domain.model.Movement
import app.cashflow.core.domain.model.RowWarning
import app.cashflow.core.domain.model.RowWarningReason
import app.cashflow.core.domain.model.StatementAccount
import app.cashflow.core.domain.model.StatementRow
import app.cashflow.feature.importer.data.xls.Cell

/**
 * Lysa transaction export (`.xls`, one sheet, header on row 1): Date (with time), Type, Name
 * (the Lysa account), Id (the account's id), Amount (always positive), Counterpart/Fund,
 * Shares, Price. The sign comes from the type. One file covers every Lysa account.
 */
class LysaProfile : StatementProfile {

    override val source = ImportSource.LYSA_XLS

    override fun findHeader(grid: List<List<Cell?>>): Int? =
        grid.take(HEADER_SEARCH_ROWS).indexOfFirst { row ->
            val keys = row.map { headerKey(it.text()) }
            REQUIRED.all { it in keys }
        }.takeIf { it >= 0 }

    override fun parse(grid: List<List<Cell?>>, headerRow: Int): ProfileResult {
        val header = grid[headerRow].map { headerKey(it.text()) }
        val date = header.indexOf(DATE)
        val type = header.indexOf(TYPE)
        val name = header.indexOf(NAME)
        val id = header.indexOf(ID)
        val amount = header.indexOf(AMOUNT)
        val counterpart = header.indexOf(COUNTERPART)

        val rows = ArrayList<StatementRow>()
        val warnings = ArrayList<RowWarning>()
        for (i in headerRow + 1 until grid.size) {
            val r = grid[i]
            if (r.all { it == null || it.text().isEmpty() }) continue
            val day = r.getOrNull(date).date()
            if (day == null) {
                warnings += warning(i, if (r.getOrNull(date).text().isEmpty()) RowWarningReason.MISSING_DATE else RowWarningReason.UNREADABLE_DATE)
                continue
            }
            val value = r.getOrNull(amount).money()?.abs()
            if (value == null) {
                warnings += warning(i, if (r.getOrNull(amount).text().isEmpty()) RowWarningReason.MISSING_AMOUNT else RowWarningReason.UNREADABLE_AMOUNT)
                continue
            }
            val typeText = r.getOrNull(type).text()
            val kind = TYPES[headerKey(typeText)]
            if (kind == null) {
                warnings += warning(i, RowWarningReason.UNKNOWN_TYPE)
                continue
            }
            val accountName = r.getOrNull(name).text().ifEmpty { "Lysa" }
            val accountId = r.getOrNull(id).text().ifEmpty { accountName }
            val other = r.getOrNull(counterpart).text().takeUnless { it.isEmpty() || it == "-" }
            rows += StatementRow(
                bookingDate = day,
                transactionDate = day,
                rawText = listOfNotNull(kind.label, other).joinToString(" "),
                amount = if (kind.sign < 0) Money(-value.ore) else value,
                balanceAfter = null,
                account = StatementAccount(key = "lysa:$accountId", name = accountName, institution = INSTITUTION),
                movement = kind.movement,
            )
        }
        return ProfileResult(rows, warnings)
    }

    private class Kind(val movement: Movement, val sign: Int, val label: String)

    private companion object {
        const val INSTITUTION = "Lysa"
        const val HEADER_SEARCH_ROWS = 5
        val DATE = headerKey("Date")
        val TYPE = headerKey("Type")
        val NAME = headerKey("Name")
        val ID = headerKey("Id")
        val AMOUNT = headerKey("Amount")
        val COUNTERPART = headerKey("Counterpart/Fund")
        val REQUIRED = listOf(DATE, TYPE, NAME, AMOUNT, COUNTERPART)

        /** Swedish labels become the row text, so Händelser reads the same for every bank. */
        val TYPES = mapOf(
            "deposit" to Kind(Movement.DEPOSIT, 1, "Insättning"),
            "withdrawal" to Kind(Movement.WITHDRAWAL, -1, "Uttag"),
            "internaltransferin" to Kind(Movement.INTERNAL_IN, 1, "Intern överföring in"),
            "internaltransferout" to Kind(Movement.INTERNAL_OUT, -1, "Intern överföring ut"),
            "buy" to Kind(Movement.TRADE, -1, "Köp"),
            "sell" to Kind(Movement.TRADE, 1, "Sälj"),
            "switchbuy" to Kind(Movement.TRADE, -1, "Byte köp"),
            "switchsell" to Kind(Movement.TRADE, 1, "Byte sälj"),
            "fee" to Kind(Movement.COST, -1, "Avgift"),
            "tax" to Kind(Movement.COST, -1, "Skatt"),
            "interest" to Kind(Movement.RETURN, 1, "Ränta"),
            "dividend" to Kind(Movement.RETURN, 1, "Utdelning"),
        )
    }
}
