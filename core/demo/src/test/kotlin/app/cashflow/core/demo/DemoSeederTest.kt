package app.cashflow.core.demo

import app.cashflow.core.categorization.BundledCatalog
import app.cashflow.core.categorization.Categorizer
import app.cashflow.core.categorization.PayeeNormalizer
import app.cashflow.core.domain.cashflow.FlowData
import app.cashflow.core.domain.model.CategoryId
import app.cashflow.core.domain.model.CategoryKind
import app.cashflow.core.domain.model.MortgageSplit
import app.cashflow.core.domain.model.Period
import app.cashflow.core.domain.model.TransactionStatus
import app.cashflow.core.domain.model.TransferKind
import app.cashflow.core.domain.testing.InMemoryAccountDataSource
import app.cashflow.core.domain.testing.InMemoryCategoryDataSource
import app.cashflow.core.domain.testing.InMemoryImportBatchDataSource
import app.cashflow.core.domain.testing.InMemoryMerchantMemoryDataSource
import app.cashflow.core.domain.testing.InMemoryRuleDataSource
import app.cashflow.core.domain.testing.InMemorySettingsDataSource
import app.cashflow.core.domain.testing.InMemoryTransactionDataSource
import app.cashflow.core.importengine.ReclassifyUseCase
import app.cashflow.core.importengine.TransferHints
import app.cashflow.core.importengine.TransferMatcher
import assertk.assertThat
import assertk.assertions.isBetween
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test

class DemoSeederTest {

    private val today = LocalDate(2026, 10, 21)

    private suspend fun seeded(): Pair<InMemoryTransactionDataSource, InMemorySettingsDataSource> {
        val batches = InMemoryImportBatchDataSource()
        val transactions = InMemoryTransactionDataSource(batches)
        val accounts = InMemoryAccountDataSource()
        val settings = InMemorySettingsDataSource()
        val catalog = BundledCatalog.load()
        val reclassify = ReclassifyUseCase(
            transactions, accounts, InMemoryMerchantMemoryDataSource(), InMemoryRuleDataSource(), catalog,
            TransferMatcher(), Categorizer(PayeeNormalizer()),
        )
        DemoSeeder(transactions, accounts, InMemoryCategoryDataSource(), catalog, settings, reclassify).seedIfEmpty(today)
        return transactions to settings
    }

    @Test
    fun `the real pipeline categorizes the demo, leaving a handful to review`() = runTest {
        val (transactions, settings) = seeded()
        val rows = transactions.rows.value.values.filter { it.countsInCashFlow }
        val review = rows.count { it.status == TransactionStatus.TO_REVIEW }
        assertThat(rows.size).isGreaterThan(400)
        // One payee (the preschool) recurs monthly, so one "Kom ihåg" clears most of them.
        assertThat(review).isBetween(10, 70)
        assertThat(settings.settings.first().bufferBalance).isNotNull()
    }

    @Test
    fun `savings transfers pair with their other leg and land in Buffert and Fonder`() = runTest {
        val rows = seeded().first.rows.value.values
        val toBuffer = rows.filter { it.rawText == "ÖVERFÖRING BUFFERTKONTO" && it.amount.isNegative }
        val toIsk = rows.filter { it.rawText == "ÖVERFÖRING ISK" }
        assertThat(toBuffer.all { it.transferKind == TransferKind.TO_SAVINGS && it.categoryId == CategoryId.BUFFER }).isEqualTo(true)
        assertThat(toIsk.all { it.transferKind == TransferKind.TO_SAVINGS && it.categoryId == CategoryId.FUNDS }).isEqualTo(true)
        val repair = rows.single { it.rawText == "ÖVERFÖRING BUFFERTKONTO" && it.amount.isPositive }
        assertThat(repair.transferKind).isEqualTo(TransferKind.FROM_SAVINGS)
    }

    @Test
    fun `three years up to the current month, one or two deficit months a year`() = runTest {
        val rows = seeded().first.rows.value.values.filter { it.countsInCashFlow }
        val catalog = BundledCatalog.load().defaultCategories().associate { it.id to it.kind }
        val data = FlowData.of(rows.toList(), MortgageSplit(), { catalog[it] ?: CategoryKind.UNCATEGORIZED })
        assertThat(data.periods.size).isEqualTo(DemoHousehold.MONTHS)
        assertThat(data.periods.last()).isEqualTo(Period.of(today))
        val deficits = data.periods.count { data.month(it).net.isNegative }
        // One or two deficit months a year, also in the last twelve.
        assertThat(deficits).isBetween(3, 6)
        assertThat(data.twelveMonthsTo(data.periods.last()).count { data.month(it).net.isNegative }).isBetween(1, 2)
        // A responsible family that saves some: a savings rate of 7–10 %.
        assertThat(data.sum(data.twelveMonthsTo(data.periods.last())).savingsRatePercent!!).isBetween(7.0, 10.0)
        // Little is left unplaced on the account: about 1–3 % of income.
        val year = data.sum(data.twelveMonthsTo(data.periods.last()))
        assertThat(year.left.ore.toDouble() / year.income.ore * 100).isBetween(0.5, 4.0)
    }

    @Test
    fun `no demo text looks like an account number, and the same day gives the same data`() {
        val a = DemoHousehold.generate(today)
        val texts = a.imports.flatMap { it.rows }.map { it.rawText }.distinct()
        texts.forEach { assertThat(TransferHints.accountNumberIn(it)).isNull() }
        assertThat(DemoHousehold.generate(today)).isEqualTo(a)
    }
}
