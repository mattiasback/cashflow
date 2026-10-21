package app.cashflow.core.importengine

import app.cashflow.core.categorization.BundledCatalog
import app.cashflow.core.categorization.Categorizer
import app.cashflow.core.categorization.PayeeNormalizer
import app.cashflow.core.domain.model.Account
import app.cashflow.core.domain.model.AccountId
import app.cashflow.core.domain.model.AccountType
import app.cashflow.core.domain.model.CategoryId
import app.cashflow.core.domain.model.TransactionStatus
import app.cashflow.core.domain.model.TransferKind
import app.cashflow.core.domain.testing.Fixtures.tx
import app.cashflow.core.domain.testing.InMemoryAccountDataSource
import app.cashflow.core.domain.testing.InMemoryImportBatchDataSource
import app.cashflow.core.domain.testing.InMemoryMerchantMemoryDataSource
import app.cashflow.core.domain.testing.InMemoryRuleDataSource
import app.cashflow.core.domain.testing.InMemoryTransactionDataSource
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * "Kom ihåg" on a payment to an account that is never imported (a lone leg) must take it out of
 * review, as it does for every other payee. All data is fictional.
 */
class RememberLoneLegTest {

    private val transactions = InMemoryTransactionDataSource(InMemoryImportBatchDataSource())
    private val accounts = InMemoryAccountDataSource()
    private val memories = InMemoryMerchantMemoryDataSource()
    private val normalizer = PayeeNormalizer()
    private val reclassify = ReclassifyUseCase(
        transactions, accounts, memories, InMemoryRuleDataSource(), BundledCatalog.load(), TransferMatcher(), Categorizer(normalizer),
    )
    private val categorize = CategorizeUseCase(transactions, memories, normalizer, reclassify)

    // Account-number texts with no other leg: the matcher calls them lone legs.
    private val rows = listOf(
        tx("1", date = "2026-08-30", kronor = -2717.0, text = "1234 56 78901"),
        tx("2", date = "2026-09-30", kronor = -2717.0, text = "1234 56 78901"),
    )

    private suspend fun setUp() {
        accounts.upsertAccount(Account(AccountId("acc-a"), "Lönekonto", AccountType.TRANSACTION))
        transactions.rows.value = rows.associateBy { it.id }
        reclassify()
    }

    @Test
    fun `remembering a category for a lone leg takes it out of review`() = runTest {
        setUp()
        val before = transactions.rows.value.values
        assertThat(before.all { it.transferKind == TransferKind.LONE_LEG && it.status == TransactionStatus.TO_REVIEW }).isTrue()

        categorize.categorize(before.toList(), CategoryId.HOUSING, remember = true)

        val after = transactions.rows.value.values
        assertThat(after.map { it.status }.toSet()).isEqualTo(setOf(TransactionStatus.AUTO))
        assertThat(after.map { it.categoryId }.toSet()).isEqualTo(setOf(CategoryId.HOUSING))
        assertThat(after.map { it.transferKind }.toSet()).isEqualTo(setOf(TransferKind.NONE))
    }

    @Test
    fun `remembering Överföring confirms it as a household transfer`() = runTest {
        setUp()
        categorize.categorize(transactions.rows.value.values.toList(), CategoryId.TRANSFER, remember = true)

        val after = transactions.rows.value.values
        assertThat(after.all { it.loneLegConfirmed && it.status != TransactionStatus.TO_REVIEW }).isTrue()
    }
}
