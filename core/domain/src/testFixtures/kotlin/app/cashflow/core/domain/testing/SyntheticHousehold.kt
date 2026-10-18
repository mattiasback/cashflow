package app.cashflow.core.domain.testing

import app.cashflow.core.domain.model.CategorizedBy
import app.cashflow.core.domain.model.Category
import app.cashflow.core.domain.model.CategoryId
import app.cashflow.core.domain.model.CategoryKind
import app.cashflow.core.domain.model.Period
import app.cashflow.core.domain.model.Transaction
import app.cashflow.core.domain.testing.Fixtures.tx
import kotlin.random.Random

/**
 * A made-up household for previews, screenshot tests and performance runs. Every payee and
 * amount is invented; nothing here comes from a real statement.
 */
object SyntheticHousehold {

    val categories = listOf(
        Category(CategoryId("lon"), "Lön", CategoryKind.INCOME),
        Category(CategoryId("barnbidrag"), "Barnbidrag", CategoryKind.INCOME),
        Category(CategoryId("boende"), "Boende", CategoryKind.FIXED),
        Category(CategoryId("transport"), "Transport", CategoryKind.FIXED),
        Category(CategoryId("abonnemang"), "Abonnemang", CategoryKind.FIXED),
        Category(CategoryId("forsakringar"), "Försäkringar", CategoryKind.FIXED),
        Category(CategoryId("mat"), "Mat", CategoryKind.VARIABLE),
        Category(CategoryId("restaurang"), "Restaurang", CategoryKind.VARIABLE),
        Category(CategoryId("shopping"), "Shopping", CategoryKind.VARIABLE),
        Category(CategoryId("noje"), "Nöje", CategoryKind.VARIABLE),
        Category(CategoryId("fonder"), "Fonder", CategoryKind.SAVINGS),
        Category(CategoryId("buffert"), "Buffert", CategoryKind.SAVINGS),
        Category(CategoryId("pension"), "Pension", CategoryKind.SAVINGS),
        Category(CategoryId.UNCATEGORIZED, "Ej kategoriserat", CategoryKind.UNCATEGORIZED),
    )

    /** [months] months of rows ending with [last], about 40 rows a month. */
    fun transactions(last: Period = Period(2026, 10), months: Int = 12, seed: Int = 7): List<Transaction> {
        val random = Random(seed)
        var p = last
        repeat(months - 1) { p = p.previous() }
        val out = ArrayList<Transaction>()
        var n = 0
        fun add(day: Int, kronor: Double, text: String, category: String) {
            val d = day.coerceIn(1, 28).toString().padStart(2, '0')
            out += tx(
                id = "s${n++}",
                date = "$p-$d",
                kronor = kronor,
                text = text,
                category = CategoryId(category),
                categorizedBy = if (category == CategoryId.UNCATEGORIZED.value) CategorizedBy.NONE else CategorizedBy.BUILT_IN_RULE,
            )
        }
        repeat(months) {
            fun vary(base: Double, spread: Double) = base + random.nextDouble(-spread, spread)
            add(25, vary(34_000.0, 600.0), "Exempelbolaget AB", "lon")
            add(25, vary(29_500.0, 500.0), "Testföretaget AB", "lon")
            add(20, 1_250.0, "Försäkringskassan", "barnbidrag")
            add(1, -12_400.0, "Bostadsrättsföreningen Exempel", "boende")
            add(28, -vary(4_800.0, 300.0), "Banken bolån", "boende")
            add(3, -930.0, "Lokaltrafiken", "transport")
            add(12, -vary(700.0, 300.0), "Bensinstationen", "transport")
            add(5, -499.0, "Streamingtjänsten", "abonnemang")
            add(6, -349.0, "Mobiloperatören", "abonnemang")
            add(15, -612.0, "Försäkringsbolaget", "forsakringar")
            repeat(random.nextInt(8, 13)) { add(random.nextInt(1, 29), -vary(650.0, 450.0), listOf("Matbutiken", "Livsmedelshallen", "Närbutiken")[random.nextInt(3)], "mat") }
            repeat(random.nextInt(2, 6)) { add(random.nextInt(1, 29), -vary(420.0, 250.0), "Restaurang Exempel", "restaurang") }
            repeat(random.nextInt(1, 5)) { add(random.nextInt(1, 29), -vary(900.0, 700.0), "Varuhuset", "shopping") }
            repeat(random.nextInt(0, 3)) { add(random.nextInt(1, 29), -vary(350.0, 200.0), "Biografen", "noje") }
            repeat(random.nextInt(1, 4)) { add(random.nextInt(1, 29), -vary(300.0, 250.0), "Okänd betalning", CategoryId.UNCATEGORIZED.value) }
            add(26, -5_000.0, "Överföring fonder", "fonder")
            add(26, -2_000.0, "Överföring buffert", "buffert")
            if (random.nextInt(4) == 0) add(18, -vary(14_000.0, 5_000.0), "Resebyrån", "noje")
            p = p.next()
        }
        return out
    }
}
