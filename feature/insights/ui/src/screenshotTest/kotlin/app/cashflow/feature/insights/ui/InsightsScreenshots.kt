package app.cashflow.feature.insights.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.cashflow.core.designsystem.theme.CashFlowTheme
import app.cashflow.core.domain.cashflow.FlowData
import app.cashflow.core.domain.model.CategoryKind
import app.cashflow.core.domain.model.Goals
import app.cashflow.core.domain.model.Money
import app.cashflow.core.domain.model.MortgageSplit
import app.cashflow.core.domain.testing.SyntheticHousehold
import app.cashflow.core.ui.format.AppLocale
import com.android.tools.screenshot.PreviewTest

/** The nine window sizes from testing-setup, dark and 1.5× text. */
@Preview(locale = "sv", name = "400x500", widthDp = 400, heightDp = 500, showBackground = true)
@Preview(locale = "sv", name = "400x1000", widthDp = 400, heightDp = 1000, showBackground = true)
@Preview(locale = "sv", name = "610x1000", widthDp = 610, heightDp = 1000, showBackground = true)
@Preview(locale = "sv", name = "900x500", widthDp = 900, heightDp = 500, showBackground = true)
@Preview(locale = "sv", name = "dark", widthDp = 400, heightDp = 500, uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(locale = "sv", name = "font150", widthDp = 400, heightDp = 500, fontScale = 1.5f, showBackground = true)
annotation class InsightsSizes

/** The whole screen: plan, suggestions, Tänk om, benchmarks and the AI card. */
@Preview(locale = "sv", name = "full-light", widthDp = 400, heightDp = 3200, showBackground = true)
@Preview(locale = "sv", name = "full-dark", widthDp = 400, heightDp = 3200, uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
annotation class InsightsTall

private fun state(bufferNow: Money?): InsightsState {
    val txs = SyntheticHousehold.transactions(months = 14)
    val byId = SyntheticHousehold.categories.associateBy { it.id }
    val input = InsightsInput(
        data = FlowData.of(txs, MortgageSplit(), { byId[it]?.kind ?: CategoryKind.UNCATEGORIZED }),
        transactions = txs,
        categories = byId,
        goals = Goals(),
        bufferNow = bufferNow,
        done = emptySet(),
        percentDecimals = false,
    )
    val builder = InsightsBuilder(AppLocale.SV)
    return builder.build(input, builder.model(input), extraKronor = 2_000, sheet = null, copied = false)
}

@Composable
private fun Screen(bufferNow: Money?) {
    CashFlowTheme { InsightsScreen(state(bufferNow), onAction = {}) }
}

@PreviewTest @InsightsSizes @Composable fun Insights() = Screen(Money.kr(60_000))
@PreviewTest @InsightsTall @Composable fun InsightsFull() = Screen(Money.kr(60_000))
@PreviewTest @InsightsTall @Composable fun InsightsNoBalance() = Screen(null)
