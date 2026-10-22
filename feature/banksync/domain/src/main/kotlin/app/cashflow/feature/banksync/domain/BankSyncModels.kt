package app.cashflow.feature.banksync.domain

import app.cashflow.core.domain.model.AccountId
import app.cashflow.core.domain.model.Money
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** Everything that can go wrong with bank sync, whichever provider is used. */
enum class BankError : app.cashflow.core.domain.Error {
    /** No key or application id imported yet. */
    NO_CREDENTIALS,

    /** The key file could not be read as an RSA private key. */
    INVALID_KEY,

    /** The provider refused the key or the application id. */
    UNAUTHORIZED,

    /** The redirect URL you entered is not one of the application's registered URLs. */
    REDIRECT_NOT_REGISTERED,

    /** The application is not active yet: link your accounts in the provider's control panel. */
    APP_INACTIVE,

    /** The consent ran out or was revoked: link the bank again. */
    CONSENT_EXPIRED,

    /** The answer from the bank did not belong to the authorization this app started. */
    STATE_MISMATCH,

    /** You stopped at the bank's page. */
    CANCELLED,
    RATE_LIMITED,
    BANK_UNAVAILABLE,
    NETWORK,
    INVALID_RESPONSE,
    STORAGE,
    UNKNOWN,
}

// Temporary (step 9): the key import is a stand-in until a backend holds the key (docs/bank-sync.md).
/** Your own application at the provider: the key and id you created there. */
data class BankCredentials(
    val applicationId: String,
    /** PEM text of the RSA private key. Kept encrypted on the device, never logged. */
    val privateKeyPem: String,
    /** Must equal one of the redirect URLs registered for the application. */
    val redirectUrl: String,
)

/** A bank you can link ("ASPSP"). */
data class BankInstitution(
    val name: String,
    val country: String,
    /** The longest consent the bank gives, in days; null when the provider does not say. */
    val maxConsentDays: Int?,
)

data class AuthorizationStart(
    /** The bank's BankID page, opened in the browser. */
    val url: String,
)

/** Started in the browser, completed when the bank sends you back with a code. */
data class PendingAuthorization(
    val state: String,
    val institution: String,
    val startedAt: Instant,
)

enum class BankAccountKind { CURRENT, SAVINGS, OTHER }

/** What [BankSyncProvider.transactions] returned; rows the bank sent without any date are only counted. */
data class FetchedTransactions(val rows: List<BankTransaction>, val undated: Int = 0)

/** A bank account in a consent. */
data class BankAccount(
    /** The same across consents, so renewing does not ask for the mapping again. */
    val stableKey: String,
    /** The provider's id for this consent only; null when it cannot be read. */
    val providerUid: String?,
    /** The account's product name (never the holder's name); null when the bank gives none. */
    val name: String?,
    /** IBAN or clearing + account number, digits only; never logged. */
    val number: String?,
    val currency: String,
    val kind: BankAccountKind,
)

data class BankConsent(
    val sessionId: String,
    val institution: String,
    val validUntil: Instant,
    val accounts: List<BankAccount>,
)

/** A row as the bank delivers it, signed from the account's point of view. */
data class BankTransaction(
    /** The bank's own reference for the row, when it has one. */
    val reference: String?,
    val bookingDate: LocalDate,
    val transactionDate: LocalDate?,
    val amount: Money,
    val currency: String,
    val text: String,
    val balanceAfter: Money?,
    /** Only booked rows are stored; pending ones change reference when they book. */
    val booked: Boolean,
)

/** "Länsförsäkringar ··1234 · Privatkonto": the account number's end, never the holder's name. */
fun bankAccountLabel(institution: String, product: String?, number: String?): String =
    listOfNotNull(
        listOfNotNull(institution, number?.takeLast(4)?.let { "··$it" }).joinToString(" "),
        product?.trim()?.takeIf { it.isNotEmpty() },
    ).joinToString(" · ")

/** A bank account linked to one of your CashFlow accounts. */
data class BankLink(
    val stableKey: String,
    val provider: String,
    val accountId: AccountId,
    val institution: String,
    val sessionId: String,
    val providerAccountUid: String,
    val validUntil: Instant,
    val displayName: String,
    /** The last four digits, for display. */
    val maskedNumber: String?,
    val lastSynced: LocalDate?,
)
