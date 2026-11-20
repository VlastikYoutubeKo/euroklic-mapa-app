package cz.euroklicmapa.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** Redraws the widgets from the saved location and cached places (every ~30 min while one is on the home screen). */
class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { NearestToiletWidget().updateAll(applicationContext) }
        return Result.success()
    }

    companion object {
        const val NAME = "widget_refresh"
    }
}

/** Asks the launcher to pin the "Nearest toilet" widget (Android shows its own confirmation). */
fun requestNearestWidget(context: Context) {
    val manager = android.appwidget.AppWidgetManager.getInstance(context)
    if (manager.isRequestPinAppWidgetSupported) {
        manager.requestPinAppWidget(
            android.content.ComponentName(context, NearestToiletReceiver::class.java),
            null,
            null,
        )
    } else {
        android.widget.Toast.makeText(
            context,
            cz.euroklicmapa.util.t("Widget přidáš dlouhým podržením plochy."),
            android.widget.Toast.LENGTH_LONG,
        ).show()
    }
}
