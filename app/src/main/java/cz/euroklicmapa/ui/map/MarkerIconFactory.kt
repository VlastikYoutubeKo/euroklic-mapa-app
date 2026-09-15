package cz.euroklicmapa.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

/**
 * WC / pickup markers are drawn by [EuroklicMap] via osmdroid's SimpleFastPointOverlay
 * (fast + declutters thousands of points). This only builds the "you are here" dot.
 */
object UserLocationIcon {

    // Deliberately NOT brand blue: ColorOfficial (ui/theme/Color.kt) — the "verified ČD source"
    // marker fill — is also blue with a white ring, which on a real device read as the same dot
    // as "you are here". Green matches no marker fill (official=blue, community=amber,
    // pickup=slate) and reuses the already contrast-verified LightSuccess token.
    private const val LOCATION_GREEN = 0xFF0D8259.toInt()

    fun build(context: Context): Drawable {
        val density = context.resources.displayMetrics.density
        // Bigger canvas than the dot itself so the translucent accuracy halo has room — no place
        // marker ever draws one, so its presence alone (not just the colour) says "this is you".
        val size = (40 * density).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val c = size / 2f

        paint.color = LOCATION_GREEN
        paint.alpha = 55
        canvas.drawCircle(c, c, c, paint)

        paint.alpha = 255
        paint.color = Color.WHITE
        canvas.drawCircle(c, c, 8f * density, paint)
        paint.color = LOCATION_GREEN
        canvas.drawCircle(c, c, 6f * density, paint)

        return BitmapDrawable(context.resources, bitmap)
    }
}
