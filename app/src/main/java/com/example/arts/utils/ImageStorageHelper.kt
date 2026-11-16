package com.example.arts.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream

/**
 * Photos are shrunk and stored inside the Firestore document itself as a
 * data URL. Firestore documents are limited to 1 MB.
 */
object ImageStorageHelper {

    private const val TAG = "ARTS-Images"
    private const val MAX_ORIGINAL_SIZE = 10 * 1024 * 1024 // 10MB limit for the original file to avoid OOM

    /**
     * Reads and shrinks an image from a Uri.
     * Uses a single read operation into a byte array to avoid multiple stream opens,
     * which can fail on certain content providers.
     */
    fun toDataUrl(context: Context, uri: Uri, maxDimension: Int = 900, quality: Int = 70): String? {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArrayOutputStream()
                val data = ByteArray(16384)
                var nRead: Int
                var totalRead = 0
                while (input.read(data, 0, data.size).also { nRead = it } != -1) {
                    totalRead += nRead
                    if (totalRead > MAX_ORIGINAL_SIZE) {
                        Log.w(TAG, "Original image too large: $totalRead bytes")
                        return null
                    }
                    buffer.write(data, 0, nRead)
                }
                buffer.toByteArray()
            } ?: return null

            if (bytes.isEmpty()) return null

            // 1. Get original dimensions
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

            if (options.outWidth <= 0 || options.outHeight <= 0) return null

            // 2. Calculate sample size
            var sampleSize = 1
            val longestSide = maxOf(options.outWidth, options.outHeight)
            while (longestSide / (sampleSize * 2) >= maxDimension) {
                sampleSize *= 2
            }

            // 3. Decode scaled bitmap
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val sampledBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions) ?: return null

            // 4. Fine-tune scale if needed
            val finalBitmap = if (maxOf(sampledBitmap.width, sampledBitmap.height) > maxDimension) {
                val scale = maxDimension.toFloat() / maxOf(sampledBitmap.width, sampledBitmap.height)
                val destW = (sampledBitmap.width * scale).toInt().coerceAtLeast(1)
                val destH = (sampledBitmap.height * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(sampledBitmap, destW, destH, true)
                if (scaled !== sampledBitmap) sampledBitmap.recycle()
                scaled
            } else {
                sampledBitmap
            }

            // 5. Compress to JPEG
            val out = ByteArrayOutputStream()
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            finalBitmap.recycle()
            
            val compressedBytes = out.toByteArray()
            // 600KB check to ensure document < 1MB (Base64 adds ~33%)
            if (compressedBytes.size > 600 * 1024) {
                Log.w(TAG, "Image still too large after compression: ${compressedBytes.size} bytes")
                return null
            }

            "data:image/jpeg;base64," + Base64.encodeToString(compressedBytes, Base64.NO_WRAP)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to process image: ${e.message}")
            null
        }
    }

    fun decodeDataUrl(dataUrl: String?): Bitmap? {
        if (dataUrl.isNullOrBlank() || !dataUrl.startsWith("data:image")) return null
        return try {
            val base64Data = if (dataUrl.contains(",")) dataUrl.substringAfter(",") else dataUrl
            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode data URL: ${e.message}")
            null
        }
    }
}
