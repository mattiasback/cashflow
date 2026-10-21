package app.cashflow.feature.banksync.data

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import app.cashflow.core.domain.EmptyResult
import app.cashflow.core.domain.Result
import app.cashflow.feature.banksync.domain.BankCredentialStore
import app.cashflow.feature.banksync.domain.BankCredentials
import app.cashflow.feature.banksync.domain.BankError
import app.cashflow.feature.banksync.domain.PendingAuthorization
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.time.Instant

/**
 * Your Enable Banking key and application id, encrypted with an AES-GCM key that never leaves
 * the Android Keystore. The files live in `noBackupFilesDir`, so they are in no cloud backup
 * or device transfer, and they are never logged. "Ta bort nyckel" deletes both.
 */
class KeystoreCredentialStore(context: Context) : BankCredentialStore {

    private val dir = File(context.applicationContext.noBackupFilesDir, "banksync")
    private val credentialsFile = File(dir, "credentials.bin")
    private val pendingFile = File(dir, "pending.bin")
    private val mutex = Mutex()
    private val present = MutableStateFlow(credentialsFile.exists())

    override val hasCredentials: Flow<Boolean> = present.asStateFlow()

    override suspend fun load(): BankCredentials? = read(credentialsFile)?.let {
        runCatching { json.decodeFromString<StoredCredentials>(it) }.getOrNull()
            ?.let { s -> BankCredentials(s.applicationId, s.privateKeyPem, s.redirectUrl) }
    }

    override suspend fun save(credentials: BankCredentials): EmptyResult<BankError> {
        val text = json.encodeToString(StoredCredentials(credentials.applicationId, credentials.privateKeyPem, credentials.redirectUrl))
        return if (write(credentialsFile, text)) {
            present.value = true
            Result.Success(Unit)
        } else {
            Result.Error(BankError.STORAGE)
        }
    }

    override suspend fun clear(): EmptyResult<BankError> = withContext(Dispatchers.IO) {
        mutex.withLock {
            credentialsFile.delete()
            pendingFile.delete()
            runCatching { keyStore().deleteEntry(ALIAS) }
            present.value = false
        }
        Result.Success(Unit)
    }

    override suspend fun pending(): PendingAuthorization? = read(pendingFile)?.let {
        runCatching { json.decodeFromString<StoredPending>(it) }.getOrNull()
            ?.let { p -> PendingAuthorization(p.state, p.institution, Instant.fromEpochMilliseconds(p.startedAtEpochMillis)) }
    }

    override suspend fun setPending(pending: PendingAuthorization?) {
        if (pending == null) {
            withContext(Dispatchers.IO) { mutex.withLock { pendingFile.delete() } }
        } else {
            write(pendingFile, json.encodeToString(StoredPending(pending.state, pending.institution, pending.startedAt.toEpochMilliseconds())))
        }
    }

    private suspend fun read(file: File): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!file.exists()) return@withLock null
            try {
                val bytes = file.readBytes()
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
                String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
            } catch (e: Exception) {
                // A key removed by the system (e.g. a new screen lock on some devices) cannot decrypt: ask again.
                Logger.w { "Bank sync secrets unreadable: ${e::class.simpleName}" }
                null
            }
        }
    }

    private suspend fun write(file: File, text: String): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                dir.mkdirs()
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, key())
                val sealed = cipher.iv + cipher.doFinal(text.toByteArray(Charsets.UTF_8))
                val tmp = File(dir, "${file.name}.tmp")
                tmp.writeBytes(sealed)
                tmp.renameTo(file)
            } catch (e: Exception) {
                Logger.e { "Bank sync secrets not saved: ${e::class.simpleName}" }
                false
            }
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private fun key(): SecretKey {
        (keyStore().getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) setUnlockedDeviceRequired(true) }
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply { init(spec) }.generateKey()
    }

    @Serializable
    private data class StoredCredentials(val applicationId: String, val privateKeyPem: String, val redirectUrl: String)

    @Serializable
    private data class StoredPending(val state: String, val institution: String, val startedAtEpochMillis: Long)

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "cashflow-banksync"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        val json = Json { ignoreUnknownKeys = true }
    }
}
