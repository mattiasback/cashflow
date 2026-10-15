package app.cashflow.feature.importer.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import app.cashflow.core.domain.ImportError
import app.cashflow.core.domain.Result
import app.cashflow.feature.importer.domain.PickedFile
import app.cashflow.feature.importer.domain.StatementFileSource
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads `content://` URIs from the system picker or a validated share intent. Only content
 * URIs are accepted, and files above [MAX_BYTES] are refused, so a share cannot point the
 * app at its own private files or exhaust memory.
 */
class ContentResolverFileSource(
    private val context: Context,
) : StatementFileSource {

    override suspend fun open(uri: String): Result<PickedFile, ImportError> = withContext(Dispatchers.IO) {
        val parsed = Uri.parse(uri)
        if (parsed.scheme != "content") return@withContext Result.Error(ImportError.UNREADABLE_FILE)
        try {
            val resolver = context.contentResolver
            val name = resolver.query(parsed, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: parsed.lastPathSegment ?: "fil"
            val bytes = resolver.openInputStream(parsed)?.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > MAX_BYTES) return@withContext Result.Error(ImportError.UNSUPPORTED_FORMAT)
                }
                out.toByteArray()
            } ?: return@withContext Result.Error(ImportError.UNREADABLE_FILE)
            Result.Success(PickedFile(name, bytes))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w { "Could not open picked file: ${e::class.simpleName}" }
            Result.Error(ImportError.UNREADABLE_FILE)
        }
    }

    private companion object {
        const val MAX_BYTES = 20 * 1024 * 1024
    }
}
