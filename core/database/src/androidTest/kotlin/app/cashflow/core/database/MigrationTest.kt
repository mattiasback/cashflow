package app.cashflow.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Step 1 databases upgrade to step 2 in place: a v1 row survives 1 → 2 → 3 with the new
 * columns at their defaults.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), CashFlowDatabase::class.java)

    @Test
    fun migrate1To3_keepsRowsAndAddsDefaults() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL("INSERT INTO accounts (id, name, type, clearingPrefix, accountNumber) VALUES ('a1', 'Konto', 'TRANSACTION', NULL, NULL)")
            db.execSQL(
                """
                INSERT INTO transactions (id, accountId, importId, bookingEpochDay, transactionEpochDay, amountOre, rawText, normalizedText,
                balanceAfterOre, categoryId, categorizedBy, transferKind, transferPartnerId, loneLegConfirmed, fileOrder, countsInCashFlow)
                VALUES ('t1', 'a1', 'i1', 20000, 20000, -10000, 'MATBUTIK', 'Matbutik', 500, 'mat', 'BUILT_IN_RULE', 'NONE', NULL, 0, 0, 1)
                """.trimIndent(),
            )
            db.execSQL("INSERT INTO rules (id, pattern, `match`, categoryId, sign) VALUES ('r1', 'ICA', 'CONTAINS', 'mat', NULL)")
            db.execSQL("INSERT INTO merchant_memory (payeeKey, categoryId) VALUES ('KAFE', 'restaurang')")
        }

        helper.runMigrationsAndValidate(DB, 2, true).close()
        helper.runMigrationsAndValidate(DB, 3, true).use { db ->
            db.query("SELECT hidden, undone, duplicateChecked, movement, amountOre FROM transactions WHERE id = 't1'").use { c ->
                c.moveToFirst()
                assertThat(c.getInt(0)).isEqualTo(0)
                assertThat(c.getInt(1)).isEqualTo(0)
                assertThat(c.getInt(2)).isEqualTo(0)
                assertThat(c.isNull(3)).isEqualTo(true)
                assertThat(c.getLong(4)).isEqualTo(-10000L)
            }
            db.query("SELECT origin FROM rules WHERE id = 'r1'").use { c ->
                c.moveToFirst()
                assertThat(c.getString(0)).isEqualTo("CREATED")
            }
            db.query("SELECT COUNT(*) FROM merchant_memory").use { c ->
                c.moveToFirst()
                assertThat(c.getInt(0)).isEqualTo(1)
            }
        }
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
