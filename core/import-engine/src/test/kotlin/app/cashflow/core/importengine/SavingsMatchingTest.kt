package app.cashflow.core.importengine

import app.cashflow.core.domain.model.Account
import app.cashflow.core.domain.model.AccountId
import app.cashflow.core.domain.model.AccountType
import app.cashflow.core.domain.model.Movement
import app.cashflow.core.domain.model.TransferKind
import app.cashflow.core.domain.testing.Fixtures.tx
import assertk.assertThat
import assertk.assertions.doesNotContainKey
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

/** Pairing between the bank and savings exports. All names and amounts are fictional. */
class SavingsMatchingTest {

    private val matcher = TransferMatcher()
    private val accounts = listOf(
        Account(AccountId("bank-a"), "Lönekonto", AccountType.TRANSACTION),
        Account(AccountId("bank-b"), "Gemensamt", AccountType.TRANSACTION),
        Account(AccountId("save"), "Sparkonto Exempel", AccountType.SAVINGS, institution = "Avanza", externalKey = "avanza:Sparkonto Exempel"),
        Account(AccountId("isk"), "ISK Exempel", AccountType.ISK, institution = "Avanza", externalKey = "avanza:ISK Exempel"),
    )

    @Test
    fun `a bank outflow and a savings deposit within three days pair as savings`() {
        val out = tx("o", "bank-a", "2026-05-27", -6000.0, "Lysa Spar")
        val dep = tx("d", "isk", "2026-05-29", 6000.0, "Insättning Autogiro", countsInCashFlow = false, movement = Movement.DEPOSIT)

        val result = matcher.match(listOf(out, dep), accounts)

        assertThat(result[out.id]).isEqualTo(TransferResult(TransferKind.TO_SAVINGS, dep.id))
        assertThat(result[dep.id]).isEqualTo(TransferResult(TransferKind.TO_SAVINGS, out.id))
    }

    @Test
    fun `a withdrawal to the bank is negative savings`() {
        val wd = tx("w", "save", "2026-09-18", -20000.0, "Uttag", countsInCashFlow = false, movement = Movement.WITHDRAWAL)
        val inn = tx("i", "bank-a", "2026-09-18", 20000.0, "Från sparkonto")

        assertThat(matcher.match(listOf(wd, inn), accounts)[inn.id]).isEqualTo(TransferResult(TransferKind.FROM_SAVINGS, wd.id))
    }

    @Test
    fun `small round amounts between bank and savings pair only on the same day`() {
        val weekly = tx("d", "save", "2026-05-06", 500.0, "Insättning", countsInCashFlow = false, movement = Movement.DEPOSIT)
        val sameDay = tx("o1", "bank-a", "2026-05-06", -500.0, "Buffert")
        val earlier = tx("o2", "bank-a", "2026-05-04", -500.0, "Överf ISK")

        val result = matcher.match(listOf(weekly, sameDay, earlier), accounts)

        assertThat(result[weekly.id]?.partner).isEqualTo(sameDay.id)
        assertThat(result[earlier.id]?.partner).isEqualTo(null)
    }

    @Test
    fun `a transfer between two bank accounts is never taken by a nearby savings deposit`() {
        val out = tx("o", "bank-a", "2026-02-06", -10000.0, "Överföring")
        val inn = tx("i", "bank-b", "2026-02-06", 10000.0, "Överföring")
        val dep = tx("d", "save", "2026-02-05", 10000.0, "Insättning", countsInCashFlow = false, movement = Movement.DEPOSIT)

        val result = matcher.match(listOf(out, inn, dep), accounts)

        assertThat(result[out.id]).isEqualTo(TransferResult(TransferKind.INTERNAL, inn.id))
        assertThat(result).doesNotContainKey(dep.id)
    }

    @Test
    fun `trades, return and cost inside savings never pair`() {
        val buy = tx("b", "isk", "2026-05-10", -3000.0, "Köp Fond", countsInCashFlow = false, movement = Movement.TRADE)
        val out = tx("o", "bank-a", "2026-05-10", 3000.0, "Swish från vän")
        val fee = tx("f", "isk", "2026-05-10", -3000.0, "Avgift", countsInCashFlow = false, movement = Movement.COST)

        val result = matcher.match(listOf(buy, out, fee), accounts)

        assertThat(result).doesNotContainKey(buy.id)
        assertThat(result).doesNotContainKey(fee.id)
    }

    @Test
    fun `moves between two savings accounts are internal, and a deposit never pairs inside savings`() {
        val outInternal = tx("o", "save", "2026-06-01", -200.0, "Intern överföring", countsInCashFlow = false, movement = Movement.INTERNAL_OUT)
        val inInternal = tx("i", "isk", "2026-06-01", 200.0, "Intern överföring", countsInCashFlow = false, movement = Movement.INTERNAL_IN)
        val deposit = tx("d", "isk", "2026-06-01", 200.0, "Insättning", countsInCashFlow = false, movement = Movement.DEPOSIT)

        val result = matcher.match(listOf(outInternal, inInternal, deposit), accounts)

        assertThat(result[outInternal.id]).isEqualTo(TransferResult(TransferKind.INTERNAL, inInternal.id))
        assertThat(result).doesNotContainKey(deposit.id)
    }

    @Test
    fun `an unpaired savings row with an account number in its text stays inside savings`() {
        val wd = tx("w", "save", "2025-12-23", -35000.0, "Uttag 1234.56.789.01", countsInCashFlow = false, movement = Movement.WITHDRAWAL)
        assertThat(matcher.match(listOf(wd), accounts)).doesNotContainKey(wd.id)
    }
}
