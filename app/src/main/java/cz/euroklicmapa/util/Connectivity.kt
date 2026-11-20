package cz.euroklicmapa.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

private fun Context.isOnlineNow(): Boolean {
    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

/**
 * Live "does the device have an internet-capable network" flag. Drives the offline mode: the map
 * stops trying to download tiles (uses only the cache) and an "Offline" pill explains that the
 * app runs from downloaded data. Defaults to online if the system service is unavailable.
 */
@Composable
fun rememberIsOnline(): State<Boolean> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(context.isOnlineNow()) }
    DisposableEffect(context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { state.value = true }
            override fun onLost(network: Network) { state.value = context.isOnlineNow() }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                state.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        }
        try {
            cm?.registerNetworkCallback(
                NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
                callback,
            )
        } catch (_: Exception) {
        }
        onDispose { try { cm?.unregisterNetworkCallback(callback) } catch (_: Exception) {} }
    }
    return state
}
