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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(c.bgCanvas),
        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        item(key = "header") {
            AccentHeader(
                accent = Accent.Events,
                title = stringResource(R.string.review_title),
                actions = { BandIconButton(CfIcons.ChevronLeft, stringResource(R.string.cd_back), { onAction(ReviewAction.OnBackClick) }) },
            ) {
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
        }
        if (!state.isLoading && state.groups.isEmpty()) {
            item(key = "done") {
                CfCard(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter), color = c.surfaceStatus) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.review_done_title), style = CashFlowTheme.typography.title, color = c.textOnStatus)
                        Text(stringResource(R.string.review_done_body), style = CashFlowTheme.typography.body, color = c.textOnStatus)
                    }
                }
            }
        }
        items(state.groups, key = { it.key }) { group ->
            ReviewGroupCard(group, onAction, Modifier.padding(horizontal = Spacing.gutter).animateItem())
        }
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
                CfChip(
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
            CfButton(
                text = stringResource(R.string.review_approve),
                onClick = { onAction(ReviewAction.OnApprove(group.key)) },
                variant = CfButtonVariant.Accent,
                accent = c.actionEvents,
                icon = CfIcons.Check,
                enabled = group.selectedId != null && !group.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
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
