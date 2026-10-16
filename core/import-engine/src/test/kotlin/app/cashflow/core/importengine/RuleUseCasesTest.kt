package app.cashflow.core.importengine

import app.cashflow.core.categorization.BundledCatalog
import app.cashflow.core.categorization.Categorizer
import app.cashflow.core.categorization.PayeeNormalizer
import app.cashflow.core.domain.model.Account
import app.cashflow.core.domain.model.AccountId
import app.cashflow.core.domain.model.AccountType
import app.cashflow.core.domain.model.CategorizedBy
import app.cashflow.core.domain.model.CategoryId
import app.cashflow.core.domain.model.CategoryRule
import app.cashflow.core.domain.model.MerchantMemory
import app.cashflow.core.domain.model.Money
import app.cashflow.core.domain.model.RuleMatch
import app.cashflow.core.domain.model.TxId
import app.cashflow.core.domain.testing.Fixtures.tx
import app.cashflow.core.domain.testing.InMemoryAccountDataSource
import app.cashflow.core.domain.testing.InMemoryMerchantMemoryDataSource
import app.cashflow.core.domain.testing.InMemoryRuleDataSource
import app.cashflow.core.domain.testing.InMemoryTransactionDataSource
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** Your own rules: reach, "Använd på tidigare köp" and delete. All data is fictional. */
class RuleUseCasesTest {

    private val transactions = InMemoryTransactionDataSource()
    private val accounts = InMemoryAccountDataSource().apply { accounts.value = listOf(Account(AccountId("acc-a"), "Konto", AccountType.TRANSACTION)) }
    private val rules = InMemoryRuleDataSource()
    private val memories = InMemoryMerchantMemoryDataSource()
    private val normalizer = PayeeNormalizer()
    private val reclassify = ReclassifyUseCase(transactions, accounts, memories, rules, BundledCatalog.load(), TransferMatcher(), Categorizer(normalizer))
    private val useCases = RuleUseCases(rules, memories, transactions, normalizer, reclassify)

    private val rule = CategoryRule("r1", "BOLÅN", RuleMatch.STARTS_WITH, CategoryId("boende"), isBuiltIn = false, minAmount = Money.kr(5000))

    private fun seed() {
        transactions.rows.update {
            listOf(
                tx("big", kronor = -9000.0, text = "BOLÅN 1234", category = CategoryId("lan"), categorizedBy = CategorizedBy.MANUAL),
                tx("small", kronor = -300.0, text = "BOLÅN AVGIFT"),
                tx("other", kronor = -9000.0, text = "HYRA EXEMPEL"),
            ).associateBy { it.id }
        }
    }

    @Test
    fun `reach counts matches and rows you set to something else`() = runTest {
        seed()
        val reach = useCases.reach(rule, transactions.rows.value.values.toList())
        assertThat(reach).isEqualTo(RuleReach(matches = 1, pastWithOtherCategory = 1))
    }

    @Test
    fun `apply to earlier purchases moves rows you set by hand, and forgets their payee`() = runTest {
        seed()
        memories.remember(MerchantMemory(normalizer.key("BOLÅN 1234"), CategoryId("lan")))
        useCases.save(rule, applyToPast = true)
        val big = transactions.rows.value.getValue(TxId("big"))
        assertThat(big.categoryId).isEqualTo(CategoryId("boende"))
        assertThat(big.categorizedBy).isEqualTo(CategorizedBy.USER_RULE)
        assertThat(memories.memories.value).isEmpty()
    }

    @Test
    fun `without apply, rows you set by hand keep their category, and delete restores the rest`() = runTest {
        seed()
        useCases.save(rule.copy(minAmount = null), applyToPast = false)
        assertThat(transactions.rows.value.getValue(TxId("big")).categoryId).isEqualTo(CategoryId("lan"))
        assertThat(transactions.rows.value.getValue(TxId("small")).categorizedBy).isEqualTo(CategorizedBy.USER_RULE)

        useCases.delete(rule.id)
        assertThat(transactions.rows.value.getValue(TxId("small")).categorizedBy).isEqualTo(CategorizedBy.BUILT_IN_RULE)
    }
}
