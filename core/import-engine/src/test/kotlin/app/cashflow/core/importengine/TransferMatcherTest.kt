package app.cashflow.core.importengine

import app.cashflow.core.domain.model.Account
import app.cashflow.core.domain.model.AccountId
import app.cashflow.core.domain.model.AccountType
import app.cashflow.core.domain.model.TransferKind
import app.cashflow.core.domain.model.TxId
import app.cashflow.core.domain.testing.Fixtures.tx
import assertk.assertThat
import assertk.assertions.doesNotContainKey
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test
import kotlin.random.Random

class TransferMatcherTest {

    private val matcher = TransferMatcher()
    private val accounts = listOf(
        Account(AccountId("acc-a"), "Lönekonto", AccountType.TRANSACTION),
        Account(AccountId("acc-b"), "Gemensamt", AccountType.TRANSACTION),
        Account(AccountId("acc-c"), "Räkningar", AccountType.TRANSACTION),
    )

    @Test
    fun `a mirrored pair on two own accounts is an internal transfer`() {
        val out = tx("o1", "acc-a", "2026-05-25", -12000.0, "1234.56.789.01")
        val inn = tx("i1", "acc-b", "2026-05-25", 12000.0, "Från lönekonto")

        val result = matcher.match(listOf(out, inn), accounts)

        assertThat(result[out.id]).isEqualTo(TransferResult(TransferKind.INTERNAL, inn.id))
        assertThat(result[inn.id]).isEqualTo(TransferResult(TransferKind.INTERNAL, out.id))
    }

    @Test
    fun `rows on different days are not paired between bank accounts`() {
        val out = tx("o1", "acc-a", "2026-05-25", -12000.0)
        val inn = tx("i1", "acc-b", "2026-05-26", 12000.0)
        assertThat(matcher.match(listOf(out, inn), accounts)).doesNotContainKey(out.id)
    }

    @Test
    fun `weekly identical transfers pair one to one`() {
        val days = listOf("2026-05-04", "2026-05-11", "2026-05-18", "2026-05-25")
        val rows = days.flatMapIndexed { i, d ->
            listOf(tx("o$i", "acc-a", d, -2500.0, "Överföring"), tx("i$i", "acc-b", d, 2500.0, "Överföring"))
        }

        val result = matcher.match(rows.shuffled(Random(7)), accounts)

        days.indices.forEach { i ->
            assertThat(result[TxId("o$i")]?.partner).isEqualTo(TxId("i$i"))
            assertThat(result[TxId("i$i")]?.partner).isEqualTo(TxId("o$i"))
        }
    }

    @Test
    fun `same-day duplicates all pair, each partner used once`() {
        val rows = (0 until 3).flatMap { i ->
            listOf(tx("o$i", "acc-a", "2026-05-25", -5000.0), tx("i$i", "acc-b", "2026-05-25", 5000.0))
        }

        val result = matcher.match(rows, accounts)

        val partners = (0 until 3).map { result.getValue(TxId("o$it")).partner }
        assertThat(partners.toSet()).hasSize(3)
        assertThat(result.values.count { it.kind == TransferKind.INTERNAL }).isEqualTo(6)
    }

    @Test
    fun `property - matching is always one to one and never pairs an account with itself`() {
        val random = Random(42)
        repeat(200) {
            val rows = (0 until random.nextInt(2, 30)).map { i ->
                val amount = listOf(500.0, 2500.0, 10000.0, 1234.5)[random.nextInt(4)]
                tx(
                    id = "t$i",
                    account = accounts[random.nextInt(3)].id.value,
                    date = "2026-05-${random.nextInt(1, 4).toString().padStart(2, '0')}",
                    kronor = if (random.nextBoolean()) amount else -amount,
                    text = if (random.nextBoolean()) "Överföring" else "Swish",
                )
            }
            val byId = rows.associateBy { it.id }
            val result = matcher.match(rows, accounts)
            val pairs = result.filterValues { it.partner != null }
            pairs.forEach { (id, r) ->
                val a = byId.getValue(id)
                val b = byId.getValue(r.partner!!)
                assertThat(result.getValue(b.id).partner).isEqualTo(a.id)
                assertThat(a.accountId == b.accountId).isEqualTo(false)
                assertThat(a.amount).isEqualTo(-b.amount)
                assertThat(a.bookingDate).isEqualTo(b.bookingDate)
            }
        }
    }

    @Test
    fun `an unequal group spread over several accounts goes to review instead of a guess`() {
        val out = tx("o1", "acc-a", "2026-05-25", -3000.0, "Hyra")
        val inB = tx("i1", "acc-b", "2026-05-25", 3000.0, "Swish")
        val inC = tx("i2", "acc-c", "2026-05-25", 3000.0, "Swish")

        val result = matcher.match(listOf(out, inB, inC), accounts)

        assertThat(result[out.id]?.partner).isNull()
    }

    @Test
    fun `small round amounts need a hint`() {
        val out = tx("o1", "acc-a", "2026-05-25", -500.0, "Swish till Vän")
        val inn = tx("i1", "acc-b", "2026-05-25", 500.0, "Swish från Annan")
        assertThat(matcher.match(listOf(out, inn), accounts)).doesNotContainKey(out.id)

        val hinted = out.copy(rawText = "Överföring")
        assertThat(matcher.match(listOf(hinted, inn), accounts)[hinted.id]?.kind).isEqualTo(TransferKind.INTERNAL)
    }

    @Test
    fun `an unpaired transfer to a savings institution counts as savings, others are lone legs`() {
        val toAvanza = tx("s1", "acc-a", "2026-05-25", -1000.0, "9550.12.345.67")
        val toPartner = tx("p1", "acc-a", "2026-05-25", -4321.0, "8327-9,123 456 789-0")
        val toPartnerPlain = tx("p2", "acc-a", "2026-05-26", -4321.0, "12345678901")

        val result = matcher.match(listOf(toAvanza, toPartner, toPartnerPlain), accounts)

        assertThat(result[toAvanza.id]?.kind).isEqualTo(TransferKind.TO_SAVINGS)
        assertThat(result[toPartnerPlain.id]?.kind).isEqualTo(TransferKind.LONE_LEG)
    }

    @Test
    fun `a transfer to a savings account is savings, from it negative savings`() {
        val withSavings = accounts + Account(AccountId("acc-s"), "Sparkonto", AccountType.SAVINGS)
        val out = tx("o1", "acc-a", "2026-05-25", -2000.0)
        val inn = tx("i1", "acc-s", "2026-05-25", 2000.0)

        val result = matcher.match(listOf(out, inn), withSavings)

        assertThat(result[out.id]?.kind).isEqualTo(TransferKind.TO_SAVINGS)
    }
}
