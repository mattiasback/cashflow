package app.cashflow.core.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import app.cashflow.core.database.dao.AccountDao
import app.cashflow.core.database.dao.BankLinkDao
import app.cashflow.core.database.dao.CategoryDao
import app.cashflow.core.database.dao.CsvProfileDao
import app.cashflow.core.database.dao.GoalDao
import app.cashflow.core.database.dao.ImportBatchDao
import app.cashflow.core.database.dao.MerchantMemoryDao
import app.cashflow.core.database.dao.RuleDao
import app.cashflow.core.database.dao.TransactionDao
import app.cashflow.core.database.entity.AccountEntity
import app.cashflow.core.database.entity.BalanceStateEntity
import app.cashflow.core.database.entity.BankLinkEntity
import app.cashflow.core.database.entity.CategoryEntity
import app.cashflow.core.database.entity.CsvProfileEntity
import app.cashflow.core.database.entity.GoalEntity
import app.cashflow.core.database.entity.ImportBatchEntity
import app.cashflow.core.database.entity.MerchantMemoryEntity
import app.cashflow.core.database.entity.RuleEntity
import app.cashflow.core.database.entity.TombstoneEntity
import app.cashflow.core.database.entity.TransactionAliasEntity
import app.cashflow.core.database.entity.TransactionEntity
import app.cashflow.core.database.entity.TransactionFtsEntity

/**
 * Version 2 (step 2) adds savings movements, hide, undo and duplicate flags on transactions,
 * named savings accounts, tombstones for deleted rows, rule limits and saved CSV profiles.
 * Version 3 adds the sign flag of saved CSV profiles. Every change is additive, so both steps
 * are auto-migrations. Version 4 (step 4) adds household goals. Version 5 (step 8) adds
 * the origin of each row, aliases between file and bank rows, and bank links.
 */
@Database(
    entities = [
        AccountEntity::class,
        TransactionEntity::class,
        TransactionFtsEntity::class,
        BalanceStateEntity::class,
        ImportBatchEntity::class,
        CategoryEntity::class,
        RuleEntity::class,
        MerchantMemoryEntity::class,
        TombstoneEntity::class,
        CsvProfileEntity::class,
        GoalEntity::class,
        TransactionAliasEntity::class,
        BankLinkEntity::class,
    ],
    version = 5,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
    ],
)
abstract class CashFlowDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun ruleDao(): RuleDao
    abstract fun merchantMemoryDao(): MerchantMemoryDao
    abstract fun importBatchDao(): ImportBatchDao
    abstract fun csvProfileDao(): CsvProfileDao
    abstract fun goalDao(): GoalDao
    abstract fun bankLinkDao(): BankLinkDao

    companion object {
        /** Kept out of cloud backup by the app's data_extraction_rules.xml. */
        const val FILE_NAME = "cashflow.db"

        fun create(context: Context, fileName: String = FILE_NAME): CashFlowDatabase =
            Room.databaseBuilder(context.applicationContext, CashFlowDatabase::class.java, fileName).build()
    }
}
