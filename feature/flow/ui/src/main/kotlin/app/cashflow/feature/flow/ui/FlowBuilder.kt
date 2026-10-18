package app.cashflow.feature.flow.ui

import app.cashflow.core.domain.cashflow.CashFlowSummary
import app.cashflow.core.domain.cashflow.FlowData
import app.cashflow.core.domain.cashflow.PeriodMode
import app.cashflow.core.domain.cashflow.PeriodScope
import app.cashflow.core.domain.model.Category
import app.cashflow.core.domain.model.CategoryId
import app.cashflow.core.domain.model.CategoryKind
import app.cashflow.core.domain.model.Money
import app.cashflow.core.domain.model.Period
import app.cashflow.core.domain.model.Transaction
import app.cashflow.core.domain.model.TransferKind
import app.cashflow.core.ui.FlowTone
import app.cashflow.core.ui.UiText
import app.cashflow.core.ui.categoryName
import app.cashflow.core.ui.format.AppLocale
import app.cashflow.core.ui.format.MoneyFormatter
import app.cashflow.core.ui.format.PercentFormatter
import app.cashflow.core.ui.format.PeriodFormatter
import app.cashflow.core.ui.tone
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlin.math.abs
import kotlin.math.roundToInt

/** What the person has picked on Flöde. Only tab, mode and period are kept in settings. */
internal data class FlowSelection(
    val tab: FlowTab = FlowTab.CASHFLOW,
    val mode: PeriodMode = PeriodMode.MONTH,
    val month: Period? = null,
    val year: Int? = null,
    /** The month scrubbed in the Alla line chart, as an index into the months with data. */
    val scrub: Int? = null,
    /** Donut drill-down: ids from the top level down. */
    val path: List<String> = emptyList(),
    val sheet: String? = null,
    val picker: Boolean = false,
    val pickerYear: Int? = null,
)

internal class FlowInput(
    val data: FlowData,
    val transactions: List<Transaction>,
    val categories: Map<CategoryId, Category>,
    /** The household's buffer (savings accounts), null when there is no savings account. */
    val buffer: Money?,
    val percentDecimals: Boolean,
)

/** The resolved period: the scope, its sum and what it is compared with. */
internal class Resolved(
    val scope: PeriodScope,
    val a: CashFlowSummary,
    val prev: PeriodScope?,
    val pa: CashFlowSummary?,
    val label: String,
    val prevLabel: String?,
    /** The 12 months ending at the scope's last month. */
    val months12: List<Period>,
    val a12: CashFlowSummary,
    val scrub: Int,
)

/**
 * Turns the month sums into everything Flöde shows. Pure and Android-free apart from resource
 * ids, so it is unit-tested directly. Every figure is pre-formatted here; the UI only lays out.
 */
internal class FlowBuilder(private val locale: AppLocale = AppLocale.current()) {

    private fun res(id: Int, vararg args: Any): UiText = UiText.StringResource(id, arrayOf(*args))
    private fun text(s: String): UiText = UiText.DynamicString(s)
    private fun kr(m: Money, signed: Boolean = false) = MoneyFormatter.format(m, signed = signed, locale = locale)
    private fun kr(ore: Long, signed: Boolean = false) = kr(Money(ore), signed)
    private fun spoken(m: Money) = MoneyFormatter.spoken(m, locale)
    private fun monthLabel(p: Period) = PeriodFormatter.month(p, locale)
    private fun monthShort(p: Period) = monthLabel(p).substringBefore(' ')
    private var decimals = 0
    private fun pct(v: Double, signed: Boolean = false) = PercentFormatter.format(v, decimals = decimals, signed = signed, locale = locale)
    private fun share(part: Long, whole: Long) = PercentFormatter.share(part, whole, locale)

    fun build(input: FlowInput, sel: FlowSelection): FlowState {
        val data = input.data
        if (data.isEmpty) return FlowState(tab = sel.tab, mode = sel.mode, hasData = false, isLoading = false)
        decimals = if (input.percentDecimals) 1 else 0
        val r = resolve(data, sel)
        val tab = sel.tab
        val tot = total(tab, r.a)
        val prevTot = r.pa?.let { total(tab, it) }

        val deltaText = if (prevTot != null && r.prevLabel != null) {
            res(R.string.flow_delta_vs, kr(tot - prevTot, signed = true), r.prevLabel)
        } else {
            res(R.string.flow_delta_avg, kr(tot.ore / r.scope.monthCount, signed = tab == FlowTab.CASHFLOW))
        }
        val better = prevTot == null || if (tab == FlowTab.EXPENSE) tot <= prevTot else tot >= prevTot

        val isCash = tab == FlowTab.CASHFLOW
        return FlowState(
            tab = tab,
            mode = sel.mode,
            periodPill = when (sel.mode) {
                PeriodMode.MONTH -> r.label
                PeriodMode.YEAR -> r.scope.key
                PeriodMode.ALL -> null
            },
            summaryLabel = res(R.string.flow_summary_label, res(tab.summaryTitle()), r.label),
            summaryValue = kr(tot, signed = isCash),
            summarySpoken = spoken(tot),
            delta = deltaText,
            deltaPositive = better,
            tiles = tiles(input, r, tab),
            chartTitle = if (sel.mode == PeriodMode.ALL) monthLabel(data.periods[r.scrub]) else r.label,
            chartKeys = chartKeys(data, r, tab, sel.mode),
            barGroups = if (sel.mode == PeriodMode.ALL) emptyList() else bars(data, sel.mode, tab),
            selectedBar = if (sel.mode == PeriodMode.ALL) null else r.scope.key,
            average = if (!isCash && sel.mode == PeriodMode.MONTH) average(data, tab) else null,
            line = if (sel.mode == PeriodMode.ALL) line(data, tab, r.scrub) else null,
            lineSpoken = if (sel.mode == PeriodMode.ALL) monthLabel(data.periods[r.scrub]) + ", " + kr(total(tab, data.month(data.periods[r.scrub])), signed = isCash) else "",
            donut = if (isCash) donut(input, r, sel.path) else null,
            sankey = if (isCash) sankey(input, r) else null,
            categoryBars = if (isCash) emptyList() else categoryBars(input, r, tab, sel.mode),
            barsTotal = kr(tot),
            latest = if (isCash) emptyList() else latest(input, r, tab),
            picker = if (sel.picker) picker(data, sel, r) else null,
            sheet = sel.sheet?.let { sheet(input, r, tab, it) },
            hasData = true,
            isLoading = false,
        )
    }

    // ───────────────────────────── Period ─────────────────────────────

    fun resolve(data: FlowData, sel: FlowSelection): Resolved {
        val scope = when (sel.mode) {
            PeriodMode.MONTH -> data.monthScope(sel.month?.takeIf { it in data.periods } ?: data.periods.last())
            PeriodMode.YEAR -> data.yearScope(sel.year?.takeIf { it in data.years } ?: data.years.last())
            PeriodMode.ALL -> data.allScope()
        }
        val prev = if (sel.mode == PeriodMode.ALL) null else data.previous(scope)
        val months12 = data.twelveMonthsTo(scope.last)
        return Resolved(
            scope = scope,
            a = data.sum(scope),
            prev = prev,
            pa = prev?.let(data::sum),
            label = scopeLabel(data, scope),
            prevLabel = prev?.let { scopeLabel(data, it) },
            months12 = months12,
            a12 = data.sum(months12),
            scrub = (sel.scrub ?: data.periods.lastIndex).coerceIn(0, data.periods.lastIndex),
        )
    }

    /** "okt 2026", "2026", "jan–okt 2026" for the running year, "sedan jan 2022" for Alla. */
    private fun scopeLabel(data: FlowData, scope: PeriodScope): String = when (scope.mode) {
        PeriodMode.MONTH -> monthLabel(scope.last)
        PeriodMode.YEAR -> if (scope.yearToDate) "${monthShort(scope.first)}–${monthLabel(scope.last)}" else scope.key
        PeriodMode.ALL -> (if (locale == AppLocale.SV) "sedan " else "since ") + monthLabel(data.periods.first())
    }

    private fun FlowTab.summaryTitle() = when (this) {
        FlowTab.CASHFLOW -> R.string.flow_net_title
        FlowTab.INCOME -> R.string.tab_income
        FlowTab.EXPENSE -> R.string.tab_expense
        FlowTab.SAVINGS -> R.string.tab_savings
    }

    private fun total(tab: FlowTab, s: CashFlowSummary): Money = when (tab) {
        FlowTab.CASHFLOW -> s.net
        FlowTab.INCOME -> s.income
        FlowTab.EXPENSE -> s.expenses
        FlowTab.SAVINGS -> s.savings
    }

    // ───────────────────────────── Chart ─────────────────────────────

    private fun chartKeys(data: FlowData, r: Resolved, tab: FlowTab, mode: PeriodMode): List<ChartKeyUi> {
        val all = mode == PeriodMode.ALL
        val s = if (all) data.month(data.periods[r.scrub]) else r.a
        val ps = if (all) data.periods.getOrNull(r.scrub - 1)?.let(data::month) else r.pa
        val psLabel = if (all) data.periods.getOrNull(r.scrub - 1)?.let(::monthLabel) else r.prevLabel
        val keys = when (tab) {
            FlowTab.CASHFLOW -> listOf(
                ChartKeyUi(res(R.string.key_income), kr(s.income), FlowTone.Income),
                ChartKeyUi(res(R.string.key_expenses), kr(s.expenses), FlowTone.Fixed),
                ChartKeyUi(res(R.string.key_net), kr(s.net, signed = true), null, negative = s.net.ore < 0),
            )
            FlowTab.EXPENSE -> listOfNotNull(
                ChartKeyUi(res(R.string.flow_fixed), kr(s.fixedExpenses), FlowTone.Fixed),
                ChartKeyUi(res(R.string.flow_variable), kr(s.variableExpenses), FlowTone.Variable),
                if (s.uncategorizedExpenses.ore > 0) ChartKeyUi(res(R.string.flow_uncat_short), kr(s.uncategorizedExpenses), FlowTone.Neutral) else null,
            )
            FlowTab.SAVINGS -> listOf(
                ChartKeyUi(res(R.string.key_saved), kr(s.savings), FlowTone.Savings),
                ChartKeyUi(res(R.string.key_rate), pct(s.savingsRatePercent ?: 0.0), null),
            )
            FlowTab.INCOME -> listOf(ChartKeyUi(res(R.string.key_income), kr(s.income), FlowTone.Income))
        }
        if (tab == FlowTab.CASHFLOW || ps == null || psLabel == null) return keys
        val now = total(tab, s).ore
        val before = total(tab, ps).ore
        if (before == 0L) return keys
        val ch = (now - before).toDouble() / abs(before) * 100
        val worse = if (tab == FlowTab.EXPENSE) ch > 0.5 else ch < -0.5
        return keys + ChartKeyUi(res(R.string.key_vs, psLabel), pct(ch, signed = true), null, negative = worse)
    }

    private fun series(tab: FlowTab, s: CashFlowSummary): List<List<BarSegmentUi>> {
        fun seg(m: Money, tone: FlowTone) = BarSegmentUi(m.ore.coerceAtLeast(0) / 100f, tone)
        return when (tab) {
            FlowTab.CASHFLOW -> listOf(listOf(seg(s.income, FlowTone.Income)), listOf(seg(s.expenses, FlowTone.Fixed)))
            FlowTab.INCOME -> listOf(listOf(seg(s.income, FlowTone.Income)))
            FlowTab.EXPENSE -> listOf(listOf(seg(s.fixedExpenses, FlowTone.Fixed), seg(s.variableExpenses, FlowTone.Variable), seg(s.uncategorizedExpenses, FlowTone.Neutral)))
            FlowTab.SAVINGS -> listOf(listOf(seg(s.savings, FlowTone.Savings)))
        }
    }

    private fun bars(data: FlowData, mode: PeriodMode, tab: FlowTab): List<BarGroupUi> {
        val scopes = data.scopes(mode)
        return scopes.mapIndexed { i, scope ->
            val s = data.sum(scope)
            val label = scopeLabel(data, scope)
            val spokenText = if (tab == FlowTab.CASHFLOW) {
                res(R.string.cd_bar_cash, label, spoken(s.income), spoken(s.expenses), spoken(s.net))
            } else {
                res(R.string.cd_bar_single, label, res(tab.summaryTitle()), spoken(total(tab, s)))
            }
            BarGroupUi(
                key = scope.key,
                label = if (mode == PeriodMode.MONTH) monthShort(scope.last) else scope.key,
                mark = if (mode == PeriodMode.MONTH && (scope.last.month == 1 || i == 0)) scope.last.year.toString() else null,
                bars = series(tab, s),
                deficit = tab == FlowTab.CASHFLOW && s.net.ore < 0,
                spoken = spokenText,
            )
        }
    }

    /** The dashed "snitt" line: the average of the latest 12 months. */
    private fun average(data: FlowData, tab: FlowTab): Float? {
        val months = data.periods.takeLast(12)
        if (months.isEmpty()) return null
        return months.sumOf { total(tab, data.month(it)).ore.coerceAtLeast(0) }.toFloat() / months.size / 100f
    }

    private fun line(data: FlowData, tab: FlowTab, scrub: Int): LineUi {
        val months = data.periods.map(data::month)
        fun values(get: (CashFlowSummary) -> Money) = months.map { get(it).ore.coerceAtLeast(0) / 100f }
        val series = if (tab == FlowTab.CASHFLOW) {
            listOf(FlowTone.Income to values { it.income }, FlowTone.Fixed to values { it.expenses })
        } else {
            val tone = when (tab) {
                FlowTab.INCOME -> FlowTone.Income
                FlowTab.EXPENSE -> FlowTone.Fixed
                else -> FlowTone.Savings
            }
            listOf(tone to values { total(tab, it) })
        }
        val ticks = data.periods.mapIndexedNotNull { i, p -> if (p.month == 1 || i == 0) i to p.year.toString() else null }
            // Keep at most one tick per year and drop a first-year tick that would collide.
            .let { t -> if (t.size > 1 && t[1].first - t[0].first < 4) t.drop(1) else t }
        return LineUi(series, scrub, ticks, overspend = tab == FlowTab.CASHFLOW, area = tab != FlowTab.CASHFLOW)
    }

    // ───────────────────────────── Tiles ─────────────────────────────

    private fun tiles(input: FlowInput, r: Resolved, tab: FlowTab): List<TileUi> {
        val data = input.data
        val a = r.a
        val n = r.scope.monthCount.coerceAtLeast(1)
        val n12 = r.months12.size.coerceAtLeast(1)
        val mode = r.scope.mode
        val isLatest = r.scope.last == data.periods.last()
        val avgSub: UiText = when (mode) {
            PeriodMode.MONTH -> if (isLatest) res(R.string.sub_last_12) else res(R.string.sub_12_to, r.label)
            PeriodMode.YEAR -> res(R.string.sub_per_month_in, r.label)
            PeriodMode.ALL -> res(R.string.sub_per_month_since, data.periods.first().year.toString())
        }
        fun savedOfFlow(): TileUi = if (a.net.ore > 0) {
            val v = a.savings.ore.toDouble() / a.net.ore * 100
            TileUi(res(R.string.tile_saved_of_flow), text(pct(v)), null, goal = (v / 100).toFloat().coerceIn(0f, 1f))
        } else {
            TileUi(res(R.string.tile_saved_of_flow), text("–"), res(R.string.sub_flow_negative), goal = 0f)
        }

        return when (tab) {
            FlowTab.CASHFLOW -> {
                val scopeMonths = when (mode) {
                    PeriodMode.MONTH -> r.months12
                    else -> r.scope.months
                }
                val scopeSub: UiText = when (mode) {
                    PeriodMode.MONTH -> if (isLatest) res(R.string.sub_last_12) else res(R.string.sub_12_to, r.label)
                    PeriodMode.YEAR -> text(r.label)
                    PeriodMode.ALL -> text(r.label)
                }
                val sums = scopeMonths.map { it to data.month(it) }
                val deficits = sums.count { it.second.net.ore < 0 }
                val best = sums.maxBy { it.second.net.ore }
                val avg = if (mode == PeriodMode.MONTH) r.a12.net.ore / n12 else a.net.ore / n
                listOf(
                    TileUi(res(R.string.tile_avg_month), text(kr(avg, signed = true)), avgSub),
                    savedOfFlow(),
                    TileUi(res(R.string.tile_deficit_months), text(deficits.toString()), scopeSub),
                    TileUi(res(R.string.tile_best_month), text(kr(best.second.net, signed = true)), text(monthLabel(best.first))),
                )
            }
            FlowTab.INCOME -> {
                val avg = if (mode == PeriodMode.MONTH) r.a12.income.ore / n12 else a.income.ore / n
                val ly: Pair<String, CashFlowSummary>? = when (mode) {
                    PeriodMode.MONTH -> Period(r.scope.last.year - 1, r.scope.last.month).takeIf { it in data.periods }?.let { monthLabel(it) to data.month(it) }
                    PeriodMode.YEAR -> r.pa?.let { (r.prevLabel ?: "") to it }
                    PeriodMode.ALL -> null
                }
                val yoy = ly?.takeIf { it.second.income.ore != 0L }?.let { (a.income.ore - it.second.income.ore).toDouble() / abs(it.second.income.ore) * 100 }
                val salary = a.incomeByCategory[SALARY]?.ore ?: 0L
                val top = a.incomeByCategory.filterValues { it.ore > 0 }.maxByOrNull { it.value.ore }
                listOf(
                    TileUi(res(R.string.tile_avg_month), text(kr(avg)), avgSub),
                    TileUi(
                        res(R.string.tile_vs_last_year),
                        text(yoy?.let { pct(it, signed = true) } ?: "–"),
                        if (yoy != null && ly != null) res(R.string.sub_label_amount, ly.first, kr(ly.second.income)) else res(R.string.sub_no_compare),
                    ),
                    TileUi(res(R.string.tile_salary_share), text(if (a.income.ore > 0) pct(salary.toDouble() / a.income.ore * 100) else "–"), res(R.string.sub_salary, kr(salary))),
                    if (top != null) {
                        TileUi(res(R.string.tile_top_source), nameOf(input, top.key), res(R.string.sub_top_source, kr(top.value), share(top.value.ore, a.income.ore)))
                    } else {
                        TileUi(res(R.string.tile_top_source), text("–"), null)
                    },
                )
            }
            FlowTab.EXPENSE -> {
                val rows = expenseRows(input, r.scope.months)
                val biggest = rows.minByOrNull { it.amount.ore }
                val end = rows.maxOfOrNull { it.transactionDate }
                val recent = if (end == null) emptyList() else rows.filter { it.transactionDate > end.minus(30, DateTimeUnit.DAY) }
                val common = recent.groupBy { payee(it) }.maxWithOrNull(compareBy({ it.value.size }, { -it.value.sumOf { t -> t.amount.ore } }))
                val varAvg = r.a12.variableExpenses.ore / n12
                val varNow = if (mode == PeriodMode.MONTH) a.variableExpenses.ore else a.variableExpenses.ore / n
                val vd = if (varAvg > 0) (varNow - varAvg).toDouble() / varAvg * 100 else null
                listOf(
                    TileUi(res(R.string.tile_fixed_share), text(if (a.expenses.ore > 0) pct(a.fixedExpenses.ore.toDouble() / a.expenses.ore * 100) else "–"), res(R.string.sub_fixed_share, kr(a.fixedExpenses))),
                    TileUi(res(R.string.tile_variable_vs_avg), text(vd?.let { pct(it, signed = true) } ?: "–"), res(R.string.sub_avg_12, kr(varAvg))),
                    if (biggest != null) {
                        TileUi(res(R.string.tile_biggest), text(kr(-biggest.amount.ore)), res(R.string.sub_label_amount, payee(biggest), PeriodFormatter.iso(biggest.transactionDate, locale)))
                    } else {
                        TileUi(res(R.string.tile_biggest), text("–"), null)
                    },
                    if (common != null) {
                        val sum = -common.value.sumOf { it.amount.ore }
                        TileUi(res(R.string.tile_most_common), text(common.key), UiText.PluralResource(R.plurals.sub_most_common, common.value.size, arrayOf(common.value.size, kr(sum))))
                    } else {
                        TileUi(res(R.string.tile_most_common), text("–"), null)
                    },
                )
            }
            FlowTab.SAVINGS -> {
                val rate = a.savingsRatePercent ?: 0.0
                val year = r.scope.last.year
                val ytdMonths = data.periods.filter { it.year == year && it <= r.scope.last }
                val ytd = data.sum(ytdMonths)
                val ytdLabel = if (ytdMonths.size > 1) "${monthShort(ytdMonths.first())}–${monthLabel(ytdMonths.last())}" else monthLabel(ytdMonths.last())
                val monthlyCost = r.a12.expenses.ore / n12
                val bufferTile = if (input.buffer != null && input.buffer.ore > 0 && monthlyCost > 0) {
                    val months = input.buffer.ore.toDouble() / monthlyCost
                    TileUi(
                        res(R.string.tile_buffer),
                        res(R.string.value_months, PercentFormatter.format(months, decimals = 1, locale = locale).replace("%", "").trim()),
                        res(R.string.sub_buffer, BUFFER_TARGET_MONTHS, pct(months / BUFFER_TARGET_MONTHS * 100)),
                        goal = (months / BUFFER_TARGET_MONTHS).toFloat().coerceIn(0f, 1f),
                    )
                } else {
                    TileUi(res(R.string.tile_buffer), text("–"), res(R.string.sub_no_buffer))
                }
                listOf(
                    TileUi(
                        res(R.string.tile_rate), text(pct(rate)),
                        res(if (rate >= SAVINGS_GOAL_PERCENT) R.string.sub_rate_reached else R.string.sub_rate, SAVINGS_GOAL_PERCENT),
                        goal = (rate / SAVINGS_GOAL_PERCENT).toFloat().coerceIn(0f, 1f),
                    ),
                    savedOfFlow(),
                    bufferTile,
                    TileUi(res(R.string.tile_saved_ytd), text(kr(ytd.savings)), res(R.string.sub_saved_ytd, ytdLabel, kr(ytd.savings.ore / ytdMonths.size.coerceAtLeast(1)))),
                )
            }
        }
    }

    // ───────────────────────────── Donut ─────────────────────────────

    /** One node of the "how was the income used" tree. */
    private class Node(val id: String, val label: UiText, val v: Long, val tone: FlowTone, val kids: List<Node>? = null)

    private fun tree(input: FlowInput, a: CashFlowSummary): Map<String, Node> {
        fun leaves(values: Map<CategoryId, Money>, kind: CategoryKind?, tone: FlowTone) = values
            .filter { (id, m) -> m.ore > 0 && (kind == null || kindOf(input, id) == kind) }
            .entries.sortedByDescending { it.value.ore }
            .map { (id, m) -> Node(id.value, nameOf(input, id), m.ore, tone) }
        val fixed = Node(FIXED, res(R.string.flow_fixed), a.fixedExpenses.ore, FlowTone.Fixed, leaves(a.expensesByCategory, CategoryKind.FIXED, FlowTone.Fixed))
        val variable = Node(VARIABLE, res(R.string.flow_variable), a.variableExpenses.ore, FlowTone.Variable, leaves(a.expensesByCategory, CategoryKind.VARIABLE, FlowTone.Variable))
        val uncategorized = Node(UNCAT, res(R.string.flow_uncat), a.uncategorizedExpenses.ore, FlowTone.Neutral)
        val expenses = Node(EXPENSES, res(R.string.node_expenses), a.expenses.ore, FlowTone.Fixed, listOf(fixed, variable, uncategorized).filter { it.v > 0 })
        val savings = Node(SAVINGS, res(R.string.node_savings), a.savings.ore, FlowTone.Savings, leaves(a.savingsByCategory, null, FlowTone.Savings))
        val left = Node(LEFT, res(R.string.node_left), a.left.ore, FlowTone.Neutral)
        return listOf(expenses, fixed, variable, uncategorized, savings, left).associateBy { it.id }
    }

    private fun donut(input: FlowInput, r: Resolved, path: List<String>): DonutUi {
        val a = r.a
        val nodes = tree(input, a)
        val parent = path.lastOrNull()?.let { nodes[it] }?.takeIf { it.kids != null }
        val deficit = if (a.left.ore < 0) -a.left.ore else 0L
        val outflow = a.expenses.ore + a.savings.ore
        val items = parent?.kids ?: listOfNotNull(nodes[EXPENSES], nodes[SAVINGS], nodes[LEFT]).filter { it.v > 0 }
        val base = (parent?.v ?: if (deficit > 0) outflow else a.income.ore).coerceAtLeast(1)
        val ofWhat: UiText = parent?.label ?: res(if (deficit > 0) R.string.of_outflow else R.string.of_income)
        val segments = items.mapIndexed { i, it ->
            val same = items.take(i).count { o -> o.tone == it.tone }
            val sh = share(it.v, base)
            DonutSegmentUi(
                id = it.id, label = it.label, amount = kr(it.v), share = sh, value = it.v.toFloat(), tone = it.tone,
                alpha = TIERS[same.coerceAtMost(TIERS.lastIndex)], leaf = it.kids == null,
                spoken = res(if (it.kids == null) R.string.cd_donut_leaf else R.string.cd_donut_branch, it.label, spoken(Money(it.v)), sh, ofWhat),
            )
        }
        val center = when {
            parent != null -> parent.label to kr(parent.v)
            deficit > 0 -> res(R.string.center_outflow) to kr(outflow)
            else -> res(R.string.key_income) to kr(a.income)
        }
        val deficitText = if (parent == null && deficit > 0) {
            if (a.net.ore < 0) res(R.string.donut_deficit_negative, kr(a.net, signed = true)) else res(R.string.donut_deficit_saved, kr(deficit))
        } else {
            null
        }
        return DonutUi(segments, center.first, center.second, parent?.label, parent?.id, deficitText)
    }

    // ───────────────────────────── Sankey ─────────────────────────────

    private fun sankey(input: FlowInput, r: Resolved): SankeyUi {
        val a = r.a
        val nodes = tree(input, a)
        val out = ArrayList<SankeyNodeUiModel>()
        val links = ArrayList<Triple<String, String, Float>>()
        fun node(id: String, label: UiText, v: Long, tone: FlowTone, column: Int) {
            out += SankeyNodeUiModel(id, label, kr(v), tone, column)
        }
        val fromAccount = (-a.left.ore).coerceAtLeast(0)
        val toExpenses = (-a.net.ore).coerceAtLeast(0)
        val toSavings = fromAccount - toExpenses
        node(INCOME, res(R.string.key_income), a.income.ore, FlowTone.Income, 0)
        if (fromAccount > 0) node(FROM_ACCOUNT, res(R.string.node_from_account), fromAccount, FlowTone.Neutral, 0)
        node(EXPENSES, res(R.string.node_expenses), a.expenses.ore, FlowTone.Fixed, 1)
        node(SAVINGS, res(R.string.node_savings), a.savings.ore, FlowTone.Savings, 1)
        links += Triple(INCOME, EXPENSES, (a.expenses.ore - toExpenses).toFloat())
        links += Triple(INCOME, SAVINGS, (a.savings.ore - toSavings).toFloat())
        if (toExpenses > 0) links += Triple(FROM_ACCOUNT, EXPENSES, toExpenses.toFloat())
        if (toSavings > 0) links += Triple(FROM_ACCOUNT, SAVINGS, toSavings.toFloat())
        if (a.left.ore > 0) {
            node(LEFT, res(R.string.node_left), a.left.ore, FlowTone.Neutral, 1)
            links += Triple(INCOME, LEFT, a.left.ore.toFloat())
        }
        nodes[EXPENSES]?.kids.orEmpty().forEach { k ->
            node(k.id, k.label, k.v, k.tone, 2)
            links += Triple(EXPENSES, k.id, k.v.toFloat())
        }
        // At most three savings nodes: the two largest and "Övrigt sparande".
        val kids = nodes[SAVINGS]?.kids.orEmpty()
        val shown = if (kids.size > 3) kids.take(2) else kids
        shown.forEach { k ->
            node(k.id, k.label, k.v, FlowTone.Savings, 2)
            links += Triple(SAVINGS, k.id, k.v.toFloat())
        }
        if (kids.size > 3) {
            val rest = kids.drop(2).sumOf { it.v }
            node(OTHER_SAVINGS, res(R.string.node_other_savings), rest, FlowTone.Savings, 2)
            links += Triple(SAVINGS, OTHER_SAVINGS, rest.toFloat())
        }
        val keptLinks = links.filter { it.third > 0f }
        val used = keptLinks.flatMap { listOf(it.first, it.second) }.toSet()
        val spokenText = if (fromAccount > 0) {
            res(R.string.cd_sankey_from_account, r.label, spoken(a.income), spoken(a.expenses), spoken(a.savings), spoken(Money(fromAccount)))
        } else {
            res(R.string.cd_sankey, r.label, spoken(a.income), spoken(a.expenses), spoken(a.savings), spoken(a.left))
        }
        return SankeyUi(out.filter { it.id in used }, keptLinks, spokenText)
    }

    // ───────────────────────────── Category bars and latest rows ─────────────────────────────

    private fun valuesOf(tab: FlowTab, s: CashFlowSummary): Map<CategoryId, Money> = when (tab) {
        FlowTab.INCOME -> s.incomeByCategory
        FlowTab.EXPENSE -> s.expensesByCategory
        FlowTab.SAVINGS -> s.savingsByCategory
        FlowTab.CASHFLOW -> emptyMap()
    }

    private fun categoryBars(input: FlowInput, r: Resolved, tab: FlowTab, mode: PeriodMode): List<CategoryBarUi> {
        val values = valuesOf(tab, r.a).filterValues { it.ore > 0 }
        val max = values.values.maxOfOrNull { it.ore } ?: return emptyList()
        val whole = total(tab, r.a).ore.coerceAtLeast(values.values.sumOf { it.ore })
        val avg12 = valuesOf(tab, r.a12)
        val n12 = r.months12.size.coerceAtLeast(1)
        val n = r.scope.monthCount.coerceAtLeast(1)
        return values.entries.sortedByDescending { it.value.ore }.map { (id, m) ->
            val kind = kindOf(input, id)
            val avg = (avg12[id]?.ore ?: 0L).toDouble() / n12
            val now = if (mode == PeriodMode.MONTH) m.ore.toDouble() else m.ore.toDouble() / n
            val dp = if (avg > 0) (now - avg) / avg * 100 else 0.0
            val showTrend = mode != PeriodMode.ALL && kind != CategoryKind.UNCATEGORIZED && avg > 0 && abs(dp) >= 3
            CategoryBarUi(
                id = id.value,
                name = nameOf(input, id),
                amount = kr(m),
                share = share(m.ore, whole),
                fraction = m.ore.toFloat() / max,
                tone = kind.tone(),
                spokenAmount = spoken(m),
                trend = if (showTrend) (if (dp > 0) "↑ " else "↓ ") + PercentFormatter.format(abs(dp).roundToInt().toDouble(), decimals = 0, locale = locale) else null,
                trendBetter = if (tab == FlowTab.EXPENSE) dp < 0 else dp > 0,
            )
        }
    }

    private fun counted(input: FlowInput, months: List<Period>): List<Transaction> {
        val set = months.toSet()
        return input.transactions.filter { it.countsInCashFlow && !it.hidden && it.period in set }
    }

    private fun expenseRows(input: FlowInput, months: List<Period>) = counted(input, months).filter { t ->
        t.amount.ore < 0 && t.transferKind == TransferKind.NONE && kindOf(input, t.categoryId).let { k ->
            k == CategoryKind.FIXED || k == CategoryKind.VARIABLE || k == CategoryKind.UNCATEGORIZED
        }
    }

    private fun rowsFor(input: FlowInput, months: List<Period>, tab: FlowTab): List<Transaction> = when (tab) {
        FlowTab.EXPENSE -> expenseRows(input, months)
        FlowTab.INCOME -> counted(input, months).filter { it.transferKind == TransferKind.NONE && it.amount.ore > 0 && kindOf(input, it.categoryId).let { k -> k == CategoryKind.INCOME || k == CategoryKind.UNCATEGORIZED } }
        FlowTab.SAVINGS -> counted(input, months).filter {
            it.transferKind == TransferKind.TO_SAVINGS || it.transferKind == TransferKind.FROM_SAVINGS ||
                (it.transferKind == TransferKind.NONE && kindOf(input, it.categoryId) == CategoryKind.SAVINGS)
        }
        FlowTab.CASHFLOW -> emptyList()
    }.sortedWith(compareByDescending<Transaction> { it.transactionDate }.thenBy { it.fileOrder })

    private fun latest(input: FlowInput, r: Resolved, tab: FlowTab): List<LatestTxUi> =
        rowsFor(input, r.scope.months, tab).take(10).map { t -> t.toLatest(input, tab) }

    private fun Transaction.toLatest(input: FlowInput, tab: FlowTab) = LatestTxUi(
        id = id.value,
        title = payee(this),
        sub = res(R.string.latest_sub, PeriodFormatter.iso(transactionDate, locale), nameOf(input, categoryId)),
        amount = if (tab == FlowTab.INCOME) kr(amount, signed = true) else kr(-amount.ore),
        tone = kindOf(input, categoryId).tone().let { if (tab == FlowTab.SAVINGS) FlowTone.Savings else it },
    )

    // ───────────────────────────── Sheets ─────────────────────────────

    private fun sheet(input: FlowInput, r: Resolved, tab: FlowTab, id: String): NodeSheetUi? {
        val a = r.a
        val inc = a.income.ore
        fun ofIncome(v: Long) = if (inc > 0) pct(v.toDouble() / inc * 100) else "–"
        fun bar(v: Long, whole: Long) = if (whole > 0) (v.toFloat() / whole).coerceIn(0f, 1f) else 0f
        val nodes = tree(input, a)
        return when (id) {
            "net" -> NodeSheetUi(
                eyebrow = res(R.string.flow_summary_label, res(R.string.flow_net_title), r.label),
                title = res(R.string.sheet_net_title),
                figure = kr(a.net, signed = true),
                description = res(R.string.sheet_net_desc),
                shareALabel = res(R.string.tile_saved_of_flow),
                shareA = if (a.net.ore > 0) pct(a.savings.ore.toDouble() / a.net.ore * 100) else "–",
                shareBLabel = if (r.pa != null && r.prevLabel != null) res(R.string.sheet_vs, r.prevLabel) else res(R.string.tile_avg_month),
                shareB = if (r.pa != null) kr(a.net - r.pa.net, signed = true) else kr(a.net.ore / r.scope.monthCount, signed = true),
                bar = bar(a.savings.ore, a.net.ore),
                barTone = FlowTone.Savings,
                rows = listOf(
                    SheetRowUi(res(R.string.key_income), res(R.string.row_inflow), kr(a.income), null, FlowTone.Income),
                    SheetRowUi(res(R.string.key_expenses), res(R.string.row_outflow), kr(-a.expenses.ore), null, FlowTone.Fixed),
                    SheetRowUi(res(R.string.row_of_which_saved), res(R.string.row_to_savings), kr(a.savings), null, FlowTone.Savings),
                    SheetRowUi(res(R.string.node_left), res(R.string.row_not_placed), kr(a.left, signed = true), null, FlowTone.Neutral),
                ),
                cta = res(R.string.cta_show_savings),
                action = SheetCta.Tab(FlowTab.SAVINGS),
            )
            "total" -> {
                val tot = total(tab, a)
                NodeSheetUi(
                    eyebrow = res(R.string.flow_summary_label, res(tab.summaryTitle()), r.label),
                    title = res(tab.summaryTitle()),
                    figure = kr(tot),
                    description = res(R.string.sheet_total_desc),
                    shareALabel = if (r.pa != null && r.prevLabel != null) res(R.string.sheet_vs, r.prevLabel) else res(R.string.tile_avg_month),
                    shareA = r.pa?.let { kr(tot - total(tab, it), signed = true) } ?: kr(tot.ore / r.scope.monthCount),
                    shareBLabel = res(R.string.sub_avg_12_label),
                    shareB = kr(total(tab, r.a12).ore / r.months12.size.coerceAtLeast(1)),
                    bar = 1f,
                    barTone = tab.tone(),
                    rows = emptyList(),
                    cta = res(R.string.cta_close),
                    action = SheetCta.Close,
                )
            }
            LEFT -> NodeSheetUi(
                eyebrow = res(R.string.flow_summary_label, res(R.string.section_distribution), r.label),
                title = res(R.string.node_left),
                figure = kr(a.left, signed = true),
                description = res(R.string.sheet_left_desc),
                shareALabel = res(R.string.share_of_income),
                shareA = ofIncome(a.left.ore),
                shareBLabel = res(R.string.share_of_flow),
                shareB = if (a.net.ore > 0) pct(a.left.ore.toDouble() / a.net.ore * 100) else "–",
                bar = bar(a.left.ore, inc),
                barTone = FlowTone.Neutral,
                rows = emptyList(),
                cta = res(R.string.cta_show_savings),
                action = SheetCta.Tab(FlowTab.SAVINGS),
            )
            FROM_ACCOUNT, "deficit" -> {
                val deficit = (-a.left.ore).coerceAtLeast(0)
                val negative = a.net.ore < 0
                NodeSheetUi(
                    eyebrow = res(R.string.flow_summary_label, res(if (negative) R.string.eyebrow_deficit else R.string.section_distribution), r.label),
                    title = res(if (negative) R.string.sheet_deficit_title else R.string.sheet_oversaved_title),
                    figure = kr(if (negative) a.net.ore else -deficit, signed = true),
                    description = if (negative) res(R.string.sheet_deficit_desc, kr(-a.net.ore), kr(a.savings), kr(deficit)) else res(R.string.sheet_oversaved_desc, kr(a.net, signed = true), kr(a.savings), kr(deficit)),
                    shareALabel = res(R.string.key_income),
                    shareA = kr(a.income),
                    shareBLabel = res(R.string.key_expenses),
                    shareB = kr(a.expenses),
                    bar = bar(deficit, inc),
                    barTone = FlowTone.Fixed,
                    rows = nodes[VARIABLE]?.kids.orEmpty().take(3).map { SheetRowUi(it.label, res(R.string.row_biggest_variable), kr(-it.v), null, FlowTone.Variable) },
                    cta = res(R.string.cta_show_expenses),
                    action = SheetCta.Tab(FlowTab.EXPENSE),
                )
            }
            INCOME -> NodeSheetUi(
                eyebrow = text(r.label),
                title = res(R.string.key_income),
                figure = kr(a.income),
                description = null,
                shareALabel = res(R.string.tile_salary_share),
                shareA = ofIncome(a.incomeByCategory[SALARY]?.ore ?: 0L),
                shareBLabel = res(R.string.sub_avg_12_label),
                shareB = kr(r.a12.income.ore / r.months12.size.coerceAtLeast(1)),
                bar = 1f,
                barTone = FlowTone.Income,
                rows = a.incomeByCategory.filterValues { it.ore > 0 }.entries.sortedByDescending { it.value.ore }.map { (cid, m) ->
                    SheetRowUi(nameOf(input, cid), null, kr(m), share(m.ore, inc), FlowTone.Income)
                },
                cta = res(R.string.cta_show_income),
                action = SheetCta.Tab(FlowTab.INCOME),
            )
            EXPENSES, FIXED, VARIABLE, SAVINGS, OTHER_SAVINGS, UNCAT -> {
                val n = nodes[if (id == OTHER_SAVINGS) SAVINGS else id] ?: return null
                val isSavings = n.id == SAVINGS
                val kids = n.kids
                NodeSheetUi(
                    eyebrow = text(r.label),
                    title = n.label,
                    figure = kr(if (isSavings) n.v else -n.v),
                    description = if (n.id == UNCAT) res(R.string.sheet_uncat_desc) else kids?.let { UiText.PluralResource(R.plurals.sheet_category_count, it.size) },
                    shareALabel = res(R.string.share_of_income),
                    shareA = ofIncome(n.v),
                    shareBLabel = res(if (isSavings) R.string.share_of_flow_short else R.string.share_of_expenses),
                    shareB = if (isSavings) (if (a.net.ore > 0) pct(n.v.toDouble() / a.net.ore * 100) else "–") else (if (a.expenses.ore > 0) pct(n.v.toDouble() / a.expenses.ore * 100) else "–"),
                    bar = bar(n.v, inc),
                    barTone = n.tone,
                    rows = kids?.map { k -> SheetRowUi(k.label, res(R.string.row_share_of, share(k.v, n.v), n.label), kr(k.v), share(k.v, inc), k.tone) }
                        ?: categoryTxRows(input, r, CategoryId.UNCATEGORIZED),
                    cta = when (n.id) {
                        UNCAT -> res(R.string.cta_review)
                        SAVINGS -> res(R.string.cta_show_savings)
                        else -> res(R.string.cta_show_expenses)
                    },
                    action = when (n.id) {
                        UNCAT -> SheetCta.Events(CategoryId.UNCATEGORIZED.value)
                        SAVINGS -> SheetCta.Tab(FlowTab.SAVINGS)
                        else -> SheetCta.Tab(FlowTab.EXPENSE)
                    },
                )
            }
            else -> categorySheet(input, r, CategoryId(id))
        }
    }

    private fun categoryTxRows(input: FlowInput, r: Resolved, id: CategoryId): List<SheetRowUi> =
        counted(input, r.scope.months).filter { it.categoryId == id }
            .sortedByDescending { it.transactionDate }.take(6)
            .map { t ->
                val k = kindOf(input, t.categoryId)
                val amount = if (k == CategoryKind.INCOME) t.amount.ore else -t.amount.ore
                SheetRowUi(text(payee(t)), text(PeriodFormatter.iso(t.transactionDate, locale)), kr(amount), null, k.tone())
            }

    private fun categorySheet(input: FlowInput, r: Resolved, id: CategoryId): NodeSheetUi? {
        val a = r.a
        val kind = kindOf(input, id)
        val (v, group, eyebrowRes, shareRes) = when (kind) {
            CategoryKind.INCOME -> Quad(a.incomeByCategory[id], a.income, R.string.key_income, R.string.share_of_income)
            CategoryKind.SAVINGS -> Quad(a.savingsByCategory[id], a.savings, R.string.node_savings, R.string.share_of_savings)
            CategoryKind.FIXED -> Quad(a.expensesByCategory[id], a.fixedExpenses, R.string.flow_fixed, R.string.share_of_fixed)
            CategoryKind.VARIABLE -> Quad(a.expensesByCategory[id], a.variableExpenses, R.string.flow_variable, R.string.share_of_variable)
            else -> Quad(a.expensesByCategory[id], a.expenses, R.string.key_expenses, R.string.share_of_expenses)
        }
        val value = v?.ore ?: return null
        val gain = kind == CategoryKind.INCOME || kind == CategoryKind.SAVINGS
        val avg = (valuesOf(if (kind == CategoryKind.INCOME) FlowTab.INCOME else if (kind == CategoryKind.SAVINGS) FlowTab.SAVINGS else FlowTab.EXPENSE, r.a12)[id]?.ore ?: 0L) / r.months12.size.coerceAtLeast(1)
        return NodeSheetUi(
            eyebrow = res(R.string.flow_summary_label, res(eyebrowRes), r.label),
            title = nameOf(input, id),
            figure = kr(if (gain) value else -value),
            description = null,
            shareALabel = res(shareRes),
            shareA = share(value, group.ore),
            shareBLabel = res(R.string.sub_avg_12_label),
            shareB = kr(avg),
            bar = if (group.ore > 0) (value.toFloat() / group.ore).coerceIn(0f, 1f) else 0f,
            barTone = kind.tone(),
            rows = categoryTxRows(input, r, id),
            cta = res(R.string.cta_show_all_events),
            action = SheetCta.Events(id.value),
        )
    }

    private data class Quad(val a: Money?, val b: Money, val c: Int, val d: Int)

    // ───────────────────────────── Picker ─────────────────────────────

    private fun picker(data: FlowData, sel: FlowSelection, r: Resolved): PickerUi {
        val months = data.periods.toSet()
        val year = sel.pickerYear?.takeIf { it in data.years } ?: r.scope.last.year
        val selectedYear = if (sel.mode == PeriodMode.YEAR) r.scope.last.year else year
        return PickerUi(
            years = if (sel.mode == PeriodMode.ALL) emptyList() else data.years.map { PickerChipUi(it.toString(), it.toString(), it == selectedYear) },
            months = if (sel.mode != PeriodMode.MONTH) {
                emptyList()
            } else {
                (1..12).map { m ->
                    val p = Period(year, m)
                    val on = p in months
                    PickerChipUi(p.toString(), monthShort(p), sel.mode == PeriodMode.MONTH && p == r.scope.last, on, if (on) PeriodFormatter.monthLong(p, locale) else PeriodFormatter.monthLong(p, locale) + NO_DATA_SUFFIX.getValue(locale))
                }
            },
            allText = if (sel.mode == PeriodMode.ALL) res(R.string.picker_all_text, monthLabel(data.periods.first())) else null,
        )
    }

    // ───────────────────────────── Helpers ─────────────────────────────

    private fun kindOf(input: FlowInput, id: CategoryId) = input.categories[id]?.kind ?: CategoryKind.UNCATEGORIZED
    private fun nameOf(input: FlowInput, id: CategoryId): UiText = categoryName(id, input.categories[id]?.name ?: id.value)
    private fun payee(t: Transaction) = t.normalizedText.ifBlank { t.rawText }

    private fun FlowTab.tone() = when (this) {
        FlowTab.INCOME -> FlowTone.Income
        FlowTab.EXPENSE -> FlowTone.Fixed
        FlowTab.SAVINGS -> FlowTone.Savings
        FlowTab.CASHFLOW -> FlowTone.Neutral
    }

    companion object {
        const val INCOME = "inkomst"
        const val FROM_ACCOUNT = "fran_konto"
        const val EXPENSES = "utg"
        const val FIXED = "fasta"
        const val VARIABLE = "rorliga"
        const val UNCAT = "ej_kat"
        const val SAVINGS = "spar"
        const val OTHER_SAVINGS = "ovrigt_spar"
        const val LEFT = "kvar"
        val SALARY = CategoryId("lon")
        const val SAVINGS_GOAL_PERCENT = 10
        const val BUFFER_TARGET_MONTHS = 6
        private val TIERS = listOf(1f, 0.78f, 0.6f, 0.45f, 0.32f, 0.22f)
        private val NO_DATA_SUFFIX = mapOf(AppLocale.SV to ", ingen data", AppLocale.EN to ", no data")
    }
}
