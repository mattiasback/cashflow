package app.cashflow.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import app.cashflow.core.database.entity.AccountEntity
import app.cashflow.core.database.entity.BalanceStateEntity
import app.cashflow.core.database.entity.CategoryEntity
import app.cashflow.core.database.entity.ImportBatchEntity
import app.cashflow.core.database.entity.MerchantMemoryEntity
import app.cashflow.core.database.entity.RuleEntity
import app.cashflow.core.database.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY bookingEpochDay DESC, fileOrder DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observe(id: String): Flow<TransactionEntity?>

    @Query("SELECT * FROM transactions")
    suspend fun getAll(): List<TransactionEntity>

    @Query("SELECT id FROM transactions WHERE id IN (:ids)")
    suspend fun existingIds(ids: List<String>): List<String>

    @Query(
        """
        SELECT transactions.id FROM transactions
        JOIN transactions_fts ON transactions.rowid = transactions_fts.rowid
        WHERE transactions_fts MATCH :match
        """,
    )
    fun search(match: String): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringExisting(rows: List<TransactionEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatchRecord(batch: ImportBatchEntity)

    /** Inserts a whole import in one transaction and returns how many rows were new. */
    @Transaction
    suspend fun insertBatch(batch: ImportBatchEntity, rows: List<TransactionEntity>): Int {
        val newCount = insertIgnoringExisting(rows).count { it != -1L }
        insertBatchRecord(batch.copy(newCount = newCount, duplicateCount = rows.size - newCount))
        return newCount
    }

    @Query(
        """
        UPDATE transactions SET categoryId = :categoryId, categorizedBy = :categorizedBy, transferKind = :transferKind,
        transferPartnerId = :transferPartnerId, loneLegConfirmed = :loneLegConfirmed WHERE id = :id
        """,
    )
    suspend fun updateClassification(
        id: String,
        categoryId: String,
        categorizedBy: String,
        transferKind: String,
        transferPartnerId: String?,
        loneLegConfirmed: Boolean,
    )
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY name")
    fun observeAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts")
    suspend fun getAccounts(): List<AccountEntity>

    @Upsert
    suspend fun upsert(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM balance_states")
    suspend fun getBalanceStates(): List<BalanceStateEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBalanceStates(states: List<BalanceStateEntity>)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder")
    suspend fun getCategories(): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissing(categories: List<CategoryEntity>)
}

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules")
    fun observeRules(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules")
    suspend fun getRules(): List<RuleEntity>

    @Upsert
    suspend fun upsert(rule: RuleEntity)
}

@Dao
interface MerchantMemoryDao {
    @Query("SELECT * FROM merchant_memory")
    suspend fun getAll(): List<MerchantMemoryEntity>

    @Upsert
    suspend fun upsert(memory: MerchantMemoryEntity)

    @Query("DELETE FROM merchant_memory WHERE payeeKey = :payeeKey")
    suspend fun delete(payeeKey: String)
}

@Dao
interface ImportBatchDao {
    @Query("SELECT * FROM import_batches ORDER BY importedAtEpochMillis DESC")
    fun observeBatches(): Flow<List<ImportBatchEntity>>

    @Query("SELECT * FROM import_batches WHERE id = :id")
    suspend fun getBatch(id: String): ImportBatchEntity?
}
