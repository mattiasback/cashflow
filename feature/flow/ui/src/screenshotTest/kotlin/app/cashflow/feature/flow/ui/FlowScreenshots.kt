package app.cashflow.feature.flow.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.cashflow.core.designsystem.theme.CashFlowTheme
import app.cashflow.core.domain.cashflow.FlowData
import app.cashflow.core.domain.model.CategoryKind
import app.cashflow.core.domain.model.Money
import app.cashflow.core.domain.model.MortgageSplit
import app.cashflow.core.domain.testing.SyntheticHousehold
import app.cashflow.core.ui.format.AppLocale
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.cashflow.core.designsystem.component.LocalNavRailInset
import app.cashflow.core.designsystem.component.NavItem
import app.cashflow.core.designsystem.component.NavRailPill
import app.cashflow.core.designsystem.component.NavRailWidth
import app.cashflow.core.designsystem.icon.CfIcons
import app.cashflow.core.designsystem.theme.Accent

import com.android.tools.screenshot.PreviewTest

/** The nine window sizes from testing-setup: widths 400/610/900 × heights 400/500/1000 dp. */
@Preview(locale = "sv", name = "400x400", widthDp = 400, heightDp = 400, showBackground = true)
@Preview(locale = "sv", name = "400x500", widthDp = 400, heightDp = 500, showBackground = true)
@Preview(locale = "sv", name = "400x1000", widthDp = 400, heightDp = 1000, showBackground = true)
@Preview(locale = "sv", name = "610x400", widthDp = 610, heightDp = 400, showBackground = true)
@Preview(locale = "sv", name = "610x500", widthDp = 610, heightDp = 500, showBackground = true)
@Preview(locale = "sv", name = "610x1000", widthDp = 610, heightDp = 1000, showBackground = true)
@Preview(locale = "sv", name = "900x400", widthDp = 900, heightDp = 400, showBackground = true)
@Preview(locale = "sv", name = "900x500", widthDp = 900, heightDp = 500, showBackground = true)
@Preview(locale = "sv", name = "900x1000", widthDp = 900, heightDp = 1000, showBackground = true)
@Preview(locale = "sv", name = "dark", widthDp = 400, heightDp = 500, uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(locale = "sv", name = "font150", widthDp = 400, heightDp = 500, fontScale = 1.5f, showBackground = true)
@Preview(locale = "sv", name = "font200", widthDp = 400, heightDp = 900, fontScale = 2f, showBackground = true)
annotation class FlowSizes

/** A tall render of each tab so the lower sections (donut, Sankey, bars, latest) are checked too. */
@Preview(locale = "sv", name = "full-light", widthDp = 400, heightDp = 2600, showBackground = true)
@Preview(locale = "sv", name = "full-dark", widthDp = 400, heightDp = 2600, uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
annotation class FlowFull

private val synthetic by lazy {
    val txs = SyntheticHousehold.transactions(months = 14)
    val byId = SyntheticHousehold.categories.associateBy { it.id }
    FlowInput(
        FlowData.of(txs, MortgageSplit(), { byId[it]?.kind ?: CategoryKind.UNCATEGORIZED }),
        txs, byId, Money.kr(120_000), percentDecimals = false,
    )
}

private fun state(tab: FlowTab) = FlowBuilder(AppLocale.SV).build(synthetic, FlowSelection(tab = tab))

@Composable
private fun Screen(tab: FlowTab) {
    CashFlowTheme { FlowScreen(state(tab), onAction = {}) }
}

@PreviewTest @FlowSizes @Composable fun CashflowTab() = Screen(FlowTab.CASHFLOW)
@PreviewTest @FlowSizes @Composable fun IncomeTab() = Screen(FlowTab.INCOME)
@PreviewTest @FlowSizes @Composable fun ExpenseTab() = Screen(FlowTab.EXPENSE)
@PreviewTest @FlowSizes @Composable fun SavingsTab() = Screen(FlowTab.SAVINGS)

@PreviewTest @FlowFull @Composable fun CashflowTabFull() = Screen(FlowTab.CASHFLOW)
@PreviewTest @FlowFull @Composable fun IncomeTabFull() = Screen(FlowTab.INCOME)
@PreviewTest @FlowFull @Composable fun ExpenseTabFull() = Screen(FlowTab.EXPENSE)
@PreviewTest @FlowFull @Composable fun SavingsTabFull() = Screen(FlowTab.SAVINGS)

/** Tablet, foldable and landscape: the app's side rail at the start edge, as the shell draws it. */
@Preview(locale = "sv", name = "tablet", device = "spec:width=1280dp,height=800dp,dpi=240", showBackground = true)
@Preview(locale = "sv", name = "tablet-portrait", device = "spec:width=800dp,height=1280dp,dpi=240", showBackground = true)
@Preview(locale = "sv", name = "foldable", device = "spec:width=673dp,height=841dp,dpi=420", showBackground = true)
@Preview(locale = "sv", name = "tablet-dark", device = "spec:width=1280dp,height=800dp,dpi=240", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
annotation class TabletSizes

@Composable
private fun WithRail(selected: String, content: @Composable () -> Unit) {
    val items = listOf(
        NavItem("home", "Hem", CfIcons.Home, Accent.Home),
        NavItem("flow", "Flöde", CfIcons.Cashflow, Accent.Cashflow),
        NavItem("insights", "Insikter", CfIcons.Bulb, Accent.Insights),
        NavItem("events", "Händelser", CfIcons.Transfer, Accent.Events),
    )
    Box(Modifier.fillMaxSize().background(CashFlowTheme.colors.bgCanvas)) {
        CompositionLocalProvider(LocalNavRailInset provides NavRailWidth) { content() }
        NavRailPill(items, selected, {}, Modifier.align(Alignment.CenterStart))
    }
}

@PreviewTest @TabletSizes @Composable fun CashflowTabTablet() = CashFlowTheme { WithRail("flow") { FlowScreen(state(FlowTab.CASHFLOW), onAction = {}) } }
@PreviewTest @TabletSizes @Composable fun ExpenseTabTablet() = CashFlowTheme { WithRail("flow") { FlowScreen(state(FlowTab.EXPENSE), onAction = {}) } }

/** Loading and "Försök igen": the states that are too quick to see on a phone. */
@PreviewTest @Preview(locale = "sv", name = "loading", widthDp = 400, heightDp = 900, showBackground = true) @Composable fun FlowLoading() = CashFlowTheme { FlowScreen(FlowState(isLoading = true), onAction = {}) }
@PreviewTest @Preview(locale = "sv", name = "error", widthDp = 400, heightDp = 900, showBackground = true) @Composable fun FlowError() = CashFlowTheme { FlowScreen(FlowState(isLoading = false, loadError = true), onAction = {}) }
