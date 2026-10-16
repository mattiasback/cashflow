package app.cashflow.core.domain.testing

import app.cashflow.core.domain.DataError
import app.cashflow.core.domain.EmptyResult
import app.cashflow.core.domain.Result
import app.cashflow.core.domain.datasource.AccountLocalDataSource
import app.cashflow.core.domain.datasource.CategoryLocalDataSource
import app.cashflow.core.domain.datasource.Classification
import app.cashflow.core.domain.datasource.CsvProfileLocalDataSource
import app.cashflow.core.domain.datasource.ImportBatchLocalDataSource
import app.cashflow.core.domain.datasource.MerchantMemoryLocalDataSource
import app.cashflow.core.domain.datasource.RuleCatalog
import app.cashflow.core.domain.datasource.RuleLocalDataSource
import app.cashflow.core.domain.datasource.SettingsDataSource
import app.cashflow.core.domain.datasource.TransactionLocalDataSource
import app.cashflow.core.domain.model.Account
import app.cashflow.core.domain.model.AccountId
import app.cashflow.core.domain.model.AppSettings
import app.cashflow.core.domain.model.BalanceState
import app.cashflow.core.domain.model.Category
import app.cashflow.core.domain.model.CategoryRule
import app.cashflow.core.domain.model.CsvProfile
import app.cashflow.core.domain.model.ImportBatch
import app.cashflow.core.domain.model.ImportId
import app.cashflow.core.domain.model.InsertSummary
import app.cashflow.core.domain.model.MerchantMemory
import app.cashflow.core.domain.model.Transaction
import app.cashflow.core.domain.model.TxId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.time.Instant

/**
 * In-memory data sources: the test fakes for every interface the app reads through.
 * [rows] holds live rows; rows of an undone import wait in [setAside]; deleted ids are in
 * [tombstones].
 */
class InMemoryTransactionDataSource(
    private val batches: InMemoryImportBatchDataSource = InMemoryImportBatchDataSource(),
) : TransactionLocalDataSource {
    val rows = MutableStateFlow<Map<TxId, Transaction>>(emptyMap())
    val setAside = MutableStateFlow<Map<TxId, Transaction>>(emptyMap())
    val tombstones = MutableStateFlow<Set<TxId>>(emptySet())
    var failNextWrite: DataError.Local? = null

    init {
        batches.setAside = { id -> setAside.value.values.count { it.importId == id } }
    }

    override fun observeAll(): Flow<List<Transaction>> = rows.map { it.values.toList() }
    override fun observe(id: TxId): Flow<Transaction?> = rows.map { it[id] }
    override fun search(query: String): Flow<Set<TxId>> = rows.map { all ->
        val q = query.trim().lowercase()
        all.values.filter { q.isEmpty() || it.rawText.lowercase().contains(q) || it.normalizedText.lowercase().contains(q) }.map { it.id }.toSet()
    }

    override suspend fun getAll(): Result<List<Transaction>, DataError.Local> = Result.Success(rows.value.values.toList())
    override suspend fun existingIds(ids: Collection<TxId>): Result<Set<TxId>, DataError.Local> =
        Result.Success(ids.filter { it in rows.value }.toSet())

    override suspend fun insertBatch(batch: ImportBatch, rows: List<Transaction>): Result<InsertSummary, DataError.Local> {
        failNextWrite?.let { failNextWrite = null; return Result.Error(it) }
        val revived = rows.mapNotNull { r -> setAside.value[r.id]?.copy(importId = batch.id) }
        setAside.update { it - revived.map { r -> r.id }.toSet() }
        val fresh = rows.filter { it.id !in this.rows.value && it.id !in tombstones.value && revived.none { r -> r.id == it.id } }
        this.rows.update { current -> current + fresh.associateBy { it.id } + revived.associateBy { it.id } }
        val newCount = fresh.size + revived.size
        val summary = InsertSummary(newCount = newCount, duplicateCount = rows.size - newCount)
        batches.add(batch.copy(newCount = summary.newCount, duplicateCount = summary.duplicateCount))
        return Result.Success(summary)
    }

    override suspend fun updateClassifications(updates: List<Classification>): EmptyResult<DataError.Local> {
        failNextWrite?.let { failNextWrite = null; return Result.Error(it) }
        edit(updates.map { it.id }) { tx ->
            val u = updates.first { it.id == tx.id }
            tx.copy(
                categoryId = u.categoryId,
                categorizedBy = u.categorizedBy,
                transferKind = u.transferKind,
                transferPartnerId = u.transferPartnerId,
                loneLegConfirmed = u.loneLegConfirmed,
            )
        }
        return Result.Success(Unit)
    }

    override suspend fun setHidden(ids: Collection<TxId>, hidden: Boolean): EmptyResult<DataError.Local> {
        edit(ids) { it.copy(hidden = hidden) }
        return Result.Success(Unit)
    }

    override suspend fun delete(ids: Collection<TxId>, at: Instant): EmptyResult<DataError.Local> {
        failNextWrite?.let { failNextWrite = null; return Result.Error(it) }
        rows.update { it - ids.toSet() }
        setAside.update { it - ids.toSet() }
        tombstones.update { it + ids }
        return Result.Success(Unit)
    }

    override suspend fun setDuplicates(duplicateOf: Map<TxId, TxId?>): EmptyResult<DataError.Local> {
        edit(duplicateOf.keys) { it.copy(duplicateOf = duplicateOf[it.id]) }
        return Result.Success(Unit)
    }

    override suspend fun setDuplicateChecked(id: TxId): EmptyResult<DataError.Local> {
        edit(listOf(id)) { it.copy(duplicateChecked = true) }
        return Result.Success(Unit)
    }

    override suspend fun undoImport(id: ImportId, at: Instant): EmptyResult<DataError.Local> {
        failNextWrite?.let { failNextWrite = null; return Result.Error(it) }
        val moving = rows.value.values.filter { it.importId == id }
        rows.update { it - moving.map { tx -> tx.id }.toSet() }
        setAside.update { it + moving.associateBy { tx -> tx.id } }
        batches.update(id) { it.copy(undoneAt = at) }
        return Result.Success(Unit)
    }

    override suspend fun redoImport(id: ImportId): EmptyResult<DataError.Local> {
        val moving = setAside.value.values.filter { it.importId == id }
        setAside.update { it - moving.map { tx -> tx.id }.toSet() }
        rows.update { it + moving.associateBy { tx -> tx.id } }
        batches.update(id) { it.copy(undoneAt = null) }
        return Result.Success(Unit)
    }

    private fun edit(ids: Collection<TxId>, change: (Transaction) -> Transaction) {
        rows.update { current ->
            val next = current.toMutableMap()
            ids.forEach { id -> next[id]?.let { next[id] = change(it) } }
            next
        }
    }
}

class InMemoryImportBatchDataSource : ImportBatchLocalDataSource {
    val batches = MutableStateFlow<List<ImportBatch>>(emptyList())
    internal var setAside: (ImportId) -> Int = { 0 }
    fun add(batch: ImportBatch) = batches.update { it + batch }
    fun update(id: ImportId, change: (ImportBatch) -> ImportBatch) = batches.update { list -> list.map { if (it.id == id) change(it) else it } }
    override fun observeBatches(): Flow<List<ImportBatch>> = batches
    override suspend fun getBatch(id: ImportId): Result<ImportBatch, DataError.Local> =
        batches.value.firstOrNull { it.id == id }?.let { Result.Success(it) } ?: Result.Error(DataError.Local.NOT_FOUND)
    override suspend fun undoneRowCount(id: ImportId): Result<Int, DataError.Local> = Result.Success(setAside(id))
}

class InMemoryAccountDataSource : AccountLocalDataSource {
    val accounts = MutableStateFlow<List<Account>>(emptyList())
    val balanceStates = MutableStateFlow<List<BalanceState>>(emptyList())

    override fun observeAccounts(): Flow<List<Account>> = accounts
    override suspend fun getAccounts(): Result<List<Account>, DataError.Local> = Result.Success(accounts.value)
    override suspend fun getBalanceStates(): Result<List<BalanceState>, DataError.Local> = Result.Success(balanceStates.value)
    override suspend fun upsertAccount(account: Account): EmptyResult<DataError.Local> {
        accounts.update { list -> list.filterNot { it.id == account.id } + account }
        return Result.Success(Unit)
    }

    override suspend fun addBalanceStates(states: List<BalanceState>): EmptyResult<DataError.Local> {
        balanceStates.update { (it + states).distinct() }
        return Result.Success(Unit)
    }

    override suspend fun deleteAccount(id: AccountId): EmptyResult<DataError.Local> {
        accounts.update { list -> list.filterNot { it.id == id } }
        return Result.Success(Unit)
    }
}

class InMemoryCategoryDataSource(initial: List<Category> = emptyList()) : CategoryLocalDataSource {
    val categories = MutableStateFlow(initial)
    override fun observeCategories(): Flow<List<Category>> = categories
    override suspend fun getCategories(): Result<List<Category>, DataError.Local> = Result.Success(categories.value)
    override suspend fun ensureDefaults(defaults: List<Category>): EmptyResult<DataError.Local> {
        categories.update { current -> current + defaults.filter { d -> current.none { it.id == d.id } } }
        return Result.Success(Unit)
    }
}

class InMemoryRuleDataSource : RuleLocalDataSource {
    val rules = MutableStateFlow<List<CategoryRule>>(emptyList())
    override fun observeUserRules(): Flow<List<CategoryRule>> = rules
    override suspend fun getUserRules(): Result<List<CategoryRule>, DataError.Local> = Result.Success(rules.value)
    override suspend fun upsertRule(rule: CategoryRule): EmptyResult<DataError.Local> {
        rules.update { list -> list.filterNot { it.id == rule.id } + rule }
        return Result.Success(Unit)
    }

    override suspend fun deleteRule(id: String): EmptyResult<DataError.Local> {
        rules.update { list -> list.filterNot { it.id == id } }
        return Result.Success(Unit)
    }
}

class InMemoryMerchantMemoryDataSource : MerchantMemoryLocalDataSource {
    val memories = MutableStateFlow<List<MerchantMemory>>(emptyList())
    override fun observeAll(): Flow<List<MerchantMemory>> = memories
    override suspend fun getAll(): Result<List<MerchantMemory>, DataError.Local> = Result.Success(memories.value)
    override suspend fun remember(memory: MerchantMemory): EmptyResult<DataError.Local> {
        memories.update { list -> list.filterNot { it.payeeKey == memory.payeeKey } + memory }
        return Result.Success(Unit)
    }

    override suspend fun forget(payeeKey: String): EmptyResult<DataError.Local> {
        memories.update { list -> list.filterNot { it.payeeKey == payeeKey } }
        return Result.Success(Unit)
    }
}

class FixedRuleCatalog(
    private val rules: List<CategoryRule> = emptyList(),
    private val categories: List<Category> = emptyList(),
) : RuleCatalog {
    override suspend fun builtInRules(): List<CategoryRule> = rules
    override suspend fun defaultCategories(): List<Category> = categories
}

class InMemoryCsvProfileDataSource : CsvProfileLocalDataSource {
    val profiles = MutableStateFlow<List<CsvProfile>>(emptyList())
    override fun observeProfiles(): Flow<List<CsvProfile>> = profiles
    override suspend fun getProfiles(): Result<List<CsvProfile>, DataError.Local> = Result.Success(profiles.value)
    override suspend fun upsertProfile(profile: CsvProfile): EmptyResult<DataError.Local> {
        profiles.update { list -> list.filterNot { it.id == profile.id } + profile }
        return Result.Success(Unit)
    }

    override suspend fun deleteProfile(id: String): EmptyResult<DataError.Local> {
        profiles.update { list -> list.filterNot { it.id == id } }
        return Result.Success(Unit)
    }
}

class InMemorySettingsDataSource(initial: AppSettings = AppSettings()) : SettingsDataSource {
    val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = state
    override suspend fun update(transform: (AppSettings) -> AppSettings): EmptyResult<DataError.Local> {
        state.update(transform)
        return Result.Success(Unit)
    }
}
