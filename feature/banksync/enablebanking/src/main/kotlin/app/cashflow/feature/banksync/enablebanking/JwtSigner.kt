package app.cashflow.feature.banksync.enablebanking

import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

// Temporary (step 9): the key import is a stand-in until a backend holds the key (docs/bank-sync.md).
/**
 * Enable Banking authenticates every call with a JWT that your own application's RSA key signs
 * (RS256): header `kid` = application id, claims iss `enablebanking.com`, aud
 * `api.enablebanking.com`, at most 24 h long. A token is reused until shortly before it ends.
 */
class JwtSigner(private val clock: Clock = Clock.System) {

    private var cached: Pair<String, Instant>? = null
    private var cachedFor: String? = null

    fun token(applicationId: String, key: PrivateKey): String {
        val now = clock.now()
        val cacheKey = "$applicationId|${key.hashCode()}"
        cached?.let { (token, expires) -> if (cachedFor == cacheKey && now < expires - REFRESH_MARGIN) return token }
        val iat = now.epochSeconds
        val exp = (now + LIFETIME).epochSeconds
        val header = """{"typ":"JWT","alg":"RS256","kid":"${applicationId.jsonEscaped()}"}"""
        val body = """{"iss":"enablebanking.com","aud":"api.enablebanking.com","iat":$iat,"exp":$exp}"""
        val signingInput = "${b64(header.toByteArray())}.${b64(body.toByteArray())}"
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(key)
            update(signingInput.toByteArray(Charsets.US_ASCII))
            sign()
        }
        val token = "$signingInput.${b64(signature)}"
        cached = token to now + LIFETIME
        cachedFor = cacheKey
        return token
    }

    private fun b64(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun String.jsonEscaped() = replace("\\", "\\\\").replace("\"", "\\\"")

    private companion object {
        val LIFETIME = 1.hours
        val REFRESH_MARGIN = kotlin.time.Duration.parse("5m")
    }
}

/**
 * Reads the `.pem` the Enable Banking control panel gives you: PKCS#8 (`BEGIN PRIVATE KEY`) or
 * PKCS#1 (`BEGIN RSA PRIVATE KEY`). Null when the text is not an RSA private key.
 */
object PemKeys {

    fun privateKey(pem: String): PrivateKey? = runCatching {
        val pkcs1 = pem.contains("BEGIN RSA PRIVATE KEY")
        val base64 = pem.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("-----") }
            .joinToString("")
        val der = Base64.getMimeDecoder().decode(base64)
        val pkcs8 = if (pkcs1) wrapPkcs1(der) else der
        KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(pkcs8))
    }.getOrNull()

    /** PKCS#8 = SEQUENCE { INTEGER 0, SEQUENCE { rsaEncryption, NULL }, OCTET STRING { PKCS#1 } }. */
    private fun wrapPkcs1(pkcs1: ByteArray): ByteArray {
        val version = byteArrayOf(0x02, 0x01, 0x00)
        val algorithm = byteArrayOf(
            0x30, 0x0d, 0x06, 0x09, 0x2a, 0x86.toByte(), 0x48, 0x86.toByte(), 0xf7.toByte(), 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00,
        )
        val octets = byteArrayOf(0x04) + derLength(pkcs1.size) + pkcs1
        val content = version + algorithm + octets
        return byteArrayOf(0x30) + derLength(content.size) + content
    }

    private fun derLength(length: Int): ByteArray = when {
        length < 0x80 -> byteArrayOf(length.toByte())
        length < 0x100 -> byteArrayOf(0x81.toByte(), length.toByte())
        length < 0x10000 -> byteArrayOf(0x82.toByte(), (length shr 8).toByte(), length.toByte())
        else -> byteArrayOf(0x83.toByte(), (length shr 16).toByte(), (length shr 8).toByte(), length.toByte())
    }
}
