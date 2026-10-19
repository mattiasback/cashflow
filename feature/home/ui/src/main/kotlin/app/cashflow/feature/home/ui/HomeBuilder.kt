package app.cashflow.feature.home.ui

import app.cashflow.core.designsystem.component.TrendDirection
import app.cashflow.core.domain.cashflow.Buffer
import app.cashflow.core.domain.cashflow.CashFlowSummary
import app.cashflow.core.domain.cashflow.FlowData
import app.cashflow.core.domain.model.Account
import app.cashflow.core.domain.model.AccountType
import app.cashflow.core.domain.model.Category
import app.cashflow.core.domain.model.CategoryId
import app.cashflow.core.domain.model.CategoryKind
import app.cashflow.core.domain.model.Goals
import app.cashflow.core.domain.model.ImportBatch
import app.cashflow.core.domain.model.Money
import app.cashflow.core.domain.model.Period
import app.cashflow.core.domain.model.Transaction
import app.cashflow.core.domain.model.TransactionStatus
import app.cashflow.core.ui.DetailSheetUi
import app.cashflow.core.ui.FlowTone
import app.cashflow.core.ui.SheetRowUi
import app.cashflow.core.ui.UiText
import app.cashflow.core.ui.categoryName
import app.cashflow.core.ui.format.AppLocale
import app.cashflow.core.ui.format.MoneyFormatter
import app.cashflow.core.ui.format.PercentFormatter
import app.cashflow.core.ui.format.PeriodFormatter
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.roundToInt

internal class HomeInput(
    val data: FlowData,
    val transactions: List<Transaction>,
    val categories: Map<CategoryId, Category>,
    val accounts: List<Account>,
    val buffer: Money?,
    val goals: Goals,
    val percentDecimals: Boolean,
    val amortizationAsSavings: Boolean,
    val batches: List<ImportBatch>,
    val today: LocalDate,
)

/**
 * Hem is a thin view over the same month sums as Flöde: always the latest month with data, its
 * four key figures, 12-month trends and what needs doing. Pure and unit-tested.
 */
internal class HomeBuilder(private val locale: AppLocale = AppLocale.current()) {

    private fun res(id: Int, vararg args: Any): UiText = UiText.StringResource(id, arrayOf(*args))
    private fun plural(id: Int, count: Int, vararg args: Any): UiText = UiText.PluralResource(id, count, arrayOf(*args))
    private fun text(s: String): UiText = UiText.DynamicString(s)
    private fun kr(m: Money, signed: Boolean = false) = MoneyFormatter.format(m, signed = signed, locale = locale)
    private fun kr(ore: Long, signed: Boolean = false) = kr(Money(ore), signed)
    private var decimals = 0
    private fun pct(v: Double, signed: Boolean = false) = PercentFormatter.format(v, decimals = decimals, signed = signed, locale = locale)
    private fun share(part: Long, whole: Long) = PercentFormatter.share(part, whole, locale)
    private fun month(p: Period) = PeriodFormatter.month(p, locale)
    private fun one(v: Double) = PercentFormatter.format(v, decimals = 1, locale = locale).replace("%", "").trim()

    fun build(input: HomeInput, sheet: HomeSheet?): HomeState {
        decimals = if (input.percentDecimals) 1 else 0
        val todo = todo(input)
        val footer = footer(input)
        val data = input.data
        if (data.isEmpty) return HomeState(todo = todo, footer = footer, hasData = false, isLoading = false)
        val m = Metrics(input)
        return HomeState(
            summaryLabel = res(R.string.home_summary_label, month(m.last)),
            summaryValue = kr(m.a.net, signed = true),
            summarySpoken = MoneyFormatter.spoken(m.a.net, locale),
            delta = m.pa?.let { res(R.string.home_delta_vs, kr(m.a.net - it.net, signed = true), month(m.prev!!)) }
                ?: res(R.string.home_delta_first),
            deltaPositive = m.pa == null || m.a.net >= m.pa.net,
            kpis = kpis(m),
            trends = trends(input, m),
            todo = todo,
            footer = footer,
            sheet = sheet?.let { sheet(input, m, it) },
            hasData = true,
            isLoading = false,
        )
    }

    /** The latest month, the month before and the 12 months up to it. */
    private inner class Metrics(input: HomeInput) {
        val data = input.data
        val last: Period = data.periods.last()
        val prev: Period? = data.periods.getOrNull(data.periods.lastIndex - 1)
        val a: CashFlowSummary = data.month(last)
        val pa: CashFlowSummary? = prev?.let(data::month)
        val months12 = data.twelveMonthsTo(last)
        val a12 = data.sum(months12)
        val n12 = months12.size.coerceAtLeast(1)
        val rate = a.savingsRatePercent ?: 0.0
        val prevRate = pa?.savingsRatePercent
        val rateGoal = input.goals.savingsRatePercent
        val bufferGoal = input.goals.bufferMonths
        val fixedPerMonth = Money(a12.fixedExpenses.ore / n12)
        val bufferMonths = Buffer.months(input.buffer, fixedPerMonth)
        val buffer = input.buffer
    }

    // ───────────────────────────── Key figures ─────────────────────────────

    private fun direction(d: Double?, flat: Double) = when {
        d == null -> null
        abs(d) < flat -> TrendDirection.Flat
        d > 0 -> TrendDirection.Up
        else -> TrendDirection.Down
    }

    private fun change(now: Long, before: Long?): Double? =
        if (before == null || before == 0L) null else (now - before).toDouble() / abs(before) * 100

    private fun kpis(m: Metrics): List<KpiUi> {
        val vs = m.prev?.let { month(it) }
        fun spokenTrend(d: Double?, dir: TrendDirection?, good: Boolean?, amount: Any): UiText? = when {
            d == null || dir == null || vs == null -> null
            dir == TrendDirection.Flat -> res(R.string.cd_trend_flat, vs)
            else -> res(
                if (dir == TrendDirection.Up) R.string.cd_trend_up else R.string.cd_trend_down,
                amount, vs, res(if (good == true) R.string.better else R.string.worse),
            )
        }
        val dInc = change(m.a.income.ore, m.pa?.income?.ore)
        val dExp = change(m.a.expenses.ore, m.pa?.expenses?.ore)
        val dRate = m.prevRate?.let { m.rate - it }
        val incDir = direction(dInc, 0.3)
        val expDir = direction(dExp, 0.5)
        val rateDir = direction(dRate, 0.3)
        val incGood = dInc?.let { it > 0 }
        val expGood = dExp?.let { it < 0 }
        val rateGood = dRate?.let { it > 0 }
        val rateReached = m.rate >= m.rateGoal
        val bufferShare = m.bufferMonths?.let { it / m.bufferGoal }
        val bufferReached = bufferShare != null && bufferShare >= 1.0

        fun spoken(label: Int, value: String, trend: UiText?, goal: UiText?) =
            res(R.string.cd_kpi, res(label), value, trend ?: text(""), goal ?: text(""))

        val incValue = kr(m.a.income)
        val expValue = kr(m.a.expenses)
        val rateValue = pct(m.rate)
        val bufferValue = bufferShare?.let { pct(minOf(999.0, it * 100)) } ?: "–"
        return listOf(
            KpiUi(
                KpiId.INCOME, res(R.string.kpi_income), incValue,
                spoken(R.string.kpi_income, incValue, spokenTrend(dInc, incDir, incGood, dInc?.let { pct(abs(it)) }.orEmpty()), null),
                incDir, incGood,
            ),
            KpiUi(
                KpiId.EXPENSES, res(R.string.kpi_expenses), expValue,
                spoken(R.string.kpi_expenses, expValue, spokenTrend(dExp, expDir, expGood, dExp?.let { pct(abs(it)) }.orEmpty()), null),
                expDir, expGood,
            ),
            KpiUi(
                KpiId.SAVINGS_RATE, res(R.string.kpi_rate), rateValue,
                spoken(
                    R.string.kpi_rate, rateValue,
                    spokenTrend(dRate, rateDir, rateGood, res(R.string.points, one(abs(dRate ?: 0.0)))),
                    if (rateReached) res(R.string.cd_goal_reached, pct(m.rateGoal)) else res(R.string.cd_goal_progress, pct(m.rate / m.rateGoal * 100), pct(m.rateGoal)),
                ),
                rateDir, rateGood,
                goal = (m.rate / m.rateGoal).toFloat().coerceIn(0f, 1f), goalReached = rateReached,
            ),
            KpiUi(
                KpiId.BUFFER, res(R.string.kpi_buffer), bufferValue,
                spoken(
                    R.string.kpi_buffer, bufferValue, null,
                    m.bufferMonths?.let { res(R.string.cd_buffer_months, one(it), m.bufferGoal.roundToInt()) } ?: res(R.string.cd_no_buffer),
                ),
                goal = bufferShare?.toFloat()?.coerceIn(0f, 1f) ?: 0f, goalReached = bufferReached,
            ),
        )
    }

    // ───────────────────────────── Trender ─────────────────────────────

    private fun trends(input: HomeInput, m: Metrics): List<TrendUi> {
        val data = input.data
        val months = m.months12.map(data::month)
        val short = PeriodFormatter.month(m.last, locale).substringBefore(' ')
        fun row(id: TrendId, label: Int, tone: TrendTone, higher: Boolean, points: (CashFlowSummary) -> Double, fmt: (Double) -> String, unitPoints: Boolean): TrendUi {
            val values = months.map(points)
            val last = values.last()
            val avg = values.average()
            val d = last - avg
            val rel = if (unitPoints) d else if (avg != 0.0) d / abs(avg) * 100 else 0.0
            val flat = abs(rel) < if (unitPoints) 0.3 else 1.0
            val good = if (flat) null else (if (higher) d > 0 else d < 0)
            val changeText = when {
                flat -> "→"
                unitPoints -> (if (d > 0) "↑ " else "↓ ") + one(abs(d)) + POINTS_UNIT.getValue(locale)
                else -> (if (d > 0) "↑ " else "↓ ") + pct(abs(rel))
            }
            val valueText = res(R.string.trend_value, fmt(last), short)
            val spoken = res(
                R.string.cd_trend_row, res(label), fmt(last), month(m.last),
                res(if (flat) R.string.trend_level else if (d > 0) R.string.trend_above else R.string.trend_below),
                if (good == null) text("") else res(if (good) R.string.better_suffix else R.string.worse_suffix),
            )
            return TrendUi(id, res(label), valueText, values.map { it.toFloat() }, tone, changeText, good, spoken)
        }
        return listOf(
            row(TrendId.NET, R.string.trend_net, TrendTone.Cashflow, true, { it.net.ore / 100.0 }, { kr(Money((it * 100).toLong()), signed = true) }, false),
            row(TrendId.INCOME, R.string.kpi_income, TrendTone.Income, true, { it.income.ore / 100.0 }, { kr(Money((it * 100).toLong())) }, false),
            row(TrendId.EXPENSES, R.string.kpi_expenses, TrendTone.Expenses, false, { it.expenses.ore / 100.0 }, { kr(Money((it * 100).toLong())) }, false),
            row(TrendId.SAVINGS_RATE, R.string.kpi_rate, TrendTone.Savings, true, { it.savingsRatePercent ?: 0.0 }, { pct(it) }, true),
        )
    }

    // ───────────────────────────── Att göra ─────────────────────────────

    private fun todo(input: HomeInput): List<TodoUi> {
        val out = ArrayList<TodoUi>()
        val review = input.transactions.filter { it.status == TransactionStatus.TO_REVIEW && !it.isPossibleDuplicate }
        if (review.isNotEmpty()) {
            val sum = review.sumOf { abs(it.amount.ore) }
            out += TodoUi(
                TodoKind.REVIEW, res(R.string.todo_review_eyebrow),
                plural(R.plurals.todo_review_title, review.size, review.size),
                res(R.string.todo_review_text, kr(sum)), res(R.string.todo_review_action),
            )
        }
        val duplicates = input.transactions.count { it.isPossibleDuplicate }
        if (duplicates > 0) {
            out += TodoUi(
                TodoKind.DUPLICATES, res(R.string.todo_duplicates_eyebrow),
                plural(R.plurals.todo_duplicates_title, duplicates, duplicates),
                res(R.string.todo_duplicates_text), res(R.string.todo_duplicates_action),
            )
        }
        val latest = input.transactions.maxOfOrNull { it.transactionDate }
        if (latest == null || latest < input.today.minus(30, DateTimeUnit.DAY)) {
            out += TodoUi(
                TodoKind.IMPORT, res(R.string.todo_import_eyebrow),
                res(R.string.todo_import_title, PeriodFormatter.monthLong(Period.of(input.today), locale).substringBefore(' ')),
                latest?.let { res(R.string.todo_import_text, PeriodFormatter.iso(it, locale)) } ?: res(R.string.todo_import_text_empty),
                res(R.string.todo_import_action),
            )
        }
        if (out.isEmpty()) {
            out += TodoUi(TodoKind.ALL_DONE, res(R.string.todo_done_eyebrow), res(R.string.todo_done_title), res(R.string.todo_done_text), res(R.string.todo_done_action))
        }
        return out
    }

    private fun footer(input: HomeInput): UiText? {
        val batch = input.batches.filter { it.undoneAt == null }.maxByOrNull { it.importedAt } ?: return null
        val date = batch.importedAt.toLocalDateTime(TimeZone.currentSystemDefault()).date
        val review = input.transactions.count { it.status == TransactionStatus.TO_REVIEW }
        return res(R.string.home_footer, PeriodFormatter.iso(date, locale), input.transactions.size, review)
    }

    // ───────────────────────────── Sheets ─────────────────────────────

    private fun sheet(input: HomeInput, m: Metrics, sheet: HomeSheet): HomeSheetUi {
        val a = m.a
        val inc = a.income.ore
        fun ofIncome(v: Long) = if (inc > 0) pct(v.toDouble() / inc * 100) else "–"
        fun bar(v: Long, whole: Long) = if (whole > 0) (v.toFloat() / whole).coerceIn(0f, 1f) else 0f
        fun name(id: CategoryId) = categoryName(id, input.categories[id]?.name ?: id.value)
        fun kindOf(id: CategoryId) = input.categories[id]?.kind ?: CategoryKind.UNCATEGORIZED
        val label = month(m.last)
        val vsLabel = m.prev?.let { res(R.string.sheet_vs, month(it)) } ?: res(R.string.sheet_avg_month)
        return when (sheet) {
            HomeSheet.NET -> HomeSheetUi(
                DetailSheetUi(
                    eyebrow = res(R.string.home_summary_label, label),
                    title = res(R.string.sheet_net_title),
                    figure = kr(a.net, signed = true),
                    description = res(R.string.sheet_net_desc),
                    shareALabel = res(R.string.sheet_saved_of_flow),
                    shareA = if (a.net.ore > 0) pct(a.savings.ore.toDouble() / a.net.ore * 100) else "–",
                    shareBLabel = vsLabel,
                    shareB = m.pa?.let { kr(a.net - it.net, signed = true) } ?: kr(a.net, signed = true),
                    bar = bar(a.savings.ore, a.net.ore),
                    barTone = FlowTone.Savings,
                    rows = listOf(
                        SheetRowUi(res(R.string.kpi_income), null, kr(a.income), null, FlowTone.Income),
                        SheetRowUi(res(R.string.kpi_expenses), null, kr(a.expenses), null, FlowTone.Fixed),
                        SheetRowUi(res(R.string.row_saved), null, kr(a.savings), null, FlowTone.Savings),
                        SheetRowUi(res(R.string.row_left), null, kr(a.left, signed = true), null, FlowTone.Neutral),
                    ),
                    cta = res(R.string.cta_open_flow),
                ),
                HomeTarget.Flow(TrendId.NET),
            )
            HomeSheet.INCOME -> HomeSheetUi(
                DetailSheetUi(
                    eyebrow = res(R.string.sheet_kpi_eyebrow, label),
                    title = res(R.string.kpi_income),
                    figure = kr(a.income),
                    description = res(R.string.sheet_income_desc),
                    shareALabel = vsLabel,
                    shareA = change(a.income.ore, m.pa?.income?.ore)?.let { pct(it, signed = true) } ?: kr(a.income),
                    shareBLabel = res(R.string.sheet_salary_share),
                    shareB = ofIncome(a.incomeByCategory[SALARY]?.ore ?: 0L),
                    bar = 1f,
                    barTone = FlowTone.Income,
                    rows = a.incomeByCategory.filterValues { it.ore > 0 }.entries.sortedByDescending { it.value.ore }
                        .map { (id, v) -> SheetRowUi(name(id), null, kr(v), share(v.ore, inc), FlowTone.Income) },
                    cta = res(R.string.cta_open_income),
                ),
                HomeTarget.Flow(TrendId.INCOME),
            )
            HomeSheet.EXPENSES -> HomeSheetUi(
                DetailSheetUi(
                    eyebrow = res(R.string.sheet_kpi_eyebrow, label),
                    title = res(R.string.kpi_expenses),
                    figure = kr(a.expenses),
                    description = res(R.string.sheet_expenses_desc),
                    shareALabel = vsLabel,
                    shareA = change(a.expenses.ore, m.pa?.expenses?.ore)?.let { pct(it, signed = true) } ?: kr(a.expenses),
                    shareBLabel = res(R.string.sheet_share_of_income),
                    shareB = ofIncome(a.expenses.ore),
                    bar = bar(a.expenses.ore, inc),
                    barTone = FlowTone.Fixed,
                    rows = listOf(
                        Triple(R.string.group_fixed, a.fixedExpenses, FlowTone.Fixed),
                        Triple(R.string.group_variable, a.variableExpenses, FlowTone.Variable),
                        Triple(R.string.group_uncategorized, a.uncategorizedExpenses, FlowTone.Neutral),
                    ).filter { it.second.ore > 0 }.map { (l, v, tone) -> SheetRowUi(res(l), null, kr(v), share(v.ore, a.expenses.ore), tone) },
                    cta = res(R.string.cta_open_expenses),
                ),
                HomeTarget.Flow(TrendId.EXPENSES),
            )
            HomeSheet.SAVINGS_RATE -> HomeSheetUi(
                DetailSheetUi(
                    eyebrow = res(R.string.sheet_kpi_eyebrow, label),
                    title = res(R.string.kpi_rate),
                    figure = pct(m.rate),
                    description = res(if (input.amortizationAsSavings) R.string.sheet_rate_desc_amort else R.string.sheet_rate_desc, pct(m.rateGoal)),
                    shareALabel = res(R.string.row_saved),
                    shareA = kr(a.savings),
                    shareBLabel = res(R.string.sheet_goal),
                    shareB = pct(m.rateGoal),
                    bar = (m.rate / m.rateGoal).toFloat().coerceIn(0f, 1f),
                    barTone = FlowTone.Savings,
                    rows = a.savingsByCategory.filterValues { it.ore > 0 }.entries.sortedByDescending { it.value.ore }
                        .map { (id, v) -> SheetRowUi(name(id), null, kr(v), share(v.ore, inc), FlowTone.Savings) },
                    cta = res(R.string.cta_open_savings),
                ),
                HomeTarget.Flow(TrendId.SAVINGS_RATE),
            )
            HomeSheet.BUFFER -> {
                val buffers = input.accounts.filter { it.type == AccountType.SAVINGS }
                val goalValue = m.fixedPerMonth.ore * m.bufferGoal
                val rest = (goalValue - (m.buffer?.ore ?: 0L)).toLong()
                HomeSheetUi(
                    DetailSheetUi(
                        eyebrow = res(R.string.sheet_kpi_eyebrow, label),
                        title = res(R.string.sheet_buffer_title),
                        figure = m.bufferMonths?.let { one(it) + MONTHS_UNIT.getValue(locale) } ?: "–",
                        description = if (m.bufferMonths != null) res(R.string.sheet_buffer_desc, m.bufferGoal.roundToInt()) else res(R.string.sheet_buffer_desc_empty),
                        shareALabel = res(R.string.sheet_buffer),
                        shareA = m.buffer?.let { kr(it) } ?: "–",
                        shareBLabel = res(R.string.sheet_fixed_per_month),
                        shareB = kr(m.fixedPerMonth),
                        bar = m.bufferMonths?.let { (it / m.bufferGoal).toFloat().coerceIn(0f, 1f) } ?: 0f,
                        barTone = FlowTone.Savings,
                        rows = buffers.map { acc ->
                            SheetRowUi(
                                text(acc.name),
                                acc.marketValueDate?.let { res(R.string.row_value_updated, PeriodFormatter.iso(it, locale)) } ?: res(R.string.row_value_imported),
                                acc.marketValue?.let { kr(it) } ?: "–",
                                null, FlowTone.Savings,
                            )
                        } + if (m.bufferMonths != null) {
                            listOf(
                                SheetRowUi(
                                    res(R.string.row_buffer_goal, m.bufferGoal.roundToInt()),
                                    res(if (rest > 0) R.string.row_left_to_save else R.string.row_goal_reached),
                                    kr(abs(rest)), null, FlowTone.Neutral,
                                ),
                            )
                        } else {
                            emptyList()
                        },
                        cta = res(if (buffers.isEmpty() || m.bufferMonths == null) R.string.cta_open_accounts else R.string.cta_open_savings),
                    ),
                    if (buffers.isEmpty() || m.bufferMonths == null) HomeTarget.Accounts else HomeTarget.Flow(TrendId.SAVINGS_RATE),
                )
            }
        }
    }

    private companion object {
        val SALARY = CategoryId("lon")
        // No-break space so the unit never wraps away from the number.
        val POINTS_UNIT = mapOf(AppLocale.SV to " pe", AppLocale.EN to " pp")
        val MONTHS_UNIT = mapOf(AppLocale.SV to " mån", AppLocale.EN to " mo")
    }
}
