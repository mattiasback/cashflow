package app.cashflow.core.demo

import app.cashflow.core.domain.DataError
import app.cashflow.core.domain.EmptyResult
import app.cashflow.core.domain.Result
import app.cashflow.core.domain.datasource.AccountLocalDataSource
import app.cashflow.core.domain.datasource.CategoryLocalDataSource
import app.cashflow.core.domain.datasource.RuleCatalog
import app.cashflow.core.domain.datasource.SettingsDataSource
import app.cashflow.core.domain.datasource.TransactionLocalDataSource
import app.cashflow.core.domain.model.BufferBalance
import app.cashflow.core.importengine.ReclassifyUseCase
import kotlinx.datetime.LocalDate

/**
 * Fills the demo database once: accounts, one file per month, then the same transfer matching
 * and categorization as a real import. Runs only against the demo database, never the user's.
 */
class DemoSeeder(
    private val transactions: TransactionLocalDataSource,
    private val accounts: AccountLocalDataSource,
    private val categories: CategoryLocalDataSource,
    private val catalog: RuleCatalog,
    private val settings: SettingsDataSource,
    private val reclassify: ReclassifyUseCase,
) {

    suspend fun seedIfEmpty(today: LocalDate): EmptyResult<DataError.Local> {
        val existing = transactions.getAll()
        if (existing is Result.Success && existing.data.isNotEmpty()) return Result.Success(Unit)
        categories.ensureDefaults(catalog.defaultCategories())
        val demo = DemoHousehold.generate(today)
        for (account in demo.accounts) {
            accounts.upsertAccount(account).let { if (it is Result.Error) return it }
        }
        for (import in demo.imports) {
            transactions.insertBatch(import.batch, import.rows).let { if (it is Result.Error) return Result.Error(it.error) }
        }
        settings.update { it.copy(bufferBalance = BufferBalance(demo.bufferBalance, demo.bufferBalanceDate)) }
        return reclassify()
    }
}
