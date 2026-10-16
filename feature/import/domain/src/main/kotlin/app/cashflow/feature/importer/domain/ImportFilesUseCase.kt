package app.cashflow.feature.importer.domain

import app.cashflow.core.domain.ImportError
import app.cashflow.core.domain.Result
import app.cashflow.core.domain.model.AccountType
import app.cashflow.core.domain.model.ParsedStatement
import app.cashflow.core.importengine.AccountAnswer
import app.cashflow.core.importengine.ImportOutcome
import app.cashflow.core.importengine.ImportPlan
import app.cashflow.core.importengine.ImportStatementUseCase

/** A file that could not be read at all, shown by name with the reason. */
data class FileFailure(val fileName: String, val error: ImportError)

data class PreparedImport(
    val plan: ImportPlan?,
    val failures: List<FileFailure>,
)

/**
 * File import on top of the shared engine: open each picked file, read it through the bank
 * profiles, then let the engine plan (infer accounts) and, once questions are answered,
 * execute. Several files can be picked at once; a file that fails does not stop the others.
 */
class ImportFilesUseCase(
    private val fileSource: StatementFileSource,
    private val reader: StatementReader,
    private val engine: ImportStatementUseCase,
) {

    suspend fun prepare(uris: List<String>): Result<PreparedImport, ImportError> {
        val statements = ArrayList<ParsedStatement>()
        val failures = ArrayList<FileFailure>()
        for (uri in uris) {
            val file = when (val r = fileSource.open(uri)) {
                is Result.Error -> {
                    failures += FileFailure(uri.substringAfterLast('/'), r.error)
                    continue
                }
                is Result.Success -> r.data
            }
            when (val r = reader.read(file)) {
                is Result.Error -> failures += FileFailure(file.name, r.error)
                is Result.Success -> statements += r.data
            }
        }
        if (statements.isEmpty()) {
            return if (failures.size == 1) Result.Error(failures.single().error) else Result.Success(PreparedImport(null, failures))
        }
        return when (val plan = engine.plan(statements)) {
            is Result.Error -> plan
            is Result.Success -> Result.Success(PreparedImport(plan.data, failures))
        }
    }

    suspend fun execute(
        plan: ImportPlan,
        answers: Map<Int, AccountAnswer>,
        types: Map<String, AccountType> = emptyMap(),
    ): Result<ImportOutcome, ImportError> = engine.execute(plan, answers, types)
}
