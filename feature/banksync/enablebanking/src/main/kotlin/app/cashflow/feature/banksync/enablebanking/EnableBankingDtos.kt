package app.cashflow.feature.banksync.enablebanking

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Only the fields CashFlow reads; everything else in Enable Banking's answers is ignored.

@Serializable
internal data class AspspsDto(val aspsps: List<AspspDto> = emptyList())

@Serializable
internal data class AspspDto(
    val name: String,
    val country: String,
    /** Seconds. */
    @SerialName("maximum_consent_validity") val maximumConsentValidity: Long? = null,
    @SerialName("psu_types") val psuTypes: List<String>? = null,
)

@Serializable
internal data class AspspRefDto(val name: String, val country: String)

@Serializable
internal data class AccessDto(@SerialName("valid_until") val validUntil: String)

@Serializable
internal data class StartAuthorizationDto(
    val access: AccessDto,
    val aspsp: AspspRefDto,
    val state: String,
    @SerialName("redirect_url") val redirectUrl: String,
    @SerialName("psu_type") val psuType: String = "personal",
)

@Serializable
internal data class StartAuthorizationResponseDto(
    val url: String,
    @SerialName("authorization_id") val authorizationId: String? = null,
)

@Serializable
internal data class AuthorizeSessionDto(val code: String)

@Serializable
internal data class SessionDto(
    @SerialName("session_id") val sessionId: String,
    val accounts: List<AccountDto> = emptyList(),
    val aspsp: AspspRefDto,
    val access: AccessDto,
)

@Serializable
internal data class AccountDto(
    val uid: String? = null,
    @SerialName("identification_hash") val identificationHash: String,
    @SerialName("account_id") val accountId: AccountIdentificationDto? = null,
    val name: String? = null,
    val details: String? = null,
    val product: String? = null,
    val currency: String,
    @SerialName("cash_account_type") val cashAccountType: String? = null,
)

@Serializable
internal data class AccountIdentificationDto(
    val iban: String? = null,
    val other: GenericIdentificationDto? = null,
)

@Serializable
internal data class GenericIdentificationDto(
    val identification: String,
    @SerialName("scheme_name") val schemeName: String? = null,
)

@Serializable
internal data class TransactionsDto(
    val transactions: List<TransactionDto> = emptyList(),
    @SerialName("continuation_key") val continuationKey: String? = null,
)

@Serializable
internal data class TransactionDto(
    @SerialName("entry_reference") val entryReference: String? = null,
    @SerialName("transaction_id") val transactionId: String? = null,
    @SerialName("booking_date") val bookingDate: String? = null,
    @SerialName("value_date") val valueDate: String? = null,
    @SerialName("transaction_date") val transactionDate: String? = null,
    @SerialName("transaction_amount") val transactionAmount: AmountDto,
    @SerialName("credit_debit_indicator") val creditDebitIndicator: String,
    val status: String,
    @SerialName("remittance_information") val remittanceInformation: List<String>? = null,
    val creditor: PartyDto? = null,
    val debtor: PartyDto? = null,
    @SerialName("balance_after_transaction") val balanceAfterTransaction: AmountDto? = null,
    val note: String? = null,
)

@Serializable
internal data class AmountDto(val currency: String, val amount: String)

@Serializable
internal data class PartyDto(val name: String? = null)

@Serializable
internal data class ErrorDto(
    val code: Int? = null,
    val error: String? = null,
    val message: String? = null,
)

@Serializable
internal data class ApplicationDto(
    val name: String? = null,
    val environment: String? = null,
    @SerialName("redirect_urls") val redirectUrls: List<String> = emptyList(),
    val active: Boolean? = null,
)
