package app.cashflow.core.domain.model

/**
 * Whose data the app shows: yours, the made-up demo household, or an empty app as a new user
 * sees it before the first import. Each has its own database.
 */
enum class AppMode { PRIVATE, DEMO, EMPTY }

/**
 * Switches between your data and the demo. Each mode has its own database and settings, so
 * the demo never touches your rows; switching restarts the app on the other set.
 */
interface AppModeController {
    val mode: AppMode
    fun switchTo(mode: AppMode)
}
