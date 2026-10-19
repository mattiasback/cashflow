package app.cashflow.feature.insights.ui

import app.cashflow.core.domain.cashflow.FlowData
import app.cashflow.core.domain.model.CategoryKind
import app.cashflow.core.domain.model.Goals
import app.cashflow.core.domain.model.Money
import app.cashflow.core.domain.model.MortgageSplit
import app.cashflow.core.domain.model.Transaction
import app.cashflow.core.domain.testing.SyntheticHousehold
import app.cashflow.core.ui.format.AppLocale
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test

/** All figures are fictional (the synthetic household). */
class InsightsBuilderTest {

    private fun input(txs: List<Transaction>, bufferNow: Money? = null): InsightsInput {
        val byId = SyntheticHousehold.categories.associateBy { it.id }
        return InsightsInput(
            data = FlowData.of(txs, MortgageSplit(), { byId[it]?.kind ?: CategoryKind.UNCATEGORIZED }),
            transactions = txs, categories = byId, goals = Goals(), bufferNow = bufferNow, done = emptySet(), percentDecimals = false,
        )
    }

    private fun build(input: InsightsInput): InsightsState {
        val b = InsightsBuilder(AppLocale.SV)
        return b.build(input, b.model(input), extraKronor = 2_000, sheet = null, copied = false)
    }

    @Test
    fun `less than three months shows no suggestions`() {
        val state = build(input(SyntheticHousehold.transactions(months = 2)))
        assertThat(state.hasData).isTrue()
        assertThat(state.enoughData).isFalse()
        assertThat(state.opportunities).isEmpty()
    }

    @Test
    fun `without a balance Tänk om hides the buffer date, with one it shows it`() {
        val txs = SyntheticHousehold.transactions(months = 12)
        assertThat(build(input(txs)).whatIf?.bufferFull).isNull()
        assertThat(build(input(txs, bufferNow = Money.kr(10_000))).whatIf?.bufferFull).isNotNull()
        assertThat(build(input(txs)).prompt).isNotNull()
    }
}
