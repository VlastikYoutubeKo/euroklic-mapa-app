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

    private const val BRAND_BLUE = 0xFF2454E0.toInt()

    fun build(context: Context): Drawable {
        val density = context.resources.displayMetrics.density
        val size = (22 * density).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val c = size / 2f

        paint.color = Color.WHITE
        canvas.drawCircle(c, c, c, paint)
        paint.color = BRAND_BLUE
        canvas.drawCircle(c, c, c - 3 * density, paint)

        return BitmapDrawable(context.resources, bitmap)
    }
}
