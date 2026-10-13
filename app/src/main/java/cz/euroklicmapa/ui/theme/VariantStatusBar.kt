package cz.euroklicmapa.ui.theme

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import cz.euroklicmapa.data.prefs.DesignVariant

/** Keep system icons readable over light raster tiles and dark hero panels, including scrolling. */
@Composable
fun VariantStatusBar(color: Color = MaterialTheme.colorScheme.background) {
    if (EuroklicTheme.variant == DesignVariant.SOUCASNY) return
    val view = LocalView.current
    val window = (view.context as? Activity)?.window
    val useDarkIcons = color.luminance() > 0.179f
    val defaultDarkIcons = MaterialTheme.colorScheme.background.luminance() > 0.179f
    SideEffect {
        window?.let { WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = useDarkIcons }
    }
    DisposableEffect(window, defaultDarkIcons) {
        onDispose {
            window?.let { WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = defaultDarkIcons }
        }
    }
    Box(Modifier.fillMaxWidth().background(color).statusBarsPadding())
}
