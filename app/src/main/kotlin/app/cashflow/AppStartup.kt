package app.cashflow

import app.cashflow.core.domain.datasource.CategoryLocalDataSource
import app.cashflow.core.domain.datasource.RuleCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Seeds the default category tree once; existing categories are kept. */
class AppStartup(
    private val scope: CoroutineScope,
    private val categories: CategoryLocalDataSource,
    private val catalog: RuleCatalog,
) {
    fun run() {
        scope.launch { categories.ensureDefaults(catalog.defaultCategories()) }
    }
}
