package app.cashflow.feature.insights.ui

import app.cashflow.core.domain.cashflow.FlowData
import app.cashflow.core.domain.model.CategoryKind
import app.cashflow.core.domain.model.Goals
import app.cashflow.core.domain.model.Money
import app.cashflow.core.domain.model.MortgageSplit
import app.cashflow.core.domain.model.Transaction
import app.cashflow.core.domain.testing.SyntheticHousehold
import app.cashflow.core.ui.format.AppLocale
import app.cashflow.feature.insights.domain.PlanStepId
import app.cashflow.feature.insights.domain.StepState
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test

/** All figures are fictional (the synthetic household). */
class InsightsBuilderTest {

    private fun input(txs: List<Transaction>, bufferNow: Money? = null, done: Set<String> = emptySet()): InsightsInput {
        val byId = SyntheticHousehold.categories.associateBy { it.id }
        return InsightsInput(
            data = FlowData.of(txs, MortgageSplit(), { byId[it]?.kind ?: CategoryKind.UNCATEGORIZED }),
            transactions = txs, categories = byId, goals = Goals(), bufferNow = bufferNow, done = done, percentDecimals = false,
        )
    }

    private fun build(input: InsightsInput): InsightsState {
        val b = InsightsBuilder(AppLocale.SV)
        return b.build(input, b.model(input), extraKronor = 2_000, sheet = null, copied = false)
    }

    @Test
    fun `less than three months shows no plan or suggestions`() {
        val state = build(input(SyntheticHousehold.transactions(months = 2)))
        assertThat(state.hasData).isTrue()
        assertThat(state.enoughData).isFalse()
        assertThat(state.steps.size).isEqualTo(0)
    }

    @Test
    fun `without a balance the buffer step has no bar and Tänk om hides the buffer date`() {
        val state = build(input(SyntheticHousehold.transactions(months = 12)))
        val buffer = state.steps.first { it.id == PlanStepId.BUFFER }
        assertThat(buffer.bar).isNull()
        assertThat(state.whatIf?.bufferFull).isNull()
        assertThat(state.prompt).isNotNull()
    }

    @Test
    fun `with a balance the buffer step shows progress, and a marked step is done`() {
        val txs = SyntheticHousehold.transactions(months = 12)
        val state = build(input(txs, bufferNow = Money.kr(10_000), done = setOf(PlanStepId.LONG_TERM.key)))
        assertThat(state.steps.first { it.id == PlanStepId.BUFFER }.bar).isNotNull()
        assertThat(state.whatIf?.bufferFull).isNotNull()
        assertThat(state.steps.first { it.id == PlanStepId.LONG_TERM }.state).isEqualTo(StepState.DONE)
    }
}
