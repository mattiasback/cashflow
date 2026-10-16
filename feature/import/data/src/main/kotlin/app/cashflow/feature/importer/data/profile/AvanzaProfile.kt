package app.cashflow.feature.importer.data.profile

import app.cashflow.core.domain.model.ImportSource
import app.cashflow.core.domain.model.Movement
import app.cashflow.core.domain.model.RowWarning
import app.cashflow.core.domain.model.RowWarningReason
import app.cashflow.core.domain.model.StatementAccount
import app.cashflow.core.domain.model.StatementRow
import app.cashflow.feature.importer.data.xls.Cell

/**
 * Avanza transaction export: UTF-8 CSV with a BOM, `;` separators and decimal commas, header
 * on row 1: Datum, Konto, Typ av transaktion, Värdepapper/beskrivning, Antal, Kurs, Belopp, …
 * Amounts are signed and there is no balance column. One file covers every Avanza account.
 */
class AvanzaProfile : StatementProfile {

    override val source = ImportSource.AVANZA_CSV

    override fun findHeader(grid: List<List<Cell?>>): Int? =
        grid.take(HEADER_SEARCH_ROWS).indexOfFirst { row ->
            val keys = row.map { headerKey(it.text()) }
            REQUIRED.all { it in keys }
        }.takeIf { it >= 0 }

    override fun parse(grid: List<List<Cell?>>, headerRow: Int): ProfileResult {
        val header = grid[headerRow].map { headerKey(it.text()) }
        val date = header.indexOf(DATE)
        val account = header.indexOf(ACCOUNT)
        val type = header.indexOf(TYPE)
        val description = header.indexOf(DESCRIPTION)
        val amount = header.indexOf(AMOUNT)

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
            val typeText = r.getOrNull(type).text()
            val movement = movementOf(headerKey(typeText))
            if (movement == null) {
                warnings += warning(i, RowWarningReason.UNKNOWN_TYPE)
                continue
            }
            val value = r.getOrNull(amount).money()
            if (value == null) {
                // A trade or clean-up without a cash amount changes no money; anything else is a warning.
                if (movement != Movement.TRADE) {
                    warnings += warning(i, if (r.getOrNull(amount).text().isEmpty()) RowWarningReason.MISSING_AMOUNT else RowWarningReason.UNREADABLE_AMOUNT)
                }
                continue
            }
            val accountName = r.getOrNull(account).text().ifEmpty { "Avanza" }
            val text = listOf(typeText, r.getOrNull(description).text()).filter { it.isNotEmpty() }.joinToString(" ")
            rows += StatementRow(
                bookingDate = day,
                transactionDate = day,
                rawText = text,
                amount = value,
                balanceAfter = null,
                account = StatementAccount(key = "avanza:$accountName", name = accountName, institution = INSTITUTION),
                movement = when (movement) {
                    // Moves between Avanza accounts carry their direction in the sign.
                    Movement.INTERNAL_IN, Movement.INTERNAL_OUT -> if (value.isNegative) Movement.INTERNAL_OUT else Movement.INTERNAL_IN
                    else -> movement
                },
            )
        }
        return ProfileResult(rows, warnings)
    }

    private fun movementOf(key: String): Movement? = TYPES[key]

    private companion object {
        const val INSTITUTION = "Avanza"
        const val HEADER_SEARCH_ROWS = 5
        val DATE = headerKey("Datum")
        val ACCOUNT = headerKey("Konto")
        val TYPE = headerKey("Typ av transaktion")
        val DESCRIPTION = headerKey("Värdepapper/beskrivning")
        val AMOUNT = headerKey("Belopp")
        val REQUIRED = listOf(DATE, ACCOUNT, TYPE, AMOUNT)

        val TYPES: Map<String, Movement> = buildMap {
            fun put(movement: Movement, vararg names: String) = names.forEach { put(headerKey(it), movement) }
            put(Movement.DEPOSIT, "Insättning")
            put(Movement.WITHDRAWAL, "Uttag")
            put(Movement.INTERNAL_IN, "Intern överföring", "Delåterköp AVA internt", "Överföring från delåterköp")
            put(Movement.TRADE, "Köp", "Sälj", "Rensning av värdepapper", "Byte", "Tilldelning", "Emission", "Split", "Omvandling")
            put(Movement.RETURN, "Utdelning", "Inlåningsränta", "Återbetalning utländsk källskatt", "Ränta")
            put(
                Movement.COST,
                "Utländsk källskatt", "Preliminärskatt kapitalränta", "Avkastningsskatt", "Utlåningsränta", "Uttag av riskkostnad", "Avgift", "Courtage",
            )
        }
    }
}
