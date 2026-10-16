package app.cashflow.core.importengine

import app.cashflow.core.categorization.BundledCatalog
import app.cashflow.core.categorization.Categorizer
import app.cashflow.core.categorization.PayeeNormalizer
import app.cashflow.core.domain.Result
import app.cashflow.core.domain.model.AccountType
import app.cashflow.core.domain.model.CategoryId
import app.cashflow.core.domain.model.ImportSource
import app.cashflow.core.domain.model.Money
import app.cashflow.core.domain.model.Movement
import app.cashflow.core.domain.model.ParsedStatement
import app.cashflow.core.domain.model.StatementAccount
import app.cashflow.core.domain.model.StatementRow
import app.cashflow.core.domain.model.TransactionStatus
import app.cashflow.core.domain.testing.InMemoryAccountDataSource
import app.cashflow.core.domain.testing.InMemoryMerchantMemoryDataSource
import app.cashflow.core.domain.testing.InMemoryRuleDataSource
import app.cashflow.core.domain.testing.InMemoryTransactionDataSource
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test

/** Savings exports that name several accounts in one file. All data is fictional. */
class SavingsImportTest {

    private val transactions = InMemoryTransactionDataSource()
    private val accounts = InMemoryAccountDataSource()
    private val hasher = TransactionHasher()
    private val normalizer = PayeeNormalizer()
    private val useCase = ImportStatementUseCase(
        transactions = transactions,
        accounts = accounts,
        inferrer = AccountInferrer(hasher),
        hasher = hasher,
        normalizer = normalizer,
        reclassify = ReclassifyUseCase(
            transactions, accounts, InMemoryMerchantMemoryDataSource(), InMemoryRuleDataSource(),
            BundledCatalog.load(), TransferMatcher(), Categorizer(normalizer),
        ),
    )

    private val isk = StatementAccount("avanza:ISK Exempel", "ISK Exempel", "Avanza")
    private val bag = StatementAccount("avanza:Burken", "Burken", "Avanza")

    private fun row(date: String, account: StatementAccount, text: String, kronor: Double, movement: Movement) = StatementRow(
        bookingDate = LocalDate.parse(date),
        transactionDate = LocalDate.parse(date),
        rawText = text,
        amount = Money.ofDouble(kronor),
        balanceAfter = null,
        account = account,
        movement = movement,
    )

    private fun export(vararg rows: StatementRow) = ParsedStatement("transaktioner.csv", ImportSource.AVANZA_CSV, rows.toList(), emptyList())

    @Test
    fun `named accounts are created once with the type their name gives, and unknown names are asked`() = runTest {
        val file = export(
            row("2026-05-02", isk, "Insättning Exempel", 1000.0, Movement.DEPOSIT),
            row("2026-05-03", isk, "Köp Fond", -900.0, Movement.TRADE),
            row("2026-05-04", bag, "Utdelning Bolag", 12.5, Movement.RETURN),
        )
        val plan = (useCase.plan(listOf(file)) as Result.Success).data
        assertThat(plan.questions).isEqualTo(emptyList())
        assertThat(plan.typeQuestions.map { it.source.key }).isEqualTo(listOf(bag.key))

        useCase.execute(plan, emptyMap(), mapOf(bag.key to AccountType.KF))

        assertThat(accounts.accounts.value.map { it.name to it.type }).containsExactlyInAnyOrder("ISK Exempel" to AccountType.ISK, "Burken" to AccountType.KF)
        val rows = transactions.rows.value.values
        assertThat(rows.all { !it.countsInCashFlow && it.status == TransactionStatus.AUTO }).isEqualTo(true)
        assertThat(rows.first { it.movement == Movement.TRADE }.categoryId).isEqualTo(CategoryId.TRADES)

        // The same accounts are found again by key, so the next export asks nothing.
        val again = (useCase.plan(listOf(file)) as Result.Success).data
        assertThat(again.typeQuestions).isEqualTo(emptyList())
        val outcome = (useCase.execute(again, emptyMap()) as Result.Success).data
        assertThat(outcome.newCount).isEqualTo(0)
        assertThat(accounts.accounts.value.size).isEqualTo(2)
    }

    @Test
    fun `a slightly changed row in a later export is flagged as a possible duplicate`() = runTest {
        useCase.execute((useCase.plan(listOf(export(row("2026-05-02", isk, "Insättning Exempel", 1000.0, Movement.DEPOSIT)))) as Result.Success).data, emptyMap())
        val later = export(
            // Same deposit, booked a day later in the new export.
            row("2026-05-03", isk, "Insättning Exempel", 1000.0, Movement.DEPOSIT),
            row("2026-05-20", isk, "Insättning Exempel", 1000.0, Movement.DEPOSIT),
        )
        useCase.execute((useCase.plan(listOf(later)) as Result.Success).data, emptyMap())

        val rows = transactions.rows.value.values.sortedBy { it.bookingDate }
        assertThat(rows[0].duplicateOf).isNull()
        assertThat(rows[1].duplicateOf).isEqualTo(rows[0].id)
        assertThat(rows[2].duplicateOf).isNull()
        assertThat(rows[1].status).isEqualTo(TransactionStatus.TO_REVIEW)
        assertThat(rows[1].isPossibleDuplicate).isEqualTo(true)
        assertThat(rows[0].transactionDate).isNotNull()
    }
}
