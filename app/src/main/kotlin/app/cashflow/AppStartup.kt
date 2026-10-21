package app.cashflow

import app.cashflow.core.demo.DemoSeeder
import app.cashflow.core.domain.datasource.CategoryLocalDataSource
import app.cashflow.core.domain.datasource.RuleCatalog
import app.cashflow.core.domain.model.AppMode
import app.cashflow.core.domain.model.AppModeController
import app.cashflow.core.importengine.ReclassifyUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * Seeds the default category tree once (existing categories are kept) and re-applies
 * transfer matching and rules, so improved built-in rules in an app update reach rows that
 * were imported before it. Rows categorized by hand are never touched. In demo mode the
 * made-up household is imported first, the first time.
 */
class AppStartup(
    private val scope: CoroutineScope,
    private val categories: CategoryLocalDataSource,
    private val catalog: RuleCatalog,
    private val reclassify: ReclassifyUseCase,
    private val mode: AppModeController,
    private val demo: DemoSeeder,
) {
    fun run() {
        scope.launch {
            if (mode.mode == AppMode.DEMO) demo.seedIfEmpty(Clock.System.todayIn(TimeZone.currentSystemDefault()))
            categories.ensureDefaults(catalog.defaultCategories())
            reclassify()
        }
    }
}
