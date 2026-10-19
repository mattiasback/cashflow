package app.cashflow.feature.flow.ui

import app.cashflow.core.domain.cashflow.FlowData
import app.cashflow.core.domain.model.Category
import app.cashflow.core.domain.model.CategoryId
import app.cashflow.core.domain.model.CategoryKind
import app.cashflow.core.domain.model.MortgageSplit
import app.cashflow.core.domain.testing.Fixtures.tx
import app.cashflow.core.ui.format.AppLocale
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import org.junit.jupiter.api.Test

/** All figures are fictional. */
class FlowBuilderTest {

    private val categories = listOf(
        Category(CategoryId("lon"), "Lön", CategoryKind.INCOME),
        Category(CategoryId("boende"), "Boende", CategoryKind.FIXED),
        Category(CategoryId("mat"), "Mat", CategoryKind.VARIABLE),
    ).associateBy { it.id }

    @Test
    fun `sankey never shows a negative amount when refunds outweigh a group`() {
        // Boende gets a refund larger than any fixed spending this month: Fasta is −500 kr.
        val rows = listOf(
            tx("1", date = "2026-05-25", kronor = 20000.0, category = CategoryId("lon")),
            tx("2", date = "2026-05-10", kronor = 500.0, category = CategoryId("boende")),
            tx("3", date = "2026-05-12", kronor = -300.0, category = CategoryId("mat")),
        )
        val input = FlowInput(FlowData.of(rows, MortgageSplit(), { categories[it]?.kind ?: CategoryKind.UNCATEGORIZED }), rows, categories, null, false)
        val sankey = FlowBuilder(AppLocale.SV).build(input, FlowSelection()).sankey!!

        assertThat(sankey.nodes.filter { it.amount.startsWith("−") }).isEmpty()
        // Fasta drops out; Rörliga and the kept nodes show what is drawn through them.
        assertThat(sankey.nodes.map { it.id }).containsExactlyInAnyOrder(FlowBuilder.INCOME, FlowBuilder.EXPENSES, FlowBuilder.LEFT, FlowBuilder.VARIABLE)
    }
}
