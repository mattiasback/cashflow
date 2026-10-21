package app.cashflow.core.data.mode

import android.content.Context
import androidx.core.content.edit
import app.cashflow.core.domain.model.AppMode

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
        context.deleteDatabase(DEMO_DATABASE)
        java.io.File(context.filesDir, "datastore/$DEMO_SETTINGS").delete()
        prefs.edit(commit = true) { putString(DEMO_MONTH, month) }
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
        const val DEMO_DATABASE = "cashflow-demo.db"
        const val DEMO_SETTINGS = "cashflow-demo-settings.json"
    }
}
