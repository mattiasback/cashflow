package app.cashflow.feature.banksync.data

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import app.cashflow.feature.banksync.domain.KeyFileReader
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Temporary (step 9): the key import is a stand-in until a backend holds the key (docs/bank-sync.md).
class ContentKeyFileReader(context: Context) : KeyFileReader {
    private val resolver = context.applicationContext.contentResolver

    override suspend fun read(uri: String): String? = withContext(Dispatchers.IO) {
        try {
            resolver.openInputStream(Uri.parse(uri))?.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (out.size() <= MAX_BYTES) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                }
                if (out.size() > MAX_BYTES) null else out.toString(Charsets.US_ASCII.name())
            }
        } catch (e: Exception) {
            Logger.w { "Key file unreadable: ${e::class.simpleName}" }
            null
        }
    }

    private companion object {
        /** An RSA key in PEM is a few kilobytes. */
        const val MAX_BYTES = 32 * 1024
    }
}
