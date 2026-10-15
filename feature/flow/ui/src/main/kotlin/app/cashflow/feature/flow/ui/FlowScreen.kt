package app.cashflow.feature.flow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cashflow.core.designsystem.component.AccentHeader
import app.cashflow.core.designsystem.component.BandPeriodStepper
import app.cashflow.core.designsystem.component.CategoryBar
import app.cashflow.core.designsystem.component.CfButton
import app.cashflow.core.designsystem.component.CfCard
import app.cashflow.core.designsystem.component.SectionHeader
import app.cashflow.core.designsystem.component.StatTile
import app.cashflow.core.designsystem.component.SummaryCard
import app.cashflow.core.designsystem.theme.Accent
import app.cashflow.core.designsystem.theme.CashFlowTheme
import app.cashflow.core.designsystem.theme.Spacing
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
    onOpenCategory: (categoryId: String, period: String) -> Unit,
    onOpenImport: () -> Unit,
    scrollToTop: Flow<Unit> = emptyFlow(),
    viewModel: FlowViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is FlowEvent.OpenCategory -> onOpenCategory(event.categoryId, event.period)
            FlowEvent.OpenImport -> onOpenImport()
        }
    }
    FlowScreen(state, viewModel::onAction, contentPadding, scrollToTop)
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
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(c.bgCanvas),
        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + Spacing.s4),
    ) {
        item(key = "header") {
            AccentHeader(
                accent = Accent.Cashflow,
                title = stringResource(R.string.flow_title),
                actions = {
                    if (state.hasData) {
                        BandPeriodStepper(
                            label = state.periodLabel,
                            previousLabel = stringResource(R.string.cd_previous_month, state.previousLabel),
                            nextLabel = stringResource(R.string.cd_next_month, state.nextLabel),
                            canGoBack = state.canGoBack,
                            canGoForward = state.canGoForward,
                            onPrevious = { onAction(FlowAction.OnPreviousPeriod) },
                            onNext = { onAction(FlowAction.OnNextPeriod) },
                        )
                    }
                },
            ) {
                if (state.hasData) {
                    val label = stringResource(R.string.flow_net_label, state.periodLabel)
                    val delta = state.delta?.asString()
                    SummaryCard(
                        label = label,
                        value = state.net,
                        delta = delta,
                        deltaPositive = state.deltaPositive,
                        accessibilityText = stringResource(R.string.flow_net_spoken, state.periodSpoken, state.netSpoken) + (delta?.let { ". $it" } ?: ""),
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
        }
        if (state.hasData) {
            item(key = "tiles") {
                Column(
                    Modifier.padding(horizontal = Spacing.gutter).padding(top = Spacing.s4),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s3),
                ) {
                    state.tiles.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                            row.forEach { t ->
                                val label = t.label.asString()
                                StatTile(
                                    label = label,
                                    value = t.value,
                                    sub = t.sub?.asString(),
                                    accentDot = t.tone.colors().strong,
                                    accessibilityText = "$label, ${t.spoken}" + (t.sub?.asString()?.let { ", $it" } ?: ""),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
            barSection("expenses", R.string.flow_expenses_by_category, state.expenseBars, onAction)
            barSection("income", R.string.flow_income_by_category, state.incomeBars, onAction)
            barSection("savings", R.string.flow_savings_by_category, state.savingsBars, onAction)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.barSection(
    key: String,
    title: Int,
    bars: List<CategoryBarUi>,
    onAction: (FlowAction) -> Unit,
) {
    if (bars.isEmpty()) return
    item(key = "bars-$key") {
        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            SectionHeader(stringResource(title))
            CfCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(Spacing.s2)) {
                Column {
                    bars.forEach { bar ->
                        val name = bar.name.asString()
                        CategoryBar(
                            name = name,
                            amount = bar.amount,
                            share = bar.share,
                            fraction = bar.fraction,
                            color = bar.tone.colors().strong,
                            accessibilityText = stringResource(R.string.cd_category_bar, name, bar.spokenAmount, bar.share),
                            onClick = { onAction(FlowAction.OnCategoryClick(bar.id)) },
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FlowScreenPreview() {
    app.cashflow.core.designsystem.theme.CashFlowTheme {
        FlowScreen(
            FlowState(
                periodLabel = "okt 2026", net = "+8 450 kr", delta = UiText.DynamicString("+1 830 kr mot sep 2026"),
                canGoBack = true, hasData = true, isLoading = false,
                tiles = listOf(
                    TileUi(UiText.DynamicString("Inkomster"), "48 200 kr", null, FlowTone.Income, ""),
                    TileUi(UiText.DynamicString("Utgifter"), "31 750 kr", UiText.DynamicString("Fasta 18 900 · Rörliga 12 850"), FlowTone.Fixed, ""),
                    TileUi(UiText.DynamicString("Sparande"), "8 000 kr", UiText.DynamicString("Sparkvot 16,6 %"), FlowTone.Savings, ""),
                    TileUi(UiText.DynamicString("Kvar på kontot"), "+450 kr", null, FlowTone.Neutral, ""),
                ),
                expenseBars = listOf(
                    CategoryBarUi("boende", UiText.DynamicString("Boende"), "12 200 kr", "38 %", 1f, FlowTone.Fixed, ""),
                    CategoryBarUi("mat", UiText.DynamicString("Mat"), "8 400 kr", "26 %", 0.69f, FlowTone.Variable, ""),
                ),
            ),
            onAction = {},
        )
    }
}
