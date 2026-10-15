package app.cashflow.core.importengine

import app.cashflow.core.categorization.BundledCatalog
import app.cashflow.core.categorization.Categorizer
import app.cashflow.core.categorization.PayeeNormalizer
import app.cashflow.core.domain.Result
import app.cashflow.core.domain.model.Account
import app.cashflow.core.domain.model.AccountId
import app.cashflow.core.domain.model.AccountType
import app.cashflow.core.domain.model.CategoryId
import app.cashflow.core.domain.model.ImportSource
import app.cashflow.core.domain.model.ParsedStatement
import app.cashflow.core.domain.model.TransferKind
import app.cashflow.core.domain.testing.Fixtures.chain
import app.cashflow.core.domain.testing.InMemoryAccountDataSource
import app.cashflow.core.domain.testing.InMemoryMerchantMemoryDataSource
import app.cashflow.core.domain.testing.InMemoryRuleDataSource
import app.cashflow.core.domain.testing.InMemoryTransactionDataSource
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ImportStatementUseCaseTest {

    private val transactions = InMemoryTransactionDataSource()
    private val accounts = InMemoryAccountDataSource()
    private val hasher = TransactionHasher()
    private val normalizer = PayeeNormalizer()
    private val useCase = ImportStatementUseCase(
        transactions = transactions,
        accounts = accounts,
        inferrer = AccountInferrer(hasher),
        hasher = hasher,
        normalizer = normalizer,
        reclassify = ReclassifyUseCase(
            transactions, accounts, InMemoryMerchantMemoryDataSource(), InMemoryRuleDataSource(),
            BundledCatalog.load(), TransferMatcher(), Categorizer(normalizer),
        ),
    )

    private val salaryAccount = chain(
        1000.0,
        Triple("2026-04-25", "LÖN", 25000.0),
        Triple("2026-04-26", "Överföring", -12000.0),
        Triple("2026-04-27", "ICA NARA EXEMPEL,STAD,SE", -450.0),
    )
    private val sharedAccount = chain(
        300.0,
        Triple("2026-04-26", "Överföring", 12000.0),
        Triple("2026-04-28", "HYRA BRF EXEMPEL", -9000.0),
    )

    private fun statement(name: String, rows: List<app.cashflow.core.domain.model.StatementRow>) =
        ParsedStatement(name, ImportSource.LANSFORSAKRINGAR_XLS, rows, emptyList())

    private suspend fun importBoth(): ImportOutcome {
        val plan = (useCase.plan(listOf(statement("a.xls", salaryAccount), statement("b.xls", sharedAccount))) as Result.Success).data
        val answers = plan.questions.mapIndexed { i, q ->
            q.group to AccountAnswer.New(Account(AccountId("acc-$i"), "Konto ${i + 1}", AccountType.TRANSACTION))
        }.toMap()
        return (useCase.execute(plan, answers) as Result.Success).data
    }

    @Test
    fun `first import asks once per account, stores rows, pairs the transfer and categorizes`() = runTest {
        val plan = (useCase.plan(listOf(statement("a.xls", salaryAccount), statement("b.xls", sharedAccount))) as Result.Success).data
        assertThat(plan.questions).hasSize(2)

        val outcome = importBoth()

        assertThat(outcome.newCount).isEqualTo(5)
        val rows = transactions.rows.value.values
        assertThat(rows.count { it.transferKind == TransferKind.INTERNAL }).isEqualTo(2)
        assertThat(rows.first { it.rawText.startsWith("ICA") }.categoryId).isEqualTo(CategoryId("mat"))
        assertThat(rows.first { it.rawText == "LÖN" }.categoryId).isEqualTo(CategoryId("lon"))
    }

    @Test
    fun `importing the same files again finds the accounts and adds nothing`() = runTest {
        importBoth()

        val again = (useCase.plan(listOf(statement("b.xls", sharedAccount), statement("a.xls", salaryAccount))) as Result.Success).data
        assertThat(again.questions).hasSize(0)
        again.files.forEach { assertThat(it.guess).isInstanceOf(AccountGuess.Known::class) }

        val outcome = (useCase.execute(again, emptyMap()) as Result.Success).data
        assertThat(outcome.newCount).isEqualTo(0)
        assertThat(outcome.duplicateCount).isEqualTo(5)
        assertThat(accounts.accounts.value).hasSize(2)
    }
}
