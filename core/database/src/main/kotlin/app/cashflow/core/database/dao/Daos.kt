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
import app.cashflow.core.database.entity.CsvProfileEntity
import app.cashflow.core.database.entity.ImportBatchEntity
import app.cashflow.core.database.entity.MerchantMemoryEntity
import app.cashflow.core.database.entity.RuleEntity
import app.cashflow.core.database.entity.TombstoneEntity
import app.cashflow.core.database.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

private const val CHUNK = 500

/** Every read skips rows set aside by an undone import (`undone = 1`). */
@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE undone = 0 ORDER BY bookingEpochDay DESC, fileOrder DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id AND undone = 0")
    fun observe(id: String): Flow<TransactionEntity?>

    @Query("SELECT * FROM transactions WHERE undone = 0")
    suspend fun getAll(): List<TransactionEntity>

    @Query("SELECT id FROM transactions WHERE id IN (:ids) AND undone = 0")
    suspend fun existingIds(ids: List<String>): List<String>

    @Query("SELECT id FROM transactions WHERE id IN (:ids) AND undone = 1")
    suspend fun undoneIds(ids: List<String>): List<String>

    @Query("SELECT id FROM tombstones WHERE id IN (:ids)")
    suspend fun tombstonedIds(ids: List<String>): List<String>

    @Query(
        """
        SELECT transactions.id FROM transactions
        JOIN transactions_fts ON transactions.rowid = transactions_fts.rowid
        WHERE transactions_fts MATCH :match AND transactions.undone = 0
        """,
    )
    fun search(match: String): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringExisting(rows: List<TransactionEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatchRecord(batch: ImportBatchEntity)

    @Query("UPDATE transactions SET undone = 0, importId = :importId WHERE id IN (:ids)")
    suspend fun revive(ids: List<String>, importId: String)

    /**
     * Inserts a whole import in one transaction and returns how many rows were new. Rows of an
     * undone import come back under this import; deleted rows (tombstones) stay deleted.
     */
    @Transaction
    suspend fun insertBatch(batch: ImportBatchEntity, rows: List<TransactionEntity>): Int {
        val ids = rows.map { it.id }
        val deleted = ids.chunked(CHUNK).flatMap { tombstonedIds(it) }.toHashSet()
        val setAside = ids.chunked(CHUNK).flatMap { undoneIds(it) }.toHashSet()
        setAside.toList().chunked(CHUNK).forEach { revive(it, batch.id) }
        val fresh = rows.filter { it.id !in deleted && it.id !in setAside }
        val newCount = insertIgnoringExisting(fresh).count { it != -1L } + setAside.size
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

    @Query("UPDATE transactions SET hidden = :hidden WHERE id IN (:ids)")
    suspend fun setHidden(ids: List<String>, hidden: Boolean)

    @Query("DELETE FROM transactions WHERE id IN (:ids)")
    suspend fun deleteRows(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTombstones(tombstones: List<TombstoneEntity>)

    /** Deletes rows and remembers their ids, in one transaction. */
    @Transaction
    suspend fun deleteWithTombstones(ids: List<String>, atEpochMillis: Long) {
        ids.chunked(CHUNK).forEach { deleteRows(it) }
        insertTombstones(ids.map { TombstoneEntity(it, atEpochMillis) })
    }

    @Query("UPDATE transactions SET duplicateOf = :duplicateOf WHERE id = :id")
    suspend fun setDuplicateOf(id: String, duplicateOf: String?)

    @Query("UPDATE transactions SET duplicateChecked = 1 WHERE id = :id")
    suspend fun setDuplicateChecked(id: String)

    @Query("UPDATE transactions SET undone = :undone WHERE importId = :importId")
    suspend fun setUndone(importId: String, undone: Boolean)

    @Query("UPDATE import_batches SET undoneAtEpochMillis = :at WHERE id = :importId")
    suspend fun setBatchUndone(importId: String, at: Long?)

    @Transaction
    suspend fun undoImport(importId: String, atEpochMillis: Long) {
        setUndone(importId, true)
        setBatchUndone(importId, atEpochMillis)
    }

    @Transaction
    suspend fun redoImport(importId: String) {
        setUndone(importId, false)
        setBatchUndone(importId, null)
    }

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

    @Query("DELETE FROM rules WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface MerchantMemoryDao {
    @Query("SELECT * FROM merchant_memory")
    fun observeAll(): Flow<List<MerchantMemoryEntity>>

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

    @Query("SELECT COUNT(*) FROM transactions WHERE importId = :id AND undone = 1")
    suspend fun undoneRowCount(id: String): Int
}

@Dao
interface CsvProfileDao {
    @Query("SELECT * FROM csv_profiles ORDER BY name")
    fun observeProfiles(): Flow<List<CsvProfileEntity>>

    @Query("SELECT * FROM csv_profiles")
    suspend fun getProfiles(): List<CsvProfileEntity>

    @Upsert
    suspend fun upsert(profile: CsvProfileEntity)

    @Query("DELETE FROM csv_profiles WHERE id = :id")
    suspend fun delete(id: String)
}
