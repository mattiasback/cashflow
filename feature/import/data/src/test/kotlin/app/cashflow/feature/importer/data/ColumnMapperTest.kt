package app.cashflow.feature.importer.data

import app.cashflow.core.domain.ImportError
import app.cashflow.core.domain.Result
import app.cashflow.core.domain.model.CsvProfile
import app.cashflow.core.domain.model.ImportSource
import app.cashflow.core.domain.model.Money
import app.cashflow.core.domain.testing.InMemoryCsvProfileDataSource
import app.cashflow.feature.importer.data.mapping.DefaultColumnMapper
import app.cashflow.feature.importer.data.profile.LansforsakringarProfile
import app.cashflow.feature.importer.domain.ColumnRole
import app.cashflow.feature.importer.domain.PickedFile
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import kotlin.time.Instant

/** "Mappa kolumner" on an unknown, fictional bank format. */
class ColumnMapperTest {

    private val csv = """
        Exempelbanken kontohändelser
        Bokförd;Specifikation;Belopp;Saldo
        13.10.2026;MATBUTIK EXEMPEL;412,50;9 587,50
        12.10.2026;LÖN EXEMPEL;-25 000,00;10 000,00
        10.10.2026;KAFE EXEMPEL;45,00;-15 000,00
    """.trimIndent().encodeToByteArray()

    private val mapper = DefaultColumnMapper()

    @Test
    fun `guesses the header row and the columns, and sees that expenses have no sign`() {
        val draft = (mapper.inspect(PickedFile("export.csv", csv)) as Result.Success).data
        assertThat(draft.headerRow).isEqualTo(1)
        assertThat(draft.columns.map { it.role }).containsExactly(ColumnRole.DATE, ColumnRole.TEXT, ColumnRole.AMOUNT, ColumnRole.BALANCE)
        assertThat(draft.columns.all { it.sure }).isEqualTo(true)
        assertThat(draft.decimalComma).isEqualTo(true)
    }

    @Test
    fun `a saved mapping parses the file and is recognized again by its columns`() = runBlocking {
        val draft = (mapper.inspect(PickedFile("export.csv", csv)) as Result.Success).data
        val profile = CsvProfile(
            id = "p1", name = "Exempelbanken", fingerprint = draft.fingerprint, headerRow = 1,
            dateColumn = 0, textColumn = 1, amountColumn = 2, inColumn = null, outColumn = null, balanceColumn = 3,
            negateAmounts = true, createdAt = Instant.fromEpochMilliseconds(0),
        )
        val saved = InMemoryCsvProfileDataSource()
        val reader = ProfileStatementReader(listOf(LansforsakringarProfile()), saved)
        assertThat(reader.read(PickedFile("next.csv", csv))).isInstanceOf(Result.Error::class)

        saved.upsertProfile(profile)
        val statement = (reader.read(PickedFile("next.csv", csv)) as Result.Success).data
        assertThat(statement.source).isEqualTo(ImportSource.CSV_MAPPED)
        assertThat(statement.rows.map { it.amount }).containsExactly(Money(-41_250), Money.kr(25_000), Money.kr(-45))
        assertThat(statement.rows.first().bookingDate).isEqualTo(LocalDate(2026, 10, 13))
        assertThat((reader.read(PickedFile("x.csv", "a;b\n1;2".encodeToByteArray())) as Result.Error).error).isEqualTo(ImportError.UNSUPPORTED_FORMAT)
    }
}
