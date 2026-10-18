package app.cashflow.core.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cashflow.core.designsystem.theme.CashFlowTheme
import app.cashflow.core.designsystem.theme.Radius
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Progress of a chart's entry animation. Previews and screenshot tests start finished, since
 * they do not run the animation.
 */
@Composable
private fun rememberEntry(key: Any? = Unit): Animatable<Float, androidx.compose.animation.core.AnimationVector1D> {
    val inspecting = LocalInspectionMode.current
    return remember(key) { Animatable(if (inspecting) 1f else 0f) }
}

// ───────────────────────────── Sub-tabs ─────────────────────────────

@Immutable
data class TabItem(val key: String, val label: String, val ink: Color)

/** Text tabs under a hairline (cf-tabs); the selected tab is underlined in its own accent ink. */
@Composable
fun FlowTabRow(tabs: List<TabItem>, selectedKey: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = CashFlowTheme.colors
    Box(modifier.fillMaxWidth()) {
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(1.dp).background(c.divider))
        Row(Modifier.fillMaxWidth()) {
            tabs.forEach { tab ->
                val selected = tab.key == selectedKey
                val underline by animateFloatAsState(if (selected) 1f else 0f, tween(200), label = "tab")
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(tab.key) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        tab.label,
                        style = CashFlowTheme.typography.bodyStrong,
                        color = if (selected) c.textPrimary else c.textSecondary,
                        maxLines = 1,
                    )
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.dp)
                            .graphicsLayer { alpha = underline }
                            .background(tab.ink),
                    )
                }
            }
        }
    }
}

// ───────────────────────────── Bar chart ─────────────────────────────

@Immutable
data class BarSegment(val value: Float, val color: Color)

/** One period: up to three bars (each a stack of segments), its labels and whether it ran a deficit. */
@Immutable
data class BarGroup(
    val key: String,
    val label: String,
    val mark: String?,
    val bars: List<List<BarSegment>>,
    val deficit: Boolean,
    val spoken: String,
)

/**
 * The swipeable bar chart: newest period on the right, drag sideways with fling and snap. The
 * selected period is lifted, the others are faded, a tint marks deficit periods and a dashed
 * line shows the average. Bars rise in sequence on first show.
 */
@Composable
fun BarChart(
    groups: List<BarGroup>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    average: Float? = null,
    averageLabel: String? = null,
) {
    val c = CashFlowTheme.colors
    val plot = 168.dp
    val maxValue = remember(groups) { groups.maxOfOrNull { g -> g.bars.maxOfOrNull { b -> b.sumOf { it.value.toDouble() }.toFloat() } ?: 0f }?.coerceAtLeast(1f) ?: 1f }
    val multi = groups.any { it.bars.size > 1 }
    val groupWidth = if (multi) 44.dp else 34.dp
    val barWidth = if (multi) 12.dp else 18.dp
    // Newest first: with reverseLayout the first item sits at the right edge.
    val ordered = remember(groups) { groups.asReversed() }
    val listState = rememberLazyListState()
    val rise = rememberEntry()
    LaunchedEffect(Unit) { rise.animateTo(1f, tween(600)) }
    LaunchedEffect(selectedKey, ordered.size) {
        val i = ordered.indexOfFirst { it.key == selectedKey }
        if (i >= 0) listState.animateScrollToItem(maxOf(0, i - 3))
    }
    Box(modifier.fillMaxWidth().height(plot + 40.dp)) {
        Canvas(Modifier.fillMaxWidth().height(plot)) {
            drawLine(c.chartBaseline, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            if (average != null && average > 0f) {
                val y = size.height - (average / maxValue).coerceIn(0f, 1f) * (size.height - 8.dp.toPx())
                drawLine(
                    c.textSecondary, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                )
            }
        }
        if (average != null && averageLabel != null && average > 0f) {
            Text(
                averageLabel,
                style = CashFlowTheme.typography.caption,
                color = c.textSecondary,
                modifier = Modifier.align(Alignment.TopEnd).clip(Radius.xs).background(c.surfaceCard).padding(horizontal = 4.dp),
            )
        }
        LazyRow(
            state = listState,
            reverseLayout = true,
            flingBehavior = rememberSnapFlingBehavior(listState),
            horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.End),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(ordered, key = { _, g -> g.key }) { index, group ->
                val selected = group.key == selectedKey
                val alpha by animateFloatAsState(if (selected || selectedKey == null) 1f else 0.45f, tween(250), label = "bar")
                Column(
                    Modifier
                        .width(groupWidth)
                        .clip(Radius.sm)
                        .background(if (selected) c.textPrimary.copy(alpha = 0.06f) else Color.Transparent)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(group.key) })
                        .semantics(mergeDescendants = true) { contentDescription = group.spoken },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Canvas(Modifier.fillMaxWidth().height(plot)) {
                        if (group.deficit) drawRect(c.vizFixed.copy(alpha = 0.14f))
                        val bw = barWidth.toPx()
                        val gap = 3.dp.toPx()
                        val n = group.bars.size
                        val total = n * bw + (n - 1) * gap
                        var x = (size.width - total) / 2
                        // Bars rise one after another, oldest last.
                        val delay = (index * 0.04f).coerceAtMost(0.5f)
                        val grow = ((rise.value - delay) / (1f - delay)).coerceIn(0f, 1f)
                        group.bars.forEach { stack ->
                            var y = size.height
                            stack.forEach { seg ->
                                val h = seg.value / maxValue * (size.height - 8.dp.toPx()) * grow
                                if (h > 0f) {
                                    drawRect(seg.color.copy(alpha = seg.color.alpha * alpha), Offset(x, y - h), Size(bw, h))
                                    y -= h
                                }
                            }
                            x += bw + gap
                        }
                    }
                    Text(
                        group.label,
                        style = CashFlowTheme.typography.caption.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
                        color = if (selected) c.textPrimary else c.textSecondary,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (group.mark != null) Text(group.mark, style = CashFlowTheme.typography.caption.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold), color = c.textSecondary)
                        if (group.deficit) Box(Modifier.size(5.dp).clip(Radius.pill).background(c.vizFixed))
                    }
                }
            }
        }
    }
}

// ───────────────────────────── Line chart ─────────────────────────────

@Immutable
data class LineSeries(val values: List<Float>, val color: Color, val area: Boolean = false)

/**
 * The "Alla" line chart: draws in from the left; drag or tap to scrub one month, with a
 * haptic tick per month. For TalkBack it is a slider with step actions.
 */
@Composable
fun LineChart(
    series: List<LineSeries>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    ticks: List<Pair<Int, String>>,
    spokenValue: String,
    modifier: Modifier = Modifier,
    overspend: Boolean = false,
) {
    val c = CashFlowTheme.colors
    val count = series.firstOrNull()?.values?.size ?: 0
    if (count == 0) return
    val maxValue = remember(series) { series.maxOf { s -> s.values.maxOrNull() ?: 0f }.coerceAtLeast(1f) }
    val draw = rememberEntry()
    LaunchedEffect(Unit) { draw.animateTo(1f, tween(700)) }
    val haptics = LocalHapticFeedback.current
    val currentSelect by rememberUpdatedState(onSelect)
    val currentIndex by rememberUpdatedState(selectedIndex)
    val measurer = rememberTextMeasurer()
    val tickStyle = TextStyle(fontSize = 11.sp, color = c.textSecondary)
    fun indexAt(x: Float, width: Float) = (x / width * count).toInt().coerceIn(0, count - 1)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(170.dp)
            .semantics {
                contentDescription = spokenValue
                stateDescription = spokenValue
                progressBarRangeInfo = ProgressBarRangeInfo(selectedIndex.toFloat(), 0f..(count - 1).toFloat(), steps = (count - 2).coerceAtLeast(0))
                setProgress { v -> currentSelect(v.roundToInt().coerceIn(0, count - 1)); true }
            }
            .pointerInput(count) {
                detectTapGestures { p -> currentSelect(indexAt(p.x, size.width.toFloat())) }
            }
            .pointerInput(count) {
                detectDragGestures { change, _ ->
                    val i = indexAt(change.position.x, size.width.toFloat())
                    if (i != currentIndex) {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        currentSelect(i)
                    }
                }
            },
    ) {
        val chartH = size.height - 22.dp.toPx()
        val top = 8.dp.toPx()
        fun x(i: Int) = (i + 0.5f) * size.width / count
        fun y(v: Float) = chartH - (v / maxValue).coerceIn(0f, 1f) * (chartH - top)
        drawLine(c.divider, Offset(0f, chartH), Offset(size.width, chartH), strokeWidth = 1.dp.toPx())
        clipRect(right = size.width * draw.value) {
            if (overspend && series.size >= 2) {
                // Shade where the second series (spending) runs above the first (income), with the
                // crossings interpolated so the band starts and ends exactly where the lines meet.
                val a = series[0].values
                val b = series[1].values
                val band = Path()
                for (i in 0 until count - 1) {
                    val d0 = b[i] - a[i]
                    val d1 = b[i + 1] - a[i + 1]
                    if (d0 <= 0f && d1 <= 0f) continue
                    // The part of segment i→i+1 where spending is higher, as fractions 0..1.
                    val t = if (d0 != d1) d0 / (d0 - d1) else 0f
                    val from = if (d0 > 0f) 0f else t
                    val to = if (d1 > 0f) 1f else t
                    fun xi(f: Float) = x(i) + (x(i + 1) - x(i)) * f
                    fun av(f: Float) = a[i] + (a[i + 1] - a[i]) * f
                    fun bv(f: Float) = b[i] + (b[i + 1] - b[i]) * f
                    band.moveTo(xi(from), y(bv(from)))
                    band.lineTo(xi(to), y(bv(to)))
                    band.lineTo(xi(to), y(av(to)))
                    band.lineTo(xi(from), y(av(from)))
                    band.close()
                }
                drawPath(band, c.vizFixed.copy(alpha = 0.3f))
            }
            series.forEach { s ->
                val path = Path()
                s.values.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
                if (s.area) {
                    val area = Path().apply {
                        addPath(path)
                        lineTo(x(count - 1), chartH)
                        lineTo(x(0), chartH)
                        close()
                    }
                    drawPath(area, s.color.copy(alpha = 0.14f))
                }
                drawPath(path, s.color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        val sx = x(selectedIndex.coerceIn(0, count - 1))
        drawLine(c.textSecondary, Offset(sx, top / 2), Offset(sx, chartH), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())))
        series.forEach { s ->
            val sy = y(s.values[selectedIndex.coerceIn(0, count - 1)])
            drawCircle(c.surfaceCard, 4.dp.toPx(), Offset(sx, sy))
            drawCircle(s.color, 4.dp.toPx(), Offset(sx, sy), style = Stroke(2.5.dp.toPx()))
        }
        ticks.forEach { (i, label) ->
            val layout = measurer.measure(label, tickStyle)
            drawText(layout, topLeft = Offset((x(i) - 2.dp.toPx()).coerceIn(0f, size.width - layout.size.width), chartH + 4.dp.toPx()))
        }
    }
}

// ───────────────────────────── Donut ─────────────────────────────

@Immutable
data class DonutSegment(val key: String, val value: Float, val color: Color)

/**
 * Ring chart (r 70 of 180): segments sweep in clockwise from the top; the highlighted one
 * thickens while the others recede. Tapping a segment reports its key.
 */
@Composable
fun DonutChart(
    segments: List<DonutSegment>,
    centerLabel: String,
    centerValue: String,
    onSegmentClick: (String) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    highlightedKey: String? = null,
) {
    val c = CashFlowTheme.colors
    val sweep = rememberEntry(segments.map { it.key })
    LaunchedEffect(segments.map { it.key }) { sweep.animateTo(1f, tween(500)) }
    val total = segments.sumOf { it.value.toDouble() }.toFloat().coerceAtLeast(1f)
    val currentClick by rememberUpdatedState(onSegmentClick)
    Box(modifier.size(260.dp), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .size(260.dp)
                .semantics { this.contentDescription = contentDescription }
                .pointerInput(segments) {
                    detectTapGestures { p ->
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val r = size.width * 70f / 180f
                        val d = hypot(p.x - cx, p.y - cy)
                        if (d < r * 0.65f || d > r * 1.35f) return@detectTapGestures
                        var angle = (atan2(p.y - cy, p.x - cx) * 180 / PI).toFloat() + 90f
                        if (angle < 0) angle += 360f
                        var acc = 0f
                        for (s in segments) {
                            acc += s.value / total * 360f
                            if (angle <= acc) {
                                currentClick(s.key)
                                break
                            }
                        }
                    }
                },
        ) {
            val r = size.width * 70f / 180f
            val stroke = size.width * 18f / 180f
            val center = Offset(size.width / 2, size.height / 2)
            val box = Rect(center.x - r, center.y - r, center.x + r, center.y + r)
            drawCircle(c.surfaceControl, r, center, style = Stroke(stroke))
            var start = -90f
            val gap = if (segments.size > 1) 1.6f else 0f
            segments.forEach { s ->
                val angle = s.value / total * 360f * sweep.value
                val on = highlightedKey == null || highlightedKey == s.key
                val width = if (highlightedKey == s.key) stroke * 1.33f else stroke
                if (angle - gap > 0f) {
                    drawArc(
                        s.color.copy(alpha = s.color.alpha * if (on) 1f else 0.35f),
                        startAngle = start, sweepAngle = angle - gap, useCenter = false,
                        topLeft = box.topLeft, size = box.size, style = Stroke(width),
                    )
                }
                start += angle
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerLabel, style = CashFlowTheme.typography.label, color = c.textSecondary, maxLines = 1)
            Text(centerValue, style = CashFlowTheme.typography.bodyStrong.copy(fontFeatureSettings = "tnum"), color = c.textPrimary, maxLines = 1)
        }
    }
}

// ───────────────────────────── Sankey ─────────────────────────────

@Immutable
data class SankeyNodeUi(val id: String, val label: String, val amount: String, val color: Color, val fill: Color, val column: Int)

@Immutable
data class SankeyLinkUi(val source: String, val target: String, val value: Float)

/**
 * Cash-flow Sankey: sources on the left, the split in the middle, groups on the right. Node
 * bars are 8dp in the strong colour; links take the fill of their destination and widen with
 * the amount. Ribbons flow in from the left on first show. Every node is a tap target; the
 * whole chart also reads as one sentence for TalkBack.
 */
@Composable
fun SankeyChart(
    nodes: List<SankeyNodeUi>,
    links: List<SankeyLinkUi>,
    onNodeClick: (String) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val c = CashFlowTheme.colors
    val flow = rememberEntry(nodes.map { it.id })
    LaunchedEffect(nodes.map { it.id }) { flow.animateTo(1f, tween(800)) }
    val measurer = rememberTextMeasurer()
    val nameStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.textPrimary)
    val amountStyle = TextStyle(fontSize = 12.sp, color = c.textSecondary, fontFeatureSettings = "tnum")
    val currentClick by rememberUpdatedState(onNodeClick)
    val layout = remember(nodes, links) { SankeyLayout(nodes, links) }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(380.dp)
            .semantics { this.contentDescription = contentDescription }
            .pointerInput(layout) {
                detectTapGestures { p ->
                    val rects = layout.rects(size.width.toFloat(), size.height.toFloat(), 92.dp.toPx(), 120.dp.toPx(), 8.dp.toPx(), 14.dp.toPx(), 40.dp.toPx(), 34.dp.toPx())
                    rects.entries.minByOrNull { (_, r) ->
                        val dx = max(0f, max(r.left - p.x, p.x - r.right))
                        val dy = max(0f, max(r.top - p.y, p.y - r.bottom))
                        dx + dy
                    }?.let { (id, r) ->
                        val near = p.y in (r.top - 24.dp.toPx())..(r.bottom + 24.dp.toPx())
                        if (near) currentClick(id)
                    }
                }
            },
    ) {
        val labelW = 92.dp.toPx()
        val barW = 8.dp.toPx()
        val rects = layout.rects(size.width, size.height, labelW, 120.dp.toPx(), barW, 14.dp.toPx(), 40.dp.toPx(), 34.dp.toPx())
        val outUsed = HashMap<String, Float>()
        val inUsed = HashMap<String, Float>()
        clipRect(right = size.width * flow.value) {
            layout.links.forEach { link ->
                val s = rects[link.source] ?: return@forEach
                val t = rects[link.target] ?: return@forEach
                val sTotal = layout.outTotal(link.source)
                val tTotal = layout.inTotal(link.target)
                val sh = s.height * link.value / sTotal
                val th = t.height * link.value / tTotal
                val sy = s.top + (outUsed[link.source] ?: 0f)
                val ty = t.top + (inUsed[link.target] ?: 0f)
                outUsed[link.source] = (outUsed[link.source] ?: 0f) + sh
                inUsed[link.target] = (inUsed[link.target] ?: 0f) + th
                val x0 = s.right
                val x1 = t.left
                val mx = (x0 + x1) / 2
                val ribbon = Path().apply {
                    moveTo(x0, sy)
                    cubicTo(mx, sy, mx, ty, x1, ty)
                    lineTo(x1, ty + th)
                    cubicTo(mx, ty + th, mx, sy + sh, x0, sy + sh)
                    close()
                }
                val color = layout.byId[link.target]?.fill ?: c.vizNeutralFill
                drawPath(ribbon, color.copy(alpha = 0.85f))
            }
        }
        layout.nodes.forEach { n ->
            val r = rects[n.id] ?: return@forEach
            drawRoundRect(n.color, r.topLeft, r.size, CornerRadius(2.dp.toPx()))
            val name = measurer.measure(n.label, nameStyle, maxLines = 1, softWrap = false, constraints = androidx.compose.ui.unit.Constraints(maxWidth = ((if (n.column == layout.maxColumn) 120.dp.toPx() else labelW) - 6.dp.toPx()).toInt()))
            val amount = measurer.measure(n.amount, amountStyle, maxLines = 1, softWrap = false)
            val lastColumn = n.column == layout.maxColumn
            val textX = when {
                n.column == 0 -> r.left - 6.dp.toPx() - max(name.size.width, amount.size.width)
                lastColumn -> r.right + 6.dp.toPx()
                else -> r.left - max(name.size.width, amount.size.width) / 2f + barW / 2
            }
            val textY = if (n.column == 0 || lastColumn) r.top + max(r.height, 34.dp.toPx()) / 2f - (name.size.height + amount.size.height) / 2f else r.top - name.size.height - amount.size.height - 2.dp.toPx()
            drawText(name, topLeft = Offset(textX.coerceAtLeast(0f), textY.coerceAtLeast(0f)))
            drawText(amount, topLeft = Offset(textX.coerceAtLeast(0f), textY.coerceAtLeast(0f) + name.size.height))
        }
    }
}

/** Node positions: columns spread across the width, node heights proportional to their flow. */
private class SankeyLayout(val nodes: List<SankeyNodeUi>, val links: List<SankeyLinkUi>) {
    val byId = nodes.associateBy { it.id }
    val maxColumn = nodes.maxOfOrNull { it.column } ?: 0
    private val outs = links.groupBy { it.source }.mapValues { (_, l) -> l.sumOf { it.value.toDouble() }.toFloat() }
    private val ins = links.groupBy { it.target }.mapValues { (_, l) -> l.sumOf { it.value.toDouble() }.toFloat() }
    fun outTotal(id: String) = outs[id]?.takeIf { it > 0f } ?: 1f
    fun inTotal(id: String) = ins[id]?.takeIf { it > 0f } ?: 1f
    private fun value(id: String) = max(outs[id] ?: 0f, ins[id] ?: 0f)

    /**
     * Middle columns carry their labels above each bar, so their gaps (and the top margin) are
     * [labelGap] tall; the outer columns label beside the bar and use [gap]. The right labels get
     * [rightW], wider than the left, since group names ("Ej kategoriserat") run longer.
     */
    fun rects(width: Float, height: Float, labelW: Float, rightW: Float, barW: Float, gap: Float, labelGap: Float, minSlot: Float): Map<String, Rect> {
        val columns = nodes.groupBy { it.column }
        fun outer(col: Int) = col == 0 || col == maxColumn
        fun gapOf(col: Int) = if (outer(col)) gap else labelGap
        // Outer columns label beside the bar, so even a tiny node takes a label's height.
        fun slot(col: Int, h: Float) = if (outer(col)) max(h, minSlot) else h
        val top = labelGap
        val avail = height - top - 8f
        fun columnHeight(col: Int, list: List<SankeyNodeUi>, s: Float) = list.sumOf { slot(col, max(2f, value(it.id) * s)).toDouble() }.toFloat() + gapOf(col) * (list.size - 1)
        // One scale for every column: the largest that lets the tightest column fit.
        var lo = 0f
        var hi = avail / (columns.values.maxOfOrNull { l -> l.minOfOrNull { value(it.id) } ?: 1f }?.coerceAtLeast(1f) ?: 1f)
        repeat(40) {
            val mid = (lo + hi) / 2
            if (columns.all { (col, list) -> columnHeight(col, list, mid) <= avail }) lo = mid else hi = mid
        }
        val scale = lo
        val out = HashMap<String, Rect>()
        val span = width - labelW - rightW - barW
        columns.forEach { (col, list) ->
            val x = labelW + if (maxColumn == 0) 0f else span * col / maxColumn
            val g = gapOf(col)
            val colTotal = columnHeight(col, list, scale)
            var y = top + (avail - colTotal) / 2f
            list.forEach { n ->
                val h = max(2f, value(n.id) * scale)
                out[n.id] = Rect(x, y, x + barW, y + h)
                y += slot(col, h) + g
            }
        }
        return out
    }
}

// ───────────────────────────── Goal bar & odometer ─────────────────────────────

/** A thin progress bar (cf-kpi-goalbar) that fills on first show. */
@Composable
fun GoalBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val c = CashFlowTheme.colors
    val inspecting = LocalInspectionMode.current
    val fill = remember { Animatable(if (inspecting) fraction.coerceIn(0f, 1f) else 0f) }
    LaunchedEffect(fraction) { fill.animateTo(fraction.coerceIn(0f, 1f), tween(800)) }
    Box(modifier.fillMaxWidth().height(6.dp).clip(Radius.pill).background(c.surfaceControl)) {
        Box(Modifier.fillMaxWidth(fill.value).height(6.dp).clip(Radius.pill).background(color))
    }
}

/**
 * Digits roll like an odometer when the value changes: each character slides in from below
 * (or above when [up] is false) only where it changed.
 */
@Composable
fun OdometerText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier, up: Boolean = true) {
    Row(modifier.semantics(mergeDescendants = true) { contentDescription = text }) {
        // Keyed from the right so "9 999" → "10 000" rolls the units, not every slot.
        text.forEachIndexed { i, ch ->
            androidx.compose.runtime.key(text.length - i) {
                AnimatedContent(
                    targetState = ch,
                    transitionSpec = {
                        val dir = if (up) 1 else -1
                        (slideInVertically(tween(300)) { it * dir } + fadeIn(tween(300)))
                            .togetherWith(slideOutVertically(tween(300)) { -it * dir } + fadeOut(tween(300)))
                    },
                    label = "digit",
                ) { digit -> Text(digit.toString(), style = style, color = color, textAlign = TextAlign.Center) }
            }
        }
    }
}
