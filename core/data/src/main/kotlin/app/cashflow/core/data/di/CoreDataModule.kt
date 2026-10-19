package app.cashflow.core.data.di

import app.cashflow.core.categorization.BundledCatalog
import app.cashflow.core.data.local.RoomAccountDataSource
import app.cashflow.core.data.local.RoomCategoryDataSource
import app.cashflow.core.data.local.RoomCsvProfileDataSource
import app.cashflow.core.data.local.RoomGoalDataSource
import app.cashflow.core.data.local.RoomImportBatchDataSource
import app.cashflow.core.data.local.RoomMerchantMemoryDataSource
import app.cashflow.core.data.local.RoomRuleDataSource
import app.cashflow.core.data.local.RoomTransactionDataSource
import app.cashflow.core.data.settings.DataStoreSettingsDataSource
import app.cashflow.core.database.CashFlowDatabase
import app.cashflow.core.domain.datasource.AccountLocalDataSource
import app.cashflow.core.domain.datasource.CategoryLocalDataSource
import app.cashflow.core.domain.datasource.CsvProfileLocalDataSource
import app.cashflow.core.domain.datasource.GoalLocalDataSource
import app.cashflow.core.domain.datasource.ImportBatchLocalDataSource
import app.cashflow.core.domain.datasource.MerchantMemoryLocalDataSource
import app.cashflow.core.domain.datasource.RuleCatalog
import app.cashflow.core.domain.datasource.RuleLocalDataSource
import app.cashflow.core.domain.datasource.SettingsDataSource
import app.cashflow.core.domain.datasource.TransactionLocalDataSource
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import java.io.File

val coreDataModule = module {
    // Lambda form: the database is built by a factory method, not a constructor.
    single { CashFlowDatabase.create(androidContext()) }
    // The bundled catalog (categories and built-in rules) is read once from the app's classpath.
    single<RuleCatalog> { BundledCatalog.load() }
    singleOf(::RoomTransactionDataSource) { bind<TransactionLocalDataSource>() }
    singleOf(::RoomAccountDataSource) { bind<AccountLocalDataSource>() }
    singleOf(::RoomCategoryDataSource) { bind<CategoryLocalDataSource>() }
    singleOf(::RoomRuleDataSource) { bind<RuleLocalDataSource>() }
    singleOf(::RoomMerchantMemoryDataSource) { bind<MerchantMemoryLocalDataSource>() }
    singleOf(::RoomImportBatchDataSource) { bind<ImportBatchLocalDataSource>() }
    singleOf(::RoomCsvProfileDataSource) { bind<CsvProfileLocalDataSource>() }
    singleOf(::RoomGoalDataSource) { bind<GoalLocalDataSource>() }
    // Lambda form: the DataStore file lives in the app's private files, excluded from backup.
    single<SettingsDataSource> {
        DataStoreSettingsDataSource.create(File(androidContext().filesDir, "datastore/${DataStoreSettingsDataSource.FILE_NAME}"))
    }
}
