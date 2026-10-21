package dev.pierre.japaneseanki

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

sealed interface OcrSetupStatus {
    data object NotStarted : OcrSetupStatus
    data object Installing : OcrSetupStatus
    data object Ready : OcrSetupStatus
    data class Error(val message: String) : OcrSetupStatus
}

data class JapaneseOcrResult(
    val text: String,
    val blocks: List<OcrBlock>,
    val sourceWidth: Int = 0,
    val sourceHeight: Int = 0,
)

/** Uses the bundled ML Kit Japanese model; recognition itself does not download a model or use network. */
class JapaneseOcr(@Suppress("UNUSED_PARAMETER") context: Context) {
    @Volatile var setupStatus: OcrSetupStatus = OcrSetupStatus.NotStarted
        private set

    suspend fun recognize(image: File): JapaneseOcrResult = withContext(Dispatchers.IO) {
        require(image.isFile) { "Image file does not exist" }
        synchronized(lock) {
            setupStatus = OcrSetupStatus.Installing
            var bitmap: Bitmap? = null
            try {
                var sourceWidth = 0
                var sourceHeight = 0
                val decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(image)) { decoder, info, _ ->
                    sourceWidth = info.size.width
                    sourceHeight = info.size.height
                    decoder.setTargetSampleSize(sampleSize(sourceWidth, sourceHeight))
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
                bitmap = decoded
                val recognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
                try {
                    val result = Tasks.await(recognizer.process(InputImage.fromBitmap(decoded, 0)))
                    val blocks = result.textBlocks.mapNotNull { block ->
                        val bounds = block.boundingBox ?: return@mapNotNull null
                        if (block.text.isBlank()) return@mapNotNull null
                        OcrBlock(
                            block.text,
                            100f,
                            scaleOcrBounds(
                                OcrBounds(bounds.left, bounds.top, bounds.right, bounds.bottom),
                                decoded.width,
                                decoded.height,
                                sourceWidth,
                                sourceHeight,
                            ),
                        )
                    }
                    setupStatus = OcrSetupStatus.Ready
                    JapaneseOcrResult(result.text, blocks, sourceWidth, sourceHeight)
                } finally {
                    recognizer.close()
                }
            } catch (error: Exception) {
                setupStatus = OcrSetupStatus.Error(error.message ?: "Japanese OCR failed")
                throw error
            } finally {
                bitmap?.recycle()
            }
        }
    }

    private fun sampleSize(width: Int, height: Int): Int {
        val longest = width.coerceAtLeast(height)
        var sample = 1
        while (longest / sample > 2048) sample *= 2
        return sample
    }

    companion object {
        private val lock = Any()
    }
}
