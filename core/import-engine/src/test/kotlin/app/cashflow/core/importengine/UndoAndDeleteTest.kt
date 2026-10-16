package app.cashflow.core.importengine

import app.cashflow.core.categorization.BundledCatalog
import app.cashflow.core.categorization.Categorizer
import app.cashflow.core.categorization.PayeeNormalizer
import app.cashflow.core.domain.Result
import app.cashflow.core.domain.cashflow.CashFlowCalculator
import app.cashflow.core.domain.model.Account
import app.cashflow.core.domain.model.AccountId
import app.cashflow.core.domain.model.AccountType
import app.cashflow.core.domain.model.CategoryKind
import app.cashflow.core.domain.model.ImportSource
import app.cashflow.core.domain.model.ParsedStatement
import app.cashflow.core.domain.model.Period
import app.cashflow.core.domain.model.StatementRow
import app.cashflow.core.domain.model.TransferKind
import app.cashflow.core.domain.testing.Fixtures.chain
import app.cashflow.core.domain.testing.InMemoryAccountDataSource
import app.cashflow.core.domain.testing.InMemoryImportBatchDataSource
import app.cashflow.core.domain.testing.InMemoryMerchantMemoryDataSource
import app.cashflow.core.domain.testing.InMemoryRuleDataSource
import app.cashflow.core.domain.testing.InMemoryTransactionDataSource
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** Undo, redo and delete keep totals and transfer pairs exact. All data is fictional. */
class UndoAndDeleteTest {

    private val batches = InMemoryImportBatchDataSource()
    private val transactions = InMemoryTransactionDataSource(batches)
    private val accounts = InMemoryAccountDataSource()
    private val hasher = TransactionHasher()
    private val normalizer = PayeeNormalizer()
    private val reclassify = ReclassifyUseCase(
        transactions, accounts, InMemoryMerchantMemoryDataSource(), InMemoryRuleDataSource(), BundledCatalog.load(), TransferMatcher(), Categorizer(normalizer),
    )
    private val importer = ImportStatementUseCase(transactions, accounts, AccountInferrer(hasher), hasher, normalizer, reclassify)
    private val undo = UndoImportUseCase(transactions, reclassify)
    private val hideDelete = HideDeleteUseCase(transactions, reclassify)
    private val kinds = BundledCatalog.load()

    private val salary = chain(
        1000.0,
        Triple("2026-04-25", "LÖN", 25000.0),
        Triple("2026-04-26", "Överföring", -12000.0),
        Triple("2026-04-27", "ICA NARA EXEMPEL,STAD,SE", -450.0),
    )
    private val shared = chain(
        300.0,
        Triple("2026-04-26", "Överföring", 12000.0),
        Triple("2026-04-28", "HYRA BRF EXEMPEL", -9000.0),
    )

    private fun statement(name: String, rows: List<StatementRow>) = ParsedStatement(name, ImportSource.LANSFORSAKRINGAR_XLS, rows, emptyList())

    private suspend fun import(vararg files: ParsedStatement): ImportOutcome {
        val plan = (importer.plan(files.toList()) as Result.Success).data
        val answers = plan.questions.mapIndexed { i, q ->
            q.group to AccountAnswer.New(Account(AccountId("acc-${accounts.accounts.value.size + i}"), "Konto", AccountType.TRANSACTION))
        }.toMap()
        return (importer.execute(plan, answers) as Result.Success).data
    }

    private suspend fun summary(): app.cashflow.core.domain.cashflow.CashFlowSummary {
        val categories = kinds.defaultCategories().associate { it.id to it.kind }
        return CashFlowCalculator().summarize(
            Period(2026, 4),
            transactions.rows.value.values.toList(),
            kindOf = { id -> categories[id] ?: CategoryKind.UNCATEGORIZED },
        )
    }

    @Test
    fun `undoing an import restores the previous totals and pairs, and redo brings it back`() = runTest {
        import(statement("a.xls", salary))
        val before = summary()
        val pairsBefore = transactions.rows.value.values.associate { it.id to it.transferKind }

        val second = import(statement("b.xls", shared))
        assertThat(transactions.rows.value.values.count { it.transferKind == TransferKind.INTERNAL }).isEqualTo(2)

        undo.undo(second.importIds)
        assertThat(summary()).isEqualTo(before)
        assertThat(transactions.rows.value.values.associate { it.id to it.transferKind }).isEqualTo(pairsBefore)
        assertThat(batches.batches.value.first { it.id == second.importIds.single() }.undoneAt).isNotNull()

        undo.redo(second.importIds.single())
        assertThat(transactions.rows.value.values.count { it.transferKind == TransferKind.INTERNAL }).isEqualTo(2)
        assertThat(batches.batches.value.first { it.id == second.importIds.single() }.undoneAt).isNull()
    }

    @Test
    fun `a deleted row stays deleted after re-importing the same file`() = runTest {
        import(statement("a.xls", salary))
        val food = transactions.rows.value.values.first { it.rawText.startsWith("ICA") }
        hideDelete.delete(food.id)

        val again = import(statement("a.xls", salary))

        assertThat(again.newCount).isEqualTo(0)
        assertThat(transactions.rows.value.containsKey(food.id)).isEqualTo(false)
    }

    @Test
    fun `a hidden row leaves the totals but stays listed`() = runTest {
        import(statement("a.xls", salary))
        val food = transactions.rows.value.values.first { it.rawText.startsWith("ICA") }
        val before = summary().expenses
        hideDelete.setHidden(food.id, true)
        assertThat(summary().expenses).isEqualTo(before - food.amount.abs())
        assertThat(transactions.rows.value.containsKey(food.id)).isEqualTo(true)
    }
}
