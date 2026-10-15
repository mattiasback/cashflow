package app.cashflow.core.database.entity

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
)

/** Full-text search over the bank text and the cleaned payee. */
@Fts4(contentEntity = TransactionEntity::class)
@Entity(tableName = "transactions_fts")
data class TransactionFtsEntity(
    val rawText: String,
    val normalizedText: String,
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
)

@Entity(tableName = "merchant_memory")
data class MerchantMemoryEntity(
    @PrimaryKey val payeeKey: String,
    val categoryId: String,
)
