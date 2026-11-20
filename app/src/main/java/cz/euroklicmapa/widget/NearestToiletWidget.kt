package cz.euroklicmapa.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import cz.euroklicmapa.EuroklicApplication
import cz.euroklicmapa.MainActivity
import cz.euroklicmapa.data.prefs.LastLocationPrefs
import cz.euroklicmapa.util.distanceBetween
import cz.euroklicmapa.util.formatDistance
import cz.euroklicmapa.util.formatWalkingTime
import cz.euroklicmapa.util.t
import org.osmdroid.util.GeoPoint

/** What the widget shows. [name] is null until a location and the place cache are available. */
data class NearestToiletState(
    val placeId: Int? = null,
    val name: String? = null,
    val distance: String? = null,
    val walking: String? = null,
    val hint: String? = null,
)

/**
 * "Nearest toilet" home-screen widget. A widget can't ask for a location in the background, so it
 * uses the last fix the app saved ([LastLocationPrefs]) and the places already cached in Room;
 * both stay fresh whenever the app is opened, and a periodic worker redraws it.
 */
class NearestToiletWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = loadState(context)
        provideContent { GlanceTheme { Content(context, state) } }
    }

    @Composable
    private fun Content(context: Context, state: NearestToiletState) {
        val onClick = if (state.placeId != null) {
            actionStartActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("euroklicmapa://detail?id=${state.placeId}&type=WC"))
                    .setPackage(context.packageName),
            )
        } else {
            actionStartActivity(Intent(context, MainActivity::class.java))
        }
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(22.dp)
                .padding(14.dp)
                .clickable(onClick),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Text(
                text = t("Nejbližší WC"),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
            Spacer(GlanceModifier.height(4.dp))
            if (state.name == null) {
                Text(
                    text = state.hint ?: "",
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
                    maxLines = 3,
                )
            } else {
                Text(
                    text = state.distance ?: "",
                    style = TextStyle(
                        color = ColorProvider(BRAND),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
                state.walking?.let {
                    Text(
                        text = t("%s pěšky", it),
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                        maxLines = 1,
                    )
                }
                Spacer(GlanceModifier.height(6.dp))
                Text(
                    text = state.name,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 2,
                )
            }
        }
    }

    companion object {
        private val SMALL = DpSize(110.dp, 110.dp)
        private val WIDE = DpSize(250.dp, 110.dp)

        /** Brand blue, readable on both the light and the dark widget background. */
        private val BRAND = androidx.compose.ui.graphics.Color(0xFF3B6CF0)

        suspend fun loadState(context: Context): NearestToiletState {
            val app = context.applicationContext as EuroklicApplication
            val saved = LastLocationPrefs(context).read()
                ?: return NearestToiletState(hint = t("Otevři aplikaci a povol polohu"))
            val places = app.repository.getAllLocationsOnce()
            if (places.isEmpty()) return NearestToiletState(hint = t("Zatím žádná stažená místa"))
            val here = GeoPoint(saved.lat, saved.lon)
            // Skip places the community reported as not working, unless nothing else is left.
            val usable = places.filter { it.dislikes <= it.likes }.ifEmpty { places }
            val nearest = usable.minBy { distanceBetween(here, GeoPoint(it.latitude, it.longitude)) }
            val meters = distanceBetween(here, GeoPoint(nearest.latitude, nearest.longitude))
            return NearestToiletState(
                placeId = nearest.id,
                name = nearest.name.ifBlank { t("Bezbariérové WC") },
                distance = formatDistance(meters),
                walking = formatWalkingTime(meters),
            )
        }
    }
}
