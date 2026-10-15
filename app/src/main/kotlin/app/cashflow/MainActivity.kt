package app.cashflow

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import app.cashflow.core.designsystem.theme.CashFlowTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

class MainActivity : ComponentActivity() {

    private val sharedFiles = Channel<List<String>>(Channel.BUFFERED)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Every screen draws an accent band behind the status bar, so its icons are always light.
        // The navigation bar follows the theme.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // The floating nav draws its own fade over the navigation bar.
            window.isNavigationBarContrastEnforced = false
        }
        if (savedInstanceState == null) handleShare(intent)
        setContent {
            CashFlowTheme {
                CashFlowAppShell(sharedUris = sharedFiles.receiveAsFlow())
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /**
     * Accepts only bank exports shared from another app: ACTION_SEND(_MULTIPLE) with content
     * URIs of a statement MIME type that do not point back into this app (android-intent-security).
     */
    private fun handleShare(intent: Intent?) {
        intent ?: return
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> return
        }
        val type = intent.type.orEmpty()
        val allowedType = ALLOWED_TYPES.any { type.startsWith(it) } || type == "*/*"
        val valid = uris.filter { uri ->
            uri.scheme == "content" && uri.authority?.startsWith(packageName) != true
        }
        if (allowedType && valid.isNotEmpty()) sharedFiles.trySend(valid.map { it.toString() })
        // Never handle the same share twice (e.g. after a configuration change).
        setIntent(Intent(this, MainActivity::class.java))
    }

    private companion object {
        val ALLOWED_TYPES = listOf(
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/csv",
            "text/comma-separated-values",
            "application/octet-stream",
        )
    }
}
