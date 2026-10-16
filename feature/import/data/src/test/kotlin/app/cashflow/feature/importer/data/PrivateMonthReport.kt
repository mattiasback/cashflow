package app.cashflow.feature.importer.data

import app.cashflow.core.categorization.BundledCatalog
import app.cashflow.core.categorization.Categorizer
import app.cashflow.core.categorization.PayeeNormalizer
import app.cashflow.core.domain.Result
import app.cashflow.core.domain.model.Account
import app.cashflow.core.domain.model.AccountId
import app.cashflow.core.domain.model.AccountType
import app.cashflow.core.domain.model.Period
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
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Developer aid: writes one month's classified rows to a report in the git-ignored private
 * folder, to debug totals against the real exports. Runs only when CASHFLOW_MONTH_REPORT is
 * set to a month like 2026-09; nothing is printed to the build log.
 */
class PrivateMonthReport {

    @Test
    fun report() = runTest {
        val month = System.getenv("CASHFLOW_MONTH_REPORT")
        assumeTrue(month != null)
        val dir = System.getProperty("cashflow.privateStatements")?.let(::File)
        assumeTrue(dir != null && dir.isDirectory)
        val reader = ProfileStatementReader(listOf(LansforsakringarProfile()))
        val statements = dir!!.listFiles { f -> f.name.startsWith("Kontoutdrag") && f.name.endsWith(".xls") }!!
            .map { (reader.readNow(PickedFile(it.name, it.readBytes())) as Result.Success).data }
        val txs = InMemoryTransactionDataSource()
        val accounts = InMemoryAccountDataSource()
        val normalizer = PayeeNormalizer()
        val hasher = TransactionHasher()
        val catalog = BundledCatalog.load()
        val engine = ImportStatementUseCase(
            txs, accounts, AccountInferrer(hasher), hasher, normalizer,
            ReclassifyUseCase(txs, accounts, InMemoryMerchantMemoryDataSource(), InMemoryRuleDataSource(), catalog, TransferMatcher(), Categorizer(normalizer)),
        )
        val plan = (engine.plan(statements) as Result.Success).data
        engine.execute(plan, plan.questions.associate { it.group to AccountAnswer.New(Account(AccountId("a${it.group}"), "Konto ${it.group}", AccountType.TRANSACTION)) })
        val kinds = catalog.defaultCategories().associate { it.id to it.kind }
        val period = Period.parse(month!!)
        val report = txs.rows.value.values.filter { it.period == period }
            .sortedWith(compareBy({ kinds[it.categoryId]?.name }, { it.amount.ore }))
            .joinToString("\n") { "${kinds[it.categoryId]}\t${it.categoryId.value}\t${it.transferKind}\t${it.amount.ore / 100}\t${it.accountId.value}\t${it.rawText}" }
        File(dir, "month-report.txt").writeText(report)
    }
}
