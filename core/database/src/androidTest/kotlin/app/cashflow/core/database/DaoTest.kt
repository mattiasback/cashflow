package app.cashflow.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cashflow.core.database.entity.AccountEntity
import app.cashflow.core.database.entity.BalanceStateEntity
import app.cashflow.core.database.entity.CategoryEntity
import app.cashflow.core.database.entity.CsvProfileEntity
import app.cashflow.core.database.entity.ImportBatchEntity
import app.cashflow.core.database.entity.MerchantMemoryEntity
import app.cashflow.core.database.entity.RuleEntity
import app.cashflow.core.database.entity.TransactionEntity
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Every DAO against a real in-memory Room database. All data is fictional. */
@RunWith(AndroidJUnit4::class)
class DaoTest {

    private lateinit var db: CashFlowDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), CashFlowDatabase::class.java).build()
    }

    @After
    fun tearDown() = db.close()

    private fun tx(id: String, importId: String = "i1", day: Long = 20_000, ore: Long = -10_000, text: String = "MATBUTIK EXEMPEL") = TransactionEntity(
        id = id, accountId = "a1", importId = importId, bookingEpochDay = day, transactionEpochDay = day, amountOre = ore,
        rawText = text, normalizedText = text, balanceAfterOre = null, categoryId = "okat", categorizedBy = "NONE",
        transferKind = "NONE", transferPartnerId = null, loneLegConfirmed = false, fileOrder = 0, countsInCashFlow = true,
    )

    private fun batch(id: String) = ImportBatchEntity(id, "LANSFORSAKRINGAR_XLS", "$id.xls", "a1", 0, 0, 0, 0, "")

    private suspend fun seedAccount() = db.accountDao().upsert(AccountEntity("a1", "Konto", "TRANSACTION", null, null))

    @Test
    fun insertBatch_skipsExistingIds_andCountsNewRows() = runTest {
        seedAccount()
        val dao = db.transactionDao()
        assertThat(dao.insertBatch(batch("i1"), listOf(tx("t1"), tx("t2")))).isEqualTo(2)
        assertThat(dao.insertBatch(batch("i2"), listOf(tx("t2", "i2"), tx("t3", "i2")))).isEqualTo(1)
        assertThat(dao.getAll().map { it.id }).containsExactlyInAnyOrder("t1", "t2", "t3")
        assertThat(db.importBatchDao().getBatch("i2")!!.duplicateCount).isEqualTo(1)
    }

    @Test
    fun undoAndRedo_setRowsAsideAndBringThemBack() = runTest {
        seedAccount()
        val dao = db.transactionDao()
        dao.insertBatch(batch("i1"), listOf(tx("t1")))
        dao.insertBatch(batch("i2"), listOf(tx("t2", "i2")))

        dao.undoImport("i2", 1_000)
        assertThat(dao.getAll().map { it.id }).containsExactly("t1")
        assertThat(db.importBatchDao().undoneRowCount("i2")).isEqualTo(1)
        assertThat(db.importBatchDao().getBatch("i2")!!.undoneAtEpochMillis).isEqualTo(1_000L)

        dao.redoImport("i2")
        assertThat(dao.getAll().map { it.id }).containsExactlyInAnyOrder("t1", "t2")
        assertThat(db.importBatchDao().getBatch("i2")!!.undoneAtEpochMillis).isNull()
    }

    @Test
    fun reimportingAnUndoneFile_revivesItsRowsUnderTheNewImport() = runTest {
        seedAccount()
        val dao = db.transactionDao()
        dao.insertBatch(batch("i1"), listOf(tx("t1")))
        dao.undoImport("i1", 1_000)
        assertThat(dao.insertBatch(batch("i2"), listOf(tx("t1", "i2")))).isEqualTo(1)
        assertThat(dao.getAll().single().importId).isEqualTo("i2")
    }

    @Test
    fun deletedRows_leaveTombstones_andStayDeletedOnReimport() = runTest {
        seedAccount()
        val dao = db.transactionDao()
        dao.insertBatch(batch("i1"), listOf(tx("t1"), tx("t2")))
        dao.deleteWithTombstones(listOf("t1"), 5_000)
        assertThat(dao.insertBatch(batch("i2"), listOf(tx("t1", "i2"), tx("t2", "i2")))).isEqualTo(0)
        assertThat(dao.getAll().map { it.id }).containsExactly("t2")
    }

    @Test
    fun hiddenAndDuplicateFlags_areStored() = runTest {
        seedAccount()
        val dao = db.transactionDao()
        dao.insertBatch(batch("i1"), listOf(tx("t1"), tx("t2")))
        dao.setHidden(listOf("t1"), true)
        dao.setDuplicateOf("t2", "t1")
        dao.setDuplicateChecked("t2")
        val rows = dao.getAll().associateBy { it.id }
        assertThat(rows.getValue("t1").hidden).isEqualTo(true)
        assertThat(rows.getValue("t2").duplicateOf).isEqualTo("t1")
        assertThat(rows.getValue("t2").duplicateChecked).isEqualTo(true)
    }

    @Test
    fun search_findsLiveRowsOnly() = runTest {
        seedAccount()
        val dao = db.transactionDao()
        dao.insertBatch(batch("i1"), listOf(tx("t1", text = "KAFE EXEMPEL")))
        dao.insertBatch(batch("i2"), listOf(tx("t2", "i2", text = "KAFE ANNAT")))
        dao.undoImport("i2", 1)
        assertThat(dao.search("kafe*").first()).containsExactly("t1")
    }

    @Test
    fun accounts_balances_categories_rules_memories_and_profiles() = runTest {
        val accounts = db.accountDao()
        accounts.upsert(AccountEntity("s1", "ISK Exempel", "ISK", null, null, institution = "Avanza", externalKey = "avanza:ISK Exempel", marketValueOre = 100_00))
        accounts.insertBalanceStates(listOf(BalanceStateEntity("s1", 1, 5), BalanceStateEntity("s1", 1, 5)))
        assertThat(accounts.getAccounts().single().externalKey).isEqualTo("avanza:ISK Exempel")
        assertThat(accounts.getBalanceStates().size).isEqualTo(1)

        db.categoryDao().insertMissing(listOf(CategoryEntity("mat", "Mat", "VARIABLE", 1)))
        db.categoryDao().insertMissing(listOf(CategoryEntity("mat", "Annat", "FIXED", 2)))
        assertThat(db.categoryDao().getCategories().single().name).isEqualTo("Mat")

        val rules = db.ruleDao()
        rules.upsert(RuleEntity("r1", "BOLÅN", "STARTS_WITH", "boende", null, minAmountOre = 500_000, origin = "OVERRIDE", createdAtEpochMillis = 1))
        assertThat(rules.getRules().single().minAmountOre).isEqualTo(500_000L)
        rules.delete("r1")
        assertThat(rules.getRules()).isEmpty()

        val memories = db.merchantMemoryDao()
        memories.upsert(MerchantMemoryEntity("KAFE", "restaurang", 9))
        assertThat(memories.observeAll().first().single().createdAtEpochMillis).isEqualTo(9L)
        memories.delete("KAFE")
        assertThat(memories.getAll()).isEmpty()

        val profiles = db.csvProfileDao()
        profiles.upsert(CsvProfileEntity("p1", "Bank", "datum|text|belopp", 0, 0, 1, 2, null, null, null, null, 1, null, negateAmounts = true))
        assertThat(profiles.getProfiles().single().negateAmounts).isEqualTo(true)
        profiles.delete("p1")
        assertThat(profiles.getProfiles()).isEmpty()
        assertThat(accounts.getAccounts().firstOrNull()).isNotNull()
    }
}
