package app.cashflow.feature.importer.data

import app.cashflow.core.domain.Result
import app.cashflow.core.domain.model.ParsedStatement
import app.cashflow.feature.importer.data.profile.LansforsakringarProfile
import app.cashflow.feature.importer.domain.PickedFile
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Step 0 acceptance against the developer's own Länsförsäkringar exports. The files live in
 * the git-ignored `.private/statements` folder and never enter the repo; without them the
 * test is skipped. Only aggregate numbers are asserted or printed.
 */
class PrivateStatementsTest {

    private val dir = System.getProperty("cashflow.privateStatements")?.let(::File)
    private val reader = ProfileStatementReader(listOf(LansforsakringarProfile()))

    private fun lfFiles(): List<File> {
        assumeTrue(dir != null && dir.isDirectory, "no private statements folder")
        val files = dir!!.listFiles { f -> f.name.startsWith("Kontoutdrag") && f.name.endsWith(".xls") }.orEmpty().toList()
        assumeTrue(files.isNotEmpty(), "no Länsförsäkringar exports")
        return files
    }

    @Test
    fun `every export reads without warnings and its running balance chains`() {
        val statements = lfFiles().map { file ->
            val result = reader.read(PickedFile(file.name, file.readBytes()))
            check(result is Result.Success) { "could not read a file: $result" }
            result.data
        }
        statements.forEach { s ->
            assertThat(s.warnings).isEmpty()
            assertThat(chainBreaks(s)).isEqualTo(0)
        }
        println("PRIVATE: ${statements.size} files, ${statements.sumOf { it.rows.size }} rows, all balance chains intact")
    }

    private fun chainBreaks(s: ParsedStatement): Int =
        s.rows.zipWithNext().count { (a, b) -> a.balanceAfter!! + b.amount != b.balanceAfter }
}
