package cz.euroklicmapa.ui.map

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/** Non-interactive locator map for the detail screen — shows "where it is", eats gestures. */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun MiniMap(lat: Double, lon: Double, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val mapView = remember {
        Configuration.getInstance().load(
            context,
            context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE),
        )
        MapView(context).apply {
            setTileSource(MapyCzTileSource())
            setMultiTouchControls(false)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false
            controller.setZoom(16.0)
            setOnTouchListener { _, _ -> true } // let the parent scroll instead
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { mv ->
            val point = GeoPoint(lat, lon)
            mv.controller.setCenter(point)
            mv.overlays.removeAll { it is Marker }
            mv.overlays.add(
                Marker(mv).apply {
                    position = point
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = UserLocationIcon.build(mv.context)
                    setInfoWindow(null)
                },
            )
            mv.invalidate()
        },
    )

    DisposableEffect(Unit) {
        onDispose { mapView.onDetach() }
    }
}
