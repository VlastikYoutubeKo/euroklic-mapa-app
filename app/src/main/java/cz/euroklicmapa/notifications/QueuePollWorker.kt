package cz.euroklicmapa.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cz.euroklicmapa.EuroklicApplication
import cz.euroklicmapa.MainActivity
import cz.euroklicmapa.R
import cz.euroklicmapa.data.auth.AuthState
import cz.euroklicmapa.util.distanceBetween
import kotlinx.coroutines.flow.first
import org.osmdroid.util.GeoPoint

/**
 * Phase-1 local notifications (no backend, no FCM). One periodic job (15 min, scheduled in
 * [EuroklicApplication.onCreate]) with two independent parts, each fully gated inside [doWork]:
 *
 *  - **Admin queue** — when a signed-in admin has the toggle on: poll `api_admin_list.php`,
 *    notify on the `"moderation"` channel if `count + photo_count > 0` and either count changed
 *    since last poll. Tap → admin queue (via [MainActivity] `nav=admin_queue`).
 *  - **Nearby** — when the toggle is on and a location is known: diff the cached WC ids against
 *    the last snapshot; if a genuinely new id sits within ~10 km and we haven't notified in 24 h,
 *    post one `"nearby"` notification. First run seeds the snapshot silently.
 *
 * Network failures are swallowed → [Result.success] (the next period is only 15 min away; no
 * retry storm).
 */
class QueuePollWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? EuroklicApplication ?: return Result.success()
        runCatching { adminPart(app) }.onFailure { Log.w(TAG, "admin part failed", it) }
        runCatching { nearbyPart(app) }.onFailure { Log.w(TAG, "nearby part failed", it) }
        return Result.success()
    }

    private suspend fun adminPart(app: EuroklicApplication) {
        if (!app.notificationPrefs.adminQueueEnabled.first()) return
        val state = app.authRepository.state.value
        if (state !is AuthState.LoggedIn || !state.me.is_admin) return

        val resp = app.api.adminList()
        val placeCount = resp.count
        val photoCount = resp.photo_count

        val shouldNotify = QueuePollLogic.adminShouldNotify(
            placeCount = placeCount,
            photoCount = photoCount,
            lastPlaceCount = app.notificationPrefs.lastAdminPlaceCount(),
            lastPhotoCount = app.notificationPrefs.lastAdminPhotoCount(),
        )
        app.notificationPrefs.setLastAdminCounts(placeCount, photoCount)

        if (!shouldNotify) return

        notify(
            id = NOTIF_MODERATION,
            channel = NotificationChannels.MODERATION,
            title = "Čeká na schválení",
            text = "${QueuePollLogic.places(placeCount)} · ${QueuePollLogic.photos(photoCount)}",
            contentIntent = activityIntent(navExtra = NAV_ADMIN_QUEUE, requestCode = NOTIF_MODERATION),
        )
    }

    private suspend fun nearbyPart(app: EuroklicApplication) {
        if (!app.notificationPrefs.nearbyEnabled.first()) return
        val here = app.locationRepository.lastKnown.value ?: return

        val currentIds = app.repository.getAllLocationIds().toSet()
        if (currentIds.isEmpty()) return

        val known = app.notificationPrefs.knownPlaceIds()
        if (known.isEmpty()) {
            // First run — seed silently, never notify on the whole existing dataset.
            app.notificationPrefs.setKnownPlaceIds(currentIds)
            return
        }

        val newIds = (currentIds - known).toList()
        val nearbyNew = if (newIds.isEmpty()) {
            emptyList()
        } else {
            app.repository.getLocationsByIds(newIds).filter { row ->
                row.latitude.isFinite() && row.longitude.isFinite() &&
                    distanceBetween(here, GeoPoint(row.latitude, row.longitude)) <= NEARBY_RADIUS_M
            }
        }

        val now = System.currentTimeMillis()
        val throttleOk = now - app.notificationPrefs.lastNearbyNotifEpoch() >= DAY_MS
        if (nearbyNew.isNotEmpty() && throttleOk) {
            notify(
                id = NOTIF_NEARBY,
                channel = NotificationChannels.NEARBY,
                title = "Nové v okolí",
                text = QueuePollLogic.newPlacesNearby(nearbyNew.size),
                contentIntent = activityIntent(navExtra = null, requestCode = NOTIF_NEARBY),
            )
            app.notificationPrefs.setLastNearbyNotifEpoch(now)
        }
        app.notificationPrefs.setKnownPlaceIds(currentIds)
    }

    private fun activityIntent(navExtra: String?, requestCode: Int): PendingIntent {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (navExtra != null) putExtra(EXTRA_NAV, navExtra)
        }
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags = flags or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(applicationContext, requestCode, intent, flags)
    }

    private fun notify(
        id: Int,
        channel: String,
        title: String,
        text: String,
        contentIntent: PendingIntent,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notification = NotificationCompat.Builder(applicationContext, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        try {
            NotificationManagerCompat.from(applicationContext).notify(id, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "notify blocked", e)
        }
    }

    private companion object {
        const val TAG = "QueuePollWorker"
        const val EXTRA_NAV = "nav"
        const val NAV_ADMIN_QUEUE = "admin_queue"
        const val NOTIF_MODERATION = 4001
        const val NOTIF_NEARBY = 4002
        const val NEARBY_RADIUS_M = 10_000.0
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
