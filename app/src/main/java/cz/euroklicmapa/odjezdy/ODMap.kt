package cz.euroklicmapa.odjezdy

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.Modifier
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import cz.euroklicmapa.ui.map.MapyCzTileSource
import org.osmdroid.config.Configuration
import org.osmdroid.events.DelayedMapListener
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import java.io.File

/** Brand-independent colours per vehicle type (match the web: bus blue, tram red, train green). */
fun vehicleColor(type: String?): Int = when (ODVehicleType.of(type)) {
    ODVehicleType.BUS -> 0xFF1D5FD1.toInt()
    ODVehicleType.TRAM -> 0xFFC4161C.toInt()
    ODVehicleType.TROLLEYBUS -> 0xFF8A3FC0.toInt()
    ODVehicleType.TRAIN -> 0xFF0B7A4B.toInt()
    ODVehicleType.SUBWAY -> 0xFFE08A00.toInt()
    ODVehicleType.FERRY -> 0xFF0F8FA8.toInt()
}

/** Draws and caches the small coloured "line number" pills used as vehicle markers. */
private object PillFactory {
    private val cache = HashMap<String, BitmapDrawable>()

    fun pill(context: Context, type: String?, line: String, delayed: Boolean): BitmapDrawable =
        cache.getOrPut("$type|$line|$delayed") {
            val d = context.resources.displayMetrics.density
            val text = line.take(5)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFFFFF.toInt()
                textSize = 11f * d
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val w = maxOf(26f * d, paint.measureText(text) + 14f * d)
            val h = 20f * d
            val bmp = Bitmap.createBitmap(w.toInt() + 4, h.toInt() + 4, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val body = RectF(2f, 2f, w + 2f, h + 2f)
            c.drawRoundRect(body, h / 2, h / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = vehicleColor(type) })
            c.drawRoundRect(
                body, h / 2, h / 2,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 2f * d / 1.5f
                    color = if (delayed) 0xFFFFC400.toInt() else 0xFFFFFFFF.toInt()
                },
            )
            c.drawText(text, bmp.width / 2f, bmp.height / 2f - (paint.descent() + paint.ascent()) / 2f, paint)
            BitmapDrawable(context.resources, bmp)
        }

    fun stopDot(context: Context): BitmapDrawable = cache.getOrPut("stop") {
        val d = context.resources.displayMetrics.density
        val size = (14f * d).toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawCircle(size / 2f, size / 2f, size / 2f - 1f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
        c.drawCircle(
            size / 2f, size / 2f, size / 2f - 3f * d / 1.5f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF16396B.toInt() },
        )
        BitmapDrawable(context.resources, bmp)
    }
}

/** osmdroid map for the Odjezdy mode: vehicle pills, stop dots (zoomed in) and the selected vehicle's route. */
@Composable
fun ODMap(
    state: ODMapState,
    center: GeoPoint?,
    myLocation: GeoPoint?,
    onViewport: (org.osmdroid.util.BoundingBox, Double) -> Unit,
    onVehicle: (ODVehicle) -> Unit,
    onStop: (ODStop) -> Unit,
    onMapClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val onViewportState = rememberUpdatedState(onViewport)
    val onMapClickState = rememberUpdatedState(onMapClick)
    var lastCenter by remember { mutableStateOf<GeoPoint?>(null) }
    val dynamic = remember { mutableListOf<Overlay>() }

    val mapView = remember {
        Configuration.getInstance().apply {
            load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            userAgentValue = context.packageName
            osmdroidBasePath = File(context.filesDir, "osmdroid")
            osmdroidTileCache = File(osmdroidBasePath, "tiles")
            expirationOverrideDuration = 30L * 24 * 60 * 60 * 1000
            tileDownloadThreads = 8
        }
        MapView(context).apply {
            setTileSource(MapyCzTileSource())
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = 5.0
            maxZoomLevel = 19.0
            controller.setZoom(14.0)
            controller.setCenter(myLocation ?: GeoPoint(50.0755, 14.4378))
            overlays.add(
                org.osmdroid.views.overlay.MapEventsOverlay(
                    object : org.osmdroid.events.MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                            onMapClickState.value(); return false
                        }

                        override fun longPressHelper(p: GeoPoint?): Boolean = false
                    },
                ),
            )
            addMapListener(
                DelayedMapListener(
                    object : MapListener {
                        override fun onScroll(event: ScrollEvent?): Boolean {
                            onViewportState.value(boundingBox, zoomLevelDouble); return false
                        }

                        override fun onZoom(event: ZoomEvent?): Boolean {
                            onViewportState.value(boundingBox, zoomLevelDouble); return false
                        }
                    },
                    250,
                ),
            )
        }
    }

    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose { mapView.onPause(); mapView.onDetach() }
    }

    AndroidView(
        modifier = modifier,
        factory = {
            mapView.post { onViewportState.value(mapView.boundingBox, mapView.zoomLevelDouble) }
            mapView
        },
        update = { mv ->
            if (center != null && center != lastCenter) {
                lastCenter = center
                mv.controller.animateTo(center, 16.0, 600L)
            }
            mv.overlays.removeAll(dynamic.toSet())
            dynamic.clear()

            state.route?.takeIf { it.available }?.let { route ->
                fun line(points: List<List<Double>>, color: Int, width: Float) = Polyline(mv).apply {
                    setPoints(points.filter { it.size >= 2 }.map { GeoPoint(it[0], it[1]) })
                    outlinePaint.color = color
                    outlinePaint.strokeWidth = width
                }
                dynamic += line(route.remaining, 0x99000000.toInt() or (vehicleColor(state.selectedVehicle?.type) and 0xFFFFFF), 9f)
                dynamic += line(route.traveled, 0x55888888, 7f)
            }
            state.stops.forEach { stop ->
                dynamic += Marker(mv).apply {
                    position = GeoPoint(stop.lat, stop.lng)
                    icon = PillFactory.stopDot(context)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = stop.name
                    setInfoWindow(null)
                    setOnMarkerClickListener { _, _ -> onStop(stop); true }
                }
            }
            state.visible.forEach { v ->
                dynamic += Marker(mv).apply {
                    position = GeoPoint(v.location.lat, v.location.lng)
                    icon = PillFactory.pill(context, v.type, v.line.orEmpty(), (v.delayMinutes ?: 0) >= 5)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = "${v.line} ${v.destination.orEmpty()}"
                    setInfoWindow(null)
                    setOnMarkerClickListener { _, _ -> onVehicle(v); true }
                }
            }
            mv.overlays.addAll(dynamic)
            mv.invalidate()
        },
    )
}
