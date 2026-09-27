package dev.pierre.japaneseanki

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

sealed interface OcrSetupStatus {
    data object NotStarted : OcrSetupStatus
    data object Installing : OcrSetupStatus
    data object Ready : OcrSetupStatus
    data class Error(val message: String) : OcrSetupStatus
}

data class JapaneseOcrResult(val text: String, val blocks: List<OcrBlock>)

/** Offline Japanese OCR; native engine use and model installation are serialized process-wide. */
class JapaneseOcr(context: Context) {
    private val appContext = context.applicationContext
    @Volatile var setupStatus: OcrSetupStatus = OcrSetupStatus.NotStarted
        private set

    suspend fun recognize(image: File): JapaneseOcrResult = withContext(Dispatchers.IO) {
        require(image.isFile) { "Image file does not exist" }
        synchronized(lock) {
            val dataDirectory = installModels()
            val horizontal = recognizeCandidate(dataDirectory, image, "jpn")
            val vertical = recognizeCandidate(dataDirectory, image, "jpn_vert")
            val chosen = chooseOcrCandidate(horizontal, vertical)
            JapaneseOcrResult(chosen.text, chosen.blocks)
        }
    }

    private fun installModels(): File {
        setupStatus = OcrSetupStatus.Installing
        return try {
            val directory = File(appContext.filesDir, "tessdata").apply { mkdirs() }
            MODELS.forEach { model ->
                val target = File(directory, model.filename)
                if (!target.isFile || target.sha256() != model.sha256) {
                    val temporary = File(directory, "${model.filename}.tmp")
                    try {
                        appContext.assets.open("tessdata/${model.filename}").use { input ->
                            temporary.outputStream().use(input::copyTo)
                        }
                        check(temporary.sha256() == model.sha256) { "OCR model checksum mismatch: ${model.filename}" }
                        check(temporary.renameTo(target)) { "Could not install OCR model: ${model.filename}" }
                    } finally {
                        temporary.delete()
                    }
                }
            }
            setupStatus = OcrSetupStatus.Ready
            directory.parentFile ?: error("Invalid OCR data directory")
        } catch (error: Exception) {
            setupStatus = OcrSetupStatus.Error(error.message ?: "OCR model setup failed")
            throw error
        }
    }

    private fun recognizeCandidate(dataDirectory: File, image: File, language: String): OcrCandidate {
        val api = TessBaseAPI()
        var bitmap: Bitmap? = null
        try {
            val decoded = BitmapFactory.decodeFile(image.absolutePath) ?: error("Cannot decode image file")
            bitmap = decoded
            check(api.init(dataDirectory.absolutePath, language, TessBaseAPI.OEM_LSTM_ONLY)) {
                "Could not initialize Japanese OCR ($language)"
            }
            api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
            api.setImage(decoded)
            val fullText = api.getUTF8Text().orEmpty()
            val iterator = api.resultIterator ?: return OcrCandidate(fullText, emptyList(), 0f)
            val blocks = mutableListOf<OcrBlock>()
            try {
                iterator.begin()
                do {
                    val blockText = iterator.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_BLOCK).orEmpty()
                    val bounds = iterator.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_BLOCK)
                    if (blockText.isNotBlank() && !bounds.isEmpty) {
                        blocks += OcrBlock(
                            blockText,
                            iterator.confidence(TessBaseAPI.PageIteratorLevel.RIL_BLOCK),
                            OcrBounds(bounds.left, bounds.top, bounds.right, bounds.bottom),
                        )
                    }
                } while (iterator.next(TessBaseAPI.PageIteratorLevel.RIL_BLOCK))
            } finally {
                iterator.delete()
            }
            return OcrCandidate(fullText, blocks, meanBlockConfidence(blocks))
        } finally {
            try {
                api.recycle()
            } finally {
                bitmap?.recycle()
            }
        }
    }

    private data class Model(val filename: String, val sha256: String)

    private fun File.sha256(): String = inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private val lock = Any()
        private val MODELS = listOf(
            Model("jpn.traineddata", "1f5de9236d2e85f5fdf4b3c500f2d4926f8d9449f28f5394472d9e8d83b91b4d"),
            Model("jpn_vert.traineddata", "bf1e2640954691797e2dc14f38533e601b59ee37958698ae0f0b81dc6f09c71b"),
        )
    }
}
