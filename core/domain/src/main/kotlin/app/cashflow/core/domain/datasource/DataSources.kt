package app.cashflow.core.domain.datasource

import app.cashflow.core.domain.DataError
import app.cashflow.core.domain.EmptyResult
import app.cashflow.core.domain.Result
import app.cashflow.core.domain.model.Account
import app.cashflow.core.domain.model.AccountId
import app.cashflow.core.domain.model.BalanceState
import app.cashflow.core.domain.model.Category
import app.cashflow.core.domain.model.CategoryId
import app.cashflow.core.domain.model.CategoryRule
import app.cashflow.core.domain.model.CategorizedBy
import app.cashflow.core.domain.model.ImportBatch
import app.cashflow.core.domain.model.ImportId
import app.cashflow.core.domain.model.InsertSummary
import app.cashflow.core.domain.model.MerchantMemory
import app.cashflow.core.domain.model.Transaction
import app.cashflow.core.domain.model.TransferKind
import app.cashflow.core.domain.model.TxId
import kotlinx.coroutines.flow.Flow

/** Classification fields that change after import (transfers, categories). */
data class Classification(
    val id: TxId,
    val categoryId: CategoryId,
    val categorizedBy: CategorizedBy,
    val transferKind: TransferKind,
    val transferPartnerId: TxId?,
    val loneLegConfirmed: Boolean,
)

interface TransactionLocalDataSource {
    fun observeAll(): Flow<List<Transaction>>
    fun observe(id: TxId): Flow<Transaction?>

    /** Full-text search over raw and normalized text; returns matching ids. */
    fun search(query: String): Flow<Set<TxId>>
    suspend fun getAll(): Result<List<Transaction>, DataError.Local>
    suspend fun existingIds(ids: Collection<TxId>): Result<Set<TxId>, DataError.Local>

    /** Atomic: inserts the batch and its rows, skipping ids that already exist. */
    suspend fun insertBatch(batch: ImportBatch, rows: List<Transaction>): Result<InsertSummary, DataError.Local>

    /** Atomic: updates the classification of many rows at once. */
    suspend fun updateClassifications(updates: List<Classification>): EmptyResult<DataError.Local>
}

interface AccountLocalDataSource {
    fun observeAccounts(): Flow<List<Account>>
    suspend fun getAccounts(): Result<List<Account>, DataError.Local>
    suspend fun getBalanceStates(): Result<List<BalanceState>, DataError.Local>
    suspend fun upsertAccount(account: Account): EmptyResult<DataError.Local>
    suspend fun addBalanceStates(states: List<BalanceState>): EmptyResult<DataError.Local>
    suspend fun deleteAccount(id: AccountId): EmptyResult<DataError.Local>
}

interface CategoryLocalDataSource {
    fun observeCategories(): Flow<List<Category>>
    suspend fun getCategories(): Result<List<Category>, DataError.Local>

    /** Seeds the default tree once; existing rows are kept. */
    suspend fun ensureDefaults(defaults: List<Category>): EmptyResult<DataError.Local>
}

interface RuleLocalDataSource {
    fun observeUserRules(): Flow<List<CategoryRule>>
    suspend fun getUserRules(): Result<List<CategoryRule>, DataError.Local>
    suspend fun upsertRule(rule: CategoryRule): EmptyResult<DataError.Local>
}

interface MerchantMemoryLocalDataSource {
    suspend fun getAll(): Result<List<MerchantMemory>, DataError.Local>
    suspend fun remember(memory: MerchantMemory): EmptyResult<DataError.Local>
    suspend fun forget(payeeKey: String): EmptyResult<DataError.Local>
}

interface ImportBatchLocalDataSource {
    fun observeBatches(): Flow<List<ImportBatch>>
    suspend fun getBatch(id: ImportId): Result<ImportBatch, DataError.Local>
}

/** Read-only built-in reference data (bundled JSON), loaded by :core:data. */
interface RuleCatalog {
    suspend fun builtInRules(): List<CategoryRule>
    suspend fun defaultCategories(): List<Category>
}
