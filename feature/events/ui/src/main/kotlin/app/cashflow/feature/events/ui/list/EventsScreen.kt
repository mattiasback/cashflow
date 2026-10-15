package app.cashflow.feature.events.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cashflow.core.designsystem.component.AccentHeader
import app.cashflow.core.designsystem.component.CfButton
import app.cashflow.core.designsystem.component.CfButtonVariant
import app.cashflow.core.designsystem.component.CfCard
import app.cashflow.core.designsystem.component.CfChip
import app.cashflow.core.designsystem.component.FilterButton
import app.cashflow.core.designsystem.component.ListGroup
import app.cashflow.core.designsystem.component.SearchField
import app.cashflow.core.designsystem.component.SectionHeader
import app.cashflow.core.designsystem.component.SheetHeader
import app.cashflow.core.designsystem.icon.CfIcons
import app.cashflow.core.designsystem.theme.Accent
import app.cashflow.core.designsystem.theme.CashFlowTheme
import app.cashflow.core.designsystem.theme.Radius
import app.cashflow.core.designsystem.theme.Sizes
import app.cashflow.core.designsystem.theme.Spacing
import app.cashflow.core.ui.FlowTone
import app.cashflow.core.ui.ObserveAsEvents
import app.cashflow.core.ui.UiText
import app.cashflow.core.ui.colors
import app.cashflow.feature.events.ui.R
import app.cashflow.feature.events.ui.common.TxDayUi
import app.cashflow.feature.events.ui.common.TxRow
import app.cashflow.feature.events.ui.common.TxRowUi
import app.cashflow.core.designsystem.component.BadgeKind
import app.cashflow.core.designsystem.theme.CashFlowTheme as Theme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun EventsRoot(
    contentPadding: PaddingValues,
    onOpenDetail: (String) -> Unit,
    onOpenImport: () -> Unit,
    onOpenReview: () -> Unit,
    scrollToTop: Flow<Unit> = emptyFlow(),
    initialCategory: String? = null,
    initialPeriod: String? = null,
    viewModel: EventsViewModel = koinViewModel(key = "events-$initialCategory-$initialPeriod") {
        org.koin.core.parameter.parametersOf(*listOfNotNull(initialCategory, initialPeriod).toTypedArray())
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is EventsEvent.NavigateToDetail -> onOpenDetail(event.txId)
            EventsEvent.OpenImport -> onOpenImport()
            EventsEvent.OpenReview -> onOpenReview()
        }
    }
    EventsScreen(state = state, onAction = viewModel::onAction, contentPadding = contentPadding, scrollToTop = scrollToTop)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(
    state: EventsState,
    onAction: (EventsAction) -> Unit,
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
            AccentHeader(accent = Accent.Events) {
                ImportCard(state, onAction)
            }
        }
        if (state.reviewCount > 0) {
            item(key = "review") {
                Column(Modifier.padding(horizontal = Spacing.gutter)) {
                    SectionHeader(stringResource(R.string.section_review)) {
                        Text(
                            UiText.PluralResource(R.plurals.review_left, state.reviewCount).asString(),
                            style = Theme.typography.label,
                            color = c.textSecondary,
                        )
                    }
                    ReviewCard(state, onAction)
                }
            }
        }
        item(key = "tx-title") {
            Column(Modifier.padding(horizontal = Spacing.gutter)) {
                SectionHeader(stringResource(R.string.section_transactions)) {
                    state.countText?.let { Text(it.asString(), style = Theme.typography.label, color = c.textSecondary) }
                }
                SearchField(
                    value = state.query,
                    onValueChange = { onAction(EventsAction.OnQueryChange(it)) },
                    placeholder = stringResource(R.string.search_placeholder),
                )
            }
        }
        item(key = "chips") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.gutter),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
                modifier = Modifier.padding(top = Spacing.s3, bottom = Spacing.s3),
            ) {
                items(StatusFilter.entries, key = { it.name }) { f ->
                    CfChip(
                        label = stringResource(f.label()),
                        selected = state.statusFilter == f,
                        onClick = { onAction(EventsAction.OnStatusChipClick(f)) },
                    )
                }
            }
        }
        item(key = "filters") {
            val periodValue = state.periodLabel?.asString().orEmpty()
            val categoryValue = state.categoryLabel?.asString().orEmpty()
            Row(
                Modifier.padding(horizontal = Spacing.gutter).padding(bottom = Spacing.s4),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
            ) {
                FilterButton(
                    label = stringResource(R.string.filter_period),
                    value = periodValue,
                    active = state.periodActive,
                    accessibilityLabel = stringResource(R.string.cd_filter_period, periodValue),
                    onClick = { onAction(EventsAction.OnPeriodFilterClick) },
                    modifier = Modifier.weight(1f),
                )
                FilterButton(
                    label = stringResource(R.string.filter_category),
                    value = categoryValue,
                    active = state.categoryActive,
                    accessibilityLabel = stringResource(R.string.cd_filter_category, categoryValue),
                    onClick = { onAction(EventsAction.OnCategoryFilterClick) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (!state.isLoading && state.days.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(if (state.hasTransactions) R.string.empty_filtered else R.string.empty_none),
                    style = Theme.typography.body,
                    color = c.textSecondary,
                    modifier = Modifier.fillMaxWidth().padding(Spacing.s6),
                )
            }
        }
        state.days.forEach { day ->
            item(key = "day-${day.key}") {
                Text(
                    day.heading,
                    style = Theme.typography.label,
                    color = c.textSecondary,
                    modifier = Modifier
                        .padding(horizontal = Spacing.gutter + Spacing.s1)
                        .padding(top = Spacing.s3, bottom = Spacing.s2)
                        .semantics { heading() },
                )
            }
            item(key = "rows-${day.key}") {
                ListGroup(Modifier.padding(horizontal = Spacing.gutter)) {
                    day.rows.forEachIndexed { i, row ->
                        TxRow(row = row, showDivider = i > 0, onClick = { onAction(EventsAction.OnTransactionClick(row.id)) })
                    }
                }
            }
        }
    }

    when (state.sheet) {
        EventsSheet.CATEGORY -> ModalBottomSheet(
            onDismissRequest = { onAction(EventsAction.OnSheetDismiss) },
            containerColor = c.surfaceSheet,
        ) {
            FilterSheetContent(
                title = stringResource(R.string.sheet_pick_category),
                options = state.categoryOptions.map { o ->
                    OptionRow(o.id, o.name, o.group, o.tone, o.count.takeIf { it > 0 }?.toString() ?: "–", o.selected)
                },
                onPick = { onAction(EventsAction.OnCategoryPicked(it)) },
            )
        }
        EventsSheet.PERIOD -> ModalBottomSheet(
            onDismissRequest = { onAction(EventsAction.OnSheetDismiss) },
            containerColor = c.surfaceSheet,
        ) {
            FilterSheetContent(
                title = stringResource(R.string.sheet_pick_period),
                options = state.periodOptions.map { OptionRow(it.key, it.label, null, null, null, it.selected) },
                onPick = { onAction(EventsAction.OnPeriodPicked(it)) },
            )
        }
        null -> Unit
    }
}

private fun StatusFilter.label() = when (this) {
    StatusFilter.ALL -> R.string.filter_all
    StatusFilter.REVIEW -> R.string.status_review
    StatusFilter.AUTO -> R.string.status_auto
    StatusFilter.MANUAL -> R.string.status_manual
    StatusFilter.TRANSFER -> R.string.status_transfer
}

/** The step 1 "Hämta transaktioner" card: file import (bank sync arrives in step 6). */
@Composable
private fun ImportCard(state: EventsState, onAction: (EventsAction) -> Unit) {
    val c = CashFlowTheme.colors
    CfCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
                Text(stringResource(R.string.import_eyebrow).uppercase(), style = Theme.typography.overline, color = c.textSecondary)
                Text(stringResource(R.string.import_title), style = Theme.typography.titleL, color = c.textPrimary, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.import_body), style = Theme.typography.body, color = c.textSecondary)
            }
            val importLabel = stringResource(R.string.import_button)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(Radius.sm)
                    .border(1.5.dp, c.borderChip, Radius.sm)
                    .clickable(role = Role.Button, onClickLabel = importLabel) { onAction(EventsAction.OnImportClick) }
                    .padding(Spacing.s4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(40.dp).clip(Radius.pill).background(c.accentEventsTint),
                    contentAlignment = Alignment.Center,
                ) { Icon(CfIcons.File, contentDescription = null, tint = c.textOnEventsTint, modifier = Modifier.size(20.dp)) }
                Spacer(Modifier.width(Spacing.s3))
                Column(Modifier.weight(1f)) {
                    Text(importLabel, style = Theme.typography.bodyStrong, color = c.textPrimary)
                    Text(stringResource(R.string.import_hint), style = Theme.typography.label, color = c.textSecondary)
                }
            }
            state.lastImport?.let { last ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(Radius.pill).background(c.accentEventsInk))
                    Spacer(Modifier.width(Spacing.s2))
                    Text(
                        stringResource(R.string.last_import, last.fileName) + " · " + last.meta.asString(),
                        style = Theme.typography.label,
                        color = c.textSecondary,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewCard(state: EventsState, onAction: (EventsAction) -> Unit) {
    val c = CashFlowTheme.colors
    CfCard(Modifier.fillMaxWidth(), color = c.surfaceCelebrate) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
            Text(
                UiText.PluralResource(R.plurals.review_summary, state.reviewCount, arrayOf(state.reviewCount, state.reviewPayees)).asString(),
                style = Theme.typography.body,
                color = c.textOnCelebrate,
            )
            CfButton(
                text = stringResource(R.string.review_now),
                onClick = { onAction(EventsAction.OnReviewClick) },
                variant = CfButtonVariant.Accent,
                accent = c.accentEventsInk,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private data class OptionRow(
    val key: String?,
    val label: UiText,
    val group: UiText?,
    val tone: FlowTone?,
    val trailing: String?,
    val selected: Boolean,
)

@Composable
private fun FilterSheetContent(title: String, options: List<OptionRow>, onPick: (String?) -> Unit) {
    val c = CashFlowTheme.colors
    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = Spacing.s4, end = Spacing.s4, bottom = Spacing.s6),
    ) {
        item { SheetHeader(title = title, eyebrow = stringResource(R.string.sheet_filter_eyebrow), modifier = Modifier.padding(bottom = Spacing.s3)) }
        var lastGroup: UiText? = null
        options.forEach { o ->
            if (o.group != null && o.group != lastGroup) {
                val g = o.group
                item(key = "g-${o.key}") {
                    Text(g.asString().uppercase(), style = Theme.typography.overline, color = c.textSecondary, modifier = Modifier.padding(top = Spacing.s4, bottom = Spacing.s1))
                }
                lastGroup = o.group
            }
            item(key = "o-${o.key}") {
                val label = o.label.asString()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Sizes.touchMin + 4.dp)
                        .clip(Radius.sm)
                        .clickable(role = Role.RadioButton) { onPick(o.key) }
                        .semantics { selected = o.selected; contentDescription = label }
                        .padding(horizontal = Spacing.s2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    o.tone?.let { Box(Modifier.size(12.dp).clip(Radius.xs).background(it.colors().strong)) }
                    if (o.tone != null) Spacer(Modifier.width(Spacing.s3))
                    Text(label, style = Theme.typography.body, color = c.textPrimary, modifier = Modifier.weight(1f))
                    o.trailing?.let { Text(it, style = Theme.typography.label, color = c.textSecondary) }
                    Spacer(Modifier.width(Spacing.s3))
                    if (o.selected) Icon(CfIcons.Check, contentDescription = null, tint = c.accentEventsInk, modifier = Modifier.size(20.dp))
                    else Spacer(Modifier.size(20.dp))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EventsScreenPreview() {
    app.cashflow.core.designsystem.theme.CashFlowTheme {
        EventsScreen(
            state = EventsState(
                isLoading = false,
                hasTransactions = true,
                reviewCount = 12,
                reviewPayees = 5,
                periodLabel = UiText.DynamicString("okt 2026"),
                categoryLabel = UiText.DynamicString("Alla"),
                countText = UiText.DynamicString("3 st"),
                days = listOf(
                    TxDayUi(
                        "2026-10-12", "mån 12 okt",
                        listOf(
                            TxRowUi("1", "Matbutik Exempel", "2026-10-12", UiText.DynamicString("Mat"), "−412 kr", false, FlowTone.Variable, UiText.DynamicString("Automatisk"), BadgeKind.Auto, "minus 412 kronor"),
                            TxRowUi("2", "Swish till Vän", "2026-10-12", UiText.DynamicString("Ej kategoriserat"), "−350 kr", false, FlowTone.Neutral, UiText.DynamicString("Att granska"), BadgeKind.Review, "minus 350 kronor"),
                        ),
                    ),
                    TxDayUi(
                        "2026-10-10", "lör 10 okt",
                        listOf(TxRowUi("3", "Lön", "2026-10-10", UiText.DynamicString("Lön"), "+25 000 kr", true, FlowTone.Income, UiText.DynamicString("Automatisk"), BadgeKind.Auto, "25 000 kronor")),
                    ),
                ),
            ),
            onAction = {},
        )
    }
}
