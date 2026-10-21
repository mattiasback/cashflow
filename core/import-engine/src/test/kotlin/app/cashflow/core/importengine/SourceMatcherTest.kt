package app.cashflow.core.importengine

import app.cashflow.core.categorization.PayeeNormalizer
import app.cashflow.core.domain.model.TransactionOrigin
import app.cashflow.core.domain.model.TxId
import app.cashflow.core.domain.testing.Fixtures.tx
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

/**
 * The same bank event from a file and from bank sync, with different texts. All data is
 * fictional.
 */
class SourceMatcherTest {

    private val matcher = SourceMatcher(PayeeNormalizer())

    private fun file(id: String, date: String, kronor: Double, text: String = "KortkÃ¶p 261012 MATBUTIK EXEMPEL", account: String = "acc-a") =
        tx(id, account = account, date = date, kronor = kronor, text = text, importId = "file")

    private fun bank(id: String, date: String, kronor: Double, text: String = "MATBUTIK EXEMPEL", account: String = "acc-a") =
        tx(id, account = account, date = date, kronor = kronor, text = text, importId = "sync").copy(origin = TransactionOrigin.BANK)

    @Test
    fun `a synced row with the same account, amount and a close date is the file row`() {
        val result = matcher.match(
            incoming = listOf(bank("b1", "2026-10-13", -245.5)),
            existing = listOf(file("f1", "2026-10-12", -245.5)),
            alreadyAliased = emptySet(),
        )
        assertThat(result.matched).isEqualTo(mapOf(TxId("b1") to TxId("f1")))
        assertThat(result.unclear).isEmpty()
    }

    @Test
    fun `two identical purchases pair with two rows, never both with one`() {
        val result = matcher.match(
            incoming = listOf(bank("b1", "2026-10-09", -45.0), bank("b2", "2026-10-09", -45.0)),
            existing = listOf(file("f1", "2026-10-09", -45.0), file("f2", "2026-10-09", -45.0)),
            alreadyAliased = emptySet(),
        )
        assertThat(result.matched.keys).isEqualTo(setOf(TxId("b1"), TxId("b2")))
        assertThat(result.matched.values.toSet()).isEqualTo(setOf(TxId("f1"), TxId("f2")))
    }

    @Test
    fun `the closest date wins over the first row`() {
        val result = matcher.match(
            incoming = listOf(bank("b9", "2026-10-09", -45.0), bank("b10", "2026-10-10", -45.0)),
            existing = listOf(file("f10", "2026-10-10", -45.0), file("f9", "2026-10-09", -45.0)),
            alreadyAliased = emptySet(),
        )
        assertThat(result.matched).isEqualTo(mapOf(TxId("b9") to TxId("f9"), TxId("b10") to TxId("f10")))
    }

    @Test
    fun `a newer row past the file's last day is new, not a duplicate`() {
        val result = matcher.match(
            incoming = listOf(bank("b9", "2026-10-09", -45.0), bank("b12", "2026-10-12", -45.0)),
            existing = listOf(file("f9", "2026-10-09", -45.0)),
            alreadyAliased = emptySet(),
        )
        assertThat(result.matched).isEqualTo(mapOf(TxId("b9") to TxId("f9")))
        assertThat(result.unclear).isEmpty()
    }

    @Test
    fun `an extra row inside the file's days is asked about`() {
        val result = matcher.match(
            incoming = listOf(bank("b1", "2026-10-09", -45.0), bank("b2", "2026-10-10", -45.0)),
            existing = listOf(file("f1", "2026-10-09", -45.0), file("f2", "2026-10-20", -900.0)),
            alreadyAliased = emptySet(),
        )
        assertThat(result.matched).isEqualTo(mapOf(TxId("b1") to TxId("f1")))
        assertThat(result.unclear).isEqualTo(mapOf(TxId("b2") to TxId("f1")))
    }

    @Test
    fun `other amounts, other accounts and far dates never match`() {
        val result = matcher.match(
            incoming = listOf(
                bank("b1", "2026-10-09", -45.5),
                bank("b2", "2026-10-09", -45.0, account = "acc-b"),
                bank("b3", "2026-10-20", -45.0),
            ),
            existing = listOf(file("f1", "2026-10-09", -45.0), file("f2", "2026-10-25", -1.0)),
            alreadyAliased = emptySet(),
        )
        assertThat(result.matched).isEmpty()
    }

    @Test
    fun `rows of the same origin and rows already standing in are skipped`() {
        val result = matcher.match(
            incoming = listOf(bank("b2", "2026-10-09", -45.0)),
            existing = listOf(bank("b1", "2026-10-09", -45.0), file("f1", "2026-10-09", -45.0)),
            alreadyAliased = setOf(TxId("f1")),
        )
        assertThat(result.matched).isEmpty()
    }

    @Test
    fun `the reverse direction works the same, a file after a sync`() {
        val result = matcher.match(
            incoming = listOf(file("f1", "2026-10-12", -245.5)),
            existing = listOf(bank("b1", "2026-10-13", -245.5)),
            alreadyAliased = emptySet(),
        )
        assertThat(result.matched).isEqualTo(mapOf(TxId("f1") to TxId("b1")))
    }
}
