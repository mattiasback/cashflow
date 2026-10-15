package app.cashflow.core.importengine

import app.cashflow.core.domain.model.AccountId
import app.cashflow.core.domain.model.BalanceState
import app.cashflow.core.domain.model.StatementRow
import app.cashflow.core.domain.model.TxId
import app.cashflow.core.domain.testing.Fixtures.chain
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.random.Random

class AccountInferrerTest {

    private val hasher = TransactionHasher()
    private val inferrer = AccountInferrer(hasher)
    private val a = AccountId("acc-a")
    private val b = AccountId("acc-b")

    // Two fictional accounts with different running balances.
    private val aJan = chain(1000.0, Triple("2026-01-05", "MATBUTIK", -200.0), Triple("2026-01-25", "LÖN", 20000.0))
    private val aFeb = chain(20800.0, Triple("2026-02-03", "HYRA", -8000.0), Triple("2026-02-25", "LÖN", 20000.0))
    private val aMar = chain(32800.0, Triple("2026-03-02", "EL", -900.0), Triple("2026-03-25", "LÖN", 20000.0))
    private val bJan = chain(500.0, Triple("2026-01-10", "SWISH", -100.0), Triple("2026-01-20", "SWISH", 50.0))
    private val bFeb = chain(450.0, Triple("2026-02-10", "SWISH", -50.0))

    private fun states(account: AccountId, rows: List<StatementRow>) = rows.map { BalanceState(account, it.bookingDate, it.balanceAfter!!) }

    private suspend fun infer(
        files: List<List<StatementRow>>,
        known: List<AccountId> = listOf(a, b),
        states: List<BalanceState> = emptyList(),
        stored: Set<TxId> = emptySet(),
    ) = inferrer.infer(files.mapIndexed { i, r -> InferenceInput(i, r) }, known, states, { ids -> ids.filter { it in stored }.toSet() })

    @Test
    fun `a file that continues an account's last balance belongs to it`() = runTest {
        val result = infer(listOf(aFeb), states = states(a, aJan) + states(b, bJan))
        assertThat(result.getValue(0)).isEqualTo(AccountGuess.Known(a, 0))
    }

    @Test
    fun `an overlapping export is recognised by rows that already exist`() = runTest {
        val overlapping = aJan.takeLast(1) + aFeb
        val stored = hasher.hashAll(a, aJan).toSet()
        val result = infer(listOf(overlapping), states = states(a, aJan), stored = stored)
        assertThat(result.getValue(0)).isEqualTo(AccountGuess.Known(a, 1))
    }

    @Test
    fun `several files in any order chain through each other`() = runTest {
        repeat(10) { seed ->
            val files = listOf(aFeb, aMar, bFeb).shuffled(Random(seed))
            val result = infer(files, states = states(a, aJan) + states(b, bJan))
            files.forEachIndexed { i, f ->
                val expected = if (f === bFeb) b else a
                assertThat((result.getValue(i) as AccountGuess.Known).accountId).isEqualTo(expected)
            }
        }
    }

    @Test
    fun `a gap is asked about once for the whole chain`() = runTest {
        // February is missing, so March cannot chain to January.
        val result = infer(listOf(aMar), states = states(a, aJan))
        assertThat(result.getValue(0)).isInstanceOf(AccountGuess.Ask::class)
    }

    @Test
    fun `two accounts at the same balance are not guessed`() = runTest {
        val zeroA = listOf(BalanceState(a, kotlinx.datetime.LocalDate(2026, 1, 31), app.cashflow.core.domain.model.Money.ZERO))
        val zeroB = listOf(BalanceState(b, kotlinx.datetime.LocalDate(2026, 1, 31), app.cashflow.core.domain.model.Money.ZERO))
        val file = chain(0.0, Triple("2026-02-02", "INSÄTTNING", 100.0))

        val guess = infer(listOf(file), states = zeroA + zeroB).getValue(0)

        assertThat(guess).isInstanceOf(AccountGuess.Ask::class)
        assertThat((guess as AccountGuess.Ask).candidates).containsExactlyInAnyOrder(a, b)
    }

    @Test
    fun `a first import groups files by their own chains`() = runTest {
        val result = infer(listOf(aJan, bJan, aFeb, bFeb, aMar), known = emptyList())
        val groups = result.mapValues { (it.value as AccountGuess.Ask).group }
        assertThat(groups[0]).isEqualTo(groups[2])
        assertThat(groups[0]).isEqualTo(groups[4])
        assertThat(groups[1]).isEqualTo(groups[3])
        assertThat(groups[0] == groups[1]).isEqualTo(false)
    }
}
