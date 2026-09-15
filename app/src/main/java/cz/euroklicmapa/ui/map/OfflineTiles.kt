package cz.euroklicmapa.ui.map

import android.content.Context
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.views.MapView

/** Refuse a download past this many tiles — protects both the user's data and Mapy.com's tile
 * server from an accidental bulk scrape if someone taps the button zoomed out to country level. */
private const val MAX_TILES = 3000

sealed interface OfflineDownloadResult {
    data class Started(val tileCount: Int) : OfflineDownloadResult
    data class Progress(val percent: Int) : OfflineDownloadResult
    data object Done : OfflineDownloadResult
    data class Failed(val errors: Int) : OfflineDownloadResult
    data class TooLarge(val tileCount: Int) : OfflineDownloadResult
}

/**
 * Downloads Mapy.com tiles for the map's current on-screen viewport into the same disk cache
 * osmdroid already reads from when browsing online (`Configuration.osmdroidTileCache` in
 * [EuroklicMap]) — no separate offline-storage plumbing needed, and it's the same tile-fetch
 * pattern as normal panning, just batched into one deliberate action instead of scattered across
 * a session. Zoom range is the current level up to +3 (roughly city view down to street level).
 *
 * [CacheManager.CacheManagerCallback] methods run via osmdroid's internal AsyncTask, i.e. already
 * on the main thread — safe to mutate Compose state directly from [onResult].
 */
fun startOfflineDownload(mapView: MapView, context: Context, onResult: (OfflineDownloadResult) -> Unit) {
    val cacheManager = try {
        CacheManager(mapView)
    } catch (e: Exception) {
        onResult(OfflineDownloadResult.Failed(-1))
        return
    }
    val bbox = mapView.boundingBox
    val zoomMin = mapView.zoomLevelDouble.toInt().coerceIn(4, 17)
    val zoomMax = (zoomMin + 3).coerceAtMost(17)
    val tileCount = cacheManager.possibleTilesInArea(bbox, zoomMin, zoomMax)
    if (tileCount > MAX_TILES) {
        onResult(OfflineDownloadResult.TooLarge(tileCount))
        return
    }

    onResult(OfflineDownloadResult.Started(tileCount))
    // NoUI: the plain downloadAreaAsync overload also pops osmdroid's own (English,
    // unstyled) progress dialog on top — the FAB spinner + toasts in MapScreen are the UI.
    cacheManager.downloadAreaAsyncNoUI(
        context,
        bbox,
        zoomMin,
        zoomMax,
        object : CacheManager.CacheManagerCallback {
            override fun onTaskComplete() {
                onResult(OfflineDownloadResult.Done)
            }

            override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) {
                val percent = if (tileCount > 0) (progress * 100 / tileCount).coerceIn(0, 100) else 0
                onResult(OfflineDownloadResult.Progress(percent))
            }

            override fun downloadStarted() {}

            override fun setPossibleTilesInArea(total: Int) {}

            override fun onTaskFailed(errors: Int) {
                onResult(OfflineDownloadResult.Failed(errors))
            }
        },
    )
}
