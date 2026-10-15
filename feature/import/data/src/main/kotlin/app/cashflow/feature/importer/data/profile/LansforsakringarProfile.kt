package app.cashflow.feature.importer.data.profile

import app.cashflow.core.domain.model.ImportSource
import app.cashflow.core.domain.model.RowWarning
import app.cashflow.core.domain.model.RowWarningReason
import app.cashflow.core.domain.model.StatementRow
import app.cashflow.feature.importer.data.xls.Cell

/**
 * Länsförsäkringar "Kontoutdrag" export. Row 1 is a title, the header is on row 2:
 * Bokföringsdatum, Transaktionsdatum, Text, Insättning/Uttag, Behållning. Dates are ISO
 * text, amounts and balances are numeric cells. Only booked rows are exported.
 */
class LansforsakringarProfile : StatementProfile {

    override val source = ImportSource.LANSFORSAKRINGAR_XLS

    override fun findHeader(grid: List<List<Cell?>>): Int? =
        grid.take(HEADER_SEARCH_ROWS).indexOfFirst { row ->
            val keys = row.map { headerKey(it.text()) }
            REQUIRED.all { it in keys }
        }.takeIf { it >= 0 }

    override fun parse(grid: List<List<Cell?>>, headerRow: Int): ProfileResult {
        val header = grid[headerRow].map { headerKey(it.text()) }
        val booking = header.indexOf(BOOKING)
        val transaction = header.indexOf(TRANSACTION)
        val text = header.indexOf(TEXT)
        val amount = header.indexOf(AMOUNT)
        val balance = header.indexOf(BALANCE)

        val rows = ArrayList<StatementRow>()
        val warnings = ArrayList<RowWarning>()
        for (i in headerRow + 1 until grid.size) {
            val r = grid[i]
            if (r.all { it == null || it.text().isEmpty() }) continue
            val bookingCell = r.getOrNull(booking)
            val bookingDate = bookingCell.date()
            if (bookingDate == null) {
                warnings += warning(i, if (bookingCell.text().isEmpty()) RowWarningReason.MISSING_DATE else RowWarningReason.UNREADABLE_DATE)
                continue
            }
            val amountCell = r.getOrNull(amount)
            val value = amountCell.money()
            if (value == null) {
                warnings += warning(i, if (amountCell.text().isEmpty()) RowWarningReason.MISSING_AMOUNT else RowWarningReason.UNREADABLE_AMOUNT)
                continue
            }
            val balanceAfter = if (balance >= 0) r.getOrNull(balance).money() else null
            if (balance >= 0 && balanceAfter == null) {
                // A reservation has no balance yet; it is rejected and arrives booked in a later export.
                warnings += warning(i, RowWarningReason.PENDING_RESERVATION)
                continue
            }
            rows += StatementRow(
                bookingDate = bookingDate,
                transactionDate = r.getOrNull(transaction).date() ?: bookingDate,
                rawText = r.getOrNull(text).text(),
                amount = value,
                balanceAfter = balanceAfter,
            )
        }
        return ProfileResult(rows, warnings)
    }

    private companion object {
        const val HEADER_SEARCH_ROWS = 5
        val BOOKING = headerKey("Bokföringsdatum")
        val TRANSACTION = headerKey("Transaktionsdatum")
        val TEXT = headerKey("Text")
        val AMOUNT = headerKey("Insättning/Uttag")
        val BALANCE = headerKey("Behållning")
        val REQUIRED = listOf(BOOKING, TEXT, AMOUNT, BALANCE)
    }
}
