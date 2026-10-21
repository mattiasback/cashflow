package app.cashflow.feature.banksync.enablebanking

import app.cashflow.core.domain.EmptyResult
import app.cashflow.core.domain.Result
import app.cashflow.core.domain.model.Money
import app.cashflow.feature.banksync.domain.BankAccountKind
import app.cashflow.feature.banksync.domain.BankCredentialStore
import app.cashflow.feature.banksync.domain.BankCredentials
import app.cashflow.feature.banksync.domain.BankError
import app.cashflow.feature.banksync.domain.BankInstitution
import app.cashflow.feature.banksync.domain.PendingAuthorization
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import kotlin.time.Instant

/** Against Enable Banking's documented answers, with made-up accounts and rows. */
class EnableBankingProviderTest {

    private val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val pkcs8Pem = "-----BEGIN PRIVATE KEY-----\n" +
        Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(keyPair.private.encoded) + "\n-----END PRIVATE KEY-----\n"
    private val requests = ArrayList<HttpRequestData>()

    private fun provider(handler: (HttpRequestData) -> Pair<HttpStatusCode, String>): EnableBankingProvider {
        val engine = MockEngine { request ->
            requests += request
            val (status, body) = handler(request)
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return EnableBankingProvider(FixedCredentials(BankCredentials("app-123", pkcs8Pem, "https://example.org/cb/")), enableBankingClient(engine))
    }

    @Test
    fun `every call carries a JWT signed by your key, with Enable Banking's claims`() = runTest {
        provider { HttpStatusCode.OK to """{"aspsps":[]}""" }.institutions("SE")

        val token = requests.single().headers[HttpHeaders.Authorization]!!.removePrefix("Bearer ")
        val (header, body, signature) = token.split('.')
        val decoder = Base64.getUrlDecoder()
        assertThat(String(decoder.decode(header))).isEqualTo("""{"typ":"JWT","alg":"RS256","kid":"app-123"}""")
        val claims = String(decoder.decode(body))
        assertThat(claims.contains(""""iss":"enablebanking.com"""") && claims.contains(""""aud":"api.enablebanking.com"""")).isTrue()
        val verified = Signature.getInstance("SHA256withRSA").run {
            initVerify(keyPair.public)
            update("$header.$body".toByteArray())
            verify(decoder.decode(signature))
        }
        assertThat(verified).isTrue()
    }

    @Test
    fun `a PKCS#1 key from the control panel works too`() {
        val pkcs1 = keyPair.private.encoded.copyOfRange(PKCS8_HEADER, keyPair.private.encoded.size)
        val pem = "-----BEGIN RSA PRIVATE KEY-----\n" + Base64.getMimeEncoder().encodeToString(pkcs1) + "\n-----END RSA PRIVATE KEY-----"
        val key = PemKeys.privateKey(pem)
        assertThat(key).isNotNull()
        assertThat(key!!.encoded.contentEquals(keyPair.private.encoded)).isTrue()
        assertThat((keyPair.public as RSAPublicKey).modulus.bitLength()).isEqualTo(2048)
    }

    @Test
    fun `banks for private persons, with their longest consent in days`() = runTest {
        val banks = provider {
            HttpStatusCode.OK to """{"aspsps":[
                {"name":"Exempelbanken","country":"SE","maximum_consent_validity":15552000,"psu_types":["personal","business"]},
                {"name":"Företagsbanken","country":"SE","psu_types":["business"]}]}"""
        }.institutions("SE")
        assertThat(banks).isEqualTo(Result.Success(listOf(BankInstitution("Exempelbanken", "SE", 180))))
    }

    @Test
    fun `starting sends the bank, the consent end, the state and your redirect`() = runTest {
        val start = provider { HttpStatusCode.OK to """{"url":"https://tilisy.example/auth?x=1","authorization_id":"a1"}""" }
            .startAuthorization(BankInstitution("Exempelbanken", "SE", 180), Instant.parse("2027-04-19T10:00:00Z"), "st4te")

        assertThat((start as Result.Success).data.url).isEqualTo("https://tilisy.example/auth?x=1")
        val sent = (requests.single().body as TextContent).text
        assertThat(sent.contains(""""valid_until":"2027-04-19T10:00:00Z"""") && sent.contains(""""state":"st4te"""") &&
            sent.contains(""""redirect_url":"https://example.org/cb/"""") && sent.contains(""""psu_type":"personal"""")).isTrue()
    }

    @Test
    fun `a session lists its accounts with a stable key, the number and the kind`() = runTest {
        val consent = provider {
            HttpStatusCode.OK to """{"session_id":"s-1","aspsp":{"name":"Exempelbanken","country":"SE"},
                "access":{"valid_until":"2027-04-19T10:00:00.000000+00:00"},
                "accounts":[{"uid":"u-1","identification_hash":"h-1","account_id":{"iban":"SE12 3456 7890 1234 5678 9012"},
                  "name":"Lönekonto","currency":"SEK","cash_account_type":"CACC"},
                  {"identification_hash":"h-2","account_id":{"other":{"identification":"9999.12.34567","scheme_name":"BBAN"}},
                  "product":"Sparkonto","currency":"SEK","cash_account_type":"SVGS"}]}"""
        }.completeAuthorization("code-1")

        val data = (consent as Result.Success).data
        assertThat(data.sessionId).isEqualTo("s-1")
        assertThat(data.validUntil).isEqualTo(Instant.parse("2027-04-19T10:00:00Z"))
        assertThat(data.accounts.map { Triple(it.stableKey, it.number, it.kind) }).containsExactly(
            Triple("h-1", "1234567890123456789012", BankAccountKind.CURRENT),
            Triple("h-2", "99991234567", BankAccountKind.SAVINGS),
        )
        assertThat(data.accounts[1].name).isEqualTo("Sparkonto")
        assertThat(data.accounts[1].providerUid).isEqualTo(null)
    }

    @Test
    fun `transactions are paged, signed by direction and only booked`() = runTest {
        val p = provider { request ->
            if (request.url.parameters["continuation_key"] == null) {
                HttpStatusCode.OK to """{"continuation_key":"next","transactions":[
                    {"entry_reference":"e1","booking_date":"2026-10-12","transaction_date":"2026-10-11",
                     "transaction_amount":{"currency":"SEK","amount":"245.50"},"credit_debit_indicator":"DBIT","status":"BOOK",
                     "remittance_information":["MATBUTIK EXEMPEL"],"balance_after_transaction":{"currency":"SEK","amount":"1754.50"}}]}"""
            } else {
                HttpStatusCode.OK to """{"transactions":[
                    {"transaction_id":"t2","booking_date":"2026-10-13","transaction_amount":{"currency":"SEK","amount":"25000"},
                     "credit_debit_indicator":"CRDT","status":"BOOK","debtor":{"name":"ARBETSGIVARE AB"}}]}"""
            }
        }
        val rows = (p.transactions("u-1", LocalDate(2026, 10, 1), LocalDate(2026, 10, 21)) as Result.Success).data

        assertThat(rows.map { Triple(it.reference, it.amount, it.text) }).containsExactly(
            Triple("e1", Money(-24550), "MATBUTIK EXEMPEL"),
            Triple("t2", Money(2_500_000), "ARBETSGIVARE AB"),
        )
        assertThat(rows[0].balanceAfter).isEqualTo(Money(175450))
        assertThat(rows[0].transactionDate).isEqualTo(LocalDate(2026, 10, 11))
        val first = requests.first().url
        assertThat(first.encodedPath).isEqualTo("/accounts/u-1/transactions")
        assertThat(first.parameters["date_from"]).isEqualTo("2026-10-01")
        assertThat(first.parameters["transaction_status"]).isEqualTo("BOOK")
    }

    @Test
    fun `refusals become errors without reading account data`() = runTest {
        assertThat(provider { HttpStatusCode.Unauthorized to """{"error":"UNAUTHORIZED"}""" }.institutions("SE"))
            .isEqualTo(Result.Error(BankError.UNAUTHORIZED))
        assertThat(provider { HttpStatusCode.UnprocessableEntity to """{"error":"EXPIRED_SESSION"}""" }.transactions("u", null, LocalDate(2026, 10, 21)))
            .isEqualTo(Result.Error(BankError.CONSENT_EXPIRED))
        assertThat(provider { HttpStatusCode.TooManyRequests to "" }.institutions("SE")).isEqualTo(Result.Error(BankError.RATE_LIMITED))
    }

    @Test
    fun `no key, no call`() = runTest {
        val p = EnableBankingProvider(FixedCredentials(BankCredentials("app", "not a key", "https://example.org/")), enableBankingClient(MockEngine { error("no call") }))
        assertThat(p.institutions("SE")).isEqualTo(Result.Error(BankError.INVALID_KEY))
    }

    private class FixedCredentials(private val credentials: BankCredentials) : BankCredentialStore {
        override val hasCredentials = flowOf(true)
        override suspend fun load() = credentials
        override suspend fun save(credentials: BankCredentials): EmptyResult<BankError> = Result.Success(Unit)
        override suspend fun clear(): EmptyResult<BankError> = Result.Success(Unit)
        override suspend fun pending(): PendingAuthorization? = null
        override suspend fun setPending(pending: PendingAuthorization?) = Unit
    }

    private companion object {
        /** SEQUENCE, version and algorithm before the PKCS#1 bytes of a 2048-bit key. */
        const val PKCS8_HEADER = 26
    }
}

class EnableBankingCredentialCheckTest {
    private val pem = run {
        val key = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().private
        "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder().encodeToString(key.encoded) + "\n-----END PRIVATE KEY-----"
    }

    private fun check(body: String, redirect: String = "cashflow://bank-callback") = kotlinx.coroutines.test.runTest {
        val provider = EnableBankingProvider(
            object : BankCredentialStore {
                override val hasCredentials = flowOf(false)
                override suspend fun load(): BankCredentials? = null
                override suspend fun save(credentials: BankCredentials): EmptyResult<BankError> = Result.Success(Unit)
                override suspend fun clear(): EmptyResult<BankError> = Result.Success(Unit)
                override suspend fun pending(): PendingAuthorization? = null
                override suspend fun setPending(pending: PendingAuthorization?) = Unit
            },
            enableBankingClient(MockEngine { respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }),
        )
        result = provider.checkCredentials(BankCredentials("app", pem, redirect))
    }

    private var result: EmptyResult<BankError>? = null

    @Test
    fun `the application must know the redirect URL and be active`() {
        check("""{"name":"CashFlow","environment":"PRODUCTION","redirect_urls":["cashflow://bank-callback/"],"active":true}""")
        assertThat(result).isEqualTo(Result.Success(Unit))
        check("""{"redirect_urls":["https://example.org/cb"],"active":true}""")
        assertThat(result).isEqualTo(Result.Error(BankError.REDIRECT_NOT_REGISTERED))
        check("""{"redirect_urls":["cashflow://bank-callback"],"active":false}""")
        assertThat(result).isEqualTo(Result.Error(BankError.APP_INACTIVE))
    }
}
