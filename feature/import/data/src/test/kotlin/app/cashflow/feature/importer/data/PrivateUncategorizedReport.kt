package app.cashflow.feature.importer.data

import app.cashflow.core.categorization.BundledCatalog
import app.cashflow.core.categorization.CategorizationContext
import app.cashflow.core.categorization.Categorizer
import app.cashflow.core.categorization.PayeeNormalizer
import app.cashflow.core.domain.Result
import app.cashflow.core.domain.model.CategorizedBy
import app.cashflow.core.domain.model.TransferKind
import app.cashflow.feature.importer.data.profile.LansforsakringarProfile
import app.cashflow.feature.importer.domain.PickedFile
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Developer aid: writes the most frequent uncategorized payees to a report inside the
 * git-ignored private folder, to find missing public merchant rules. Runs only when
 * CASHFLOW_PAYEE_REPORT is set; nothing is printed to the build log.
 */
class PrivateUncategorizedReport {

    @Test
    fun report() = runTest {
        assumeTrue(System.getenv("CASHFLOW_PAYEE_REPORT") != null)
        val dir = System.getProperty("cashflow.privateStatements")?.let(::File)
        assumeTrue(dir != null && dir.isDirectory)
        val reader = ProfileStatementReader(listOf(LansforsakringarProfile()))
        val normalizer = PayeeNormalizer()
        val categorizer = Categorizer(normalizer)
        val ctx = CategorizationContext(emptyList(), emptyList(), BundledCatalog.load().builtInRules())
        val rows = dir!!.listFiles { f -> f.name.endsWith(".xls") && f.name.startsWith("Kontoutdrag") }!!
            .flatMap { (reader.readNow(PickedFile(it.name, it.readBytes())) as Result.Success).data.rows }
            .distinct()
        val misses = rows.filter {
            categorizer.categorize(it.rawText, it.amount, TransferKind.NONE, false, ctx).categorizedBy == CategorizedBy.NONE
        }
        val report = misses.groupingBy { normalizer.key(it.rawText) + if (it.amount.isNegative) " (ut)" else " (in)" }
            .eachCount().entries.sortedByDescending { it.value }
            .joinToString("\n") { "${it.value}\t${it.key}" }
        File(dir, "uncategorized-report.txt").writeText(report)
    }
}
