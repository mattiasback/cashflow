package app.cashflow.feature.insights.ui

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cashflow.core.designsystem.component.AccentHeader
import app.cashflow.core.designsystem.component.AccentStatusBar
import app.cashflow.core.designsystem.component.BandIconButton
import app.cashflow.core.designsystem.component.CfBottomSheet
import app.cashflow.core.designsystem.component.CfButton
import app.cashflow.core.designsystem.component.CfButtonVariant
import app.cashflow.core.designsystem.component.CfCard
import app.cashflow.core.designsystem.component.CfRange
import app.cashflow.core.designsystem.component.CfSnackbarHost
import app.cashflow.core.designsystem.component.GoalBar
import app.cashflow.core.designsystem.component.SheetHeader
import app.cashflow.core.designsystem.component.SkeletonBlock
import app.cashflow.core.designsystem.component.SummaryCard
import app.cashflow.core.designsystem.icon.CfIcons
import app.cashflow.core.designsystem.theme.Accent
import app.cashflow.core.designsystem.theme.CashFlowTheme
import app.cashflow.core.designsystem.theme.Radius
import app.cashflow.core.designsystem.theme.Spacing
import app.cashflow.core.ui.ObserveAsEvents
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun InsightsRoot(
    contentPadding: PaddingValues,
    onOpenProfile: () -> Unit,
    onOpenImport: () -> Unit,
    scrollToTop: Flow<Unit> = emptyFlow(),
    viewModel: InsightsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val copiedMessage = stringResource(R.string.copied_message)
    val shareTitle = stringResource(R.string.ai_share)
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is InsightsEvent.Copy -> scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(copiedMessage, event.text)))
                snackbar.showSnackbar(copiedMessage)
            }
            is InsightsEvent.Share -> {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, event.text)
                context.startActivity(Intent.createChooser(send, shareTitle))
            }
            InsightsEvent.OpenProfile -> onOpenProfile()
            InsightsEvent.OpenImport -> onOpenImport()
        }
    }
    Box(Modifier.fillMaxSize()) {
        InsightsScreen(state, viewModel::onAction, contentPadding, scrollToTop)
        CfSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = contentPadding.calculateBottomPadding()))
    }
    state.sheet?.let { sheet ->
        CfBottomSheet(onDismissRequest = { viewModel.onAction(InsightsAction.OnSheetDismiss) }) {
            InsightSheetContent(sheet, onAction = viewModel::onAction)
        }
    }
}

@Composable
fun InsightsScreen(
    state: InsightsState,
    onAction: (InsightsAction) -> Unit,
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
            item(key = "header") {
                AccentHeader(
                    accent = Accent.Insights,
                    leading = { BandIconButton(CfIcons.User, stringResource(R.string.cd_profile), { onAction(InsightsAction.OnProfileClick) }) },
                ) {
                    when {
                        state.enoughData -> {
                            val label = stringResource(R.string.hero_label)
                            val delta = state.gainDelta?.asString()
                            SummaryCard(
                                label = label,
                                value = state.gain,
                                delta = delta,
                                deltaPositive = true,
                                accessibilityText = stringResource(R.string.cd_hero, label, state.gain, delta.orEmpty()),
                                onInfo = { onAction(InsightsAction.OnAboutClick) },
                                infoLabel = stringResource(R.string.cd_info),
                            )
                        }
                        state.isLoading -> SkeletonBlock(Modifier.fillMaxWidth().height(150.dp))
                        else -> EmptyCard(state.hasData, onAction)
                    }
                }
            }
            if (state.enoughData) content(state, onAction)
        }
        AccentStatusBar(accent = Accent.Insights)
    }
}

private fun LazyListScope.content(state: InsightsState, onAction: (InsightsAction) -> Unit) {
    section("opps", R.string.section_opps, intro = R.string.opps_intro)
    item(key = "opps") {
        CfCard(Modifier.padding(horizontal = Spacing.gutter).fillMaxWidth(), contentPadding = PaddingValues(horizontal = Spacing.s4, vertical = 4.dp)) {
            if (state.opportunities.isEmpty()) {
                Text(stringResource(R.string.opps_empty), style = CashFlowTheme.typography.body, color = CashFlowTheme.colors.textSecondary, modifier = Modifier.padding(vertical = Spacing.s3))
            } else {
                Column {
                    state.opportunities.forEachIndexed { i, o ->
                        if (i > 0) HorizontalDivider(color = CashFlowTheme.colors.divider)
                        OpportunityRow(o) { onAction(InsightsAction.OnOpportunityClick(o.id)) }
                    }
                }
            }
        }
    }
    state.whatIf?.let { w ->
        section("whatif", R.string.section_whatif)
        item(key = "whatif") { WhatIfCard(w, onAction, Modifier.padding(horizontal = Spacing.gutter)) }
    }
    section("health", R.string.section_health, trailing = { stringResourceOf(R.string.health_span) })
    item(key = "health") { Benchmarks(state.benchmarks, Modifier.padding(horizontal = Spacing.gutter)) }
    state.prompt?.let { prompt ->
        section("ai", R.string.section_ai)
        item(key = "ai") { AiCard(prompt.asString(), state.copied, onAction, Modifier.padding(horizontal = Spacing.gutter)) }
    }
    item(key = "disclaimer") {
        Text(
            stringResource(R.string.disclaimer),
            style = CashFlowTheme.typography.label,
            color = CashFlowTheme.colors.textSecondary,
            modifier = Modifier.padding(horizontal = Spacing.gutter + 4.dp).padding(top = Spacing.s4),
        )
    }
}

@Composable
private fun stringResourceOf(id: Int) = stringResource(id)

private fun LazyListScope.section(key: String, title: Int, trailing: (@Composable () -> String)? = null, intro: Int? = null) {
    item(key = "section-$key") {
        val c = CashFlowTheme.colors
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter).padding(top = Spacing.s6, bottom = Spacing.s3)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(stringResource(title), style = CashFlowTheme.typography.titleL, color = c.textPrimary, modifier = Modifier.weight(1f).semantics { heading() })
                if (trailing != null) Text(trailing(), style = CashFlowTheme.typography.label, color = c.textSecondary)
            }
            if (intro != null) {
                Text(stringResource(intro), style = CashFlowTheme.typography.label, color = c.textSecondary, modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp))
            }
        }
    }
}

@Composable
private fun EmptyCard(hasData: Boolean, onAction: (InsightsAction) -> Unit) {
    val c = CashFlowTheme.colors
    CfCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
            Text(stringResource(if (hasData) R.string.not_enough_title else R.string.empty_title), style = CashFlowTheme.typography.title, color = c.textPrimary)
            Text(stringResource(if (hasData) R.string.not_enough_text else R.string.empty_text), style = CashFlowTheme.typography.body, color = c.textSecondary)
            CfButton(stringResource(R.string.import_cta), { onAction(InsightsAction.OnImportClick) }, Modifier.fillMaxWidth(), icon = CfIcons.File)
        }
    }
}

// ───────────────────────────── Spara utan att märka det ─────────────────────────────

@Composable
private fun OpportunityRow(o: OpportunityUi, onClick: () -> Unit) {
    val c = CashFlowTheme.colors
    val spoken = o.spoken.asString()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = spoken }
            .alpha(if (o.done) 0.55f else 1f)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(c.accentInsightsTint), contentAlignment = Alignment.Center) {
            val icon = when (o.icon) {
                OpportunityIcon.Subscriptions -> CfIcons.List
                OpportunityIcon.SmallCharges -> CfIcons.Drop
                OpportunityIcon.Category -> CfIcons.Fork
            }
            Icon(icon, contentDescription = null, tint = c.textOnInsightsTint, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(o.title.asString(), style = CashFlowTheme.typography.bodyStrong, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(o.sub.asString(), style = CashFlowTheme.typography.label, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                o.gain,
                style = CashFlowTheme.typography.bodyStrong.copy(
                    fontFeatureSettings = "tnum",
                    textDecoration = if (o.done) TextDecoration.LineThrough else null,
                ),
                color = if (o.done) c.textSecondary else c.textPositive,
            )
            Text(o.effort.asString(), style = CashFlowTheme.typography.label, color = c.textSecondary)
        }
    }
}

// ───────────────────────────── Tänk om ─────────────────────────────

@Composable
private fun WhatIfCard(w: WhatIfUi, onAction: (InsightsAction) -> Unit, modifier: Modifier = Modifier) {
    val c = CashFlowTheme.colors
    // Local while dragging so the thumb follows the finger; the view model gets whole 500 kr steps.
    var drag by remember { mutableFloatStateOf(w.extraKronor.toFloat()) }
    LaunchedEffect(w.extraKronor) { drag = w.extraKronor.toFloat() }
    CfCard(modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                Text(stringResource(R.string.sim_head), style = CashFlowTheme.typography.body, color = c.textPrimary, modifier = Modifier.weight(1f))
                Text("+" + w.extraText, style = CashFlowTheme.typography.bodyStrong.copy(fontFeatureSettings = "tnum"), color = c.textPrimary)
            }
            Column {
                CfRange(
                    value = drag,
                    onValueChange = {
                        drag = it
                        val kr = (Math.round(it / 500f) * 500)
                        if (kr != w.extraKronor) onAction(InsightsAction.OnExtraChange(kr))
                    },
                    valueRange = 0f..10_000f,
                    steps = 19,
                    label = stringResource(R.string.sim_slider),
                    valueText = w.spoken.asString(),
                )
                Row(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
                    Text(stringResource(R.string.sim_min), style = CashFlowTheme.typography.label, color = c.textSecondary, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.sim_max), style = CashFlowTheme.typography.label, color = c.textSecondary)
                }
            }
            Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                    if (w.bufferFull != null) {
                        Stat(stringResource(R.string.sim_buffer), w.bufferFull.asString(), w.bufferFullSub?.asString(), Modifier.weight(1f))
                    }
                    Stat(stringResource(R.string.sim_rate), w.rate, w.rateNow.asString(), Modifier.weight(1f))
                }
                Stat(stringResource(R.string.sim_funds), w.funds, w.fundsSub.asString(), Modifier.fillMaxWidth())
            }
            Text(w.note.asString(), style = CashFlowTheme.typography.label, color = c.textSecondary)
        }
    }
}

@Composable
private fun Stat(label: String, value: String, sub: String?, modifier: Modifier = Modifier) {
    val c = CashFlowTheme.colors
    Column(modifier.clip(Radius.xs).background(c.surfaceGroup).padding(horizontal = Spacing.s3, vertical = 10.dp)) {
        Text(label, style = CashFlowTheme.typography.label, color = c.textSecondary)
        Text(value, style = CashFlowTheme.typography.titleL.copy(fontFeatureSettings = "tnum"), color = c.textPrimary)
        if (sub != null) Text(sub, style = CashFlowTheme.typography.label, color = c.textSecondary)
    }
}

// ───────────────────────────── Så står ni dig ─────────────────────────────

@Composable
private fun Benchmarks(rows: List<BenchmarkUi>, modifier: Modifier = Modifier) {
    val c = CashFlowTheme.colors
    CfCard(modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
            rows.forEachIndexed { i, b ->
                if (i > 0) HorizontalDivider(color = c.divider)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(b.label.asString(), style = CashFlowTheme.typography.body, color = c.textPrimary, modifier = Modifier.weight(1f))
                        Text(b.value, style = CashFlowTheme.typography.bodyStrong.copy(fontFeatureSettings = "tnum"), color = c.textPrimary)
                    }
                    GoalBar(b.fraction, if (b.tone == BenchTone.Savings) c.vizSavings else c.vizFixed)
                    Text(b.note.asString(), style = CashFlowTheme.typography.label, color = c.textSecondary)
                }
            }
        }
    }
}

// ───────────────────────────── Fråga en AI ─────────────────────────────

@Composable
private fun AiCard(prompt: String, copied: Boolean, onAction: (InsightsAction) -> Unit, modifier: Modifier = Modifier) {
    val c = CashFlowTheme.colors
    val bg = if (c.isDark) c.accentInsightsTint else c.surfaceEmphasis
    val fg = if (c.isDark) c.textOnInsightsTint else c.textOnEmphasis
    val soft = fg.copy(alpha = 0.78f)
    Column(
        modifier.fillMaxWidth().clip(Radius.lg).background(bg).padding(start = Spacing.s4, end = Spacing.s4, top = Spacing.s5, bottom = Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s3)) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(c.accentInsights), contentAlignment = Alignment.Center) {
                Icon(CfIcons.Sparkle, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.ai_title), style = CashFlowTheme.typography.titleL, color = fg, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.ai_text), style = CashFlowTheme.typography.body, color = soft)
            }
        }
        val previewLabel = stringResource(R.string.ai_preview)
        Text(
            prompt,
            style = CashFlowTheme.typography.label.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp),
            color = fg.copy(alpha = 0.88f),
            maxLines = 9,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().clip(Radius.sm).background(fg.copy(alpha = 0.08f)).padding(Spacing.s3)
                .semantics { contentDescription = previewLabel + ". " + prompt },
        )
        Text(stringResource(R.string.ai_privacy), style = CashFlowTheme.typography.label, color = soft)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(Radius.xs)
                    .background(if (copied) c.surfaceStatus else Color.White)
                    .clickable(role = Role.Button) { onAction(InsightsAction.OnCopy(prompt)) }
                    .padding(horizontal = Spacing.s5),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s2, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (copied) CfIcons.Check else CfIcons.Copy, contentDescription = null, tint = if (copied) c.textOnStatus else Color(0xFF0F0C11), modifier = Modifier.size(18.dp))
                Text(
                    stringResource(if (copied) R.string.ai_copied else R.string.ai_copy),
                    style = CashFlowTheme.typography.bodyStrong,
                    color = if (copied) c.textOnStatus else Color(0xFF0F0C11),
                )
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(Radius.xs).clickable(role = Role.Button) { onAction(InsightsAction.OnShare(prompt)) },
                horizontalArrangement = Arrangement.spacedBy(Spacing.s2, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(CfIcons.Share, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.ai_share), style = CashFlowTheme.typography.bodyStrong, color = fg)
            }
        }
    }
}

// ───────────────────────────── Sheet ─────────────────────────────

@Composable
internal fun InsightSheetContent(sheet: InsightSheetUi, onAction: (InsightsAction) -> Unit) {
    val c = CashFlowTheme.colors
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.s4).padding(bottom = Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) {
        SheetHeader(eyebrow = sheet.eyebrow.asString(), title = sheet.title.asString(), figure = sheet.figure)
        sheet.description?.let { Text(it.asString(), style = CashFlowTheme.typography.body, color = c.textSecondary) }
        Explain(stringResource(R.string.sheet_why), sheet.why.asString(), sheet.assume?.asString())
        Explain(stringResource(R.string.sheet_how), sheet.how.asString(), null)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s3)) {
            CfButton(stringResource(R.string.close), { onAction(InsightsAction.OnSheetDismiss) }, Modifier.weight(1f), variant = CfButtonVariant.Outline)
            sheet.done?.let { done ->
                CfButton(done.asString(), { onAction(InsightsAction.OnSheetDone) }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Explain(caption: String, body: String, note: String?) {
    val c = CashFlowTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(Radius.sm).background(c.surfaceGroup).padding(horizontal = Spacing.s4, vertical = Spacing.s3),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(caption, style = CashFlowTheme.typography.caption, color = c.textSecondary)
        Text(body, style = CashFlowTheme.typography.body, color = c.textPrimary)
        if (note != null) Text(note, style = CashFlowTheme.typography.label, color = c.textSecondary)
    }
}
