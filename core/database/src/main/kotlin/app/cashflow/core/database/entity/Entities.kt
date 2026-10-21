package app.cashflow.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val clearingPrefix: String?,
    val accountNumber: String?,
    /** v2: savings exports name their accounts ("Lysa", "avanza:ISK …"). */
    val institution: String? = null,
    val externalKey: String? = null,
    val marketValueOre: Long? = null,
    val marketValueEpochDay: Long? = null,
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [
        Index("accountId"),
        Index("importId"),
        Index("transactionEpochDay"),
        Index("categoryId"),
    ],
)
data class TransactionEntity(
    /** sha256 of the raw row; the primary key is the unique index on the hash. */
    @PrimaryKey val id: String,
    val accountId: String,
    val importId: String,
    val bookingEpochDay: Long,
    val transactionEpochDay: Long,
    val amountOre: Long,
    val rawText: String,
    val normalizedText: String,
    val balanceAfterOre: Long?,
    val categoryId: String,
    val categorizedBy: String,
    val transferKind: String,
    val transferPartnerId: String?,
    val loneLegConfirmed: Boolean,
    val fileOrder: Int,
    val countsInCashFlow: Boolean,
    /** v2: what a savings row is (DEPOSIT, TRADE …); null for bank rows. */
    val movement: String? = null,
    @ColumnInfo(defaultValue = "0") val hidden: Boolean = false,
    /** v2: set aside because its import was undone; every read skips these rows. */
    @ColumnInfo(defaultValue = "0") val undone: Boolean = false,
    val duplicateOf: String? = null,
    @ColumnInfo(defaultValue = "0") val duplicateChecked: Boolean = false,
    /** v5: FILE or BANK (bank sync). */
    @ColumnInfo(defaultValue = "FILE") val origin: String = "FILE",
)

/** Full-text search over the bank text and the cleaned payee. */
@Fts4(contentEntity = TransactionEntity::class)
@Entity(tableName = "transactions_fts")
data class TransactionFtsEntity(
    val rawText: String,
    val normalizedText: String,
)

/**
 * v5: the id of the same bank event from the other origin (a file row for a synced row, or the
 * reverse). Stored instead of a second row, so the next import or sync skips it by id.
 */
@Entity(
    tableName = "transaction_aliases",
    foreignKeys = [
        ForeignKey(entity = TransactionEntity::class, parentColumns = ["id"], childColumns = ["txId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("txId")],
)
data class TransactionAliasEntity(
    @PrimaryKey val aliasId: String,
    val txId: String,
)

/**
 * v5: a bank account reached through bank sync, keyed by the provider's stable account hash
 * (the same across consents). Holds no secret: the session id is useless without your key.
 */
@Entity(
    tableName = "bank_links",
    foreignKeys = [
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("accountId")],
)
data class BankLinkEntity(
    @PrimaryKey val stableKey: String,
    val provider: String,
    val accountId: String,
    val institution: String,
    val sessionId: String,
    val providerAccountUid: String,
    val validUntilEpochMillis: Long,
    val displayName: String,
    val maskedNumber: String?,
    val lastSyncedEpochDay: Long?,
)

/** v2: the id of a deleted row, so a re-import does not bring it back. */
@Entity(tableName = "tombstones")
data class TombstoneEntity(
    @PrimaryKey val id: String,
    val deletedAtEpochMillis: Long,
)

@Entity(
    tableName = "balance_states",
    primaryKeys = ["accountId", "epochDay", "balanceOre"],
    foreignKeys = [
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class BalanceStateEntity(
    val accountId: String,
    val epochDay: Long,
    val balanceOre: Long,
)

@Entity(tableName = "import_batches")
data class ImportBatchEntity(
    @PrimaryKey val id: String,
    val source: String,
    val fileName: String,
    val accountId: String?,
    val importedAtEpochMillis: Long,
    val rowCount: Int,
    val newCount: Int,
    val duplicateCount: Int,
    /** Row warnings as "row:REASON" pairs separated by ';'. */
    val warnings: String,
    /** v2: when the whole import was undone. */
    val undoneAtEpochMillis: Long? = null,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: String,
    val sortOrder: Int,
)

@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey val id: String,
    val pattern: String,
    val match: String,
    val categoryId: String,
    val sign: Int?,
    /** v2: optional account and amount limits, and where the rule came from. */
    val accountId: String? = null,
    val minAmountOre: Long? = null,
    val maxAmountOre: Long? = null,
    @ColumnInfo(defaultValue = "CREATED") val origin: String = "CREATED",
    val createdAtEpochMillis: Long? = null,
)

@Entity(tableName = "merchant_memory")
data class MerchantMemoryEntity(
    @PrimaryKey val payeeKey: String,
    val categoryId: String,
    val createdAtEpochMillis: Long? = null,
)

/** v4: one household goal per type (Buffert months, Sparkvot percent). */
@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey val type: String,
    val target: Double,
    val updatedAtEpochMillis: Long,
)

/** v2: a column mapping saved for a file format the app did not know. */
@Entity(tableName = "csv_profiles")
data class CsvProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val fingerprint: String,
    val headerRow: Int,
    val dateColumn: Int,
    val textColumn: Int,
    val amountColumn: Int?,
    val inColumn: Int?,
    val outColumn: Int?,
    val transactionDateColumn: Int?,
    val balanceColumn: Int?,
    val createdAtEpochMillis: Long,
    val lastUsedAtEpochMillis: Long?,
    /** v3: the file lists money out as positive numbers. */
    @ColumnInfo(defaultValue = "0") val negateAmounts: Boolean = false,
)
