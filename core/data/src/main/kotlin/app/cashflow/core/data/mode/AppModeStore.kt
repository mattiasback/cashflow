package app.cashflow.core.data.mode

import android.content.Context
import androidx.core.content.edit
import app.cashflow.core.data.settings.DataStoreSettingsDataSource
import app.cashflow.core.database.CashFlowDatabase
import app.cashflow.core.domain.model.AppMode
import java.io.File

/**
 * The chosen mode, read synchronously at start because it decides which database file opens.
 * Plain SharedPreferences: it is one word, and the settings DataStore itself is per mode.
 */
class AppModeStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("cashflow-mode", Context.MODE_PRIVATE)

    val mode: AppMode get() = AppMode.entries.firstOrNull { it.name == prefs.getString(KEY, null) } ?: AppMode.PRIVATE

    /**
     * The demo always ends with the current month, so a new month starts it afresh: the demo
     * database and settings are removed before they open, and the household is seeded again.
     */
    fun refreshDemoFor(month: String, context: Context) {
        if (prefs.getString(DEMO_MONTH, null) == month) return
        clear(AppMode.DEMO, context)
        prefs.edit(commit = true) { putString(DEMO_MONTH, month) }
    }

    /** Removes a mode's database and settings. Never called for [AppMode.PRIVATE]. */
    fun clear(mode: AppMode, context: Context) {
        require(mode != AppMode.PRIVATE) { "Your own data is never cleared here" }
        context.deleteDatabase(databaseFile(mode))
        File(context.filesDir, "datastore/${settingsFile(mode)}").delete()
    }

    /** Written synchronously, since the process restarts right after. */
    fun set(mode: AppMode) = prefs.edit(commit = true) { putString(KEY, mode.name) }

    val debugSlow: Boolean get() = prefs.getBoolean(DEBUG_SLOW, false)
    val debugFail: Boolean get() = prefs.getBoolean(DEBUG_FAIL, false)

    fun setDebug(slow: Boolean, fail: Boolean) = prefs.edit { putBoolean(DEBUG_SLOW, slow).putBoolean(DEBUG_FAIL, fail) }

    companion object {
        private const val KEY = "mode"
        private const val DEBUG_SLOW = "debug_slow"
        private const val DEMO_MONTH = "demo_month"
        private const val DEBUG_FAIL = "debug_fail"

        /** Each mode has its own files, so the demo and the empty app never touch your data. */
        fun databaseFile(mode: AppMode) = when (mode) {
            AppMode.PRIVATE -> CashFlowDatabase.FILE_NAME
            AppMode.DEMO -> "cashflow-demo.db"
            AppMode.EMPTY -> "cashflow-empty.db"
        }

        fun settingsFile(mode: AppMode) = when (mode) {
            AppMode.PRIVATE -> DataStoreSettingsDataSource.FILE_NAME
            AppMode.DEMO -> "cashflow-demo-settings.json"
            AppMode.EMPTY -> "cashflow-empty-settings.json"
        }
    }
}
