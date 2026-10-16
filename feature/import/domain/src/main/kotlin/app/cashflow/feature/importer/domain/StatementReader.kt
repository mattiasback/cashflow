package app.cashflow.feature.importer.domain

import app.cashflow.core.domain.ImportError
import app.cashflow.core.domain.Result
import app.cashflow.core.domain.model.ParsedStatement

/** A file the user picked or shared, already read into memory. */
class PickedFile(
    val name: String,
    val bytes: ByteArray,
)

/** Reads any supported bank export (`.xls` BIFF8, CSV) through the bank profiles. */
interface StatementReader {
    suspend fun read(file: PickedFile): Result<ParsedStatement, ImportError>
}

/**
 * Opens files the user picked (content URIs on Android). The system picker cannot be driven
 * in UI tests, so tests use an in-memory source.
 */
interface StatementFileSource {
    suspend fun open(uri: String): Result<PickedFile, ImportError>
}
