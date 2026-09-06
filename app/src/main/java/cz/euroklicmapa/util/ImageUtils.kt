package cz.euroklicmapa.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Decode [uri], honour its EXIF rotation, scale so the longest edge is <= [maxEdge], and
 * re-encode as JPEG at [quality]. Phone photos are 3–12 MB; `/uploads/` caps at 8 MB, so the
 * app must shrink before upload. Returns null if the image can't be read.
 */
fun downscaleToJpeg(
    context: Context,
    uri: Uri,
    maxEdge: Int = 1800,
    quality: Int = 82,
): ByteArray? {
    return try {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val longest = max(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longest / (sample * 2) >= maxEdge) sample *= 2

        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null

        val rotation = resolver.openInputStream(uri)?.use { readExifRotation(it) } ?: 0
        val oriented = orientAndScale(decoded, rotation, maxEdge)

        ByteArrayOutputStream().use { out ->
            oriented.compress(Bitmap.CompressFormat.JPEG, quality, out)
            oriented.recycle()
            out.toByteArray()
        }
    } catch (e: Exception) {
        Log.w("ImageUtils", "downscaleToJpeg failed", e)
        null
    }
}

private fun readExifRotation(stream: java.io.InputStream): Int = try {
    when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
} catch (e: Exception) {
    0
}

private fun orientAndScale(src: Bitmap, rotationDegrees: Int, maxEdge: Int): Bitmap {
    val longest = max(src.width, src.height)
    val scale = if (longest > maxEdge) maxEdge.toFloat() / longest else 1f
    if (scale == 1f && rotationDegrees == 0) return src

    val matrix = Matrix()
    if (scale != 1f) matrix.postScale(scale, scale)
    if (rotationDegrees != 0) matrix.postRotate(rotationDegrees.toFloat())
    val result = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    if (result != src) src.recycle()
    return result
}

/** Rough "will this JPEG be under the limit" check for the picked source before we bother uploading. */
fun approxMegapixels(context: Context, uri: Uri): Double = try {
    val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, b) }
    (b.outWidth.toLong() * b.outHeight / 1_000_000.0).let { (it * 100).roundToInt() / 100.0 }
} catch (e: Exception) {
    0.0
}
