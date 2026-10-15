package app.cashflow.feature.importer.data

import app.cashflow.core.categorization.BundledCatalog
import app.cashflow.core.categorization.Categorizer
import app.cashflow.core.categorization.PayeeNormalizer
import app.cashflow.core.domain.Result
import app.cashflow.core.domain.model.Account
import app.cashflow.core.domain.model.AccountId
import app.cashflow.core.domain.model.AccountType
import app.cashflow.core.domain.model.CategorizedBy
import app.cashflow.core.domain.model.MerchantMemory
import app.cashflow.core.domain.model.ParsedStatement
import app.cashflow.core.domain.model.TransferKind
import app.cashflow.core.domain.testing.InMemoryAccountDataSource
import app.cashflow.core.domain.testing.InMemoryMerchantMemoryDataSource
import app.cashflow.core.domain.testing.InMemoryRuleDataSource
import app.cashflow.core.domain.testing.InMemoryTransactionDataSource
import app.cashflow.core.importengine.AccountAnswer
import app.cashflow.core.importengine.AccountInferrer
import app.cashflow.core.importengine.ImportStatementUseCase
import app.cashflow.core.importengine.ReclassifyUseCase
import app.cashflow.core.importengine.TransactionHasher
import app.cashflow.core.importengine.TransferMatcher
import app.cashflow.feature.importer.data.profile.LansforsakringarProfile
import app.cashflow.feature.importer.domain.PickedFile
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThanOrEqualTo
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.random.Random

/**
 * Step 1 acceptance on the developer's real exports (skipped when they are absent).
 * Asserts and prints aggregates only: never text, names or account numbers.
 */
class PrivatePipelineTest {

    private val dir = System.getProperty("cashflow.privateStatements")?.let(::File)
    private val reader = ProfileStatementReader(listOf(LansforsakringarProfile()))

    private fun statements(): List<ParsedStatement> {
        assumeTrue(dir != null && dir.isDirectory, "no private statements folder")
        val files = dir!!.listFiles { f -> f.name.startsWith("Kontoutdrag") && f.name.endsWith(".xls") }.orEmpty()
        assumeTrue(files.isNotEmpty(), "no Länsförsäkringar exports")
        return files.map { (reader.read(PickedFile(it.name, it.readBytes())) as Result.Success).data }
    }

    private class Pipeline {
        val transactions = InMemoryTransactionDataSource()
        val accounts = InMemoryAccountDataSource()
        val memories = InMemoryMerchantMemoryDataSource()
        val normalizer = PayeeNormalizer()
        val hasher = TransactionHasher()
        val reclassify = ReclassifyUseCase(
            transactions, accounts, memories, InMemoryRuleDataSource(), BundledCatalog.load(), TransferMatcher(), Categorizer(normalizer),
        )
        val useCase = ImportStatementUseCase(transactions, accounts, AccountInferrer(hasher), hasher, normalizer, reclassify)

        suspend fun import(files: List<ParsedStatement>): Pair<Int, Int> {
            val plan = (useCase.plan(files) as Result.Success).data
            val answers = plan.questions.associate { q ->
                q.group to AccountAnswer.New(Account(AccountId("acc-${q.group}-${accounts.accounts.value.size}"), "Konto", AccountType.TRANSACTION))
            }
            val outcome = (useCase.execute(plan, answers) as Result.Success).data
            return plan.questions.size to outcome.newCount
        }
    }

    @Test
    fun `importing all files in any order twice gives the same accounts and no new rows`() = runTest {
        val all = statements()
        repeat(3) { seed ->
            val p = Pipeline()
            val (questions, newRows) = p.import(all.shuffled(Random(seed)))
            val (questionsAgain, newAgain) = p.import(all.shuffled(Random(seed + 100)))

            assertThat(p.accounts.accounts.value.size).isEqualTo(3)
            assertThat(questions).isEqualTo(3)
            assertThat(questionsAgain).isEqualTo(0)
            assertThat(newAgain).isEqualTo(0)
            println("PRIVATE seed=$seed: questions=$questions rows=$newRows accounts=${p.accounts.accounts.value.size} reimportNew=$newAgain")
        }
    }

    @Test
    fun `mirrored pairs are transfers and categorization hits the targets`() = runTest {
        val all = statements()
        val p = Pipeline()
        p.import(all)
        val rows = p.transactions.rows.value.values.toList()

        val internal = rows.count { it.transferKind == TransferKind.INTERNAL }
        val transferLike = rows.count { it.transferKind != TransferKind.NONE }
        val nonTransfer = rows.filter { it.transferKind == TransferKind.NONE }
        val auto = nonTransfer.count { it.categorizedBy != CategorizedBy.NONE }
        println("PRIVATE: rows=${rows.size} internalPairs=${internal / 2} transferLike=$transferLike lone=${rows.count { it.transferKind == TransferKind.LONE_LEG }} savings=${rows.count { it.transferKind == TransferKind.TO_SAVINGS || it.transferKind == TransferKind.FROM_SAVINGS }}")
        // Every row with a mirror (same day, opposite amount, other account) must be paired.
        val unpairedWithMirror = rows.filter { r ->
            r.transferKind != TransferKind.INTERNAL && rows.any { o ->
                o.accountId != r.accountId && o.bookingDate == r.bookingDate && o.amount == -r.amount && o.transferKind != TransferKind.INTERNAL
            }
        }
        println("PRIVATE: unpaired rows that have a mirror: ${unpairedWithMirror.size}")
        assertThat(internal % 2).isEqualTo(0)
        // Mirrored internal transfers never count as income or expense.
        val calc = app.cashflow.core.domain.cashflow.CashFlowCalculator()
        val internalOnly = rows.filter { it.transferKind == TransferKind.INTERNAL }
        val kinds = BundledCatalog.load().defaultCategories().associate { it.id to it.kind }
        internalOnly.map { it.period }.distinct().forEach { period ->
            val s = calc.summarize(period, internalOnly) { kinds.getValue(it) }
            assertThat(s.income.ore + s.expenses.ore + s.savings.ore).isEqualTo(0L)
        }
        println("PRIVATE: first import auto-categorized ${auto * 100 / nonTransfer.size.coerceAtLeast(1)} % of ${nonTransfer.size} non-transfer rows")
        assertThat(auto * 100 / nonTransfer.size).isGreaterThanOrEqualTo(40)

        // Remember Jan–Jun payees by their most common decision, then measure Jul–Sep.
        val firstHalf = nonTransfer.filter { it.transactionDate.month.ordinal < 6 }
        firstHalf.groupBy { p.normalizer.key(it.rawText) }.forEach { (key, txs) ->
            val manualGuess = txs.groupingBy { it.categoryId }.eachCount().maxByOrNull { it.value }!!.key
            // Simulates the user's review decision: payees with a category keep it; others get one.
            val decided = if (manualGuess.value == "okat") app.cashflow.core.domain.model.CategoryId("ovrigt_rorligt") else manualGuess
            p.memories.remember(MerchantMemory(key, decided))
        }
        p.reclassify()
        val secondHalf = p.transactions.rows.value.values.filter { it.transferKind == TransferKind.NONE && it.transactionDate.month.ordinal in 6..8 }
        val autoLater = secondHalf.count { it.categorizedBy != CategorizedBy.NONE }
        println("PRIVATE: Jul–Sep after remembering Jan–Jun: ${autoLater * 100 / secondHalf.size.coerceAtLeast(1)} % of ${secondHalf.size}")
        assertThat(autoLater * 100 / secondHalf.size).isGreaterThanOrEqualTo(70)
    }
}
