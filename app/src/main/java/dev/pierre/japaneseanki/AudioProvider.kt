package dev.pierre.japaneseanki

import java.io.File
import java.io.IOException

enum class AudioItem { SENTENCE, VOCABULARY }

/** One recording or synthesis request. Text and credentials stay out of diagnostics. */
data class AudioSynthesisRequest(
    val item: AudioItem,
    val text: String,
    val revision: Long,
    val entryIdentity: String? = null,
) {
    init {
        require(text.isNotBlank() && revision >= 0)
    }
}

/** Implementations write complete WAV, Ogg, or MP3 audio into [output] and return it. */
fun interface AudioProvider {
    fun synthesize(request: AudioSynthesisRequest, output: File): File
}

/**
 * The audio containers this app can validate, cache, materialize and hand to AnkiDroid.
 *
 * One list owns the supported extensions; cache lookup, draft naming and media-import
 * sanitising all consult it instead of repeating `wav`/`ogg`/`mp3` constants.
 */
enum class AudioFormat(val extension: String) {
    WAV("wav"),
    OGG("ogg"),
    MP3("mp3"),
    ;

    companion object {
        val extensions: List<String> = entries.map(AudioFormat::extension)
        val extensionsWithDot: List<String> = extensions.map { ".$it" }

        fun fromExtension(extension: String): AudioFormat? =
            entries.firstOrNull { it.extension.equals(extension, ignoreCase = true) }

        fun fromFilename(name: String): AudioFormat? =
            fromExtension(name.substringAfterLast('.', ""))
    }
}

/**
 * Structural checks applied to provider output, draft copies, and reusable cache entries.
 *
 * Validation is content-first: the container is sniffed from the bytes, then the matching
 * structural check runs. A file whose name claims a format it does not contain is rejected
 * instead of failing later inside the cache or behind the FileProvider.
 */
object AudioFileValidator {
    const val MAX_BYTES: Long = 20L * 1024 * 1024

    /** Sniff the container from the leading bytes; `null` when it is not supported audio. */
    fun detect(file: File): AudioFormat? {
        if (!file.isFile || file.length() < 4) return null
        val head = ByteArray(4)
        val read = runCatching { file.inputStream().use { it.read(head) } }.getOrNull() ?: return null
        if (read < 4) return null
        return when {
            head[0] == 'R'.code.toByte() && head[1] == 'I'.code.toByte() &&
                head[2] == 'F'.code.toByte() && head[3] == 'F'.code.toByte() -> AudioFormat.WAV
            head[0] == 'O'.code.toByte() && head[1] == 'g'.code.toByte() &&
                head[2] == 'g'.code.toByte() && head[3] == 'S'.code.toByte() -> AudioFormat.OGG
            isMp3Header(head) -> AudioFormat.MP3
            else -> null
        }
    }

    /** Validate by content only, ignoring the file name. Returns the detected format. */
    @Throws(IOException::class)
    fun requireContentFormat(file: File): AudioFormat {
        val format = detect(file) ?: throw IOException("Audio is not a supported WAV, Ogg or MP3 file")
        when (format) {
            AudioFormat.WAV -> requireWav(file)
            AudioFormat.OGG -> requireOgg(file)
            AudioFormat.MP3 -> requireMp3Structure(file)
        }
        return format
    }

    /** Validate content and require the file name extension to agree with the bytes. */
    @Throws(IOException::class)
    fun requireFormat(file: File): AudioFormat {
        val detected = requireContentFormat(file)
        val named = AudioFormat.fromFilename(file.name)
            ?: throw IOException("Audio filename does not use a supported extension")
        if (named != detected) throw IOException("Audio filename extension does not match its content")
        return detected
    }

    /** Compatibility wrapper: validate content and name, return the file. */
    @Throws(IOException::class)
    fun requireSupported(file: File): File {
        requireFormat(file)
        return file
    }

    @Throws(IOException::class)
    fun requireWav(file: File): File {
        if (!file.isFile || file.length() < 44 || file.length() > MAX_BYTES) {
            throw IOException("Audio provider returned no usable WAV file")
        }
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

    @Throws(IOException::class)
    fun requireOgg(file: File): File {
        if (!file.isFile || file.length() < 28 || file.length() > MAX_BYTES) {
            throw IOException("Audio provider returned no usable Ogg file")
        }
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

    /**
     * Structural MP3 check: an ID3v2 tag is skipped, then the first MPEG frame header must parse
     * and its declared frame length must fit inside the file. Google returns MP3 for the fixed
     * delivery configuration, and a WAV/Ogg payload is no longer mistaken for MP3.
     */
    @Throws(IOException::class)
    fun requireMp3Structure(file: File): File {
        if (!file.isFile || file.length() < MIN_MP3_BYTES || file.length() > MAX_BYTES) {
            throw IOException("Audio provider returned no usable MP3 file")
        }
        try {
            java.io.RandomAccessFile(file, "r").use { input ->
                var offset = 0L
                val id3 = ByteArray(10)
                input.readFully(id3)
                if (id3[0] == 'I'.code.toByte() && id3[1] == 'D'.code.toByte() && id3[2] == '3'.code.toByte()) {
                    val major = id3[3].toInt() and 0xff
                    if (major !in 2..4) throw IOException("Audio provider returned invalid MP3 data")
                    if (id3[6].toInt() and 0x80 != 0 || id3[7].toInt() and 0x80 != 0 ||
                        id3[8].toInt() and 0x80 != 0 || id3[9].toInt() and 0x80 != 0
                    ) {
                        throw IOException("Audio provider returned invalid MP3 data")
                    }
                    val size = ((id3[6].toInt() and 0x7f) shl 21) or ((id3[7].toInt() and 0x7f) shl 14) or
                        ((id3[8].toInt() and 0x7f) shl 7) or (id3[9].toInt() and 0x7f)
                    offset = 10L + size
                    if (offset >= file.length()) throw IOException("Audio provider returned invalid MP3 data")
                }
                var found = false
                while (offset + 4 <= file.length() && offset < ID3_FRAME_SEARCH_LIMIT) {
                    input.seek(offset)
                    val frame = ByteArray(4).also(input::readFully)
                    val frameLength = mpegFrameLength(frame)
                    if (frameLength != null) {
                        if (offset + frameLength > file.length()) throw IOException("Audio provider returned invalid MP3 data")
                        found = true
                        break
                    }
                    offset++
                }
                if (!found) throw IOException("Audio provider returned no usable MP3 file")
            }
        } catch (error: IOException) {
            throw error
        } catch (_: Exception) {
            throw IOException("Audio provider returned invalid MP3 data")
        }
        return file
    }

    private fun isMp3Header(head: ByteArray): Boolean =
        (head[0] == 'I'.code.toByte() && head[1] == 'D'.code.toByte() && head[2] == '3'.code.toByte()) ||
            mpegFrameLength(head) != null

    /**
     * Frame length from an MPEG audio header, or `null` when the four bytes are not a valid
     * header. Reserved version/layer/bitrate/sample-rate combinations are rejected.
     */
    private fun mpegFrameLength(header: ByteArray): Int? {
        if (header.size < 4) return null
        val b0 = header[0].toInt() and 0xff
        val b1 = header[1].toInt() and 0xff
        val b2 = header[2].toInt() and 0xff
        val b3 = header[3].toInt() and 0xff
        if (b0 != 0xff || (b1 and 0xe0) != 0xe0) return null
        val version = (b1 shr 3) and 0x03
        val layer = (b1 shr 1) and 0x03
        if (version == 1 || layer == 0) return null
        val bitrateIndex = (b2 shr 4) and 0x0f
        val sampleRateIndex = (b2 shr 2) and 0x03
        if (bitrateIndex == 0 || bitrateIndex == 0x0f || sampleRateIndex == 0x03) return null
        val mpeg1 = version == 3
        val baseRates = when {
            mpeg1 && layer == 3 ->
                intArrayOf(0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448)
            mpeg1 && layer == 2 ->
                intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384)
            mpeg1 ->
                intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)
            layer == 3 ->
                intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256)
            else ->
                intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160)
        }
        val bitrateKbps = baseRates[bitrateIndex]
        val sampleRates = intArrayOf(44100, 48000, 32000)
        val sampleRate = sampleRates[sampleRateIndex] / if (mpeg1) 1 else if (version == 2) 2 else 4
        val padding = (b2 shr 1) and 0x01
        return when (layer) {
            3 -> (12 * bitrateKbps * 1000 / sampleRate + padding) * 4
            2 -> 144 * bitrateKbps * 1000 / sampleRate + padding
            else -> (if (mpeg1) 144 else 72) * bitrateKbps * 1000 / sampleRate + padding
        }
    }

    private const val MIN_MP3_BYTES = 256L
    private const val ID3_FRAME_SEARCH_LIMIT = 1024L * 1024L
}
