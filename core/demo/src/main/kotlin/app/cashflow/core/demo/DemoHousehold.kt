package app.cashflow.core.demo

import app.cashflow.core.categorization.PayeeNormalizer
import app.cashflow.core.domain.model.Account
import app.cashflow.core.domain.model.AccountId
import app.cashflow.core.domain.model.AccountType
import app.cashflow.core.domain.model.CategorizedBy
import app.cashflow.core.domain.model.CategoryId
import app.cashflow.core.domain.model.ImportBatch
import app.cashflow.core.domain.model.ImportId
import app.cashflow.core.domain.model.ImportSource
import app.cashflow.core.domain.model.Money
import app.cashflow.core.domain.model.Movement
import app.cashflow.core.domain.model.Period
import app.cashflow.core.domain.model.Transaction
import app.cashflow.core.domain.model.TransferKind
import app.cashflow.core.domain.model.TxId
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.number
import kotlin.math.roundToLong
import kotlin.random.Random

/** One imported file of the demo: its batch and rows, before categorization. */
data class DemoImport(val batch: ImportBatch, val rows: List<Transaction>)

data class DemoData(
    val accounts: List<Account>,
    val imports: List<DemoImport>,
    /** The buffer account's balance on a day, as a user would enter it in Profil. */
    val bufferBalance: Money,
    val bufferBalanceDate: LocalDate,
)

/**
 * A made-up household for demo mode: two salaries, a mortgage, a buffer account and an ISK,
 * [MONTHS] whole months up to the month before [today]. Payees are ordinary Swedish chains and services, so the real
 * pipeline (transfer matching, built-in rules) categorizes them; a few unknown payees land in
 * Att granska. No name, account number or amount comes from a real statement, and the same
 * day always gives the same data.
 *
 * Built to show every screen: a price rise and small monthly charges for Insikter, restaurant
 * spending above its average, a buffer withdrawal, a deficit month and rows to review.
 */
object DemoHousehold {

    const val MONTHS = 14

    val BANK = Account(AccountId("demo-lonekonto"), "Lönekonto", AccountType.TRANSACTION, clearingPrefix = "9999")
    val BUFFER = Account(AccountId("demo-buffert"), "Buffertkonto", AccountType.SAVINGS, clearingPrefix = "9999")
    val ISK = Account(
        AccountId("demo-isk"), "ISK Demobanken", AccountType.ISK,
        institution = "Demobanken", externalKey = "demo-isk",
    )

    private val normalizer = PayeeNormalizer()

    fun generate(today: LocalDate): DemoData {
        val random = Random(2026)
        // Ends with the last whole month, so Hem opens on a full month (salary, savings and all).
        val last = Period.of(today).previous()
        var first = last
        repeat(MONTHS - 1) { first = first.previous() }

        val bank = mutableMapOf<Period, MutableList<Row>>()
        val buffer = mutableListOf<Row>()
        val isk = mutableListOf<Row>()
        var period = first
        var index = 0
        while (period <= last) {
            val rows = bank.getOrPut(period) { mutableListOf() }
            val p = period
            val m = index
            fun add(day: Int, kronor: Double, text: String) {
                val date = p.day(day)
                if (date <= today) rows += Row(date, kronor, text)
            }
            fun vary(base: Double, spread: Double) = base + random.nextDouble(-spread, spread)
            val winter = p.month in listOf(11, 12, 1, 2, 3)

            // Income.
            add(25, vary(34_200.0, 400.0), "LÖN EXEMPELBOLAGET AB")
            add(25, vary(29_600.0, 400.0), "LÖN DEMOFÖRETAGET AB")
            add(20, 1_250.0, "BARNBIDRAG")
            // Fixed costs.
            add(1, -5_380.0, "BRF EXEMPELGÅRDEN AVGIFT")
            add(28, -9_850.0, "BOLÅN DEMOBANKEN")
            add(9, -(if (winter) vary(1_450.0, 250.0) else vary(520.0, 150.0)), "VATTENFALL")
            add(3, -930.0, "VÄSTTRAFIK")
            add(15, -612.0, "TRYGG-HANSA")
            add(27, -320.0, "UNIONEN")
            add(4, -399.0, "TELIA")
            add(5, -169.0, "STORYTEL")
            // A price rise four months ago: Insikter's "Se över abonnemangen".
            add(7, if (m < MONTHS - 4) -129.0 else -159.0, "NETFLIX")
            add(2, -399.0, "NORDIC WELLNESS")
            // Small monthly charges: "Stoppa små dragningar".
            add(11, -29.0, "GOOGLE ONE")
            add(13, -79.0, "BONNIER NYHETER")
            add(17, -49.0, "PARKERING APPEN")
            // Food, restaurants, shopping.
            // Groceries are steady: eleven shops a month, so the category stays near its average.
            repeat(11) {
                add(random.nextInt(1, 29), -vary(620.0, 200.0), listOf("ICA SUPERMARKET", "HEMKÖP", "CITY GROSS", "LIDL")[random.nextInt(4)])
            }
            // The last two months eat out more: "Håll snittet för Restaurang".
            val eatingOut = if (m >= MONTHS - 2) 5 else 3
            repeat(eatingOut) {
                add(random.nextInt(1, 29), -vary(380.0, 180.0), listOf("PIZZERIA EXEMPEL", "ESPRESSO HOUSE", "SUSHI DEMO")[random.nextInt(3)])
            }
            repeat(random.nextInt(1, 4)) {
                add(random.nextInt(1, 29), -vary(850.0, 600.0), listOf("H&M", "CLAS OHLSON", "ELGIGANTEN")[random.nextInt(3)])
            }
            add(random.nextInt(1, 29), -vary(700.0, 400.0), "CIRCLE K")
            if (random.nextInt(3) == 0) add(random.nextInt(1, 29), -vary(240.0, 120.0), "KRONANS APOTEK")
            if (random.nextInt(3) == 0) add(random.nextInt(1, 29), -vary(420.0, 200.0), "STADIUM")
            // Unknown payees for Att granska.
            repeat(random.nextInt(1, 3)) {
                add(random.nextInt(1, 29), -vary(260.0, 180.0), listOf("LOPPIS DEMO", "FÖRENINGEN EXEMPEL", "MARKNADEN STAND", "KVARTERSBUTIKEN")[random.nextInt(4)])
            }
            // A summer trip and a deficit month.
            if (p.month == 7) {
                add(4, -18_400.0, "SJ")
                add(6, -6_900.0, "HOTELL EXEMPEL")
            }
            // Savings: the buffer and the ISK, both legs, so transfer matching pairs them.
            add(26, -2_500.0, "ÖVERFÖRING BUFFERTKONTO")
            p.day(26).takeIf { it <= today }?.let { buffer += Row(it, 2_500.0, "ÖVERFÖRING FRÅN LÖNEKONTO") }
            add(27, -4_000.0, "ÖVERFÖRING ISK")
            p.day(27).takeIf { it <= today }?.let { isk += Row(it, 4_000.0, "Insättning", Movement.DEPOSIT) }
            p.day(28).takeIf { it <= today }?.let { isk += Row(it, vary(900.0, 700.0), "Avkastning", Movement.RETURN) }
            // An unplanned car repair paid from the buffer.
            if (m == MONTHS - 6) {
                add(12, -7_800.0, "BILVERKSTAD DEMO")
                p.day(12).let { buffer += Row(it, -7_800.0, "ÖVERFÖRING TILL LÖNEKONTO") }
                add(12, 7_800.0, "ÖVERFÖRING BUFFERTKONTO")
            }
            period = period.next()
            index++
        }

        val imports = ArrayList<DemoImport>()
        var balance = 24_500.0
        var n = 0
        for ((p, rows) in bank.toSortedMap()) {
            if (rows.isEmpty()) continue
            val id = ImportId("demo-bank-$p")
            val sorted = rows.sortedBy { it.date }
            val txs = sorted.mapIndexed { i, r ->
                balance += r.kronor
                r.toTransaction("demo-${n++}", BANK, id, i, Money.ofDouble(balance))
            }
            imports += DemoImport(batch(id, "Kontoutdrag Lönekonto $p.xls", BANK.id, ImportSource.LANSFORSAKRINGAR_XLS, sorted.last().date, txs.size), txs)
        }
        var bufferBalance = 38_000.0
        val bufferId = ImportId("demo-buffert")
        val bufferTxs = buffer.sortedBy { it.date }.mapIndexed { i, r ->
            bufferBalance += r.kronor
            r.toTransaction("demo-b$i", BUFFER, bufferId, i, Money.ofDouble(bufferBalance))
        }
        imports += DemoImport(batch(bufferId, "Kontoutdrag Buffertkonto.xls", BUFFER.id, ImportSource.LANSFORSAKRINGAR_XLS, today, bufferTxs.size), bufferTxs)
        val iskId = ImportId("demo-isk")
        val iskTxs = isk.sortedBy { it.date }.mapIndexed { i, r -> r.toTransaction("demo-i$i", ISK, iskId, i, null) }
        imports += DemoImport(batch(iskId, "Transaktioner ISK Demobanken.csv", null, ImportSource.AVANZA_CSV, today, iskTxs.size), iskTxs)

        val iskValue = Money.ofDouble(isk.sumOf { it.kronor } + 120_000.0)
        val balanceDate = last.previous().first
        val bufferOnDate = 38_000.0 + buffer.filter { it.date <= balanceDate }.sumOf { it.kronor }
        return DemoData(
            accounts = listOf(BANK, BUFFER, ISK.copy(marketValue = iskValue, marketValueDate = today)),
            imports = imports,
            bufferBalance = Money.ofDouble(bufferOnDate),
            bufferBalanceDate = balanceDate,
        )
    }

    private data class Row(val date: LocalDate, val kronor: Double, val text: String, val movement: Movement? = null)

    private fun Row.toTransaction(id: String, account: Account, importId: ImportId, order: Int, balanceAfter: Money?) = Transaction(
        id = TxId(id),
        accountId = account.id,
        importId = importId,
        bookingDate = date,
        transactionDate = date,
        amount = Money((kronor * 100).roundToLong()),
        rawText = text,
        normalizedText = normalizer.displayName(text),
        balanceAfter = balanceAfter,
        categoryId = CategoryId.UNCATEGORIZED,
        categorizedBy = CategorizedBy.NONE,
        transferKind = TransferKind.NONE,
        fileOrder = order,
        countsInCashFlow = account.type.countsInCashFlow,
        movement = movement,
    )

    private fun batch(id: ImportId, file: String, account: AccountId?, source: ImportSource, day: LocalDate, rows: Int) = ImportBatch(
        id = id,
        source = source,
        fileName = file,
        accountId = account,
        importedAt = day.atStartOfDayIn(TimeZone.UTC),
        rowCount = rows,
        newCount = rows,
        duplicateCount = 0,
        warnings = emptyList(),
    )

    private fun Period.day(day: Int): LocalDate = LocalDate(year, month, day.coerceIn(1, last.day))
}

