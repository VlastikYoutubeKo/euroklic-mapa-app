package cz.euroklicmapa.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.osmdroid.util.GeoPoint

/**
 * Single source of truth for the user's location, shared by every screen so the map, list and
 * detail agree on distances. The app must work without the permission — callers just get `null`
 * and should degrade gracefully. Fine location is preferred (better distance/sort accuracy) but
 * not required: if the user only grants "approximate" (COARSE), that's honoured too — the OS
 * itself caps the fix precision to whatever was actually granted regardless of the priority we
 * request, so asking for high accuracy is safe either way.
 */
class LocationRepository(private val appContext: Context) {

    private val _lastKnown = MutableStateFlow<GeoPoint?>(null)
    val lastKnown: StateFlow<GeoPoint?> = _lastKnown.asStateFlow()

    fun hasPermission(): Boolean = hasFine() || hasCoarse()

    private fun hasFine(): Boolean =
        ContextCompat.checkSelfPermission(
            appContext,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    private fun hasCoarse(): Boolean =
        ContextCompat.checkSelfPermission(
            appContext,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Best-effort refresh. Uses the cached fix immediately if present, then asks for a fresh
     * high-accuracy fix (see class doc — harmless to request even with only coarse granted).
     * Results land in [lastKnown]; [onResult] fires once with the best value.
     */
    @SuppressLint("MissingPermission")
    fun refresh(onResult: (GeoPoint?) -> Unit = {}) {
        if (!hasPermission()) {
            onResult(null)
            return
        }
        val client = LocationServices.getFusedLocationProviderClient(appContext)

        client.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                val point = GeoPoint(loc.latitude, loc.longitude)
                _lastKnown.value = point
            }
        }

        val request = CurrentLocationRequest.Builder()
            .setPriority(if (hasFine()) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setMaxUpdateAgeMillis(60_000)
            .build()

        client.getCurrentLocation(request, null).addOnSuccessListener { loc ->
            val point = loc?.let { GeoPoint(it.latitude, it.longitude) } ?: _lastKnown.value
            if (point != null) _lastKnown.value = point
            onResult(point)
        }.addOnFailureListener {
            onResult(_lastKnown.value)
        }
    }
}
