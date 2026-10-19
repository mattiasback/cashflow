package app.cashflow.feature.insights.ui

import app.cashflow.core.domain.cashflow.FlowData
import app.cashflow.core.domain.model.Category
import app.cashflow.core.domain.model.CategoryId
import app.cashflow.core.domain.model.Goals
import app.cashflow.core.domain.model.Money
import app.cashflow.core.domain.model.Period
import app.cashflow.core.domain.model.Transaction
import app.cashflow.core.ui.UiText
import app.cashflow.core.ui.categoryName
import app.cashflow.core.ui.format.AppLocale
import app.cashflow.core.ui.format.MoneyFormatter
import app.cashflow.core.ui.format.PercentFormatter
import app.cashflow.core.ui.format.PeriodFormatter
import app.cashflow.feature.insights.domain.InsightFacts
import app.cashflow.feature.insights.domain.Opportunity
import app.cashflow.feature.insights.domain.OpportunityEngine
import app.cashflow.feature.insights.domain.BufferMath
import app.cashflow.feature.insights.domain.WhatIfCalculator
import kotlin.math.roundToInt
import kotlin.math.roundToLong

internal class InsightsInput(
    val data: FlowData,
    val transactions: List<Transaction>,
    val categories: Map<CategoryId, Category>,
    val goals: Goals,
    val bufferNow: Money?,
    val done: Set<String>,
    val percentDecimals: Boolean,
)

/** The household's figures behind the screen, kept for the actions (marking, sheets). */
internal class InsightsModel(
    val facts: InsightFacts?,
    val opportunities: List<Opportunity>,
)

/**
 * Insikter: suggestions sorted by kr per year, the what-if and the benchmarks, all
 * computed from the household's own rows. Pure and unit-tested.
 */
internal class InsightsBuilder(private val locale: AppLocale = AppLocale.current()) {

    private fun res(id: Int, vararg args: Any): UiText = UiText.StringResource(id, arrayOf(*args))
    private fun plural(id: Int, count: Int, vararg args: Any): UiText = UiText.PluralResource(id, count, arrayOf(*args))
    private fun kr(m: Money, signed: Boolean = false) = MoneyFormatter.format(m, signed = signed, locale = locale)
    private var decimals = 0
    private fun pct(v: Double) = PercentFormatter.format(v, decimals = decimals, locale = locale)
    private fun one(v: Double) = PercentFormatter.format(v, decimals = 1, locale = locale).replace("%", "").trim()
    private fun month(p: Period) = PeriodFormatter.month(p, locale)

    /** Rounded so estimates do not look more exact than they are. */
    private fun round(m: Money, to: Long) = Money((m.ore.toDouble() / (to * 100)).roundToLong() * to * 100)

    fun model(input: InsightsInput): InsightsModel {
        if (input.data.periods.size < OpportunityEngine.MIN_MONTHS) return InsightsModel(null, emptyList())
        val kindOf = { id: CategoryId -> input.categories[id]?.kind ?: app.cashflow.core.domain.model.CategoryKind.UNCATEGORIZED }
        val facts = InsightFacts.of(input.data, input.goals, input.bufferNow)
        return InsightsModel(
            facts = facts,
            opportunities = OpportunityEngine.find(input.data, input.transactions, kindOf),
        )
    }

    fun build(input: InsightsInput, model: InsightsModel, extraKronor: Int, sheet: InsightSheet?, copied: Boolean): InsightsState {
        decimals = if (input.percentDecimals) 1 else 0
        val data = input.data
        if (data.isEmpty) return InsightsState(isLoading = false, hasData = false)
        val facts = model.facts ?: return InsightsState(isLoading = false, hasData = true, enoughData = false)
        val last = data.periods.last()

        val open = model.opportunities.filter { it.id !in input.done }
        val gainLeft = Money(open.sumOf { it.perYear.ore })
        val doneCount = model.opportunities.size - open.size
        return InsightsState(
            isLoading = false,
            hasData = true,
            enoughData = true,
            gain = if (gainLeft.isPositive) "≈ " + kr(round(gainLeft, 100), signed = true) else kr(Money.ZERO),
            gainDelta = when {
                model.opportunities.isEmpty() -> res(R.string.gain_none)
                doneCount == 0 -> plural(R.plurals.gain_left, open.size, open.size)
                else -> res(R.string.gain_left_done, plural(R.plurals.gain_left, open.size, open.size), doneCount)
            },
            opportunities = model.opportunities.map { opportunity(it, it.id in input.done, input) },
            whatIf = whatIf(facts, extraKronor, last),
            benchmarks = benchmarks(facts),
            prompt = prompt(facts),
            copied = copied,
            sheet = sheet?.let { sheet(it, input, model, facts, last) },
        )
    }

    // ───────────────────────────── Spara utan att märka det ─────────────────────────────

    private fun opportunity(o: Opportunity, done: Boolean, input: InsightsInput): OpportunityUi {
        val (icon, title, sub, effort) = when (o) {
            is Opportunity.PriceRises -> Quad(
                OpportunityIcon.Subscriptions, res(R.string.opp_rises_title),
                res(R.string.opp_rises_sub, plural(R.plurals.price_rises, o.rises.size, o.rises.size), plural(R.plurals.subscriptions, o.subscriptions, o.subscriptions)),
                res(R.string.effort_10_min),
            )
            is Opportunity.SmallCharges -> Quad(
                OpportunityIcon.SmallCharges, res(R.string.opp_small_title),
                plural(R.plurals.opp_small_sub, o.charges.size, o.charges.size, kr(Money(OpportunityEngine.SMALL_CHARGE_ORE))),
                res(R.string.effort_5_min),
            )
            is Opportunity.OverAverage -> Quad(
                OpportunityIcon.Category, res(R.string.opp_over_title, name(o.categoryId, input)),
                res(R.string.opp_over_sub, month(o.period), pct(o.percentOver)),
                res(R.string.effort_habit),
            )
        }
        val gain = round(o.perYear, 10)
        return OpportunityUi(
            id = o.id,
            icon = icon,
            title = title,
            sub = if (done) res(R.string.opp_done_sub) else sub,
            gain = (if (done) "" else "+") + kr(gain) + PER_YEAR.getValue(locale),
            effort = if (done) res(R.string.effort_fixed) else effort,
            done = done,
            spoken = res(if (done) R.string.cd_opp_done else R.string.cd_opp, title, kr(gain), effort),
        )
    }

    private fun name(id: CategoryId, input: InsightsInput) = categoryName(id, input.categories[id]?.name ?: id.value)

    // ───────────────────────────── Tänk om ─────────────────────────────

    private fun whatIf(f: InsightFacts, extraKronor: Int, last: Period): WhatIfUi {
        val extra = Money.kr(extraKronor.toLong())
        val w = WhatIfCalculator.calculate(f, extra)
        val hasBalance = f.bufferNow != null
        fun full(months: Int?): UiText = when (months) {
            null -> res(R.string.sim_buffer_never)
            0 -> res(R.string.sim_buffer_full_now)
            else -> UiText.DynamicString(month(last.plus(months)))
        }
        val fundsGain = w.fundsIn10Years - w.fundsIn10YearsNow
        return WhatIfUi(
            extraKronor = extraKronor,
            extraText = kr(extra),
            bufferFull = if (hasBalance) full(w.bufferFullIn) else null,
            bufferFullSub = when {
                !hasBalance -> null
                extraKronor > 0 && w.bufferFullIn != w.bufferFullInNow -> res(R.string.sim_instead_of, full(w.bufferFullInNow))
                else -> res(R.string.sim_current_pace)
            },
            rate = pct(w.savingsRate),
            rateNow = res(R.string.sim_rate_now, pct(f.savingsRate)),
            funds = "≈ " + kr(round(w.fundsIn10Years, 1000)),
            fundsSub = if (extraKronor > 0) res(R.string.sim_funds_more, kr(round(fundsGain, 1000), signed = true)) else res(R.string.sim_funds_now, kr(f.fundsPerMonth)),
            note = if (hasBalance) res(R.string.sim_note, f.bufferGoalMonths.roundToInt()) else res(R.string.sim_note_no_balance),
            spoken = res(R.string.cd_sim, kr(extra)),
        )
    }

    // ───────────────────────────── Så står ni dig ─────────────────────────────

    private fun benchmarks(f: InsightFacts): List<BenchmarkUi> = listOfNotNull(
        BenchmarkUi(
            res(R.string.bench_rate), pct(f.savingsRate), (f.savingsRate / 40).toFloat().coerceIn(0f, 1f), BenchTone.Savings,
            res(R.string.bench_rate_note, pct(f.savingsRateGoal)),
        ),
        f.bufferMonths?.let { months ->
            BenchmarkUi(
                res(R.string.bench_buffer), one(months) + MONTHS_UNIT.getValue(locale), (months / f.bufferGoalMonths).toFloat().coerceIn(0f, 1f), BenchTone.Savings,
                res(R.string.bench_buffer_note, f.bufferGoalMonths.roundToInt()),
            )
        },
        BenchmarkUi(
            res(R.string.bench_fixed), pct(f.fixedShare), (f.fixedShare / 100).toFloat().coerceIn(0f, 1f), BenchTone.Fixed,
            res(R.string.bench_fixed_note),
        ),
    )

    // ───────────────────────────── Fråga en AI ─────────────────────────────

    /** Only rounded household figures: no names, account numbers or payees. */
    private fun prompt(f: InsightFacts): UiText {
        fun r(m: Money) = kr(round(m, 100))
        val goal = f.bufferGoalMonths.roundToInt()
        val buffer = f.bufferNow?.let { res(R.string.prompt_buffer, r(it), r(f.bufferTarget), goal) }
            ?: res(R.string.prompt_buffer_target, r(f.bufferTarget), goal)
        val loans = if (f.loansPerMonth.isPositive) res(R.string.prompt_loans, r(f.loansPerMonth)) else UiText.DynamicString("")
        return res(
            R.string.prompt,
            minOf(f.months, 12),
            r(f.incomePerMonth), r(f.incomePerMonth - f.savingsPerMonth - f.leftPerMonth), r(f.fixedPerMonth),
            r(f.savingsPerMonth), pct(f.savingsRate), r(f.leftPerMonth.coerceAtLeastZero()), buffer, loans,
        )
    }

    // ───────────────────────────── Sheets ─────────────────────────────

    private fun sheet(sheet: InsightSheet, input: InsightsInput, model: InsightsModel, f: InsightFacts, last: Period): InsightSheetUi? = when (sheet) {
        InsightSheet.About -> {
            val open = model.opportunities.filter { it.id !in input.done }
            InsightSheetUi(
                eyebrow = res(R.string.about_eyebrow), title = res(R.string.hero_label),
                figure = kr(round(Money(open.sumOf { it.perYear.ore }), 100)),
                description = null,
                why = res(R.string.about_why), assume = res(R.string.about_assume), how = res(R.string.about_how),
                done = null,
            )
        }
        is InsightSheet.Suggestion -> model.opportunities.firstOrNull { it.id == sheet.id }?.let { o ->
            val ui = opportunity(o, o.id in input.done, input)
            InsightSheetUi(
                eyebrow = res(R.string.opp_eyebrow, ui.effort), title = ui.title,
                figure = "+" + kr(round(o.perYear, 10)) + PER_YEAR_LONG.getValue(locale),
                description = ui.sub,
                why = why(o, input), assume = assume(o), how = how(o),
                done = res(if (o.id in input.done) R.string.undo_done else R.string.i_fixed_it),
            )
        }
    }

    private fun why(o: Opportunity, input: InsightsInput): UiText = when (o) {
        is Opportunity.PriceRises -> res(
            R.string.opp_rises_why,
            UiText.DynamicString(
                o.rises.joinToString("\n") { r ->
                    "${r.payee}: ${kr(r.previous ?: r.monthly)} → ${kr(r.monthly)}" + (r.risenOn?.let { " (" + PeriodFormatter.iso(it, locale) + ")" } ?: "")
                },
            ),
            kr(o.perYear),
        )
        is Opportunity.SmallCharges -> res(
            R.string.opp_small_why,
            UiText.DynamicString(o.charges.joinToString("\n") { "${it.payee}: ${kr(it.monthly)}" }),
            kr(o.perMonth), kr(o.perYear),
        )
        is Opportunity.OverAverage -> res(
            R.string.opp_over_why,
            name(o.categoryId, input), kr(o.now), kr(o.average), kr(round(o.perYear, 10)), month(o.period),
        )
    }

    private fun assume(o: Opportunity): UiText = when (o) {
        is Opportunity.PriceRises -> res(R.string.opp_rises_assume)
        is Opportunity.SmallCharges -> res(R.string.opp_small_assume)
        is Opportunity.OverAverage -> plural(R.plurals.opp_over_assume, o.averageMonths, o.averageMonths)
    }

    private fun how(o: Opportunity): UiText = when (o) {
        is Opportunity.PriceRises -> res(R.string.opp_rises_how)
        is Opportunity.SmallCharges -> res(R.string.opp_small_how)
        is Opportunity.OverAverage -> res(R.string.opp_over_how)
    }

    private data class Quad(val icon: OpportunityIcon, val title: UiText, val sub: UiText, val effort: UiText)

    private companion object {
        // No-break space so the unit never wraps away from the number.
        val PER_YEAR = mapOf(AppLocale.SV to "/år", AppLocale.EN to "/yr")
        val PER_YEAR_LONG = mapOf(AppLocale.SV to " per år", AppLocale.EN to " a year")
        val MONTHS_UNIT = mapOf(AppLocale.SV to " mån", AppLocale.EN to " mo")
    }
}

private fun Money.coerceAtLeastZero() = if (ore < 0) Money.ZERO else this

private fun Period.plus(months: Int): Period {
    val index = year * 12 + (month - 1) + months
    return Period(index / 12, index % 12 + 1)
}
