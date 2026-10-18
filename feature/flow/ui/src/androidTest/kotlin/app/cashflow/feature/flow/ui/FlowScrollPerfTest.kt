package app.cashflow.feature.flow.ui

import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cashflow.core.designsystem.theme.CashFlowTheme
import app.cashflow.core.domain.cashflow.FlowData
import app.cashflow.core.domain.cashflow.PeriodMode
import app.cashflow.core.domain.model.CategoryKind
import app.cashflow.core.domain.model.Money
import app.cashflow.core.domain.model.MortgageSplit
import app.cashflow.core.domain.testing.SyntheticHousehold
import app.cashflow.core.ui.format.AppLocale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Scrolls Flöde with five years of made-up data using real input (adb `input swipe`), so the
 * frames are drawn as on a device. Frame stats from `dumpsys gfxinfo` are written to logcat
 * under the tag FlowPerf; record a Perfetto trace alongside to inspect individual frames.
 */
@RunWith(AndroidJUnit4::class)
class FlowScrollPerfTest {

    @get:Rule val compose = createComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val pkg = instrumentation.targetContext.packageName

    private fun shell(cmd: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(cmd)).bufferedReader().readText()

    /** Calibration: a plain list of text rows, to tell the device's own limit from Flöde's cost. */
    @Test
    fun scrollPlainList() {
        compose.setContent {
            CashFlowTheme {
                androidx.compose.foundation.lazy.LazyColumn {
                    items(400) { i -> androidx.compose.material3.Text("Rad $i", modifier = androidx.compose.ui.Modifier.padding(16.dp)) }
                }
            }
        }
        compose.waitForIdle()
        Thread.sleep(1500)
        shell("dumpsys gfxinfo $pkg reset")
        repeat(4) {
            shell("input swipe 540 1900 540 500 300")
            Thread.sleep(700)
        }
        repeat(4) {
            shell("input swipe 540 1600 540 2000 300")
            Thread.sleep(700)
        }
        Log.i("FlowPerf", "baseline list: " + summary(shell("dumpsys gfxinfo $pkg")))
    }

    private fun summary(stats: String) = stats.lines().filter { line ->
        listOf("Total frames rendered", "Janky frames", "50th percentile", "90th percentile", "95th percentile").any { it in line }
    }.take(6).joinToString(" | ") { it.trim() }

    @Test
    fun scrollFiveYears() {
        val txs = SyntheticHousehold.transactions(months = 60)
        val byId = SyntheticHousehold.categories.associateBy { it.id }
        val input = FlowInput(FlowData.of(txs, MortgageSplit(), { byId[it]?.kind ?: CategoryKind.UNCATEGORIZED }), txs, byId, Money.kr(120_000), false)
        val builder = FlowBuilder(AppLocale.SV)
        var selection by mutableStateOf(FlowSelection())
        compose.setContent {
            CashFlowTheme {
                FlowScreen(builder.build(input, selection), onAction = { a ->
                    if (a is FlowAction.OnTabSelect) selection = selection.copy(tab = a.tab)
                    if (a is FlowAction.OnPeriodSelect) selection = selection.copy(month = app.cashflow.core.domain.model.Period.parse(a.key))
                })
            }
        }
        compose.waitForIdle()
        val modes = listOf(FlowTab.CASHFLOW to PeriodMode.MONTH, FlowTab.EXPENSE to PeriodMode.MONTH, FlowTab.CASHFLOW to PeriodMode.ALL)
        modes.forEach { (tab, mode) ->
            compose.runOnUiThread { selection = FlowSelection(tab = tab, mode = mode) }
            compose.waitForIdle()
            Thread.sleep(1500)
            shell("dumpsys gfxinfo $pkg reset")
            repeat(4) {
                shell("input swipe 540 1900 540 500 300")
                Thread.sleep(700)
            }
            // Swipe the bar chart sideways through older months, then scroll back up.
            repeat(4) {
                shell("input swipe 540 1600 540 2000 300")
                Thread.sleep(700)
            }
            val stats = shell("dumpsys gfxinfo $pkg")
            val summary = stats.lines().filter { line ->
                listOf("Total frames rendered", "Janky frames", "50th percentile", "90th percentile", "95th percentile", "99th percentile").any { it in line }
            }.take(6)
            Log.i("FlowPerf", "$tab/$mode with ${txs.size} rows: " + summary.joinToString(" | ") { it.trim() })
        }
    }
}
