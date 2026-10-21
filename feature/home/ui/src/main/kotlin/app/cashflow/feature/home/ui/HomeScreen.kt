package app.cashflow.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cashflow.core.designsystem.component.AccentHeader
import app.cashflow.core.designsystem.component.LocalNavRailInset
import app.cashflow.core.designsystem.component.BandStatusBar
import app.cashflow.core.designsystem.component.rememberOnBand
import app.cashflow.core.designsystem.component.BandIconButton
import app.cashflow.core.designsystem.component.BandSegmented
import app.cashflow.core.designsystem.component.CfBottomSheet
import app.cashflow.core.designsystem.component.CfButton
import app.cashflow.core.designsystem.component.CfButtonVariant
import app.cashflow.core.designsystem.component.CfCard
import app.cashflow.core.designsystem.component.CfSnackbarHost
import app.cashflow.core.designsystem.component.KpiTile
import app.cashflow.core.designsystem.component.Segment
import app.cashflow.core.designsystem.component.SkeletonBlock
import app.cashflow.core.designsystem.component.SummaryCard
import app.cashflow.core.designsystem.component.TodoCard
import app.cashflow.core.designsystem.component.TrendRow
import app.cashflow.core.designsystem.icon.CfIcons
import app.cashflow.core.designsystem.theme.Accent
import app.cashflow.core.designsystem.theme.CashFlowTheme
import app.cashflow.core.designsystem.theme.Radius
import app.cashflow.core.designsystem.theme.Spacing
import app.cashflow.core.ui.DetailSheetContent
import app.cashflow.core.ui.LoadErrorCard
import app.cashflow.core.ui.ObserveAsEvents
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeRoot(
    contentPadding: PaddingValues,
    onOpenFlow: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenAccounts: () -> Unit,
    scrollToTop: Flow<Unit> = emptyFlow(),
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            HomeEvent.OpenFlow -> onOpenFlow()
            HomeEvent.OpenReview -> onOpenReview()
            HomeEvent.OpenImport -> onOpenImport()
            HomeEvent.OpenProfile -> onOpenProfile()
            HomeEvent.OpenAccounts -> onOpenAccounts()
            is HomeEvent.ShowMessage -> scope.launch { snackbar.showSnackbar(event.text.asString(context)) }
        }
    }
    Box(Modifier.fillMaxSize()) {
        HomeScreen(state, viewModel::onAction, contentPadding, scrollToTop)
        CfSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = contentPadding.calculateBottomPadding()))
    }
    state.sheet?.let { sheet ->
        CfBottomSheet(onDismissRequest = { viewModel.onAction(HomeAction.OnSheetDismiss) }) {
            DetailSheetContent(sheet.detail) { viewModel.onAction(HomeAction.OnSheetCta) }
        }
    }
}

@Composable
fun HomeScreen(
    state: HomeState,
    onAction: (HomeAction) -> Unit,
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
            contentPadding = PaddingValues(start = LocalNavRailInset.current, bottom = contentPadding.calculateBottomPadding() + Spacing.s4),
        ) {
            item(key = "header") {
                AccentHeader(
                    accent = Accent.Home,
                    leading = { BandIconButton(CfIcons.User, stringResource(R.string.cd_profile), { onAction(HomeAction.OnProfileClick) }) },
                    actions = {
                        // Privat/Gemensamt is a stub until household sharing (step 6): Gemensamt is shown.
                        BandSegmented(
                            segments = listOf(Segment("private", stringResource(R.string.scope_private)), Segment("joint", stringResource(R.string.scope_joint))),
                            selectedKey = "joint",
                            onSelect = { if (it == "private") onAction(HomeAction.OnScopePrivate) },
                            contentDescription = stringResource(R.string.cd_scope),
                        )
                    },
                ) {
                    if (state.hasData) {
                        val label = state.summaryLabel.asString()
                        val delta = state.delta?.asString()
                        SummaryCard(
                            label = label,
                            value = state.summaryValue,
                            delta = delta,
                            deltaPositive = state.deltaPositive,
                            accessibilityText = stringResource(R.string.cd_summary, label, state.summarySpoken, delta.orEmpty()),
                            onInfo = { onAction(HomeAction.OnInfoClick) },
                            infoLabel = stringResource(R.string.cd_info),
                            rollDigits = true,
                        )
                    } else if (state.loadError) {
                        LoadErrorCard(onRetry = { onAction(HomeAction.OnRetry) })
                    } else if (state.isLoading) {
                        SkeletonBlock(Modifier.fillMaxWidth().height(150.dp))
                    } else {
                        WelcomeCard(onAction)
                    }
                }
            }
            if (state.isLoading && !state.hasData) {
                // Design FlowLoading: the key figures and trends as grey blocks until the sums are ready.
                item(key = "skeleton") { HomeSkeleton() }
            }
            if (state.hasData) {
                item(key = "kpis") { Kpis(state, onAction) }
                section("trends", R.string.section_trends, trailing = R.string.trends_span)
                item(key = "trends") { Trends(state, onAction) }
            }
            if (!state.isLoading && !state.loadError) {
                section("todo", R.string.section_todo)
                item(key = "todo") {
                    Column(Modifier.padding(horizontal = Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                        state.todo.forEach { t ->
                            val (icon, bg, fg) = when (t.kind) {
                                TodoKind.REVIEW -> Triple(CfIcons.Tag, c.accentEventsTint, c.textOnEventsTint)
                                TodoKind.DUPLICATES -> Triple(CfIcons.Copy, c.surfaceControl, c.textPrimary)
                                TodoKind.IMPORT, TodoKind.ALL_DONE -> Triple(CfIcons.File, c.surfaceControl, c.textPrimary)
                            }
                            TodoCard(
                                icon = icon, iconBackground = bg, iconTint = fg,
                                eyebrow = t.eyebrow.asString(), title = t.title.asString(), text = t.text.asString(),
                                action = t.action.asString(), onAction = { onAction(HomeAction.OnTodoClick(t.kind)) },
                            )
                        }
                    }
                }
                state.modeNote?.let { note ->
                    item(key = "mode-note") {
                        Text(
                            note.asString(),
                            style = CashFlowTheme.typography.label,
                            color = c.textSecondary,
                            modifier = Modifier.padding(horizontal = Spacing.gutter + 4.dp).padding(top = Spacing.s4),
                        )
                    }
                }
                state.footer?.let { footer ->
                    item(key = "footer") {
                        Text(
                            footer.asString(),
                            style = CashFlowTheme.typography.label,
                            color = c.textSecondary,
                            modifier = Modifier.padding(horizontal = Spacing.gutter + 4.dp).padding(top = Spacing.s4),
                        )
                    }
                }
            }
        }
        BandStatusBar(rememberOnBand(listState))
    }
}

/** First start, before any import: import your statements or try the demo household. */
@Composable
private fun WelcomeCard(onAction: (HomeAction) -> Unit) {
    val c = CashFlowTheme.colors
    CfCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
            Text(stringResource(R.string.home_welcome_title), style = CashFlowTheme.typography.title, color = c.textPrimary)
            Text(stringResource(R.string.home_welcome_text), style = CashFlowTheme.typography.body, color = c.textSecondary)
            CfButton(stringResource(R.string.home_welcome_import), { onAction(HomeAction.OnImportClick) }, Modifier.fillMaxWidth(), icon = CfIcons.File)
            CfButton(stringResource(R.string.home_welcome_demo), { onAction(HomeAction.OnTryDemo) }, Modifier.fillMaxWidth(), variant = CfButtonVariant.Outline)
        }
    }
}

@Composable
private fun HomeSkeleton() {
    Column(Modifier.padding(horizontal = Spacing.gutter).padding(top = Spacing.s4), verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                SkeletonBlock(Modifier.weight(1f).height(146.dp))
                SkeletonBlock(Modifier.weight(1f).height(146.dp))
            }
        }
        SkeletonBlock(Modifier.fillMaxWidth().padding(top = Spacing.s6).height(260.dp))
    }
}

private fun LazyListScope.section(key: String, title: Int, trailing: Int? = null) {
    item(key = "section-$key") {
        val c = CashFlowTheme.colors
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter).padding(top = Spacing.s6, bottom = Spacing.s3),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(stringResource(title), style = CashFlowTheme.typography.titleL, color = c.textPrimary, modifier = Modifier.weight(1f).semantics { heading() })
            if (trailing != null) Text(stringResource(trailing), style = CashFlowTheme.typography.label, color = c.textSecondary)
        }
    }
}

@Composable
private fun Kpis(state: HomeState, onAction: (HomeAction) -> Unit) {
    val reached = stringResource(R.string.goal_reached)
    Column(Modifier.padding(horizontal = Spacing.gutter).padding(top = Spacing.s4), verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
        // From 1.3× text the key figures stack in one column (design: cf-kpis at large text).
        val perRow = if (LocalDensity.current.fontScale >= 1.3f) 1 else 2
        state.kpis.chunked(perRow).forEach { row ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                row.forEach { k ->
                    KpiTile(
                        label = k.label.asString(),
                        value = k.value,
                        accessibilityText = k.spoken.asString(),
                        onClick = { onAction(HomeAction.OnKpiClick(k.id)) },
                        trend = k.trend,
                        trendGood = k.trendGood,
                        goal = k.goal,
                        goalReachedLabel = if (k.goalReached) reached else null,
                        sub = k.sub?.asString(),
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun Trends(state: HomeState, onAction: (HomeAction) -> Unit) {
    val c = CashFlowTheme.colors
    val vsAvg = stringResource(R.string.trend_vs_avg)
    CfCard(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter), contentPadding = PaddingValues(vertical = Spacing.s1)) {
        Column {
            state.trends.forEachIndexed { i, t ->
                if (i > 0) Box(Modifier.fillMaxWidth().padding(horizontal = Spacing.s4).height(1.dp).background(c.divider))
                TrendRow(
                    label = t.label.asString(),
                    value = t.value.asString(),
                    points = t.points,
                    color = when (t.tone) {
                        TrendTone.Cashflow -> c.accentCashflowInk
                        TrendTone.Income -> c.vizIncome
                        TrendTone.Expenses -> c.vizFixed
                        TrendTone.Savings -> c.vizSavings
                    },
                    change = t.change,
                    changeGood = t.good,
                    changeCaption = vsAvg,
                    accessibilityText = t.spoken.asString(),
                    onClick = { onAction(HomeAction.OnTrendClick(t.id)) },
                    modifier = Modifier.clip(Radius.sm),
                )
            }
        }
    }
}
