package app.cashflow.core.domain.testing

import app.cashflow.core.domain.DataError
import app.cashflow.core.domain.EmptyResult
import app.cashflow.core.domain.Result
import app.cashflow.core.domain.datasource.AccountLocalDataSource
import app.cashflow.core.domain.datasource.CategoryLocalDataSource
import app.cashflow.core.domain.datasource.Classification
import app.cashflow.core.domain.datasource.ImportBatchLocalDataSource
import app.cashflow.core.domain.datasource.MerchantMemoryLocalDataSource
import app.cashflow.core.domain.datasource.RuleCatalog
import app.cashflow.core.domain.datasource.RuleLocalDataSource
import app.cashflow.core.domain.datasource.TransactionLocalDataSource
import app.cashflow.core.domain.model.Account
import app.cashflow.core.domain.model.AccountId
import app.cashflow.core.domain.model.BalanceState
import app.cashflow.core.domain.model.Category
import app.cashflow.core.domain.model.CategoryRule
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

/** In-memory data sources: the test fakes for every interface the app reads through. */
class InMemoryTransactionDataSource(
    private val batches: InMemoryImportBatchDataSource = InMemoryImportBatchDataSource(),
) : TransactionLocalDataSource {
    val rows = MutableStateFlow<Map<TxId, Transaction>>(emptyMap())
    var failNextWrite: DataError.Local? = null

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
        val fresh = rows.filter { it.id !in this.rows.value }
        this.rows.update { current -> current + fresh.associateBy { it.id } }
        val summary = InsertSummary(newCount = fresh.size, duplicateCount = rows.size - fresh.size)
        batches.add(batch.copy(newCount = summary.newCount, duplicateCount = summary.duplicateCount))
        return Result.Success(summary)
    }

    override suspend fun updateClassifications(updates: List<Classification>): EmptyResult<DataError.Local> {
        failNextWrite?.let { failNextWrite = null; return Result.Error(it) }
        rows.update { current ->
            val next = current.toMutableMap()
            updates.forEach { u ->
                next[u.id]?.let { tx ->
                    next[u.id] = tx.copy(
                        categoryId = u.categoryId,
                        categorizedBy = u.categorizedBy,
                        transferKind = u.transferKind,
                        transferPartnerId = u.transferPartnerId,
                        loneLegConfirmed = u.loneLegConfirmed,
                    )
                }
            }
            next
        }
        return Result.Success(Unit)
    }
}

class InMemoryImportBatchDataSource : ImportBatchLocalDataSource {
    val batches = MutableStateFlow<List<ImportBatch>>(emptyList())
    fun add(batch: ImportBatch) = batches.update { it + batch }
    override fun observeBatches(): Flow<List<ImportBatch>> = batches
    override suspend fun getBatch(id: ImportId): Result<ImportBatch, DataError.Local> =
        batches.value.firstOrNull { it.id == id }?.let { Result.Success(it) } ?: Result.Error(DataError.Local.NOT_FOUND)
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
}

class InMemoryMerchantMemoryDataSource : MerchantMemoryLocalDataSource {
    val memories = MutableStateFlow<List<MerchantMemory>>(emptyList())
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
