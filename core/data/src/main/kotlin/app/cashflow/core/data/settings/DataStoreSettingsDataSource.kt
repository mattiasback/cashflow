package app.cashflow.core.data.settings

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import app.cashflow.core.data.local.dbCall
import app.cashflow.core.domain.DataError
import app.cashflow.core.domain.EmptyResult
import app.cashflow.core.domain.datasource.SettingsDataSource
import app.cashflow.core.domain.model.AppSettings
import app.cashflow.core.domain.model.BufferBalance
import app.cashflow.core.domain.model.HouseholdScope
import app.cashflow.core.domain.model.Money
import app.cashflow.core.domain.model.MortgageSplit
import app.cashflow.core.domain.model.MortgageSplitMode
import app.cashflow.core.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** Settings in one typed DataStore file (kotlinx-serialization JSON), excluded from cloud backup. */
class DataStoreSettingsDataSource(
    private val store: DataStore<StoredSettings>,
) : SettingsDataSource {

    override val settings: Flow<AppSettings> = store.data.map { it.toSettings() }

    override suspend fun update(transform: (AppSettings) -> AppSettings): EmptyResult<DataError.Local> =
        dbCall { store.updateData { stored -> transform(stored.toSettings()).toStored() } }

    companion object {
        const val FILE_NAME = "cashflow-settings.json"

        fun create(file: File): DataStoreSettingsDataSource =
            DataStoreSettingsDataSource(DataStoreFactory.create(serializer = StoredSettingsSerializer, produceFile = { file }))
    }
}

/** What is written to disk. Enums are stored by name and unknown values fall back to defaults. */
@Serializable
data class StoredSettings(
    val theme: String = ThemeMode.SYSTEM.name,
    val percentDecimals: Boolean = false,
    val scope: String = HouseholdScope.JOINT.name,
    val mortgageEnabled: Boolean = false,
    val mortgageMode: String = MortgageSplitMode.AMORTIZATION_AMOUNT.name,
    val mortgageAmortizationOre: Long = 0,
    val mortgageInterestSharePercent: Int = 0,
    val mortgagePattern: String = "BOLÅN",
    val lastTab: String? = null,
    val lastPeriod: String? = null,
    val seenFlags: Set<String> = emptySet(),
    val insightsDone: Set<String> = emptySet(),
    val bufferBalanceOre: Long? = null,
    val bufferBalanceDate: String? = null,
)

internal fun StoredSettings.toSettings() = AppSettings(
    theme = enumOr(theme, ThemeMode.SYSTEM),
    percentDecimals = percentDecimals,
    scope = enumOr(scope, HouseholdScope.JOINT),
    mortgage = MortgageSplit(
        enabled = mortgageEnabled,
        mode = enumOr(mortgageMode, MortgageSplitMode.AMORTIZATION_AMOUNT),
        amortization = Money(mortgageAmortizationOre),
        interestSharePercent = mortgageInterestSharePercent,
        pattern = mortgagePattern,
    ),
    lastTab = lastTab,
    lastPeriod = lastPeriod,
    seenFlags = seenFlags,
    insightsDone = insightsDone,
    bufferBalance = bufferBalanceOre?.let { ore -> bufferBalanceDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let { BufferBalance(Money(ore), it) } },
)

internal fun AppSettings.toStored() = StoredSettings(
    theme = theme.name,
    percentDecimals = percentDecimals,
    scope = scope.name,
    mortgageEnabled = mortgage.enabled,
    mortgageMode = mortgage.mode.name,
    mortgageAmortizationOre = mortgage.amortization.ore,
    mortgageInterestSharePercent = mortgage.interestSharePercent,
    mortgagePattern = mortgage.pattern,
    lastTab = lastTab,
    lastPeriod = lastPeriod,
    seenFlags = seenFlags,
    insightsDone = insightsDone,
    bufferBalanceOre = bufferBalance?.amount?.ore,
    bufferBalanceDate = bufferBalance?.date?.toString(),
)

private inline fun <reified E : Enum<E>> enumOr(name: String, default: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: default

internal object StoredSettingsSerializer : Serializer<StoredSettings> {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override val defaultValue = StoredSettings()

    override suspend fun readFrom(input: InputStream): StoredSettings =
        try {
            json.decodeFromString(StoredSettings.serializer(), input.readBytes().decodeToString())
        } catch (e: SerializationException) {
            throw CorruptionException("Unreadable settings", e)
        }

    override suspend fun writeTo(t: StoredSettings, output: OutputStream) {
        output.write(json.encodeToString(StoredSettings.serializer(), t).encodeToByteArray())
    }
}
