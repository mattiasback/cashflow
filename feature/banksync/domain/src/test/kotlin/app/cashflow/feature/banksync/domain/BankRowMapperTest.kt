package app.cashflow.feature.banksync.domain

import app.cashflow.core.categorization.PayeeNormalizer
import app.cashflow.core.domain.model.Account
import app.cashflow.core.domain.model.AccountId
import app.cashflow.core.domain.model.AccountType
import app.cashflow.core.domain.model.ImportId
import app.cashflow.core.domain.model.Money
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import kotlin.time.Instant

class BankRowMapperTest {

    private val mapper = BankRowMapper(PayeeNormalizer())
    private val account = Account(AccountId("acc-a"), "Lönekonto", AccountType.TRANSACTION)
    private val link = BankLink(
        "h1", "fake", account.id, "Exempelbanken", "s1", "u1", Instant.parse("2027-04-01T00:00:00Z"), "Exempelbanken ··1234", "1234", null,
    )

    private fun row(ref: String?, date: String, kronor: Long, text: String) =
        BankTransaction(ref, LocalDate.parse(date), null, Money.kr(kronor), "SEK", text, null, true)

    private fun ids(rows: List<BankTransaction>) = mapper.map(rows, link, account, ImportId("i")).map { it.id }

    @Test
    fun `rows that share one reference are all kept`() {
        val mapped = mapper.map(
            listOf(row("same", "2026-10-02", -45, "KAFE EXEMPEL"), row("same", "2026-10-03", -300, "BOKHANDEL EXEMPEL"), row("same", "2026-10-03", -300, "BOKHANDEL EXEMPEL")),
            link, account, ImportId("i"),
        )
        assertThat(mapped).hasSize(3)
        assertThat(mapped.map { it.id }.toSet()).hasSize(3)
    }

    @Test
    fun `a row keeps the same id when fetched again, in any order, and with the plain reference for the first`() {
        val a = row("r1", "2026-10-02", -45, "KAFE EXEMPEL")
        val b = row("r2", "2026-10-03", -300, "BOKHANDEL EXEMPEL")
        assertThat(ids(listOf(a, b))).isEqualTo(ids(listOf(b, a)))
        // The id of a row with its own reference only depends on the account and that reference.
        assertThat(ids(listOf(a))).isEqualTo(ids(listOf(a.copy(text = "ANNAN TEXT", bookingDate = LocalDate(2026, 10, 9)))))
    }

    @Test
    fun `the account label shows the number's end and the product, never a holder name`() {
        assertThat(bankAccountLabel("Exempelbanken", "Privatkonto", "1234567890123456789012")).isEqualTo("Exempelbanken ··9012 · Privatkonto")
        assertThat(bankAccountLabel("Exempelbanken", null, null)).isEqualTo("Exempelbanken")
    }
}
