package app.cashflow

import android.content.Context
import android.content.Intent
import app.cashflow.core.data.mode.AppModeStore
import app.cashflow.core.domain.model.AppMode
import app.cashflow.core.domain.model.AppModeController
import kotlin.system.exitProcess

/**
 * Stores the new mode and restarts the app, so every database-backed singleton opens on the
 * other set of data. Nothing is copied or deleted: your data stays where it was.
 */
class RestartingModeController(
    private val context: Context,
    private val store: AppModeStore,
) : AppModeController {

    override val mode: AppMode get() = store.mode

    override fun switchTo(mode: AppMode) {
        if (mode == store.mode) return
        // The empty app always starts empty, as for a new user.
        if (mode == AppMode.EMPTY) store.clear(AppMode.EMPTY, context)
        store.set(mode)
        val restart = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(restart)
        exitProcess(0)
    }
}
