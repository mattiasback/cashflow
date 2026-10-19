package app.cashflow.feature.insights.domain

import app.cashflow.core.domain.cashflow.FlowData
import app.cashflow.core.domain.model.CategorizedBy
import app.cashflow.core.domain.model.CategoryId
import app.cashflow.core.domain.model.CategoryKind
import app.cashflow.core.domain.model.Money
import app.cashflow.core.domain.model.MortgageSplit
import app.cashflow.core.domain.model.Transaction
import app.cashflow.core.domain.testing.Fixtures.tx
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.single
import org.junit.jupiter.api.Test

/** All payees and figures are fictional. */
class OpportunityEngineTest {

    private val kinds = mapOf(
        "abonnemang" to CategoryKind.FIXED, "boende" to CategoryKind.FIXED, "restaurang" to CategoryKind.VARIABLE,
        "mat" to CategoryKind.VARIABLE, "noje" to CategoryKind.VARIABLE, "lon" to CategoryKind.INCOME,
    )
    private val kindOf: (CategoryId) -> CategoryKind = { kinds[it.value] ?: CategoryKind.UNCATEGORIZED }
    private val months = listOf("2026-05", "2026-06", "2026-07", "2026-08", "2026-09", "2026-10")
    private var n = 0

    private fun row(month: String, day: Int, kr: Double, text: String, category: String) =
        tx("t${n++}", date = "$month-${day.toString().padStart(2, '0')}", kronor = kr, text = text, category = CategoryId(category), categorizedBy = CategorizedBy.BUILT_IN_RULE)

    private fun salary() = months.map { row(it, 25, 30000.0, "LON EXEMPEL AB", "lon") }

    private fun find(rows: List<Transaction>) = OpportunityEngine.find(FlowData.of(rows, MortgageSplit(), kindOf), rows, kindOf)

    @Test
    fun `a subscription that went from 149 to 179 kr is worth 360 kr a year`() {
        val stream = months.mapIndexed { i, m -> row(m, 9, if (i < 4) -149.0 else -179.0, "STREAMA EXEMPEL", "abonnemang") }
        val rise = find(salary() + stream).filterIsInstance<Opportunity.PriceRises>().single()
        assertThat(rise.perYear).isEqualTo(Money.kr(360))
        assertThat(rise.rises.single().previous).isEqualTo(Money.kr(149))
    }

    @Test
    fun `three small monthly charges add up per year, and bills that vary are not recurring`() {
        val small = months.flatMap { m ->
            listOf(row(m, 3, -49.0, "APP ETT", "abonnemang"), row(m, 4, -79.0, "APP TVA", "noje"), row(m, 5, -59.0, "MOLN TRE", "abonnemang"))
        }
        val power = months.mapIndexed { i, m -> row(m, 28, -(400.0 + i * 300), "ELBOLAG EXEMPEL", "boende") }
        val found = RecurringDetector.find(salary() + small + power, kindOf)
        assertThat(found.map { it.key }).containsExactly("APP TVA", "MOLN TRE", "APP ETT")
        val opp = find(salary() + small + power).filterIsInstance<Opportunity.SmallCharges>().single()
        // (49 + 79 + 59) × 12
        assertThat(opp.perYear).isEqualTo(Money.kr(2244))
    }

    @Test
    fun `restaurant 2 100 kr against an average of 1 830 kr saves 3 240 kr a year`() {
        val spend = listOf(1800.0, 1900.0, 1750.0, 1850.0, 1850.0, 2100.0)
        val rows = salary() + months.zip(spend).map { (m, kr) -> row(m, 15, -kr, "KROG EXEMPEL", "restaurang") }
        val over = find(rows).filterIsInstance<Opportunity.OverAverage>().single()
        assertThat(over.average).isEqualTo(Money.kr(1830))
        assertThat(over.perYear).isEqualTo(Money.kr(3240))
    }

    @Test
    fun `a one-off spike is not a pace to keep`() {
        val spend = listOf(800.0, 900.0, 700.0, 800.0, 600.0, 1900.0)
        val rows = salary() + months.zip(spend).map { (m, kr) -> row(m, 15, -kr, "TANDLAKARE EXEMPEL", "noje") }
        assertThat(find(rows).filterIsInstance<Opportunity.OverAverage>()).isEmpty()
    }

    @Test
    fun `the list is sorted by kr per year`() {
        val stream = months.mapIndexed { i, m -> row(m, 9, if (i < 4) -149.0 else -179.0, "STREAMA EXEMPEL", "abonnemang") }
        val spend = listOf(1800.0, 1900.0, 1750.0, 1850.0, 1850.0, 2100.0)
        val food = months.zip(spend).map { (m, kr) -> row(m, 15, -kr, "KROG EXEMPEL", "restaurang") }
        val all = find(salary() + stream + food)
        assertThat(all.map { it.perYear }).containsExactly(Money.kr(3240), Money.kr(360))
        assertThat(all.first()).isInstanceOf(Opportunity.OverAverage::class)
    }

    @Test
    fun `nothing is suggested on less than three months of data`() {
        val two = months.takeLast(2)
        val rows = two.map { row(it, 25, 30000.0, "LON EXEMPEL AB", "lon") } +
            two.mapIndexed { i, m -> row(m, 15, if (i == 0) -500.0 else -3000.0, "KROG EXEMPEL", "restaurang") }
        assertThat(find(rows)).isEmpty()
    }

    @Test
    fun `charges seen twice in a month or long ago are not subscriptions`() {
        val twice = months.flatMap { m -> listOf(row(m, 2, -99.0, "KAFE EXEMPEL", "noje"), row(m, 20, -99.0, "KAFE EXEMPEL", "noje")) }
        val stopped = months.take(3).map { row(it, 9, -129.0, "GYM EXEMPEL", "abonnemang") }
        assertThat(RecurringDetector.find(salary() + twice + stopped, kindOf)).isEmpty()
        assertThat(OpportunityEngine.priceRises(emptyList())).isNull()
    }
}
