package app.cashflow.core.domain.testing

import app.cashflow.core.domain.model.AccountId
import app.cashflow.core.domain.model.CategorizedBy
import app.cashflow.core.domain.model.CategoryId
import app.cashflow.core.domain.model.ImportId
import app.cashflow.core.domain.model.Money
import app.cashflow.core.domain.model.StatementRow
import app.cashflow.core.domain.model.Transaction
import app.cashflow.core.domain.model.TransferKind
import app.cashflow.core.domain.model.TxId
import kotlinx.datetime.LocalDate

/** Builders for fictional, anonymized test data. No real names, accounts or amounts. */
object Fixtures {

    fun row(
        date: String,
        text: String,
        kronor: Double,
        balance: Double?,
        transactionDate: String = date,
    ) = StatementRow(
        bookingDate = LocalDate.parse(date),
        transactionDate = LocalDate.parse(transactionDate),
        rawText = text,
        amount = Money.ofDouble(kronor),
        balanceAfter = balance?.let(Money::ofDouble),
    )

    /** Rows whose balance chains from [opening]: each (date, text, amount) in order. */
    fun chain(opening: Double, vararg rows: Triple<String, String, Double>): List<StatementRow> {
        var balance = Money.ofDouble(opening)
        return rows.map { (date, text, amount) ->
            balance += Money.ofDouble(amount)
            StatementRow(LocalDate.parse(date), LocalDate.parse(date), text, Money.ofDouble(amount), balance)
        }
    }

    fun tx(
        id: String,
        account: String = "acc-a",
        date: String = "2026-05-10",
        kronor: Double = -100.0,
        text: String = "MATBUTIK EXEMPEL",
        category: CategoryId = CategoryId.UNCATEGORIZED,
        categorizedBy: CategorizedBy = CategorizedBy.NONE,
        transferKind: TransferKind = TransferKind.NONE,
        transactionDate: String = date,
        countsInCashFlow: Boolean = true,
        loneLegConfirmed: Boolean = false,
    ) = Transaction(
        id = TxId(id),
        accountId = AccountId(account),
        importId = ImportId("import-1"),
        bookingDate = LocalDate.parse(date),
        transactionDate = LocalDate.parse(transactionDate),
        amount = Money.ofDouble(kronor),
        rawText = text,
        normalizedText = text,
        balanceAfter = null,
        categoryId = category,
        categorizedBy = categorizedBy,
        transferKind = transferKind,
        countsInCashFlow = countsInCashFlow,
        loneLegConfirmed = loneLegConfirmed,
    )
}
