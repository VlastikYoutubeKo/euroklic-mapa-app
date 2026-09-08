package cz.euroklicmapa.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * The three local-notification channels (phase 1 — WorkManager, no FCM). Registered once from
 * [cz.euroklicmapa.EuroklicApplication.onCreate]. Safe to call repeatedly — `createNotificationChannel`
 * is idempotent.
 */
object NotificationChannels {

    /** Admin-only: "fronta ke schválení". IMPORTANCE_HIGH. */
    const val MODERATION = "moderation"

    /** "Nové místo v okolí". IMPORTANCE_DEFAULT. */
    const val NEARBY = "nearby"

    /** Reserved for phase 2 ("oblíbené místo nahlášeno") — created now, no sender yet. IMPORTANCE_LOW. */
    const val FAVORITES = "favorites"

    fun register(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return

        mgr.createNotificationChannel(
            NotificationChannel(MODERATION, "Moderace", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Upozornění na frontu ke schválení (jen administrátoři)."
            },
        )
        mgr.createNotificationChannel(
            NotificationChannel(NEARBY, "Nové v okolí", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Nová bezbariérová místa přidaná blízko tvé polohy."
            },
        )
        mgr.createNotificationChannel(
            NotificationChannel(FAVORITES, "Stav oblíbených", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Změny u míst, která máš v oblíbených."
            },
        )
    }
}
