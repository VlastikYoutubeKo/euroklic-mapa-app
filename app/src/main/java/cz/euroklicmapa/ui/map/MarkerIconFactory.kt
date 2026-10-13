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

    // Colours come from the theme. SOUCASNY and ZNACENI deliberately keep the location green:
    // blue with a white ring was confused with official WC markers on a real phone.
    // SVEZI uses dark green; KONTRAST uses black/white plus a yellow halo and contrasting ring.
    fun build(context: Context, dotArgb: Int, haloArgb: Int, ringArgb: Int = Color.WHITE): Drawable {
        val density = context.resources.displayMetrics.density
        // Bigger canvas than the dot itself so the translucent accuracy halo has room — no place
        // marker ever draws one, so its presence alone (not just the colour) says "this is you".
        val size = (40 * density).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val c = size / 2f

        paint.color = haloArgb
        canvas.drawCircle(c, c, c, paint)

        paint.alpha = 255
        paint.color = ringArgb
        canvas.drawCircle(c, c, 8f * density, paint)
        paint.color = dotArgb
        canvas.drawCircle(c, c, 6f * density, paint)

        return BitmapDrawable(context.resources, bitmap)
    }
}
