package app.cashflow.feature.importer.data

import app.cashflow.core.domain.Result
import app.cashflow.core.domain.model.ImportSource
import app.cashflow.core.domain.model.Money
import app.cashflow.core.domain.model.Movement
import app.cashflow.core.domain.model.ParsedStatement
import app.cashflow.core.domain.model.RowWarningReason
import app.cashflow.feature.importer.data.SyntheticXls.Value.Num
import app.cashflow.feature.importer.data.SyntheticXls.Value.Text
import app.cashflow.feature.importer.data.profile.AvanzaProfile
import app.cashflow.feature.importer.data.profile.LansforsakringarProfile
import app.cashflow.feature.importer.data.profile.LysaProfile
import app.cashflow.feature.importer.domain.PickedFile
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test

/** Lysa and Avanza exports in their real layouts, with fictional rows. */
class SavingsProfilesTest {

    private val reader = ProfileStatementReader(listOf(LansforsakringarProfile(), LysaProfile(), AvanzaProfile()))

    private fun read(name: String, bytes: ByteArray) = (reader.readNow(PickedFile(name, bytes)) as Result.Success<ParsedStatement>).data

    @Test
    fun `reads a lysa export with the sign taken from the type`() {
        val bytes = SyntheticXls.workbook(
            listOf(
                listOf(Text("Date"), Text("Type"), Text("Name"), Text("Id"), Text("Amount"), Text("Counterpart/Fund"), Text("Shares"), Text("Price")),
                listOf(Text("2026-05-27 09:47:13"), Text("Deposit"), Text("Fondkonto"), Text("id-1"), Num(6000.0), Text("Autogiro")),
                listOf(Text("2026-05-28 10:00:00"), Text("Buy"), Text("Fondkonto"), Text("id-1"), Num(5990.0), Text("Global Fond"), Num(10.0), Num(599.0)),
                listOf(Text("2026-06-01 11:33:18"), Text("Fee"), Text("Fondkonto"), Text("id-1"), Num(10.0), Text("-")),
                listOf(Text("2026-06-02 08:00:00"), Text("Withdrawal"), Text("Buffert"), Text("id-2"), Num(2000.0), Text("1234.56.789.01")),
                listOf(Text("2026-06-03 08:00:00"), Text("Mystery"), Text("Buffert"), Text("id-2"), Num(1.0), Text("-")),
            ),
        )

        val s = read("Lysa.xls", bytes)

        assertThat(s.source).isEqualTo(ImportSource.LYSA_XLS)
        assertThat(s.rows.map { it.movement }).containsExactly(Movement.DEPOSIT, Movement.TRADE, Movement.COST, Movement.WITHDRAWAL)
        assertThat(s.rows.map { it.amount }).containsExactly(Money.kr(6000), Money.kr(-5990), Money.kr(-10), Money.kr(-2000))
        assertThat(s.rows.map { it.account!!.key }).containsExactly("lysa:id-1", "lysa:id-1", "lysa:id-1", "lysa:id-2")
        assertThat(s.rows.first().bookingDate).isEqualTo(LocalDate(2026, 5, 27))
        assertThat(s.rows.first().rawText).isEqualTo("Insättning Autogiro")
        assertThat(s.warnings.map { it.reason }).containsExactly(RowWarningReason.UNKNOWN_TYPE)
    }

    @Test
    fun `reads an avanza export with decimal commas and several accounts`() {
        val csv = "﻿Datum;Konto;Typ av transaktion;Värdepapper/beskrivning;Antal;Kurs;Belopp;Transaktionsvaluta;Courtage;Valutakurs;Instrumentvaluta;ISIN;Resultat\n" +
            "2026-05-14;Sparkonto Exempel;Insättning;Exempel Person;;;500;SEK;;;;;\n" +
            "2026-05-13;ISK Exempel;Köp;Exempel Fond;2;100,5;-201,00;SEK;;;SEK;SE0000000000;\n" +
            "2026-05-12;ISK Exempel;Utländsk källskatt;Exempel Bolag;;;-2,38;SEK;;;;;\n" +
            "2026-05-12;ISK Exempel;Intern överföring;Till Sparkonto;;;-300;SEK;;;;;\n" +
            "2026-05-12;Sparkonto Exempel;Intern överföring;Från ISK;;;300;SEK;;;;;\n" +
            "2026-05-11;KF Exempel;Utdelning;Exempel Bolag;;;12,50;SEK;;;;;\n"

        val s = read("transaktioner.csv", csv.encodeToByteArray())

        assertThat(s.source).isEqualTo(ImportSource.AVANZA_CSV)
        assertThat(s.rows.map { it.movement }).containsExactly(
            Movement.DEPOSIT, Movement.TRADE, Movement.COST, Movement.INTERNAL_OUT, Movement.INTERNAL_IN, Movement.RETURN,
        )
        assertThat(s.rows.map { it.amount.ore }).containsExactly(50_000L, -20_100L, -238L, -30_000L, 30_000L, 1_250L)
        assertThat(s.rows.map { it.account!!.name }.distinct()).containsExactly("Sparkonto Exempel", "ISK Exempel", "KF Exempel")
        assertThat(s.rows.all { it.balanceAfter == null }).isEqualTo(true)
    }
}
