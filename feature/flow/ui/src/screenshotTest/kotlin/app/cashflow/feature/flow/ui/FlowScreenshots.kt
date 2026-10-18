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
