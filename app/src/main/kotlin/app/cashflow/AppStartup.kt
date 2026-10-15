package app.cashflow

import app.cashflow.core.domain.datasource.CategoryLocalDataSource
import app.cashflow.core.domain.datasource.RuleCatalog
import app.cashflow.core.importengine.ReclassifyUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Seeds the default category tree once (existing categories are kept) and re-applies
 * transfer matching and rules, so improved built-in rules in an app update reach rows that
 * were imported before it. Rows categorized by hand are never touched.
 */
class AppStartup(
    private val scope: CoroutineScope,
    private val categories: CategoryLocalDataSource,
    private val catalog: RuleCatalog,
    private val reclassify: ReclassifyUseCase,
) {
    fun run() {
        scope.launch {
            categories.ensureDefaults(catalog.defaultCategories())
            reclassify()
        }
    }
}
