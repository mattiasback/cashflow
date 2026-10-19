package app.cashflow.feature.insights.domain

import app.cashflow.core.domain.model.Money
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNull
import org.junit.jupiter.api.Test

/** All figures are fictional. */
class PlanTest {

    private val facts = InsightFacts(
        months = 12,
        incomePerMonth = Money.kr(60_000),
        fixedPerMonth = Money.kr(20_000),
        savingsPerMonth = Money.kr(9_000),
        fundsPerMonth = Money.kr(5_000),
        bufferPerMonth = Money.kr(2_000),
        leftPerMonth = Money.kr(6_000),
        loansPerMonth = Money.kr(2_500),
        bufferGoalMonths = 6.0,
        bufferTarget = Money.kr(120_000),
        bufferNow = Money.kr(100_000),
        savingsRateGoal = 10.0,
    )

    @Test
    fun `the first open step is now, the rest are next, and marked steps are done`() {
        val steps = PlanEvaluator.evaluate(facts, marked = setOf(PlanStepId.SURPLUS.key))
        assertThat(steps.map { it.state }).containsExactly(StepState.NOW, StepState.DONE, StepState.NEXT, StepState.NEXT)
    }

    @Test
    fun `data closes the steps it can see, a full buffer, a small surplus and no other loans`() {
        val steps = PlanEvaluator.evaluate(facts.copy(bufferNow = Money.kr(130_000), leftPerMonth = Money.kr(500), loansPerMonth = Money.ZERO), emptySet())
        assertThat(steps.map { it.state }).containsExactly(StepState.DONE, StepState.DONE, StepState.DONE, StepState.NOW)
        assertThat(steps.map { it.doneByData }).containsExactly(true, true, true, false)
    }

    @Test
    fun `the buffer fills in 10 months at 2 000 kr, and in 5 with 2 000 kr extra`() {
        val w = WhatIfCalculator.calculate(facts, Money.kr(2_000))
        assertThat(w.bufferFullInNow).isEqualTo(10)
        assertThat(w.bufferFullIn).isEqualTo(5)
        // (9 000 + 2 000) of 60 000
        assertThat(w.savingsRate).isEqualTo(11_000.0 / 60_000 * 100)
        assertThat(w.fundsIn10Years.ore).isGreaterThan(w.fundsIn10YearsNow.ore)
    }

    @Test
    fun `5 000 kr a month for 10 years at 6 percent is about 810 000 kr`() {
        val w = WhatIfCalculator.calculate(facts, Money.ZERO)
        assertThat(w.fundsIn10YearsNow.kronor / 10_000).isEqualTo(81L)
    }

    @Test
    fun `without an entered balance the buffer date is unknown`() {
        val w = WhatIfCalculator.calculate(facts.copy(bufferNow = null), Money.kr(1_000))
        assertThat(w.bufferFullIn).isNull()
        assertThat(PlanEvaluator.monthsToFull(Money.kr(1), Money.ZERO)).isNull()
    }
}
