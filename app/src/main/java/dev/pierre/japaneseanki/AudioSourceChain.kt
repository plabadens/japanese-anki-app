package dev.pierre.japaneseanki

import java.io.File
import java.io.IOException

data class AudioSourceConfiguration(
    val azureConfigured: Boolean = false,
)

enum class AudioSource { RECORDED_PRONUNCIATION, AZURE, ANDROID_OFFLINE }

data class AudioSourceIdentity(
    val source: AudioSource,
    val providerIdentity: String,
    val engine: String? = null,
    val voice: String,
    val rate: Float,
) {
    init {
        require(providerIdentity.isNotBlank() && voice.isNotBlank())
        require(rate.isFinite() && rate > 0f)
    }
}

data class AudioSynthesisRequest(
    val item: AudioItem,
    val text: String,
    val revision: Long,
    val entryIdentity: String? = null,
) {
    init { require(text.isNotBlank() && revision >= 0) }
}

/** Implementations write complete WAV or Ogg audio and keep text and credentials out of diagnostics. */
fun interface AudioProvider {
    fun synthesize(request: AudioSynthesisRequest, output: File): File
}

data class AudioProviderBinding(
    val identity: AudioSourceIdentity,
    val provider: AudioProvider,
    val available: () -> Boolean = { true },
)

/** Simple structural WAV check applied to both provider output and reusable cache entries. */
object AudioFileValidator {
    @Throws(IOException::class)
    fun requireWav(file: File): File {
        if (!file.isFile || file.length() < 44) throw IOException("Audio provider returned no usable WAV file")
        try {
            java.io.RandomAccessFile(file, "r").use { input ->
                val riff = ByteArray(4).also(input::readFully).toString(Charsets.US_ASCII)
                val riffLength = Integer.reverseBytes(input.readInt()).toLong() and 0xffffffffL
                val wave = ByteArray(4).also(input::readFully).toString(Charsets.US_ASCII)
                if (riff != "RIFF" || wave != "WAVE" || riffLength != file.length() - 8L) {
                    throw IOException("Audio provider returned invalid WAV data")
                }
                var validFormat = false
                var hasData = false
                while (input.filePointer < file.length()) {
                    if (file.length() - input.filePointer < 8) throw IOException("Audio provider returned invalid WAV data")
                    val id = ByteArray(4).also(input::readFully).toString(Charsets.US_ASCII)
                    val size = Integer.reverseBytes(input.readInt()).toLong() and 0xffffffffL
                    val start = input.filePointer
                    val end = start + size + (size and 1L)
                    if (end < start || end > file.length()) throw IOException("Audio provider returned invalid WAV data")
                    if (id == "fmt ") {
                        if (size < 16) throw IOException("Audio provider returned invalid WAV data")
                        val format = ByteArray(16).also(input::readFully)
                        val fields = java.nio.ByteBuffer.wrap(format).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                        val encoding = fields.short.toInt() and 0xffff
                        val channels = fields.short.toInt() and 0xffff
                        val sampleRate = fields.int.toLong() and 0xffffffffL
                        val byteRate = fields.int.toLong() and 0xffffffffL
                        val blockAlign = fields.short.toInt() and 0xffff
                        val bitsPerSample = fields.short.toInt() and 0xffff
                        val expectedAlign = channels * bitsPerSample / 8
                        validFormat = encoding == 1 && channels in 1..8 && sampleRate in 1..384000 &&
                            bitsPerSample in setOf(8, 16, 24, 32) && blockAlign == expectedAlign &&
                            byteRate == sampleRate * blockAlign
                    }
                    if (id == "data" && size > 0) hasData = true
                    input.seek(end)
                }
                if (!validFormat || !hasData) throw IOException("Audio provider returned invalid WAV data")
            }
        } catch (error: IOException) {
            throw error
        } catch (_: Exception) {
            throw IOException("Audio provider returned invalid WAV data")
        }
        return file
    }

    /** Check Ogg page structure, including segment bounds, before caching or exporting recordings. */
    @Throws(IOException::class)
    fun requireSupported(file: File): File = when (file.extension.lowercase()) {
        "wav" -> requireWav(file)
        "ogg" -> requireOgg(file)
        "" -> {
            val isWave = file.isFile && file.length() >= 12 && java.io.RandomAccessFile(file, "r").use { input ->
                ByteArray(4).also(input::readFully).toString(Charsets.US_ASCII) == "RIFF" &&
                    (input.seek(8).let { ByteArray(4).also(input::readFully).toString(Charsets.US_ASCII) } == "WAVE")
            }
            if (isWave) requireWav(file) else throw IOException("Audio provider returned an unsupported audio format")
        }
        else -> throw IOException("Audio provider returned an unsupported audio format")
    }

    private fun requireOgg(file: File): File {
        if (!file.isFile || file.length() < 28) throw IOException("Audio provider returned no usable Ogg file")
        try {
            java.io.RandomAccessFile(file, "r").use { input ->
                var pages = 0
                while (input.filePointer < file.length()) {
                    if (file.length() - input.filePointer < 27) throw IOException("Audio provider returned invalid Ogg data")
                    if (ByteArray(4).also(input::readFully).toString(Charsets.US_ASCII) != "OggS" || input.readUnsignedByte() != 0) {
                        throw IOException("Audio provider returned invalid Ogg data")
                    }
                    input.skipBytes(1 + 8 + 4 + 4 + 4)
                    val segments = input.readUnsignedByte()
                    if (file.length() - input.filePointer < segments) throw IOException("Audio provider returned invalid Ogg data")
                    var payload = 0L
                    repeat(segments) { payload += input.readUnsignedByte() }
                    if (file.length() - input.filePointer < payload) throw IOException("Audio provider returned invalid Ogg data")
                    input.seek(input.filePointer + payload)
                    pages++
                }
                if (pages == 0 || input.filePointer != file.length()) throw IOException("Audio provider returned invalid Ogg data")
            }
        } catch (error: IOException) {
            throw error
        } catch (_: Exception) {
            throw IOException("Audio provider returned invalid Ogg data")
        }
        return file
    }
}

/** Pure backend source-priority seam for recorded, Azure, and on-device audio. */
class AudioSourceChain(
    private val configuration: AudioSourceConfiguration,
    private val recorded: AudioProviderBinding?,
    private val azure: AudioProviderBinding?,
    private val androidOffline: AudioProviderBinding?,
    private val reusableCache: ReusableAudioCache? = null,
) {
    fun generate(request: AudioSynthesisRequest, outputDirectory: File): AudioChainResult {
        val candidates = buildList {
            if (request.item == AudioItem.VOCABULARY && request.entryIdentity != null) recorded?.let(::add)
            if (configuration.azureConfigured) azure?.let(::add)
            androidOffline?.let(::add)
        }
        if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
            return AudioChainResult.Failed(request, "Audio output is unavailable")
        }
        for (binding in candidates) {
            val identity = binding.identity
            val key = AudioCacheKey(
                source = "${identity.source}:${identity.providerIdentity}", text = request.text,
                voice = identity.voice, rate = identity.rate, engine = identity.engine.orEmpty(),
                entryIdentity = if (request.item == AudioItem.VOCABULARY) request.entryIdentity.orEmpty() else "",
            )
            reusableCache?.get(key)?.let { cached ->
                if (runCatching { AudioFileValidator.requireSupported(cached) }.isSuccess) {
                    val copy = File(outputDirectory, "${request.item.name.lowercase()}-${request.revision}.${cached.extension.lowercase()}")
                    try {
                        cached.copyTo(copy, overwrite = true)
                        return AudioChainResult.Ready(request, identity.source, copy)
                    } catch (_: Exception) { copy.delete() }
                }
            }
            if (!runCatching(binding.available).getOrDefault(false)) continue
            val extension = if (identity.source == AudioSource.RECORDED_PRONUNCIATION) "ogg" else "wav"
            val output = File(outputDirectory, "${request.item.name.lowercase()}-${request.revision}-${identity.source.name.lowercase()}.$extension")
            try {
                val produced = AudioFileValidator.requireSupported(binding.provider.synthesize(request, output))
                reusableCache?.put(key, produced)
                return AudioChainResult.Ready(request, identity.source, produced)
            } catch (_: Exception) {
                output.delete()
            }
        }
        return AudioChainResult.Failed(request, "No audio source is currently available")
    }
}

sealed interface AudioChainResult {
    val request: AudioSynthesisRequest
    data class Ready(override val request: AudioSynthesisRequest, val source: AudioSource, val file: File) : AudioChainResult
    data class Failed(override val request: AudioSynthesisRequest, val explanation: String) : AudioChainResult
}
