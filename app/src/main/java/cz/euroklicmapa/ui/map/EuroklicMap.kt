package cz.euroklicmapa.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cz.euroklicmapa.ui.theme.ColorPickup
import cz.euroklicmapa.ui.theme.EuroklicTheme
import cz.euroklicmapa.ui.theme.isOfficialSource
import cz.euroklicmapa.ui.theme.sourceColor
import org.osmdroid.api.IGeoPoint
import org.osmdroid.config.Configuration
import org.osmdroid.events.DelayedMapListener
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.simplefastpoint.SimpleFastPointOverlay
import org.osmdroid.views.overlay.simplefastpoint.SimpleFastPointOverlayOptions
import org.osmdroid.views.overlay.simplefastpoint.SimplePointTheme
import java.io.File
import kotlin.math.floor
import kotlin.math.min

data class MapMarker(
    val id: String,
    val position: GeoPoint,
    val title: String,
    val isPickup: Boolean,
    val source: String? = null,
    val precision: String? = null,
    val status: cz.euroklicmapa.util.PlaceStatus? = null,
    val likes: Int = 0,
    val dislikes: Int = 0,
)

/** A camera position the caller can persist (e.g. via `rememberSaveable`) and restore. */
data class CameraPos(val lat: Double, val lon: Double, val zoom: Double)

/** Roughly centred on CZ+SK, zoomed out enough that towns with data are on screen. */
private val DEFAULT_CENTER = GeoPoint(49.4, 16.5)
private const val DEFAULT_ZOOM = 6.8

/** Below this zoom, nearby points collapse into one tappable count bubble. */
private const val CLUSTER_MAX_ZOOM = 13.5
/** Screen-space grid cell for clustering, in px — bigger = fewer, less-overlapping bubbles. */
private const val CLUSTER_CELL_PX = 168
private const val MIN_CLUSTER = 3

private class CameraState {
    /** True once we have framed the data (or the user) at least once. */
    var framed = false

    /** The selection we last panned to, so re-selecting the same marker doesn't re-pan every recomposition. */
    var lastSelected: String? = null
}

/**
 * Mutable holder the pan/zoom listener reads from — it re-runs [renderMarkers] after every move
 * (the `AndroidView` update lambda only fires on recomposition, which a pan is not).
 */
private class RenderState {
    var markers: List<MapMarker> = emptyList()
    var selectedId: String? = null
    var userLocation: GeoPoint? = null
    var highlightArgb: Int = 0
    var onMarkerClick: (MapMarker) -> Unit = {}

    /** Fired (debounced by the DelayedMapListener) after every pan/zoom so the caller can persist it. */
    var onCameraIdle: (lat: Double, lon: Double, zoom: Double) -> Unit = { _, _, _ -> }
}

@Composable
fun EuroklicMap(
    modifier: Modifier = Modifier,
    markers: List<MapMarker> = emptyList(),
    userLocation: GeoPoint? = null,
    recenterTarget: GeoPoint? = null,
    selectedMarkerId: String? = null,
    initialCamera: CameraPos? = null,
    onRecenterHandled: () -> Unit = {},
    onMarkerClick: (MapMarker) -> Unit = {},
    onMapClick: () -> Unit = {},
    onCameraIdle: (latitude: Double, longitude: Double, zoom: Double) -> Unit = { _, _, _ -> },
) {
    val context = LocalContext.current
    val camera = remember { CameraState() }
    val render = remember { RenderState() }
    val highlightArgb = EuroklicTheme.extended.brandButton.toArgb()
    val onMapClickState = rememberUpdatedState(onMapClick)

    val mapView = remember {
        // Full osmdroid init — without a loaded Configuration + writable cache, tiles silently
        // fail to download on a fresh emulator image.
        Configuration.getInstance().apply {
            load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            userAgentValue = context.packageName
            // Keep the tile cache in filesDir (cacheDir gets wiped by the OS on low storage) and
            // stop osmdroid from re-downloading tiles just because Mapy.com sends a short
            // Cache-Control — treat a downloaded tile as fresh for 30 days.
            osmdroidBasePath = File(context.filesDir, "osmdroid")
            osmdroidTileCache = File(osmdroidBasePath, "tiles")
            expirationOverrideDuration = 30L * 24 * 60 * 60 * 1000
            tileDownloadThreads = 8
            tileFileSystemCacheMaxBytes = 300L * 1024 * 1024
            tileFileSystemCacheTrimBytes = 250L * 1024 * 1024
        }

        MapView(context).apply {
            setTileSource(MapyCzTileSource())
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = 4.0
            maxZoomLevel = 19.0
            controller.setZoom(DEFAULT_ZOOM)
            controller.setCenter(DEFAULT_CENTER)
            // Tap on the map background (not a marker) clears the current selection.
            overlays.add(
                MapEventsOverlay(
                    object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                            onMapClickState.value(); return false
                        }

                        override fun longPressHelper(p: GeoPoint?): Boolean = false
                    },
                ),
            )
            overlays.add(
                // Bottom-left so it never fights the FAB.
                CopyrightOverlay(context).apply {
                    setAlignRight(false)
                    setAlignBottom(true)
                },
            )
            // Re-cluster / re-draw markers after any pan or zoom (debounced).
            addMapListener(
                DelayedMapListener(
                    object : MapListener {
                        override fun onScroll(event: ScrollEvent?): Boolean {
                            renderMarkers(this@apply, render); emitCamera(this@apply, render); return false
                        }

                        override fun onZoom(event: ZoomEvent?): Boolean {
                            renderMarkers(this@apply, render); emitCamera(this@apply, render); return false
                        }
                    },
                    120,
                ),
            )
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { mv ->
            render.markers = markers
            render.selectedId = selectedMarkerId
            render.userLocation = userLocation
            render.highlightArgb = highlightArgb
            render.onMarkerClick = onMarkerClick
            render.onCameraIdle = onCameraIdle
            renderMarkers(mv, render)

            when {
                recenterTarget != null -> {
                    mv.controller.animateTo(recenterTarget)
                    mv.controller.setZoom(16.0)
                    camera.framed = true
                    onRecenterHandled()
                }
                // Restore the camera the user last left (survives config change / process death).
                // One-shot via camera.framed, same as the branches below — and it must lose to an
                // explicit recenterTarget, hence its place in this `when`.
                !camera.framed && initialCamera != null -> {
                    camera.framed = true
                    mv.controller.setZoom(initialCamera.zoom)
                    mv.controller.setCenter(GeoPoint(initialCamera.lat, initialCamera.lon))
                }
                // Centre on the user the first time we get a fix; otherwise keep the CZ/SK
                // default set in the factory (never auto-fit to the whole dataset).
                !camera.framed && userLocation != null -> {
                    camera.framed = true
                    mv.controller.animateTo(userLocation)
                    mv.controller.setZoom(13.0)
                }
            }

            // On a fresh selection, nudge the camera so the marker sits above the bottom sheet
            // (shift the centre south by a slice of the visible span → marker moves up-screen).
            if (selectedMarkerId != camera.lastSelected) {
                camera.lastSelected = selectedMarkerId
                selectedMarkerId?.let { id ->
                    markers.firstOrNull { it.id == id }?.let { sel ->
                        mv.post {
                            val span = runCatching { mv.boundingBox.latitudeSpan }.getOrDefault(0.0)
                            val target = if (span > 0.0) {
                                GeoPoint(sel.position.latitude - span * 0.18, sel.position.longitude)
                            } else {
                                sel.position
                            }
                            mv.controller.animateTo(target)
                        }
                    }
                }
            }

            mv.invalidate()
        },
    )

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }
}

/** Hand the caller the current camera centre + zoom so it can persist them. */
private fun emitCamera(mv: MapView, s: RenderState) {
    val c = mv.mapCenter
    s.onCameraIdle(c.latitude, c.longitude, mv.zoomLevelDouble)
}

/**
 * Rebuild every marker overlay for the current viewport. Above [CLUSTER_MAX_ZOOM] each place is
 * its own dot (with a generous transparent hit halo so a fingertip lands); below it, places that
 * fall in the same screen grid cell collapse into one count bubble that zooms in on tap.
 */
private fun renderMarkers(mv: MapView, s: RenderState) {
    if (mv.width == 0 || mv.height == 0) return
    mv.overlays.removeAll { it is SimpleFastPointOverlay || it is Marker }

    val clustering = mv.zoomLevelDouble < CLUSTER_MAX_ZOOM && s.markers.size > 40
    if (clustering) {
        val proj = mv.projection
        val cell = CLUSTER_CELL_PX
        val grid = HashMap<Long, MutableList<MapMarker>>()
        val pt = Point()
        for (m in s.markers) {
            proj.toPixels(m.position, pt)
            val gx = floor(pt.x.toDouble() / cell).toLong()
            val gy = floor(pt.y.toDouble() / cell).toLong()
            val key = (gx shl 32) xor (gy and 0xffffffffL)
            grid.getOrPut(key) { ArrayList(4) }.add(m)
        }
        val singles = ArrayList<MapMarker>(grid.size)
        for (bucket in grid.values) {
            if (bucket.size < MIN_CLUSTER) {
                singles.addAll(bucket)
                continue
            }
            var lat = 0.0
            var lon = 0.0
            for (b in bucket) {
                lat += b.position.latitude
                lon += b.position.longitude
            }
            val center = GeoPoint(lat / bucket.size, lon / bucket.size)
            mv.overlays.add(clusterMarker(mv, center, bucket.size, s.highlightArgb))
        }
        drawIndividual(mv, singles, s)
    } else {
        drawIndividual(mv, s.markers, s)
    }

    s.userLocation?.let { loc ->
        mv.overlays.add(
            Marker(mv).apply {
                position = loc
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = "Vaše poloha"
                icon = UserLocationIcon.build(mv.context)
                setInfoWindow(null)
            },
        )
    }
    mv.invalidate()
}

private fun clusterMarker(mv: MapView, center: GeoPoint, count: Int, brandArgb: Int): Marker =
    Marker(mv).apply {
        position = center
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        icon = android.graphics.drawable.BitmapDrawable(mv.context.resources, clusterBitmap(mv.context, count, brandArgb))
        title = null
        setInfoWindow(null)
        setOnMarkerClickListener { _, _ ->
            mv.controller.animateTo(center)
            mv.controller.setZoom((mv.zoomLevelDouble + 2.0).coerceAtMost(mv.maxZoomLevel))
            true
        }
    }

private val clusterBitmapCache = HashMap<Int, Bitmap>()

private fun clusterBitmap(context: Context, count: Int, brandArgb: Int): Bitmap {
    val label = if (count > 999) 1000 else count
    clusterBitmapCache[label]?.let { return it }

    val d = context.resources.displayMetrics.density
    val text = if (count > 999) "999+" else count.toString()
    val diameter = (34f + (min(count, 250) / 250f) * 22f) * d
    val sizePx = diameter.toInt().coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val r = sizePx / 2f
    val stroke = 2f * d
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = brandArgb; style = Paint.Style.FILL }
    c.drawCircle(r, r, r - stroke, fill)
    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE; style = Paint.Style.STROKE; strokeWidth = stroke
    }
    c.drawCircle(r, r, r - stroke, ring)
    val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = 13f * d
    }
    val fm = tp.fontMetrics
    c.drawText(text, r, r - (fm.ascent + fm.descent) / 2f, tp)
    clusterBitmapCache[label] = bmp
    return bmp
}

/**
 * Individual points: one transparent oversized hit layer over the whole list (so taps land), then
 * the visible dots grouped by bucket. Buckets mirror the website — hollow = reported, filled +
 * white ring = verified, filled = unverified, slate square = pickup.
 */
private fun drawIndividual(mv: MapView, list: List<MapMarker>, s: RenderState) {
    if (list.isEmpty()) return

    // Radius in device px; grow the dots as you zoom in (they're sparse by then) so they stay
    // a comfortable tap/aim target instead of specks. Base sizes are tuned for the lower end.
    val density = mv.context.resources.displayMetrics.density
    val zoomT = ((mv.zoomLevelDouble - CLUSTER_MAX_ZOOM) / (18.0 - CLUSTER_MAX_ZOOM))
        .coerceIn(0.0, 1.0).toFloat()
    val k = density * (1f + zoomT * 0.5f)
    fun r(base: Float) = base * k

    addPoints(
        mv, list, SimpleFastPointOverlayOptions.Shape.CIRCLE, radius = r(15f),
        paint = fill(android.graphics.Color.TRANSPARENT, 0), clickable = true,
    ) { i -> i?.let { list.getOrNull(it) }?.let(s.onMarkerClick) }

    list
        .groupBy { m ->
            val g = if (isOfficialSource(m.source)) "off" else "com"
            when {
                m.isPickup -> "pickup" + (if (m.precision == "approx") "_approx" else "")
                m.status?.isHollow == true -> "hollow_$g"
                m.status?.hasRing == true -> "ring_$g"
                else -> "plain_$g"
            }
        }
        .forEach { (key, group) ->
            val m = group.first()
            val src = sourceColor(m.source).toArgb()
            when {
                m.isPickup -> {
                    val approx = m.precision == "approx"
                    addPoints(
                        mv, group, SimpleFastPointOverlayOptions.Shape.SQUARE,
                        radius = if (approx) r(6f) else r(8f),
                        paint = fill(ColorPickup.toArgb(), if (approx) 150 else 255), clickable = false,
                    ) {}
                }
                key.startsWith("hollow") -> addPoints(
                    mv, group, SimpleFastPointOverlayOptions.Shape.CIRCLE, radius = r(8f),
                    paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = src; alpha = 170; style = Paint.Style.STROKE; strokeWidth = 2.6f * k
                    },
                    clickable = false,
                ) {}
                key.startsWith("ring") -> {
                    addPoints(
                        mv, group, SimpleFastPointOverlayOptions.Shape.CIRCLE, radius = r(10.5f),
                        paint = fill(android.graphics.Color.WHITE), clickable = false,
                    ) {}
                    addPoints(
                        mv, group, SimpleFastPointOverlayOptions.Shape.CIRCLE, radius = r(8f),
                        paint = fill(src), clickable = false,
                    ) {}
                }
                else -> addPoints(
                    mv, group, SimpleFastPointOverlayOptions.Shape.CIRCLE, radius = r(8f),
                    paint = fill(src), clickable = false,
                ) {}
            }
        }

    // Selected marker: a brand halo + enlarged solid so the tapped point reads clearly.
    s.selectedId?.let { id ->
        list.firstOrNull { it.id == id }?.let { sel ->
            val shape = if (sel.isPickup) SimpleFastPointOverlayOptions.Shape.SQUARE
            else SimpleFastPointOverlayOptions.Shape.CIRCLE
            val core = if (sel.isPickup) ColorPickup.toArgb() else sourceColor(sel.source).toArgb()
            addPoints(mv, listOf(sel), shape, radius = r(17f), paint = fill(s.highlightArgb, 70), clickable = false) {}
            addPoints(mv, listOf(sel), shape, radius = r(12f), paint = fill(android.graphics.Color.WHITE), clickable = false) {}
            addPoints(mv, listOf(sel), shape, radius = r(9f), paint = fill(core), clickable = false) {}
        }
    }
}

private fun fill(argb: Int, alpha: Int = 255) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = argb
    style = Paint.Style.FILL
    this.alpha = alpha
}

private fun addPoints(
    mv: MapView,
    group: List<MapMarker>,
    shape: SimpleFastPointOverlayOptions.Shape,
    radius: Float,
    paint: Paint,
    clickable: Boolean,
    onClick: (Int?) -> Unit,
) {
    val points: List<IGeoPoint> = group.map { it.position }
    val opts = SimpleFastPointOverlayOptions.getDefaultStyle()
        .setAlgorithm(SimpleFastPointOverlayOptions.RenderingAlgorithm.MEDIUM_OPTIMIZATION)
        .setSymbol(shape)
        .setRadius(radius)
        .setIsClickable(clickable)
        .setPointStyle(paint)
    val overlay = SimpleFastPointOverlay(SimplePointTheme(points, false), opts)
    if (clickable) overlay.setOnClickListener { _, index -> onClick(index) }
    mv.overlays.add(overlay)
}
