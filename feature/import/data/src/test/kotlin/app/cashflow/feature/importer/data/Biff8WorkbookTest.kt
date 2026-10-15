package app.cashflow.feature.importer.data

import app.cashflow.feature.importer.data.SyntheticXls.Value.Num
import app.cashflow.feature.importer.data.SyntheticXls.Value.Rk
import app.cashflow.feature.importer.data.SyntheticXls.Value.Text
import app.cashflow.feature.importer.data.xls.Biff8Workbook
import app.cashflow.feature.importer.data.xls.Cell
import app.cashflow.feature.importer.data.xls.XlsFormatException
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import org.junit.jupiter.api.Test

class Biff8WorkbookTest {

    @Test
    fun `reads shared strings, numbers and rk values from the first sheet`() {
        val bytes = SyntheticXls.workbook(
            listOf(
                listOf(Text("Kontoutdrag -2026-08-31")),
                listOf(Text("Datum"), Text("Text"), Text("Belopp")),
                listOf(Text("2026-08-01"), Text("Matbutik Exempel"), Num(-412.5)),
                listOf(Text("2026-08-02"), Text("Lön"), Rk(25000.0)),
                listOf(Text("2026-08-03"), Text("Café"), Rk(-89.75)),
            ),
        )

        val sheet = Biff8Workbook.readFirstSheet(bytes)

        assertThat(sheet.name).isEqualTo("Kontoutdrag")
        assertThat(sheet.rows[1]).containsExactly(Cell.Text("Datum"), Cell.Text("Text"), Cell.Text("Belopp"))
        assertThat(sheet.rows[2][2]).isEqualTo(Cell.Number(-412.5))
        assertThat(sheet.rows[3][2]).isEqualTo(Cell.Number(25000.0))
        assertThat(sheet.rows[4][2]).isEqualTo(Cell.Number(-89.75))
        assertThat(sheet.rows[3][1]).isEqualTo(Cell.Text("Lön"))
    }

    @Test
    fun `a string split across SST and CONTINUE switches from compressed to UTF-16`() {
        val strings = listOf(Text("Header"), Text("Överföring till sparkonto"), Text("Snö ☃ och is"))
        val unsplit = SyntheticXls.workbook(listOf(strings))
        val reference = Biff8Workbook.readFirstSheet(unsplit).rows[0]

        // Cut inside every string position to exercise the continuation option byte.
        var checked = 0
        for (cut in 12..60) {
            val bytes = runCatching { SyntheticXls.workbook(listOf(strings), splitSstAt = cut) }.getOrNull() ?: continue
            assertThat(Biff8Workbook.readFirstSheet(bytes).rows[0]).isEqualTo(reference)
            checked++
        }
        assertThat(checked > 30).isEqualTo(true)
        assertThat(reference[2]).isEqualTo(Cell.Text("Snö ☃ och is"))
    }

    @Test
    fun `mulrk rows expand into one cell per column`() {
        val bytes = SyntheticXls.workbook(
            listOf(
                listOf(Text("a"), Text("b"), Text("c")),
                listOf(Rk(1.0), Rk(-2.5), Rk(300.0)),
            ),
            mulRkRow = 1,
        )

        val sheet = Biff8Workbook.readFirstSheet(bytes)

        assertThat(sheet.rows[1]).containsExactly(Cell.Number(1.0), Cell.Number(-2.5), Cell.Number(300.0))
    }

    @Test
    fun `non ole2 bytes are rejected`() {
        assertFailure { Biff8Workbook.readFirstSheet("Datum;Text;Belopp".toByteArray()) }
            .isInstanceOf(XlsFormatException::class)
    }
}
