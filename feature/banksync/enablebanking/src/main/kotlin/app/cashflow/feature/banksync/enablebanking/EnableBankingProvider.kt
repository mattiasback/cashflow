package app.cashflow.feature.banksync.enablebanking

import app.cashflow.core.domain.EmptyResult
import app.cashflow.core.domain.Result
import app.cashflow.core.domain.model.Money
import app.cashflow.feature.banksync.domain.AuthorizationStart
import app.cashflow.feature.banksync.domain.BankAccount
import app.cashflow.feature.banksync.domain.BankAccountKind
import app.cashflow.feature.banksync.domain.BankConsent
import app.cashflow.feature.banksync.domain.BankCredentialStore
import app.cashflow.feature.banksync.domain.BankCredentials
import app.cashflow.feature.banksync.domain.BankError
import app.cashflow.feature.banksync.domain.BankInstitution
import app.cashflow.feature.banksync.domain.BankSyncProvider
import app.cashflow.feature.banksync.domain.BankTransaction
import app.cashflow.feature.banksync.domain.FetchedTransactions
import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.time.Instant

/**
 * [BankSyncProvider] for Enable Banking (https://enablebanking.com/docs/api/reference/), in
 * restricted production mode: free, and it reaches only the accounts you linked to your own
 * application. Read-only: accounts and transactions. Bodies are never logged.
 */
class EnableBankingProvider(
    private val credentials: BankCredentialStore,
    private val client: HttpClient,
    private val signer: JwtSigner = JwtSigner(),
) : BankSyncProvider {

    override val id = "enablebanking"

    /** `GET /application`: the key signs, the application is active and knows the redirect URL. */
    override suspend fun checkCredentials(credentials: BankCredentials): Result<BankCredentials, BankError> =
        when (val r = call<ApplicationDto>(with = credentials) { get("$BASE/application") { auth(it) } }) {
            is Result.Error -> r
            is Result.Success -> {
                // /auth wants the URL exactly as registered, trailing slash included.
                val registered = r.data.redirectUrls.firstOrNull { it.trimEnd('/') == credentials.redirectUrl.trim().trimEnd('/') }
                when {
                    registered == null -> Result.Error(BankError.REDIRECT_NOT_REGISTERED)
                    r.data.active == false -> Result.Error(BankError.APP_INACTIVE)
                    else -> Result.Success(credentials.copy(redirectUrl = registered))
                }
            }
        }

    override suspend fun institutions(country: String): Result<List<BankInstitution>, BankError> =
        call<AspspsDto> { get("$BASE/aspsps") { auth(it); parameter("country", country); parameter("psu_type", "personal") } }
            .mapSuccess { dto ->
                dto.aspsps.filter { it.psuTypes == null || "personal" in it.psuTypes }
                    .map { BankInstitution(it.name, it.country, it.maximumConsentValidity?.let { s -> (s / SECONDS_PER_DAY).toInt() }) }
            }

    override suspend fun startAuthorization(
        institution: BankInstitution,
        validUntil: Instant,
        state: String,
    ): Result<AuthorizationStart, BankError> {
        val redirect = credentials.load()?.redirectUrl ?: return Result.Error(BankError.NO_CREDENTIALS)
        val body = StartAuthorizationDto(
            // Whole seconds: the API rejects nine-digit fractions.
            access = AccessDto(Instant.fromEpochSeconds(validUntil.epochSeconds).toString()),
            aspsp = AspspRefDto(institution.name, institution.country),
            state = state,
            redirectUrl = redirect,
        )
        return call<StartAuthorizationResponseDto> {
            post("$BASE/auth") { auth(it); contentType(ContentType.Application.Json); setBody(body) }
        }.mapSuccess { AuthorizationStart(it.url) }
    }

    override suspend fun completeAuthorization(code: String): Result<BankConsent, BankError> =
        call<SessionDto> { post("$BASE/sessions") { auth(it); contentType(ContentType.Application.Json); setBody(AuthorizeSessionDto(code)) } }
            .mapSuccess { s ->
                BankConsent(
                    sessionId = s.sessionId,
                    institution = s.aspsp.name,
                    validUntil = Instant.parse(s.access.validUntil),
                    accounts = s.accounts.map { it.toBankAccount() },
                )
            }

    override suspend fun transactions(providerAccountUid: String, from: LocalDate?, to: LocalDate): Result<FetchedTransactions, BankError> {
        val rows = ArrayList<BankTransaction>()
        var undated = 0
        var continuation: String? = null
        repeat(MAX_PAGES) {
            val page = call<TransactionsDto> {
                get("$BASE/accounts/$providerAccountUid/transactions") {
                    auth(it)
                    if (from != null) parameter("date_from", from.toString()) else parameter("strategy", "longest")
                    parameter("date_to", to.toString())
                    continuation?.let { key -> parameter("continuation_key", key) }
                }
            }
            when (page) {
                is Result.Error -> return page
                is Result.Success -> {
                    page.data.transactions.forEach { dto -> dto.toBankTransaction()?.let(rows::add) ?: undated++ }
                    continuation = page.data.continuationKey?.takeIf { it.isNotBlank() } ?: return Result.Success(FetchedTransactions(rows, undated))
                }
            }
        }
        return Result.Success(FetchedTransactions(rows, undated))
    }

    override suspend fun revoke(sessionId: String): EmptyResult<BankError> =
        call<Unit>(parse = false) { delete("$BASE/sessions/$sessionId") { auth(it) } }

    /** Signs the request with a fresh JWT from your key, or fails before any network call. */
    private suspend inline fun <reified T> call(
        parse: Boolean = true,
        with: BankCredentials? = null,
        crossinline request: suspend HttpClient.(String) -> HttpResponse,
    ): Result<T, BankError> {
        val creds = with ?: credentials.load() ?: return Result.Error(BankError.NO_CREDENTIALS)
        val key = PemKeys.privateKey(creds.privateKeyPem) ?: return Result.Error(BankError.INVALID_KEY)
        return try {
            val response = client.request(signer.token(creds.applicationId, key))
            if (!response.status.isSuccess()) {
                val text = runCatching { response.bodyAsText() }.getOrDefault("")
                val error = errorOf(response.status.value, text)
                // Error answers carry a code and a message, never account data.
                val detail = runCatching { enableBankingJson.decodeFromString<ErrorDto>(text) }.getOrNull()
                Logger.w { "Enable Banking answered ${response.status.value}: $error (${detail?.error} ${detail?.message?.take(200)})" }
                Result.Error(error)
            } else if (!parse) {
                @Suppress("UNCHECKED_CAST")
                Result.Success(Unit as T)
            } else {
                Result.Success(response.body<T>())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SerializationException) {
            Logger.w { "Enable Banking answer could not be read: ${e::class.simpleName}" }
            Result.Error(BankError.INVALID_RESPONSE)
        } catch (e: IOException) {
            Logger.w { "Enable Banking unreachable: ${e::class.simpleName}" }
            Result.Error(BankError.NETWORK)
        } catch (e: Exception) {
            Logger.e { "Enable Banking call failed: ${e::class.simpleName}" }
            Result.Error(BankError.UNKNOWN)
        }
    }

    private fun HttpRequestBuilder.auth(token: String) = bearerAuth(token)

    private inline fun <T, R> Result<T, BankError>.mapSuccess(map: (T) -> R): Result<R, BankError> = when (this) {
        is Result.Error -> this
        is Result.Success -> Result.Success(map(data))
    }

    internal companion object {
        const val BASE = "https://api.enablebanking.com"
        const val MAX_PAGES = 200
        const val SECONDS_PER_DAY = 86_400L

        /** Status and error code only; the body may hold account data and is never kept. */
        fun errorOf(status: Int, body: String): BankError {
            val upper = body.uppercase()
            return when {
                "EXPIRED" in upper || "REVOKED" in upper -> BankError.CONSENT_EXPIRED
                status == 401 || status == 403 -> BankError.UNAUTHORIZED
                status == 429 -> BankError.RATE_LIMITED
                status >= 500 -> BankError.BANK_UNAVAILABLE
                else -> BankError.INVALID_RESPONSE
            }
        }
    }
}

internal fun AccountDto.toBankAccount() = BankAccount(
    stableKey = identificationHash,
    providerUid = uid,
    // `name` is the account holder's name; the account is described by its product or description.
    name = (details ?: product)?.takeIf { it.isNotBlank() },
    number = (accountId?.iban ?: accountId?.other?.identification)?.filter { it.isDigit() }?.takeIf { it.isNotEmpty() },
    currency = currency,
    kind = when (cashAccountType) {
        "CACC", "TRAN" -> BankAccountKind.CURRENT
        "SVGS", "MOMA", "ONDP" -> BankAccountKind.SAVINGS
        else -> BankAccountKind.OTHER
    },
)

/** Null for rows without any date, which cannot be placed in a month. */
internal fun TransactionDto.toBankTransaction(): BankTransaction? {
    val booked = (bookingDate ?: valueDate ?: transactionDate)?.let(LocalDate::parse) ?: return null
    val magnitude = BigDecimal(transactionAmount.amount).abs().movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
    val debit = creditDebitIndicator == "DBIT"
    val counterparty = if (debit) creditor?.name else debtor?.name
    // The merchant or sender is often only the counterparty; the remittance text may be just "Kortköp".
    val remittance = remittanceInformation?.filter { it.isNotBlank() }?.joinToString(" ").orEmpty()
    val parts = listOfNotNull(counterparty?.trim(), remittance.trim(), note?.trim()).filter { it.isNotEmpty() }
    val text = parts.fold(emptyList<String>()) { kept, part -> if (kept.any { it.contains(part, ignoreCase = true) }) kept else kept + part }
        .joinToString(" ")
    return BankTransaction(
        reference = entryReference?.takeIf { it.isNotBlank() } ?: transactionId?.takeIf { it.isNotBlank() },
        bookingDate = booked,
        transactionDate = transactionDate?.let(LocalDate::parse),
        amount = Money(if (debit) -magnitude else magnitude),
        currency = transactionAmount.currency,
        text = text,
        balanceAfter = balanceAfterTransaction?.takeIf { it.currency == transactionAmount.currency }?.let {
            Money(BigDecimal(it.amount).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong())
        },
        booked = status == "BOOK",
    )
}
