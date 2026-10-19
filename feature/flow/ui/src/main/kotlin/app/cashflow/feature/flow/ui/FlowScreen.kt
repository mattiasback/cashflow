package app.cashflow.feature.flow.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cashflow.core.designsystem.component.AccentHeader
import app.cashflow.core.designsystem.component.AccentTopBar
import app.cashflow.core.designsystem.component.BandDropdown
import app.cashflow.core.designsystem.component.BandIconButton
import app.cashflow.core.designsystem.component.BandSegmented
import app.cashflow.core.designsystem.component.BarChart
import app.cashflow.core.designsystem.component.BarGroup
import app.cashflow.core.designsystem.component.BarSegment
import app.cashflow.core.designsystem.component.CategoryBar
import app.cashflow.core.designsystem.component.CfButton
import app.cashflow.core.designsystem.component.CfButtonVariant
import app.cashflow.core.designsystem.component.CfCard
import app.cashflow.core.designsystem.component.DonutChart
import app.cashflow.core.designsystem.component.DonutSegment
import app.cashflow.core.designsystem.component.FlowTabRow
import app.cashflow.core.designsystem.component.LineChart
import app.cashflow.core.designsystem.component.LineSeries
import app.cashflow.core.designsystem.component.SankeyChart
import app.cashflow.core.designsystem.component.SankeyLinkUi
import app.cashflow.core.designsystem.component.SankeyNodeUi
import app.cashflow.core.designsystem.component.Segment
import app.cashflow.core.designsystem.component.SkeletonBlock
import app.cashflow.core.designsystem.component.StatTile
import app.cashflow.core.designsystem.component.SummaryCard
import app.cashflow.core.designsystem.component.TabItem
import app.cashflow.core.designsystem.icon.CfIcons
import app.cashflow.core.designsystem.theme.Accent
import app.cashflow.core.designsystem.theme.CashFlowTheme
import app.cashflow.core.designsystem.theme.Radius
import app.cashflow.core.designsystem.theme.Spacing
import app.cashflow.core.domain.cashflow.PeriodMode
import app.cashflow.core.ui.FlowTone
import app.cashflow.core.ui.ObserveAsEvents
import app.cashflow.core.ui.UiText
import app.cashflow.core.ui.colors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun FlowRoot(
    contentPadding: PaddingValues,
    onOpenEvents: (categoryId: String?, period: String?) -> Unit,
    onOpenImport: () -> Unit,
    onOpenTransaction: (String) -> Unit = {},
    onOpenProfile: () -> Unit = {},
    scrollToTop: Flow<Unit> = emptyFlow(),
    viewModel: FlowViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is FlowEvent.OpenEvents -> onOpenEvents(event.categoryId, event.period)
            FlowEvent.OpenImport -> onOpenImport()
            is FlowEvent.OpenTransaction -> onOpenTransaction(event.id)
            FlowEvent.OpenProfile -> onOpenProfile()
        }
    }
    FlowScreen(state, viewModel::onAction, contentPadding, scrollToTop)
    FlowSheets(state, viewModel::onAction)
}

/** Each sub-tab has its own band: Kassaflöde teal, Inkomster blue, Utgifter orange, Sparande violet. */
fun FlowTab.accent(): Accent = when (this) {
    FlowTab.CASHFLOW -> Accent.Cashflow
    FlowTab.INCOME -> Accent.Home
    FlowTab.EXPENSE -> Accent.Goals
    FlowTab.SAVINGS -> Accent.Savings
}

@Composable
internal fun FlowTab.label(): String = stringResource(
    when (this) {
        FlowTab.CASHFLOW -> R.string.tab_cashflow
        FlowTab.INCOME -> R.string.tab_income
        FlowTab.EXPENSE -> R.string.tab_expense
        FlowTab.SAVINGS -> R.string.tab_savings
    },
)

@Composable
private fun FlowTab.ink() = with(CashFlowTheme.colors) {
    when (this@ink) {
        FlowTab.CASHFLOW -> accentCashflowInk
        FlowTab.INCOME -> accentHomeInk
        FlowTab.EXPENSE -> accentGoalsInk
        FlowTab.SAVINGS -> accentSavingsInk
    }
}

@Composable
fun FlowScreen(
    state: FlowState,
    onAction: (FlowAction) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    scrollToTop: Flow<Unit> = emptyFlow(),
) {
    val c = CashFlowTheme.colors
    val listState = rememberLazyListState()
    ObserveAsEvents(scrollToTop) { listState.requestScrollToItem(0) }
    Box(Modifier.fillMaxSize().background(c.bgCanvas)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + Spacing.s4),
        ) {
            item(key = "header") { Header(state, onAction) }
            if (!state.hasData) {
                if (state.isLoading) item(key = "skeleton") { Skeleton() }
                return@LazyColumn
            }
            item(key = "tabs") {
                val tabs = FlowTab.entries.map { TabItem(it.key, it.label(), it.ink()) }
                FlowTabRow(
                    tabs = tabs,
                    selectedKey = state.tab.key,
                    onSelect = { key -> onAction(FlowAction.OnTabSelect(FlowTab.of(key))) },
                    modifier = Modifier.padding(horizontal = Spacing.gutter).padding(top = Spacing.s4),
                )
            }
            item(key = "tiles") { Tiles(state) }
            section("over-time", R.string.section_over_time)
            item(key = "chart") { ChartCard(state, onAction) }
            if (state.tab == FlowTab.CASHFLOW) {
                state.donut?.let { donut ->
                    section("distribution", R.string.section_distribution)
                    item(key = "donut") { DonutCard(donut, onAction) }
                }
                state.sankey?.let { sankey ->
                    section("flow", R.string.section_flow)
                    item(key = "sankey") { SankeyCard(sankey, onAction) }
                }
            } else {
                section("bars", R.string.section_by_category, trailing = state.barsTotal)
                item(key = "bars") { CategoryBars(state, onAction) }
                if (state.latest.isNotEmpty()) {
                    section("latest", R.string.section_latest)
                    item(key = "latest") { Latest(state, onAction) }
                }
            }
        }
        // The controls stay at the top while the hero and the rest scroll under them.
        AccentTopBar(
            accent = state.tab.accent(),
            leading = { BandIconButton(CfIcons.User, stringResource(R.string.cd_profile), { onAction(FlowAction.OnProfileClick) }) },
            actions = { BarActions(state, onAction) },
        )
    }
}

// ───────────────────────────── Header ─────────────────────────────

@Composable
private fun Header(state: FlowState, onAction: (FlowAction) -> Unit) {
    AccentHeader(accent = state.tab.accent(), pinnedBar = true) {
        Hero(state, onAction)
    }
}

@Composable
private fun RowScope.BarActions(state: FlowState, onAction: (FlowAction) -> Unit) {
    if (state.hasData) {
        // When the band cannot fit pill, switch and profile (large text, narrow phones, longer
        // English labels) the switch moves into the period sheet (it is there already) and the
        // pill stays, reading "Alla" in Alla. The pill is never cut.
        val compact = !bandFits(state)
        val pill = state.periodPill ?: if (compact) stringResource(R.string.mode_all) else null
        if (pill != null) {
            BandDropdown(pill, stringResource(R.string.cd_period_pick, pill), { onAction(FlowAction.OnPickerOpen) })
        }
        if (!compact) BandSegmented(
            segments = listOf(
                Segment(PeriodMode.MONTH.name, stringResource(R.string.mode_month)),
                Segment(PeriodMode.YEAR.name, stringResource(R.string.mode_year)),
                Segment(PeriodMode.ALL.name, stringResource(R.string.mode_all)),
            ),
            selectedKey = state.mode.name,
            onSelect = { onAction(FlowAction.OnModeSelect(PeriodMode.valueOf(it))) },
            contentDescription = stringResource(R.string.cd_mode),
        )
    }
}

// Measures the pill, the three mode labels and the profile button against the window width.
@Composable
private fun bandFits(state: FlowState): Boolean {
    val measurer = rememberTextMeasurer()
    val style = CashFlowTheme.typography.bodyStrong
    val density = LocalDensity.current
    val width = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
    val labels = listOf(stringResource(R.string.mode_month), stringResource(R.string.mode_year), stringResource(R.string.mode_all))
    val pill = state.periodPill ?: ""
    val needed = with(density) {
        val text = { s: String -> measurer.measure(s, style).size.width.toDp() }
        val pillW = if (pill.isEmpty()) 0.dp else text(pill) + 12.dp + 8.dp + 18.dp + 4.dp
        val switchW = labels.sumOf { (text(it) + 16.dp).value.toDouble() }.dp + 8.dp
        // Side padding 2×12, profile 48, gaps 2×8.
        pillW + switchW + 48.dp + 24.dp + 16.dp
    }
    return needed <= width
}

@Composable
private fun Hero(state: FlowState, onAction: (FlowAction) -> Unit) {
    val c = CashFlowTheme.colors
    if (state.hasData) {
        val label = state.summaryLabel.asString()
        val delta = state.delta?.asString()
        val infoTitle = (if (state.tab == FlowTab.CASHFLOW) stringResource(R.string.flow_net_title) else state.tab.label()).lowercase()
        SummaryCard(
            label = label,
            value = state.summaryValue,
            delta = delta,
            deltaPositive = state.deltaPositive,
            accessibilityText = stringResource(R.string.cd_summary, label, state.summarySpoken, delta.orEmpty()),
            onInfo = { onAction(FlowAction.OnInfoClick) },
            infoLabel = stringResource(R.string.cd_info, infoTitle),
            rollDigits = true,
        )
    } else if (!state.isLoading) {
        CfCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                Text(stringResource(R.string.flow_empty_title), style = CashFlowTheme.typography.title, color = c.textPrimary)
                Text(stringResource(R.string.flow_empty_body), style = CashFlowTheme.typography.body, color = c.textSecondary)
                CfButton(stringResource(R.string.flow_empty_button), { onAction(FlowAction.OnImportClick) }, Modifier.fillMaxWidth())
            }
        }
    }
}

private fun LazyListScope.section(key: String, title: Int, trailing: String? = null) {
    item(key = "section-$key") {
        val c = CashFlowTheme.colors
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter).padding(top = Spacing.s6, bottom = Spacing.s3),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                stringResource(title),
                style = CashFlowTheme.typography.titleL,
                color = c.textPrimary,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (trailing != null) Text(trailing, style = CashFlowTheme.typography.label.copy(fontFeatureSettings = "tnum"), color = c.textSecondary)
        }
    }
}

// ───────────────────────────── Tiles ─────────────────────────────

@Composable
private fun Tiles(state: FlowState) {
    val ink = state.tab.ink()
    AnimatedContent(
        targetState = state.tiles,
        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
        contentKey = { state.tab },
        label = "tiles",
    ) { tiles ->
        Column(
            Modifier.padding(horizontal = Spacing.gutter).padding(top = Spacing.s4),
            verticalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            // From 1.5× font scale the tiles stack in one column (design system scaling rule).
            val perRow = if (LocalDensity.current.fontScale >= 1.5f) 1 else 2
            tiles.chunked(perRow).forEach { row ->
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                    row.forEach { t ->
                        val label = t.label.asString()
                        val value = t.value.asString()
                        val sub = t.sub?.asString()
                        StatTile(
                            label = label,
                            value = value,
                            sub = sub,
                            accessibilityText = "$label: $value" + (sub?.let { ", $it" } ?: ""),
                            goal = t.goal,
                            goalColor = ink,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

// ───────────────────────────── Chart ─────────────────────────────

@Composable
private fun ChartCard(state: FlowState, onAction: (FlowAction) -> Unit) {
    val c = CashFlowTheme.colors
    CfCard(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter), contentPadding = PaddingValues(start = Spacing.s4, end = Spacing.s4, top = Spacing.s4, bottom = Spacing.s2)) {
        Column {
            Column(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                Text(state.chartTitle, style = CashFlowTheme.typography.bodyStrong, color = c.textPrimary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s4), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    state.chartKeys.forEach { k ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (k.tone != null) Box(Modifier.size(8.dp).clip(Radius.pill).background(k.tone.colors().strong))
                            Text(k.label.asString(), style = CashFlowTheme.typography.label, color = c.textSecondary, maxLines = 1)
                            Text(
                                k.amount,
                                style = CashFlowTheme.typography.label.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                                color = if (k.negative) c.accentGoalsInk else c.textPrimary,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(Spacing.s3))
            val line = state.line
            if (line != null) {
                LineChart(
                    series = line.series.map { (tone, values) -> LineSeries(values, tone.colors().strong, area = line.area) },
                    selectedIndex = line.selectedIndex,
                    onSelect = { onAction(FlowAction.OnScrub(it)) },
                    ticks = line.ticks,
                    spokenValue = stringResource(R.string.cd_line, state.lineSpoken),
                    overspend = line.overspend,
                )
            } else {
                val groups = state.barGroups.map { g ->
                    BarGroup(
                        key = g.key,
                        label = g.label,
                        mark = g.mark,
                        bars = g.bars.map { stack -> stack.map { BarSegment(it.value, it.tone.colors().strong) } },
                        deficit = g.deficit,
                        spoken = g.spoken.asString(),
                    )
                }
                // Key the chart on tab and mode so a switch replays the rise-in.
                androidx.compose.runtime.key(state.tab, state.mode) {
                    BarChart(
                        groups = groups,
                        selectedKey = state.selectedBar,
                        onSelect = { onAction(FlowAction.OnPeriodSelect(it)) },
                        average = state.average,
                        averageLabel = stringResource(R.string.avg_label),
                    )
                }
            }
        }
    }
}

// ───────────────────────────── Donut ─────────────────────────────

@Composable
private fun DonutCard(donut: DonutUi, onAction: (FlowAction) -> Unit) {
    val c = CashFlowTheme.colors
    CfCard(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s4)) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                DonutChart(
                    segments = donut.segments.map { s -> DonutSegment(s.id, s.value, s.tone.colors().strong.copy(alpha = s.alpha)) },
                    centerLabel = donut.centerLabel.asString(),
                    centerValue = donut.centerValue,
                    onSegmentClick = { onAction(FlowAction.OnDonutSegment(it)) },
                    contentDescription = stringResource(R.string.cd_donut),
                )
                if (donut.parentLabel != null) {
                    val back = stringResource(R.string.donut_back, stringResource(R.string.donut_top))
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .size(48.dp)
                            .clip(Radius.pill)
                            .clickable(role = Role.Button, onClickLabel = back) { onAction(FlowAction.OnDonutBack) }
                            .semantics { contentDescription = back },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(40.dp).clip(Radius.pill).background(c.surfaceControl), contentAlignment = Alignment.Center) {
                            Icon(CfIcons.ChevronLeft, contentDescription = null, tint = c.textPrimary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
            // Two-column legend; each row is a tap target that does what the segment does.
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                donut.segments.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s4)) {
                        pair.forEach { s -> LegendRow(s, Modifier.weight(1f)) { onAction(FlowAction.OnDonutSegment(s.id)) } }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            donut.deficitText?.let { note ->
                Text(
                    note.asString(),
                    style = CashFlowTheme.typography.body,
                    color = c.textPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(Radius.sm)
                        .background(c.vizFixed.copy(alpha = 0.12f))
                        .clickable(role = Role.Button) { onAction(FlowAction.OnNodeClick("deficit")) }
                        .padding(Spacing.s3),
                )
            }
            if (donut.parentLabel != null && donut.parentId != null) {
                CfButton(
                    text = stringResource(R.string.donut_details, donut.parentLabel.asString().lowercase()),
                    onClick = { onAction(FlowAction.OnNodeClick(donut.parentId)) },
                    variant = CfButtonVariant.Outline,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun LegendRow(s: DonutSegmentUi, modifier: Modifier, onClick: () -> Unit) {
    val c = CashFlowTheme.colors
    val spoken = s.spoken.asString()
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clip(Radius.sm)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = spoken }
            .padding(horizontal = Spacing.s1, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Box(Modifier.size(10.dp).clip(Radius.xs).background(s.tone.colors().strong.copy(alpha = s.alpha)))
        Column(Modifier.weight(1f)) {
            Text(s.label.asString(), style = CashFlowTheme.typography.bodyStrong, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(s.amount, style = CashFlowTheme.typography.label.copy(fontFeatureSettings = "tnum"), color = c.textSecondary, maxLines = 1)
        }
        Text(s.share, style = CashFlowTheme.typography.bodyStrong.copy(fontFeatureSettings = "tnum"), color = c.textPrimary, maxLines = 1)
    }
}

// ───────────────────────────── Sankey ─────────────────────────────

@Composable
private fun SankeyCard(sankey: SankeyUi, onAction: (FlowAction) -> Unit) {
    CfCard(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter)) {
        SankeyChart(
            nodes = sankey.nodes.map { n -> SankeyNodeUi(n.id, n.label.asString(), n.amount, n.tone.colors().strong, n.tone.colors().fill, n.column) },
            links = sankey.links.map { (s, t, v) -> SankeyLinkUi(s, t, v) },
            onNodeClick = { onAction(FlowAction.OnNodeClick(it)) },
            contentDescription = sankey.spoken.asString(),
        )
    }
}

// ───────────────────────────── Category bars and latest ─────────────────────────────

@Composable
private fun CategoryBars(state: FlowState, onAction: (FlowAction) -> Unit) {
    CfCard(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter), contentPadding = PaddingValues(Spacing.s2)) {
        Column {
            state.categoryBars.forEach { bar ->
                val name = bar.name.asString()
                val base = stringResource(R.string.cd_category_bar, name, bar.spokenAmount, bar.share)
                val trend = bar.trend?.let { t -> ". " + stringResource(R.string.cd_category_trend, t, stringResource(if (bar.trendBetter) R.string.trend_better else R.string.trend_worse)) }
                // Keyed by tab and category so a tab switch regrows the bars.
                androidx.compose.runtime.key(state.tab, bar.id) {
                    CategoryBar(
                        name = name,
                        amount = bar.amount,
                        share = bar.share,
                        fraction = bar.fraction,
                        color = bar.tone.colors().strong,
                        accessibilityText = base + trend.orEmpty(),
                        trend = bar.trend,
                        trendBetter = bar.trendBetter,
                        onClick = { onAction(FlowAction.OnCategoryClick(bar.id)) },
                    )
                }
            }
            if (state.tab == FlowTab.EXPENSE) {
                val c = CashFlowTheme.colors
                Row(Modifier.padding(Spacing.s2), horizontalArrangement = Arrangement.spacedBy(Spacing.s4)) {
                    listOf(FlowTone.Fixed to R.string.flow_fixed, FlowTone.Variable to R.string.flow_variable, FlowTone.Neutral to R.string.flow_uncat).forEach { (tone, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(8.dp).clip(Radius.xs).background(tone.colors().strong))
                            Text(stringResource(label), style = CashFlowTheme.typography.label, color = c.textSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Latest(state: FlowState, onAction: (FlowAction) -> Unit) {
    val c = CashFlowTheme.colors
    Column(Modifier.padding(horizontal = Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
        CfCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.s1)) {
            Column {
                state.latest.forEachIndexed { i, t ->
                    if (i > 0) Box(Modifier.fillMaxWidth().padding(start = 68.dp).height(1.dp).background(c.divider))
                    val sub = t.sub.asString()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .clickable(role = Role.Button) { onAction(FlowAction.OnTransactionClick(t.id)) }
                            .semantics(mergeDescendants = true) { contentDescription = "${t.title}, $sub, ${t.amount}" }
                            .padding(horizontal = Spacing.s4, vertical = Spacing.s2),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(40.dp).clip(Radius.pill).background(t.tone.colors().fill), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(10.dp).clip(Radius.xs).background(t.tone.colors().strong))
                        }
                        Spacer(Modifier.width(Spacing.s3))
                        Column(Modifier.weight(1f)) {
                            Text(t.title, style = CashFlowTheme.typography.bodyStrong, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(sub, style = CashFlowTheme.typography.label, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.width(Spacing.s2))
                        Text(t.amount, style = CashFlowTheme.typography.bodyStrong.copy(fontFeatureSettings = "tnum"), color = c.textPrimary, maxLines = 1)
                    }
                }
            }
        }
        CfButton(stringResource(R.string.show_all_events), { onAction(FlowAction.OnShowAllEvents) }, Modifier.fillMaxWidth(), variant = CfButtonVariant.Outline)
    }
}

@Composable
private fun Skeleton() {
    Column(Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                SkeletonBlock(Modifier.weight(1f).height(118.dp))
                SkeletonBlock(Modifier.weight(1f).height(118.dp))
            }
        }
        SkeletonBlock(Modifier.fillMaxWidth().height(220.dp))
    }
}

@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun FlowScreenPreview() {
    CashFlowTheme {
        FlowScreen(PreviewData.state(), onAction = {})
    }
}

@Preview(showBackground = true, heightDp = 1400, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FlowScreenDarkPreview() {
    CashFlowTheme(darkTheme = true) {
        FlowScreen(PreviewData.state(FlowTab.EXPENSE), onAction = {})
    }
}

/** Fictional figures for previews and screenshot tests. */
internal object PreviewData {
    fun state(tab: FlowTab = FlowTab.CASHFLOW): FlowState {
        fun t(s: String) = UiText.DynamicString(s)
        val months = listOf("maj", "jun", "jul", "aug", "sep", "okt")
        return FlowState(
            tab = tab, mode = PeriodMode.MONTH, periodPill = "okt 2026",
            summaryLabel = t("Nettokassaflöde · okt 2026"), summaryValue = "+8 450 kr", delta = t("+1 830 kr mot sep 2026"),
            tiles = listOf(
                TileUi(t("Snitt per månad"), t("+6 120 kr"), t("senaste 12 mån")),
                TileUi(t("Sparat av kassaflödet"), t("71 %"), null, goal = 0.71f),
                TileUi(t("Månader med underskott"), t("2"), t("senaste 12 mån")),
                TileUi(t("Bästa månaden"), t("+14 300 kr"), t("jun 2026")),
            ),
            chartTitle = "okt 2026",
            chartKeys = listOf(ChartKeyUi(t("Inkomst"), "48 200 kr", FlowTone.Income), ChartKeyUi(t("Utgifter"), "39 750 kr", FlowTone.Fixed), ChartKeyUi(t("Netto"), "+8 450 kr", null)),
            barGroups = months.mapIndexed { i, m ->
                BarGroupUi("2026-${i + 5}", m, if (i == 0) "2026" else null, listOf(listOf(BarSegmentUi(46000f + i * 400, FlowTone.Income)), listOf(BarSegmentUi(38000f + (i % 3) * 3000, FlowTone.Fixed))), i == 2, t(m))
            },
            selectedBar = "2026-10",
            donut = DonutUi(
                listOf(
                    DonutSegmentUi("utg", t("Utgifter"), "31 750 kr", "66 %", 31750f, FlowTone.Fixed, 1f, false, t("")),
                    DonutSegmentUi("spar", t("Sparande"), "8 000 kr", "17 %", 8000f, FlowTone.Savings, 1f, false, t("")),
                    DonutSegmentUi("kvar", t("Kvar på konto"), "8 450 kr", "17 %", 8450f, FlowTone.Neutral, 1f, true, t("")),
                ),
                t("Inkomst"), "48 200 kr", null, null, null,
            ),
            categoryBars = listOf(
                CategoryBarUi("boende", t("Boende"), "12 200 kr", "38 %", 1f, FlowTone.Fixed, "", trend = "↓ 4 %", trendBetter = true),
                CategoryBarUi("mat", t("Mat"), "8 400 kr", "26 %", 0.69f, FlowTone.Variable, "", trend = "↑ 12 %", trendBetter = false),
            ),
            latest = listOf(LatestTxUi("1", "Matbutiken", t("2026-10-14 · Mat"), "642 kr", FlowTone.Variable)),
            hasData = true, isLoading = false,
        )
    }
}
