package app.cashflow.feature.importer.data

import app.cashflow.core.domain.ImportError
import app.cashflow.core.domain.Result
import app.cashflow.core.domain.model.ImportSource
import app.cashflow.core.domain.model.Money
import app.cashflow.core.domain.model.ParsedStatement
import app.cashflow.core.domain.model.RowWarningReason
import app.cashflow.feature.importer.data.SyntheticXls.Value.Num
import app.cashflow.feature.importer.data.SyntheticXls.Value.Text
import app.cashflow.feature.importer.data.profile.LansforsakringarProfile
import app.cashflow.feature.importer.domain.PickedFile
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.prop
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test

class ProfileStatementReaderTest {

    private val reader = ProfileStatementReader(listOf(LansforsakringarProfile()))

    /** Fake data in the real Länsförsäkringar layout. */
    private fun lfWorkbook(vararg rows: List<SyntheticXls.Value?>) = SyntheticXls.workbook(
        listOf(
            listOf(Text("Kontoutdrag -2026-08-31")),
            listOf(Text("Bokföringsdatum"), Text("Transaktionsdatum"), Text("Text"), Text("Insättning/Uttag"), Text("Behållning")),
        ) + rows,
    )

    @Test
    fun `reads a länsförsäkringar export`() {
        val bytes = lfWorkbook(
            listOf(Text("2026-08-03"), Text("2026-08-01"), Text("MATBUTIK EXEMPEL,STAD,SE"), Num(-412.5), Num(9587.5)),
            listOf(Text("2026-08-25"), Text(""), Text("LÖN"), Num(25000.0), Num(34587.5)),
        )

        val result = reader.read(PickedFile("Kontoutdrag.xls", bytes))

        assertThat(result).isInstanceOf(Result.Success::class)
        val statement = (result as Result.Success<ParsedStatement>).data
        assertThat(statement.source).isEqualTo(ImportSource.LANSFORSAKRINGAR_XLS)
        assertThat(statement.rows).hasSize(2)
        val first = statement.rows[0]
        assertThat(first.bookingDate).isEqualTo(LocalDate(2026, 8, 3))
        assertThat(first.transactionDate).isEqualTo(LocalDate(2026, 8, 1))
        assertThat(first.amount).isEqualTo(Money(-41250))
        assertThat(first.balanceAfter).isEqualTo(Money(958750))
        // An empty transaction date falls back to the booking date.
        assertThat(statement.rows[1].transactionDate).isEqualTo(LocalDate(2026, 8, 25))
    }

    @Test
    fun `a bad row becomes a warning, not a failed file`() {
        val bytes = lfWorkbook(
            listOf(Text("2026-08-03"), Text("2026-08-03"), Text("OK"), Num(-10.0), Num(90.0)),
            listOf(Text("inte ett datum"), Text(""), Text("TRASIG"), Num(-5.0), Num(85.0)),
            listOf(Text("2026-08-04"), Text("2026-08-04"), Text("RESERVERAT"), Num(-5.0), null),
        )

        val statement = (reader.read(PickedFile("k.xls", bytes)) as Result.Success).data

        assertThat(statement.rows).hasSize(1)
        assertThat(statement.warnings.map { it.reason })
            .containsExactly(RowWarningReason.UNREADABLE_DATE, RowWarningReason.PENDING_RESERVATION)
    }

    @Test
    fun `csv with the same columns is read through the same profile`() {
        val csv = "﻿Bokföringsdatum;Transaktionsdatum;Text;Insättning/Uttag;Behållning\n" +
            "2026-08-03;2026-08-02;\"KAFÉ; EXEMPEL\";-1 234,50;8 765,50\n"

        val statement = (reader.read(PickedFile("k.csv", csv.toByteArray())) as Result.Success).data

        assertThat(statement.rows.single()).prop("amount") { it.amount }.isEqualTo(Money(-123450))
        assertThat(statement.rows.single().rawText).isEqualTo("KAFÉ; EXEMPEL")
        assertThat(statement.rows.single().balanceAfter).isEqualTo(Money(876550))
    }

    @Test
    fun `unknown and empty files fail with a clear error`() {
        assertThat(reader.read(PickedFile("x.csv", "a;b;c\n1;2;3".toByteArray())))
            .isEqualTo(Result.Error(ImportError.UNSUPPORTED_FORMAT))
        assertThat(reader.read(PickedFile("x.csv", ByteArray(0))))
            .isEqualTo(Result.Error(ImportError.EMPTY_FILE))
        assertThat(reader.read(PickedFile("x.xlsx", byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4))))
            .isEqualTo(Result.Error(ImportError.UNSUPPORTED_FORMAT))
    }
}
