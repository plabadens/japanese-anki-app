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

/** Structural checks applied to provider output, draft copies, and reusable cache entries. */
object AudioFileValidator {
    const val MAX_BYTES: Long = 20L * 1024 * 1024

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

    /** Check container structure before caching, drafting, or exporting. */
    @Throws(IOException::class)
    fun requireSupported(file: File): File = when (file.extension.lowercase()) {
        "wav" -> requireWav(file)
        "ogg" -> requireOgg(file)
        "mp3" -> requireMp3Structure(file)
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

    /** ID3v2 tag or MPEG frame sync; Google returns MP3 for the fixed delivery configuration. */
    private fun requireMp3Structure(file: File): File {
        if (!file.isFile || file.length() < 256 || file.length() > MAX_BYTES) {
            throw IOException("Audio provider returned no usable MP3 file")
        }
        val header = ByteArray(4)
        val read = file.inputStream().use { it.read(header) }
        if (read < 4) throw IOException("Audio provider returned no usable MP3 file")
        val id3 = header[0].toInt() == 0x49 && header[1].toInt() == 0x44 && header[2].toInt() == 0x33
        val frameSync = (header[0].toInt() and 0xff) == 0xff && (header[1].toInt() and 0xe0) == 0xe0
        if (!id3 && !frameSync) throw IOException("Audio provider returned no usable MP3 file")
        return file
    }
}
