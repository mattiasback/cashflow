package app.cashflow.feature.events.ui.review

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import app.cashflow.core.designsystem.component.BadgeKind
import app.cashflow.core.designsystem.component.StatusBadge
import app.cashflow.core.designsystem.theme.Radius
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cashflow.core.designsystem.component.AccentHeader
import app.cashflow.core.designsystem.component.AccentTopBar
import app.cashflow.core.designsystem.component.BandIconButton
import app.cashflow.core.designsystem.component.CfButton
import app.cashflow.core.designsystem.component.CfButtonVariant
import app.cashflow.core.designsystem.component.CfCard
import app.cashflow.core.designsystem.component.CfChip
import app.cashflow.core.designsystem.component.CfSwitchRow
import app.cashflow.core.designsystem.icon.CfIcons
import app.cashflow.core.designsystem.theme.Accent
import app.cashflow.core.designsystem.theme.CashFlowTheme
import app.cashflow.core.designsystem.theme.Spacing
import app.cashflow.core.ui.FlowTone
import app.cashflow.core.ui.ObserveAsEvents
import app.cashflow.core.ui.UiText
import app.cashflow.core.ui.colors
import app.cashflow.feature.events.ui.R
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ReviewRoot(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    viewModel: ReviewViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            ReviewEvent.NavigateBack -> onBack()
            is ReviewEvent.ShowMessage -> scope.launch { snackbar.showSnackbar(event.text.asString(context)) }
            is ReviewEvent.ShowUndo -> scope.launch {
                val result = snackbar.showSnackbar(
                    message = event.text.asString(context),
                    actionLabel = context.getString(R.string.review_undo),
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) viewModel.onAction(ReviewAction.OnUndoClick)
            }
        }
    }
    ReviewScreen(state, viewModel::onAction, contentPadding)
    SnackbarHost(snackbar, Modifier.padding(contentPadding))
}

@Composable
fun ReviewScreen(
    state: ReviewState,
    onAction: (ReviewAction) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val c = CashFlowTheme.colors
    val total = state.decided + state.duplicates.size + state.groups.size
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(c.bgCanvas)
                .verticalScroll(rememberScrollState())
                .padding(bottom = contentPadding.calculateBottomPadding() + Spacing.s4),
            verticalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            AccentHeader(accent = Accent.Events, pinnedBar = true) {
                CfCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
                        Text(
                            UiText.PluralResource(R.plurals.review_left, state.remainingRows).asString(),
                            style = CashFlowTheme.typography.headlineL,
                            color = c.textPrimary,
                        )
                        Text(stringResource(R.string.review_intro), style = CashFlowTheme.typography.body, color = c.textSecondary)
                    }
                }
            }
            val duplicate = state.duplicates.firstOrNull()
            val group = state.groups.firstOrNull()
            Column(Modifier.padding(horizontal = Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                when {
                    state.isLoading -> Unit
                    duplicate == null && group == null -> CfCard(Modifier.fillMaxWidth(), color = c.surfaceStatus) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(stringResource(R.string.review_done_title), style = CashFlowTheme.typography.title, color = c.textOnStatus)
                            Text(stringResource(R.string.review_done_body), style = CashFlowTheme.typography.body, color = c.textOnStatus)
                        }
                    }
                    else -> {
                        DeckProgress(done = state.decided, total = total)
                        val key = duplicate?.id ?: group!!.key
                        SwipeDeck(
                            cardKey = key,
                            backCards = (state.duplicates.size + state.groups.size - 1).coerceIn(0, 2),
                            canAccept = duplicate != null || group?.selectedId != null,
                            onAccept = { if (duplicate != null) onAction(ReviewAction.OnMerge(duplicate.id)) else onAction(ReviewAction.OnApprove(group!!.key)) },
                            onSkip = { onAction(ReviewAction.OnSkip(key)) },
                        ) {
                            if (duplicate != null) DuplicateCard(duplicate, onAction) else ReviewGroupCard(group!!, onAction)
                        }
                        Row(Modifier.fillMaxWidth().clearAndSetSemantics { }) {
                            Text(stringResource(R.string.review_swipe_left), style = CashFlowTheme.typography.caption, color = c.textSecondary, modifier = Modifier.weight(1f))
                            Text(stringResource(R.string.review_swipe_right), style = CashFlowTheme.typography.caption, color = c.textSecondary)
                        }
                    }
                }
            }
        }
        // The band's controls stay at the top while the content scrolls under them.
        AccentTopBar(accent = Accent.Events, title = stringResource(R.string.review_title), actions = { BandIconButton(CfIcons.ChevronLeft, stringResource(R.string.cd_back), { onAction(ReviewAction.OnBackClick) }) })
    }
}

@Composable
private fun DeckProgress(done: Int, total: Int) {
    val c = CashFlowTheme.colors
    val fraction = if (total == 0) 0f else done.toFloat() / total
    val animated by animateFloatAsState(fraction, label = "progress")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s3)) {
        Box(
            Modifier
                .weight(1f)
                .height(6.dp)
                .clip(Radius.pill)
                .background(c.surfaceControl)
                .semantics { progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f) },
        ) {
            Box(Modifier.fillMaxWidth(animated).fillMaxHeight().clip(Radius.pill).background(c.accentEventsInk))
        }
        Text(stringResource(R.string.review_progress, (done + 1).coerceAtMost(total), total), style = CashFlowTheme.typography.caption, color = c.textSecondary)
    }
}

/**
 * The card deck: the top card follows the finger with a slight tilt, springs back when let go
 * early, and flies out when flicked: right accepts, left skips. The edges of the next cards peek
 * out underneath. The buttons on the card do the same for TalkBack and for those who prefer taps.
 */
@Composable
private fun SwipeDeck(
    cardKey: String,
    backCards: Int,
    canAccept: Boolean,
    onAccept: () -> Unit,
    onSkip: () -> Unit,
    content: @Composable () -> Unit,
) {
    val c = CashFlowTheme.colors
    val scope = rememberCoroutineScope()
    val offset = remember(cardKey) { Animatable(0f) }
    val appear = remember(cardKey) { Animatable(0.96f) }
    LaunchedEffect(cardKey) { appear.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)) }
    val threshold = with(LocalDensity.current) { 110.dp.toPx() }
    val currentAccept by rememberUpdatedState(onAccept)
    val currentSkip by rememberUpdatedState(onSkip)
    val currentCanAccept by rememberUpdatedState(canAccept)
    Box(Modifier.fillMaxWidth().padding(bottom = (backCards * 8).dp)) {
        // The next cards peek out below the top card.
        for (i in backCards downTo 1) {
            Box(
                Modifier
                    .matchParentSize()
                    .padding(horizontal = (i * 6).dp)
                    .offset(y = (i * 8).dp)
                    .clip(Radius.lg)
                    .background(c.surfaceCard.copy(alpha = if (i == 1) 0.8f else 0.55f)),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = offset.value
                    rotationZ = offset.value / 40f
                    scaleX = appear.value
                    scaleY = appear.value
                }
                .pointerInput(cardKey) {
                    val tracker = VelocityTracker()
                    detectHorizontalDragGestures(
                        onDragStart = { tracker.resetTracking() },
                        onDragEnd = {
                            // A quick flick counts even when the card has not travelled far.
                            val velocity = tracker.calculateVelocity().x
                            scope.launch {
                                val x = offset.value
                                when {
                                    (x > threshold || velocity > FLICK) && x > 0 && currentCanAccept -> {
                                        offset.animateTo(size.width * 1.5f, tween(220))
                                        currentAccept()
                                    }
                                    (x < -threshold || velocity < -FLICK) && x < 0 -> {
                                        offset.animateTo(-size.width * 1.5f, tween(220))
                                        currentSkip()
                                    }
                                    else -> offset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                }
                            }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f, spring()) } },
                    ) { change, dx ->
                        tracker.addPosition(change.uptimeMillis, change.position)
                        change.consume()
                        scope.launch { offset.snapTo(offset.value + dx) }
                    }
                },
        ) { content() }
    }
}

@Composable
private fun DuplicateCard(card: DuplicateCardUi, onAction: (ReviewAction) -> Unit) {
    val c = CashFlowTheme.colors
    CfCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s4)) {
            Row {
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }, verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
                    StatusBadge(stringResource(R.string.review_duplicate_badge), BadgeKind.Review)
                    Text(card.title, style = CashFlowTheme.typography.title, color = c.textPrimary, maxLines = 2)
                }
                Spacer(Modifier.width(Spacing.s3))
                Text(card.amount, style = CashFlowTheme.typography.title.copy(fontFeatureSettings = "tnum"), color = c.textPrimary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                DuplicateSide(card.earlier, stringResource(R.string.review_duplicate_earlier), Modifier.weight(1f))
                DuplicateSide(card.newer, stringResource(R.string.review_duplicate_newer), Modifier.weight(1f))
            }
            Text(stringResource(R.string.review_duplicate_body), style = CashFlowTheme.typography.body, color = c.textPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                CfButton(stringResource(R.string.review_keep_both), { onAction(ReviewAction.OnKeepBoth(card.id)) }, Modifier.weight(1f), variant = CfButtonVariant.Outline, enabled = !card.isSaving)
                CfButton(
                    stringResource(R.string.review_merge),
                    { onAction(ReviewAction.OnMerge(card.id)) },
                    Modifier.weight(1f),
                    variant = CfButtonVariant.Accent,
                    accent = c.actionEvents,
                    enabled = !card.isSaving,
                )
            }
        }
    }
}

@Composable
private fun DuplicateSide(side: DuplicateSideUi, label: String, modifier: Modifier = Modifier) {
    val c = CashFlowTheme.colors
    Column(
        modifier.clip(Radius.sm).background(if (c.isDark) c.surfaceGroup else c.surfaceGroup).padding(Spacing.s3),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = CashFlowTheme.typography.caption, color = c.textSecondary)
        Text(side.date, style = CashFlowTheme.typography.bodyStrong, color = c.textPrimary)
        Text(side.text, style = CashFlowTheme.typography.label, color = c.textSecondary, maxLines = 2)
        if (side.source.isNotEmpty()) Text(side.source, style = CashFlowTheme.typography.label, color = c.textSecondary, maxLines = 1)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewGroupCard(group: ReviewGroupUi, onAction: (ReviewAction) -> Unit, modifier: Modifier = Modifier) {
    val c = CashFlowTheme.colors
    CfCard(modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s4)) {
            Row {
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
                    Text(group.title, style = CashFlowTheme.typography.title, color = c.textPrimary, maxLines = 2)
                    Text(group.summary, style = CashFlowTheme.typography.label, color = c.textSecondary)
                }
                Spacer(Modifier.width(Spacing.s3))
                Text(
                    group.total,
                    style = CashFlowTheme.typography.title.copy(fontFeatureSettings = "tnum"),
                    // Not categorized yet, so not known to be income: neutral like every uncategorized amount.
                    color = c.textPrimary,
                )
            }
            if (group.isLoneLeg) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                    Text(stringResource(R.string.review_household_hint), style = CashFlowTheme.typography.label, color = c.textSecondary)
                    CfButton(
                        text = stringResource(R.string.review_household),
                        onClick = { onAction(ReviewAction.OnHousehold(group.key)) },
                        variant = CfButtonVariant.Outline,
                        enabled = !group.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Text(stringResource(R.string.review_suggestion), style = CashFlowTheme.typography.label, color = c.textSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                group.chips.forEach { chip ->
                    CfChip(
                        label = chip.name.asString(),
                        selected = chip.selected,
                        leadingColor = chip.tone.colors().strong,
                        onClick = { onAction(ReviewAction.OnCategorySelect(group.key, chip.id)) },
                    )
                }
                if (group.canExpand) CfChip(
                    label = stringResource(if (group.expanded) R.string.review_pick_fewer else R.string.review_pick_other),
                    selected = false,
                    onClick = { onAction(ReviewAction.OnExpandToggle(group.key)) },
                )
            }
            CfSwitchRow(
                label = stringResource(R.string.review_remember, group.title),
                checked = group.remember,
                onCheckedChange = { onAction(ReviewAction.OnRememberToggle(group.key, it)) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                CfButton(
                    text = stringResource(R.string.review_skip),
                    onClick = { onAction(ReviewAction.OnSkip(group.key)) },
                    variant = CfButtonVariant.Outline,
                    enabled = !group.isSaving,
                )
                CfButton(
                    // Until a category is picked the button says what is missing instead of looking faded.
                    text = stringResource(if (group.selectedId == null) R.string.review_pick_first else R.string.review_approve),
                    onClick = { onAction(ReviewAction.OnApprove(group.key)) },
                    variant = CfButtonVariant.Accent,
                    accent = c.actionEvents,
                    icon = if (group.selectedId == null) null else CfIcons.Check,
                    enabled = group.selectedId != null && !group.isSaving,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ReviewScreenPreview() {
    app.cashflow.core.designsystem.theme.CashFlowTheme {
        ReviewScreen(
            state = ReviewState(
                isLoading = false,
                remainingRows = 14,
                groups = listOf(
                    ReviewGroupUi(
                        "SWISH TILL VÄN", "Swish till Vän", "2026-09-02 – 2026-10-11", "−1 450 kr", false, false,
                        listOf(
                            CategoryChipUi("mat", UiText.DynamicString("Mat"), FlowTone.Variable, false),
                            CategoryChipUi("restaurang", UiText.DynamicString("Restaurang"), FlowTone.Variable, true),
                        ),
                        expanded = false, selectedId = "restaurang", remember = true, isSaving = false,
                    ),
                ),
            ),
            onAction = {},
        )
    }
}

/** Pixels per second that make a short swipe count as a flick. */
private const val FLICK = 1_500f
