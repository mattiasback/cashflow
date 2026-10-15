package app.cashflow.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import app.cashflow.core.database.dao.AccountDao
import app.cashflow.core.database.dao.CategoryDao
import app.cashflow.core.database.dao.ImportBatchDao
import app.cashflow.core.database.dao.MerchantMemoryDao
import app.cashflow.core.database.dao.RuleDao
import app.cashflow.core.database.dao.TransactionDao
import app.cashflow.core.database.entity.AccountEntity
import app.cashflow.core.database.entity.BalanceStateEntity
import app.cashflow.core.database.entity.CategoryEntity
import app.cashflow.core.database.entity.ImportBatchEntity
import app.cashflow.core.database.entity.MerchantMemoryEntity
import app.cashflow.core.database.entity.RuleEntity
import app.cashflow.core.database.entity.TransactionEntity
import app.cashflow.core.database.entity.TransactionFtsEntity

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
    ],
    version = 1,
    exportSchema = true,
)
abstract class CashFlowDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun ruleDao(): RuleDao
    abstract fun merchantMemoryDao(): MerchantMemoryDao
    abstract fun importBatchDao(): ImportBatchDao

    companion object {
        /** Kept out of cloud backup by the app's data_extraction_rules.xml. */
        const val FILE_NAME = "cashflow.db"

        fun create(context: Context): CashFlowDatabase =
            Room.databaseBuilder(context.applicationContext, CashFlowDatabase::class.java, FILE_NAME).build()
    }
}
