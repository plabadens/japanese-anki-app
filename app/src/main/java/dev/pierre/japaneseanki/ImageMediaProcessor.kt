package dev.pierre.japaneseanki

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class ProcessedImage(val file: File, val width: Int, val height: Int)

/**
 * One image export path: platform lossy WebP, quality 75, at most 1080 px on the long edge,
 * full frame preserved, never upscaled. Re-encoding drops EXIF/location metadata.
 */
object ImageMediaProcessor {
    const val MAX_EDGE = 1080
    const val QUALITY = 75

    suspend fun process(source: File, destination: File): ProcessedImage = withContext(Dispatchers.IO) {
        require(source.isFile) { "Image file does not exist" }
        require(source.canonicalFile != destination.canonicalFile) { "Source and destination must differ" }
        val parent = destination.absoluteFile.parentFile ?: error("Image destination has no parent directory")
        check(parent.isDirectory || parent.mkdirs()) { "Could not create image destination directory" }
        val temporary = File.createTempFile("image-", ".tmp.webp", parent)
        var bitmap: Bitmap? = null
        try {
            val decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(source)) { decoder, info, _ ->
                decoder.setTargetSampleSize(decodeSampleSize(info.size.width, info.size.height))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            bitmap = decoded
            val fitted = fitWithin(decoded.width, decoded.height, MAX_EDGE)
            if (fitted != decoded.width to decoded.height) {
                val scaled = Bitmap.createScaledBitmap(decoded, fitted.first, fitted.second, true)
                if (scaled !== decoded) decoded.recycle()
                bitmap = scaled
            }
            check(bitmap!!.compress(Bitmap.CompressFormat.WEBP_LOSSY, QUALITY, temporary.outputStream())) {
                "WebP encoding failed"
            }
            check(temporary.isFile && temporary.length() > 0) { "WebP encoder produced an empty file" }
            try {
                Files.move(
                    temporary.toPath(), destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            ProcessedImage(destination, bitmap!!.width, bitmap!!.height)
        } finally {
            bitmap?.recycle()
            temporary.delete()
        }
    }

    /** Pure geometry used by export and tests: aspect preserved, no upscale. */
    fun fitWithin(width: Int, height: Int, maxEdge: Int = MAX_EDGE): Pair<Int, Int> {
        require(width > 0 && height > 0 && maxEdge > 0)
        val longest = width.coerceAtLeast(height)
        if (longest <= maxEdge) return width to height
        val scale = maxEdge.toDouble() / longest
        return (width * scale).toInt().coerceAtLeast(1) to (height * scale).toInt().coerceAtLeast(1)
    }

    /** Decode at roughly 2× the export edge so downscaling keeps text reasonably sharp. */
    private fun decodeSampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (width.coerceAtLeast(height) / sample > MAX_EDGE * 2) sample *= 2
        return sample
    }
}
