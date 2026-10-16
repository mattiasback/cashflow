package app.cashflow.feature.rules.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cashflow.core.designsystem.component.AccentHeader
import app.cashflow.core.designsystem.component.BandIconButton
import app.cashflow.core.designsystem.component.CfButton
import app.cashflow.core.designsystem.component.CfButtonVariant
import app.cashflow.core.designsystem.component.CfCard
import app.cashflow.core.designsystem.component.GroupHeading
import app.cashflow.core.designsystem.component.Segment
import app.cashflow.core.designsystem.component.SegmentedControl
import app.cashflow.core.designsystem.icon.CfIcons
import app.cashflow.core.designsystem.theme.Accent
import app.cashflow.core.designsystem.theme.CashFlowTheme
import app.cashflow.core.designsystem.theme.Radius
import app.cashflow.core.designsystem.theme.Spacing
import app.cashflow.core.domain.model.RuleMatch
import app.cashflow.core.ui.FlowTone
import app.cashflow.core.ui.ObserveAsEvents
import app.cashflow.core.ui.UiText
import app.cashflow.core.ui.colors
import app.cashflow.feature.rules.ui.R
import app.cashflow.feature.rules.ui.RuleMatchUi
import app.cashflow.feature.rules.ui.asString
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun RulesRoot(
    contentPadding: PaddingValues,
    onOpenRule: (String?) -> Unit,
    onOpenMemory: (String) -> Unit,
    onOpenBuiltIn: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: RulesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is RulesEvent.OpenRule -> onOpenRule(event.ruleId)
            is RulesEvent.OpenMemory -> onOpenMemory(event.payeeKey)
            is RulesEvent.OpenBuiltIn -> onOpenBuiltIn(event.categoryId)
            RulesEvent.NavigateBack -> onBack()
        }
    }
    RulesScreen(state, viewModel::onAction, contentPadding)
}

@Composable
fun RulesScreen(
    state: RulesState,
    onAction: (RulesAction) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val c = CashFlowTheme.colors
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(c.bgCanvas),
        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + Spacing.s4),
    ) {
        item(key = "header") {
            AccentHeader(
                accent = Accent.Settings,
                title = stringResource(R.string.rules_title),
                actions = { BandIconButton(CfIcons.ChevronLeft, stringResource(R.string.cd_back), { onAction(RulesAction.OnBackClick) }) },
            ) {
                CfCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                        SegmentedControl(
                            segments = listOf(
                                Segment(RulesTab.MINE.name, stringResource(R.string.rules_tab_mine)),
                                Segment(RulesTab.BUILT_IN.name, stringResource(R.string.rules_tab_builtin)),
                            ),
                            selectedKey = state.tab.name,
                            onSelect = { onAction(RulesAction.OnTabSelect(RulesTab.valueOf(it))) },
                        )
                        Text(
                            stringResource(if (state.tab == RulesTab.MINE) R.string.rules_intro_mine else R.string.rules_intro_builtin),
                            style = CashFlowTheme.typography.label,
                            color = c.textSecondary,
                        )
                    }
                }
            }
        }
        item(key = "top") {
            Column(Modifier.padding(horizontal = Spacing.gutter)) {
                if (state.tab == RulesTab.MINE) {
                    CfButton(
                        stringResource(R.string.rules_new),
                        { onAction(RulesAction.OnNewRuleClick) },
                        Modifier.fillMaxWidth().padding(top = Spacing.s4),
                        variant = CfButtonVariant.Outline,
                        icon = CfIcons.Plus,
                    )
                    GroupHeading(stringResource(R.string.rules_group_mine, state.mineCount))
                } else {
                    GroupHeading(stringResource(R.string.rules_group_builtin))
                }
            }
        }
        if (state.rows.isEmpty() && !state.isLoading) {
            item(key = "empty") {
                Text(
                    stringResource(R.string.rules_empty),
                    style = CashFlowTheme.typography.body,
                    color = c.textSecondary,
                    modifier = Modifier.padding(horizontal = Spacing.gutter + Spacing.s1),
                )
            }
        }
        items(state.rows.size, key = { state.rows[it].key }) { i ->
            val row = state.rows[i]
            val first = i == 0
            val last = i == state.rows.lastIndex
            Box(Modifier.padding(horizontal = Spacing.gutter)) {
                RuleRow(row, first, last) { onAction(RulesAction.OnRowClick(row.key)) }
            }
        }
        item(key = "order") {
            Text(
                stringResource(R.string.rules_order),
                style = CashFlowTheme.typography.label,
                color = c.textSecondary,
                modifier = Modifier.padding(horizontal = Spacing.gutter + Spacing.s1, vertical = Spacing.s4),
            )
        }
    }
}

@Composable
private fun RuleRow(row: RuleRowUi, first: Boolean, last: Boolean, onClick: () -> Unit) {
    val c = CashFlowTheme.colors
    val shape = when {
        first && last -> Radius.sm
        first -> androidx.compose.foundation.shape.RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
        last -> androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
        else -> androidx.compose.ui.graphics.RectangleShape
    }
    val match = row.match.asString()
    val category = row.category.asString()
    val origin = row.origin.asString()
    val uses = row.uses.asString()
    Column(Modifier.fillMaxWidth().clip(shape).background(c.surfaceCard)) {
        if (!first) Box(Modifier.fillMaxWidth().padding(start = Spacing.s4).heightIn(min = 1.dp).background(c.divider))
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = "$match, $category. $origin, $uses" }
                .padding(horizontal = Spacing.s4, vertical = Spacing.s3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("$match → $category", style = CashFlowTheme.typography.body, color = c.textPrimary)
                Text("$origin · $uses", style = CashFlowTheme.typography.label, color = c.textSecondary)
            }
            Spacer(Modifier.width(Spacing.s2))
            Box(Modifier.size(10.dp).clip(Radius.xs).background(row.tone.colors().strong))
            Spacer(Modifier.width(Spacing.s2))
            Icon(CfIcons.ChevronRight, contentDescription = null, tint = c.textSecondary)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RulesScreenPreview() {
    CashFlowTheme {
        RulesScreen(
            RulesState(
                rows = listOf(
                    RuleRowUi(
                        "rule:1", RuleMatchUi(RuleMatch.STARTS_WITH, "ICA"), UiText.DynamicString("Mat"), FlowTone.Variable,
                        UiText.DynamicString("Skapad av dig 2026-03-02"), UiText.DynamicString("använd 48 gånger"),
                    ),
                    RuleRowUi(
                        "memory:RESTAURANG HAMNEN", RuleMatchUi(RuleMatch.EQUALS, "RESTAURANG HAMNEN"), UiText.DynamicString("Restaurang"), FlowTone.Variable,
                        UiText.DynamicString("Ihågkommen 2026-09-04"), UiText.DynamicString("använd 3 gånger"),
                    ),
                ),
                mineCount = 2,
                isLoading = false,
            ),
            onAction = {},
        )
    }
}
