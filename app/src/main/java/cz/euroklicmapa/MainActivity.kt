package cz.euroklicmapa

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cz.euroklicmapa.data.prefs.ThemeMode
import cz.euroklicmapa.ui.screens.MainScreen
import cz.euroklicmapa.ui.theme.EuroklicTheme

/** Static app-shortcut action — must match the intent in res/xml/shortcuts.xml. */
private const val ACTION_NEAREST_WC = "cz.euroklicmapa.action.NEAREST_WC"

/** Notification tap routing — keys must match [cz.euroklicmapa.notifications.QueuePollWorker]. */
private const val EXTRA_NAV = "nav"
private const val NAV_ADMIN_QUEUE = "admin_queue"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleAuthCallback(intent)
        handleShortcutIntent(intent)
        handleNavIntent(intent)
        val themeRepository = (application as EuroklicApplication).themeRepository
        setContent {
            val mode by themeRepository.mode.collectAsState(initial = ThemeMode.SYSTEM)
            val dark = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            EuroklicTheme(darkTheme = dark) {
                MainScreen()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthCallback(intent)
        handleShortcutIntent(intent)
        handleNavIntent(intent)
    }

    /**
     * Notification tap routing. The "Čeká na schválení" notification carries `nav=admin_queue`;
     * a pending flag on the Application is consumed once by [MainScreen], mirroring the launcher
     * shortcut pattern above. The "nearby" notification has no extra — it just opens the app.
     */
    private fun handleNavIntent(intent: Intent?) {
        if (intent?.getStringExtra(EXTRA_NAV) == NAV_ADMIN_QUEUE) {
            (application as EuroklicApplication).requestAdminQueueNav()
        }
    }

    /**
     * Static launcher shortcut "Nejbližší WC" (res/xml/shortcuts.xml) — cold start lands in
     * [intent] here, warm start in [onNewIntent]. MapScreen consumes the pending flag.
     */
    private fun handleShortcutIntent(intent: Intent?) {
        if (intent?.action == ACTION_NEAREST_WC) {
            (application as EuroklicApplication).requestNearestShortcut()
        }
    }

    /**
     * OAuth callback — either the verified App Link
     * `https://euroklic.odjezdy.online/app/auth-callback` or the `euroklicmapa://auth-callback`
     * custom-scheme fallback, carrying `?code=…&state=…` (or the legacy `?token=…`).
     */
    private fun handleAuthCallback(intent: Intent?) {
        val data = intent?.data ?: return
        val isCustomScheme = data.scheme == "euroklicmapa" && data.host == "auth-callback"
        val isAppLink = data.scheme == "https" &&
            data.host == "euroklic.odjezdy.online" &&
            data.path == "/app/auth-callback"
        if (isCustomScheme || isAppLink) {
            (application as EuroklicApplication).authRepository.handleCallback(data)
        }
    }
}
