package cz.euroklicmapa.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import org.osmdroid.util.GeoPoint
import java.util.Locale
import kotlin.math.roundToInt

/** Average walking speed used for the straight-line time estimate. */
private const val WALKING_KMH = 4.5

/** "120 m" / "1,4 km" / "9273 km" */
fun formatDistance(meters: Double): String {
    val km = meters / 1000.0
    return when {
        meters < 1000 -> "${meters.roundToInt()} m"
        km < 100 -> String.format(Locale.getDefault(), "%.1f km", km)
        else -> "${km.roundToInt()} km"
    }
}

/** Rough straight-line walking time, e.g. "~7 min". Always an approximation. */
fun formatWalkingTime(meters: Double): String {
    val minutes = (meters / 1000.0 / WALKING_KMH * 60.0).roundToInt().coerceAtLeast(1)
    return when {
        minutes < 90 -> "~$minutes min"
        minutes < 24 * 60 -> "~${(minutes / 60.0).roundToInt()} h"
        else -> "přes 1 den"
    }
}

fun distanceBetween(a: GeoPoint, b: GeoPoint): Double = a.distanceToAsDouble(b)

/** Czech plural for "místo": 1 místo, 2–4 místa, 0 / 5+ míst. */
fun placesCount(n: Int): String = when {
    n == 1 -> "1 místo"
    n in 2..4 -> "$n místa"
    else -> "$n míst"
}

private fun startOrToast(context: Context, intent: Intent, failMsg: String): Boolean = try {
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    Toast.makeText(context, failMsg, Toast.LENGTH_SHORT).show()
    false
}

/** Opens a URL in the user's browser. */
fun openUrl(context: Context, url: String) {
    startOrToast(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)), "Nemám čím otevřít odkaz.")
}

/**
 * Opens turn-by-turn navigation. Tries the universal Google Maps directions URL first
 * (Google Maps, Mapy.cz, Waze…), then a bare `geo:` intent, then the web URL in a browser.
 */
fun launchNavigation(context: Context, lat: Double, lon: Double, label: String?) {
    val encodedLabel = Uri.encode(label ?: "")
    val dir = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lon"),
    )
    val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon($encodedLabel)"))
    if (!startOrToast(context, dir, "")) {
        startOrToast(context, geo, "Nemám čím spustit navigaci.")
    }
}
