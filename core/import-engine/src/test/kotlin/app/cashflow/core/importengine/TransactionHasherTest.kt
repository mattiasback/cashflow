package app.cashflow.core.importengine

import app.cashflow.core.categorization.PayeeNormalizer
import app.cashflow.core.domain.model.AccountId
import app.cashflow.core.domain.testing.Fixtures.row
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.hasSize
import org.junit.jupiter.api.Test

class TransactionHasherTest {

    private val hasher = TransactionHasher()
    private val account = AccountId("acc-a")

    @Test
    fun `the id ignores the normalizer, so improving it never changes ids`() {
        val r = row("2026-05-03", "SumUp  *KAFE EXEMPEL 12,STAD,SE", -45.0, 955.0)
        val before = hasher.hash(account, r)
        // Whatever the normalizer makes of the text, the id is computed from the raw text only.
        val normalized = PayeeNormalizer().normalize(r.rawText)
        assertThat(normalized).isNotEqualTo(r.rawText)
        assertThat(hasher.hash(account, r)).isEqualTo(before)
        assertThat(hasher.hash(account, r.copy(rawText = r.rawText + "  "))).isEqualTo(before)
    }

    @Test
    fun `identical same-day rows differ by the balance after them`() {
        val a = row("2026-05-03", "BUSSBILJETT", -30.0, 970.0)
        val b = row("2026-05-03", "BUSSBILJETT", -30.0, 940.0)
        assertThat(hasher.hash(account, a)).isNotEqualTo(hasher.hash(account, b))
    }

    @Test
    fun `without a balance column identical rows use their order that day`() {
        val a = row("2026-05-03", "BUSSBILJETT", -30.0, null)
        val ids = hasher.hashAll(account, listOf(a, a, a))
        assertThat(ids.toSet()).hasSize(3)
        assertThat(hasher.hashAll(account, listOf(a, a, a))).isEqualTo(ids)
    }

    @Test
    fun `the account is part of the id`() {
        val r = row("2026-05-03", "X", -1.0, 1.0)
        assertThat(hasher.hash(AccountId("a"), r)).isNotEqualTo(hasher.hash(AccountId("b"), r))
    }
}
