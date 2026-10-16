package app.cashflow.feature.importer.data

import app.cashflow.core.categorization.BundledCatalog
import app.cashflow.core.categorization.Categorizer
import app.cashflow.core.categorization.PayeeNormalizer
import app.cashflow.core.domain.Result
import app.cashflow.core.domain.cashflow.CashFlowCalculator
import app.cashflow.core.domain.model.Account
import app.cashflow.core.domain.model.AccountId
import app.cashflow.core.domain.model.AccountType
import app.cashflow.core.domain.model.ParsedStatement
import app.cashflow.core.domain.model.Period
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
import app.cashflow.feature.importer.data.profile.AvanzaProfile
import app.cashflow.feature.importer.data.profile.LansforsakringarProfile
import app.cashflow.feature.importer.data.profile.LysaProfile
import app.cashflow.feature.importer.domain.PickedFile
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isLessThanOrEqualTo
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.math.abs

/**
 * Step 2 acceptance on the developer's real bank, Lysa and Avanza exports (skipped when they
 * are absent). Asserts and prints aggregates only: never text, names or account numbers.
 */
class PrivateSavingsTest {

    private val dir = System.getProperty("cashflow.privateStatements")?.let(::File)
    private val reader = ProfileStatementReader(listOf(LansforsakringarProfile(), LysaProfile(), AvanzaProfile()))

    private fun all(): List<ParsedStatement> {
        assumeTrue(dir != null && dir.isDirectory, "no private statements folder")
        val files = dir!!.listFiles { f -> f.name.endsWith(".xls") || f.name.endsWith(".csv") }.orEmpty()
        val parsed = files.map { (reader.read(PickedFile(it.name, it.readBytes())) as Result.Success).data }
        assumeTrue(parsed.any { it.hasNamedAccounts }, "no savings exports")
        return parsed
    }

    private class Pipeline {
        val transactions = InMemoryTransactionDataSource()
        val accounts = InMemoryAccountDataSource()
        val normalizer = PayeeNormalizer()
        val hasher = TransactionHasher()
        val reclassify = ReclassifyUseCase(
            transactions, accounts, InMemoryMerchantMemoryDataSource(), InMemoryRuleDataSource(), BundledCatalog.load(), TransferMatcher(), Categorizer(normalizer),
        )
        val useCase = ImportStatementUseCase(transactions, accounts, AccountInferrer(hasher), hasher, normalizer, reclassify)

        suspend fun import(files: List<ParsedStatement>) {
            val plan = (useCase.plan(files) as Result.Success).data
            val answers = plan.questions.associate { q ->
                q.group to AccountAnswer.New(Account(AccountId("acc-${q.group}-${accounts.accounts.value.size}"), "Konto", AccountType.TRANSACTION))
            }
            // Accounts whose name says nothing: an investment account is the safe guess for the test.
            val types = plan.typeQuestions.associate { it.source.key to AccountType.ISK }
            useCase.execute(plan, answers, types) as Result.Success
        }
    }

    @Test
    fun `savings exports pair with their bank legs and net out`() = runTest {
        val p = Pipeline()
        p.import(all())
        val rows = p.transactions.rows.value.values.toList()
        val byId = rows.associateBy { it.id }
        val types = p.accounts.accounts.value.associate { it.id to it.type }
        val firstBankDay = rows.filter { it.countsInCashFlow }.minOf { it.bookingDate }
        val lastBankDay = rows.filter { it.countsInCashFlow }.maxOf { it.bookingDate }

        fun boundary(institution: String) = rows.filter { tx ->
            tx.movement?.isBoundary == true &&
                p.accounts.accounts.value.first { it.id == tx.accountId }.institution == institution &&
                tx.bookingDate in firstBankDay..lastBankDay
        }
        for (institution in listOf("Lysa", "Avanza")) {
            val legs = boundary(institution)
            val paired = legs.count { it.transferPartnerId != null }
            println("PRIVATE $institution: boundary rows in bank period=${legs.size} paired=$paired")
            assertThat(paired * 10).isGreaterThanOrEqualTo(legs.size * 9)
        }

        // Small round amounts crossing the boundary pair on the same day only.
        val crossPairs = rows.filter { it.countsInCashFlow && it.transferPartnerId != null }
            .mapNotNull { a -> byId[a.transferPartnerId!!]?.let { b -> a to b } }
            .filter { (a, b) -> types[a.accountId]?.holdsSavings != types[b.accountId]?.holdsSavings }
        val badSmall = crossPairs.count { (a, b) ->
            abs(a.amount.ore) < 100_000 && abs(a.amount.ore) % 10_000 == 0L && a.bookingDate != b.bookingDate
        }
        println("PRIVATE: bank-savings pairs=${crossPairs.size} smallRoundNotSameDay=$badSmall")
        assertThat(badSmall).isEqualTo(0)

        // Nothing inside savings counts in cash flow, and nothing inside savings waits for review.
        val inside = rows.filter { !it.countsInCashFlow }
        println("PRIVATE: savings rows=${inside.size} movements=${inside.groupingBy { it.movement }.eachCount()}")
        assertThat(inside.count { it.status == app.cashflow.core.domain.model.TransactionStatus.TO_REVIEW }).isEqualTo(0)

        // Monthly savings from the bank side, before and after the savings exports.
        val kinds = BundledCatalog.load().defaultCategories().associate { it.id to it.kind }
        val calc = CashFlowCalculator()
        val bankOnly = Pipeline().apply { import(all().filterNot { it.hasNamedAccounts }) }.transactions.rows.value.values.toList()
        for (month in 1..9) {
            val period = Period(2026, month)
            val with = calc.summarize(period, rows, kindOf = { kinds[it] ?: app.cashflow.core.domain.model.CategoryKind.UNCATEGORIZED })
            val without = calc.summarize(period, bankOnly, kindOf = { kinds[it] ?: app.cashflow.core.domain.model.CategoryKind.UNCATEGORIZED })
            println("PRIVATE 2026-$month: savings bankOnly=${without.savings.kronor} withSavingsExports=${with.savings.kronor} income=${with.income.kronor}/${without.income.kronor} expenses=${with.expenses.kronor}/${without.expenses.kronor}")
            // Savings exports only ever turn bank rows into savings; they never add income.
            assertThat(with.income).isEqualTo(without.income)
            assertThat(with.expenses.ore).isLessThanOrEqualTo(without.expenses.ore)
        }
        val unpairedTransferLike = rows.count { it.countsInCashFlow && it.transferKind == TransferKind.LONE_LEG }
        println("PRIVATE: bank lone legs=$unpairedTransferLike")
        assertThat(crossPairs.size).isGreaterThanOrEqualTo(1)
    }
}
