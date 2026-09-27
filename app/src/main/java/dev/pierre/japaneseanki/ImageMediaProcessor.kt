package dev.pierre.japaneseanki

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.heifwriter.AvifWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Normalized crop bounds in the orientation-corrected source image. */
data class ImageCrop(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    init {
        require(left in 0f..1f && top in 0f..1f && right in 0f..1f && bottom in 0f..1f)
        require(left < right && top < bottom)
    }
}

data class ProcessedImage(val file: File, val width: Int, val height: Int)

object ImageMediaProcessor {
    const val MAX_EDGE = 2048
    private const val QUALITY = 85

    suspend fun process(source: File, destination: File, crop: ImageCrop? = null): ProcessedImage =
        withContext(Dispatchers.IO) {
            require(source.isFile) { "Image file does not exist" }
            destination.parentFile?.mkdirs()
            val temporary = File(destination.parentFile, "${destination.nameWithoutExtension}.tmp.avif")
            var bitmap: Bitmap? = null
            try {
                val decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(source)) { decoder, info, _ ->
                    val sample = sampleSize(info.size.width, info.size.height, crop)
                    decoder.setTargetSampleSize(sample)
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
                val cropped = crop?.let { cropBitmap(decoded, it) } ?: decoded
                if (cropped !== decoded) decoded.recycle()
                bitmap = cropped
                if (bitmap!!.width.coerceAtLeast(bitmap.height) > MAX_EDGE) {
                    val scale = MAX_EDGE.toFloat() / bitmap!!.width.coerceAtLeast(bitmap.height)
                    val scaled = Bitmap.createScaledBitmap(
                        bitmap!!,
                        (bitmap!!.width * scale).toInt().coerceAtLeast(1),
                        (bitmap!!.height * scale).toInt().coerceAtLeast(1),
                        true,
                    )
                    if (scaled !== bitmap) bitmap!!.recycle()
                    bitmap = scaled
                }

                temporary.delete()
                AvifWriter.Builder(
                    temporary.absolutePath,
                    bitmap!!.width,
                    bitmap!!.height,
                    AvifWriter.INPUT_MODE_BITMAP,
                ).setQuality(QUALITY).setMaxImages(1).build().use { writer ->
                    writer.start()
                    writer.addBitmap(bitmap!!)
                    writer.stop(0)
                }
                check(temporary.isFile && temporary.length() > 0) { "AVIF encoder produced no image" }
                try {
                    Files.move(
                        temporary.toPath(),
                        destination.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE,
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

    private fun sampleSize(width: Int, height: Int, crop: ImageCrop?): Int {
        val longest = width.coerceAtLeast(height)
        val largestCropFraction = crop?.let { maxOf(it.right - it.left, it.bottom - it.top) } ?: 1f
        val decodeEdge = (MAX_EDGE / largestCropFraction).toInt()
        var sample = 1
        while (longest / sample > decodeEdge) sample *= 2
        return sample
    }

    private fun cropBitmap(bitmap: Bitmap, crop: ImageCrop): Bitmap {
        val left = (bitmap.width * crop.left).toInt().coerceIn(0, bitmap.width - 1)
        val top = (bitmap.height * crop.top).toInt().coerceIn(0, bitmap.height - 1)
        val right = (bitmap.width * crop.right).toInt().coerceIn(left + 1, bitmap.width)
        val bottom = (bitmap.height * crop.bottom).toInt().coerceIn(top + 1, bitmap.height)
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }
}
