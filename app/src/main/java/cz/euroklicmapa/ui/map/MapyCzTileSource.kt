package cz.euroklicmapa.ui.map

import android.util.Log
import cz.euroklicmapa.BuildConfig
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex

class MapyCzTileSource : OnlineTileSourceBase(
    "Mapy.cz",
    0, 19, 256, ".png",
    arrayOf("https://api.mapy.com/v1/maptiles/basic/256/"),
    "© Seznam.cz a.s. a další"
) {
    // Injected at build time from local.properties (MAPY_APIKEY=…) or the
    // MAPY_APIKEY env var — never committed to VCS.
    private val apiKey = BuildConfig.MAPY_APIKEY

    init {
        if (apiKey.isBlank()) {
            Log.w("MapyTiles", "MAPY_APIKEY chybí — doplň jej do local.properties. Dlaždice se nebudou stahovat.")
        }
    }

    override fun getTileURLString(pMapTileIndex: Long): String {
        if (apiKey.isBlank()) return "" // no key → no tile request spam
        return baseUrl +
                MapTileIndex.getZoom(pMapTileIndex) + "/" +
                MapTileIndex.getX(pMapTileIndex) + "/" +
                MapTileIndex.getY(pMapTileIndex) +
                "?apikey=$apiKey"
    }
}
