package app.cashflow.core.data.local

import android.database.sqlite.SQLiteFullException
import androidx.room.withTransaction
import app.cashflow.core.database.CashFlowDatabase
import app.cashflow.core.database.entity.GoalEntity
import app.cashflow.core.domain.DataError
import app.cashflow.core.domain.EmptyResult
import app.cashflow.core.domain.Result
import app.cashflow.core.domain.datasource.AccountLocalDataSource
import app.cashflow.core.domain.datasource.CategoryLocalDataSource
import app.cashflow.core.domain.datasource.Classification
import app.cashflow.core.domain.datasource.CsvProfileLocalDataSource
import app.cashflow.core.domain.datasource.GoalLocalDataSource
import app.cashflow.core.domain.datasource.ImportBatchLocalDataSource
import app.cashflow.core.domain.datasource.MerchantMemoryLocalDataSource
import app.cashflow.core.domain.datasource.RuleLocalDataSource
import app.cashflow.core.domain.datasource.TransactionLocalDataSource
import app.cashflow.core.domain.model.Account
import app.cashflow.core.domain.model.AccountId
import app.cashflow.core.domain.model.BalanceState
import app.cashflow.core.domain.model.Category
import app.cashflow.core.domain.model.CategoryRule
import app.cashflow.core.domain.model.CsvProfile
import app.cashflow.core.domain.model.Goal
import app.cashflow.core.domain.model.GoalType
import app.cashflow.core.domain.model.ImportBatch
import app.cashflow.core.domain.model.ImportId
import app.cashflow.core.domain.model.InsertSummary
import app.cashflow.core.domain.model.MerchantMemory
import app.cashflow.core.domain.model.Transaction
import app.cashflow.core.domain.model.TxId
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

/** Maps database failures to [DataError.Local]. Never logs row contents. */
internal suspend inline fun <T> dbCall(crossinline block: suspend () -> T): Result<T, DataError.Local> =
    try {
        Result.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: SQLiteFullException) {
        Logger.w(e) { "Database full" }
        Result.Error(DataError.Local.DISK_FULL)
    } catch (e: Exception) {
        Logger.e(e) { "Database error: ${e::class.simpleName}" }
        Result.Error(DataError.Local.UNKNOWN)
    }

class RoomTransactionDataSource(private val db: CashFlowDatabase) : TransactionLocalDataSource {
    private val dao = db.transactionDao()

    override fun observeAll(): Flow<List<Transaction>> = dao.observeAll().map { list -> list.map { it.toTransaction() } }
    override fun observe(id: TxId): Flow<Transaction?> = dao.observe(id.value).map { it?.toTransaction() }

    override fun search(query: String): Flow<Set<TxId>> {
        val match = ftsQuery(query) ?: return flowOf(emptySet())
        return dao.search(match).map { ids -> ids.map(::TxId).toSet() }
    }

    override suspend fun getAll(): Result<List<Transaction>, DataError.Local> = dbCall { dao.getAll().map { it.toTransaction() } }

    override suspend fun existingIds(ids: Collection<TxId>): Result<Set<TxId>, DataError.Local> = dbCall {
        ids.map { it.value }.chunked(CHUNK).flatMap { dao.existingIds(it) }.map(::TxId).toSet()
    }

    override suspend fun insertBatch(batch: ImportBatch, rows: List<Transaction>): Result<InsertSummary, DataError.Local> = dbCall {
        val newCount = dao.insertBatch(batch.toEntity(), rows.map { it.toEntity() })
        InsertSummary(newCount = newCount, duplicateCount = rows.size - newCount)
    }

    override suspend fun updateClassifications(updates: List<Classification>): EmptyResult<DataError.Local> = dbCall {
        db.withTransaction {
            updates.forEach { u ->
                dao.updateClassification(
                    id = u.id.value,
                    categoryId = u.categoryId.value,
                    categorizedBy = u.categorizedBy.name,
                    transferKind = u.transferKind.name,
                    transferPartnerId = u.transferPartnerId?.value,
                    loneLegConfirmed = u.loneLegConfirmed,
                )
            }
        }
    }

    override suspend fun setHidden(ids: Collection<TxId>, hidden: Boolean): EmptyResult<DataError.Local> = dbCall {
        db.withTransaction { ids.map { it.value }.chunked(CHUNK).forEach { dao.setHidden(it, hidden) } }
    }

    override suspend fun delete(ids: Collection<TxId>, at: Instant): EmptyResult<DataError.Local> =
        dbCall { dao.deleteWithTombstones(ids.map { it.value }, at.toEpochMilliseconds()) }

    override suspend fun setDuplicates(duplicateOf: Map<TxId, TxId?>): EmptyResult<DataError.Local> = dbCall {
        db.withTransaction { duplicateOf.forEach { (id, of) -> dao.setDuplicateOf(id.value, of?.value) } }
    }

    override suspend fun setDuplicateChecked(id: TxId): EmptyResult<DataError.Local> = dbCall { dao.setDuplicateChecked(id.value) }

    override suspend fun undoImport(id: ImportId, at: Instant): EmptyResult<DataError.Local> =
        dbCall { dao.undoImport(id.value, at.toEpochMilliseconds()) }

    override suspend fun redoImport(id: ImportId): EmptyResult<DataError.Local> = dbCall { dao.redoImport(id.value) }

    private companion object {
        const val CHUNK = 500

        /** Prefix search on every word: "ica max" → `ica* max*`. */
        fun ftsQuery(query: String): String? {
            val words = query.trim().split(Regex("\\s+")).map { w -> w.filter { it.isLetterOrDigit() } }.filter { it.isNotEmpty() }
            return words.takeIf { it.isNotEmpty() }?.joinToString(" ") { "$it*" }
        }
    }
}

class RoomAccountDataSource(db: CashFlowDatabase) : AccountLocalDataSource {
    private val dao = db.accountDao()
    override fun observeAccounts(): Flow<List<Account>> = dao.observeAccounts().map { list -> list.map { it.toAccount() } }
    override suspend fun getAccounts() = dbCall { dao.getAccounts().map { it.toAccount() } }
    override suspend fun getBalanceStates() = dbCall { dao.getBalanceStates().map { it.toBalanceState() } }
    override suspend fun upsertAccount(account: Account): EmptyResult<DataError.Local> = dbCall { dao.upsert(account.toEntity()) }
    override suspend fun addBalanceStates(states: List<BalanceState>): EmptyResult<DataError.Local> =
        dbCall { dao.insertBalanceStates(states.map { it.toEntity() }) }
    override suspend fun deleteAccount(id: AccountId): EmptyResult<DataError.Local> = dbCall { dao.delete(id.value) }
}

class RoomCategoryDataSource(db: CashFlowDatabase) : CategoryLocalDataSource {
    private val dao = db.categoryDao()
    override fun observeCategories(): Flow<List<Category>> = dao.observeCategories().map { list -> list.map { it.toCategory() } }
    override suspend fun getCategories() = dbCall { dao.getCategories().map { it.toCategory() } }
    override suspend fun ensureDefaults(defaults: List<Category>): EmptyResult<DataError.Local> =
        dbCall { dao.insertMissing(defaults.map { it.toEntity() }) }
}

class RoomRuleDataSource(db: CashFlowDatabase) : RuleLocalDataSource {
    private val dao = db.ruleDao()
    override fun observeUserRules(): Flow<List<CategoryRule>> = dao.observeRules().map { list -> list.map { it.toRule() } }
    override suspend fun getUserRules() = dbCall { dao.getRules().map { it.toRule() } }
    override suspend fun upsertRule(rule: CategoryRule): EmptyResult<DataError.Local> = dbCall { dao.upsert(rule.toEntity()) }
    override suspend fun deleteRule(id: String): EmptyResult<DataError.Local> = dbCall { dao.delete(id) }
}

class RoomMerchantMemoryDataSource(db: CashFlowDatabase) : MerchantMemoryLocalDataSource {
    private val dao = db.merchantMemoryDao()
    override fun observeAll(): Flow<List<MerchantMemory>> = dao.observeAll().map { list -> list.map { it.toMemory() } }
    override suspend fun getAll() = dbCall { dao.getAll().map { it.toMemory() } }
    override suspend fun remember(memory: MerchantMemory): EmptyResult<DataError.Local> = dbCall { dao.upsert(memory.toEntity()) }
    override suspend fun forget(payeeKey: String): EmptyResult<DataError.Local> = dbCall { dao.delete(payeeKey) }
}

class RoomImportBatchDataSource(db: CashFlowDatabase) : ImportBatchLocalDataSource {
    private val dao = db.importBatchDao()
    override fun observeBatches(): Flow<List<ImportBatch>> = dao.observeBatches().map { list -> list.map { it.toBatch() } }
    override suspend fun getBatch(id: ImportId): Result<ImportBatch, DataError.Local> =
        when (val r = dbCall { dao.getBatch(id.value) }) {
            is Result.Error -> r
            is Result.Success -> r.data?.toBatch()?.let { Result.Success(it) } ?: Result.Error(DataError.Local.NOT_FOUND)
        }

    override suspend fun undoneRowCount(id: ImportId): Result<Int, DataError.Local> = dbCall { dao.undoneRowCount(id.value) }
}

class RoomCsvProfileDataSource(db: CashFlowDatabase) : CsvProfileLocalDataSource {
    private val dao = db.csvProfileDao()
    override fun observeProfiles(): Flow<List<CsvProfile>> = dao.observeProfiles().map { list -> list.map { it.toProfile() } }
    override suspend fun getProfiles() = dbCall { dao.getProfiles().map { it.toProfile() } }
    override suspend fun upsertProfile(profile: CsvProfile): EmptyResult<DataError.Local> = dbCall { dao.upsert(profile.toEntity()) }
    override suspend fun deleteProfile(id: String): EmptyResult<DataError.Local> = dbCall { dao.delete(id) }
}

class RoomGoalDataSource(db: CashFlowDatabase) : GoalLocalDataSource {
    private val dao = db.goalDao()
    override fun observeGoals(): Flow<List<Goal>> = dao.observeGoals().map { list ->
        list.mapNotNull { e -> GoalType.entries.firstOrNull { it.name == e.type }?.let { Goal(it, e.target) } }
    }
    override suspend fun setGoal(goal: Goal): EmptyResult<DataError.Local> = dbCall {
        dao.upsert(GoalEntity(goal.type.name, goal.target, kotlin.time.Clock.System.now().toEpochMilliseconds()))
    }
}
