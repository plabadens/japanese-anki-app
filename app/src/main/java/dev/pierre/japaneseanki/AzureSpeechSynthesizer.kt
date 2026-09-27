package dev.pierre.japaneseanki

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.UUID

/** Safe, file-backed Azure Japanese speech synthesis using the public REST endpoint. */
class AzureSpeechSynthesizer(
    private val connectionFactory: ConnectionFactory = ConnectionFactory { url ->
        url.openConnection() as HttpURLConnection
    },
) {
    fun synthesizeToFile(
        subscriptionKey: String,
        region: String,
        voice: String,
        rate: Float,
        text: String,
        output: File,
        timeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
    ): File {
        validateInputs(subscriptionKey, region, voice, rate, text, output, timeoutMillis)
        val requestBody = buildSsml(voice, rate, text).toByteArray(Charsets.UTF_8)
        if (requestBody.size > MAX_REQUEST_BYTES) {
            throw AzureSpeechException(AzureSpeechErrorKind.INVALID_INPUT, "Speech text is too long")
        }

        val parent = output.absoluteFile.parentFile
            ?: throw AzureSpeechException(AzureSpeechErrorKind.OUTPUT_FAILURE, "Audio output path is invalid")
        if ((!parent.exists() && !parent.mkdirs()) || !parent.isDirectory) {
            throw AzureSpeechException(AzureSpeechErrorKind.OUTPUT_FAILURE, "Audio output directory is unavailable")
        }
        val temporary = File(parent, ".${output.name}.${UUID.randomUUID()}.tmp")
        val endpoint = URL("https://$region.tts.speech.microsoft.com/cognitiveservices/v1")
        var connection: HttpURLConnection? = null
        try {
            connection = try {
                connectionFactory.open(endpoint)
            } catch (_: Exception) {
                throw AzureSpeechException(AzureSpeechErrorKind.NETWORK, "Speech service could not be reached")
            }
            configure(connection, subscriptionKey, requestBody, timeoutMillis)
            val status = try {
                connection.responseCode
            } catch (_: IOException) {
                throw AzureSpeechException(AzureSpeechErrorKind.NETWORK, "Speech service could not be reached")
            }
            if (status != HTTP_OK) throw statusFailure(status)

            val declaredLength = try {
                connection.contentLengthLong
            } catch (_: IOException) {
                throw AzureSpeechException(AzureSpeechErrorKind.NETWORK, "Speech audio could not be downloaded")
            }
            if (declaredLength > MAX_AUDIO_BYTES) {
                throw AzureSpeechException(AzureSpeechErrorKind.RESPONSE_TOO_LARGE, "Speech audio is too large")
            }
            downloadAudio(connection, temporary)
            when (validateWaveFile(temporary)) {
                WaveValidation.VALID -> Unit
                WaveValidation.MALFORMED -> throw AzureSpeechException(
                    AzureSpeechErrorKind.INVALID_AUDIO,
                    "Speech service returned invalid audio",
                )
                WaveValidation.UNREADABLE -> throw AzureSpeechException(
                    AzureSpeechErrorKind.OUTPUT_FAILURE,
                    "Speech audio could not be read",
                )
            }
            commit(temporary, output)
            return output
        } finally {
            temporary.delete()
            connection?.disconnect()
        }
    }

    private fun configure(
        connection: HttpURLConnection,
        subscriptionKey: String,
        requestBody: ByteArray,
        timeoutMillis: Int,
    ) {
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.doOutput = true
            connection.useCaches = false
            connection.connectTimeout = timeoutMillis
            connection.readTimeout = timeoutMillis
            connection.setRequestProperty("Ocp-Apim-Subscription-Key", subscriptionKey)
            connection.setRequestProperty("Content-Type", "application/ssml+xml")
            connection.setRequestProperty("X-Microsoft-OutputFormat", "riff-24khz-16bit-mono-pcm")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setFixedLengthStreamingMode(requestBody.size)
            connection.outputStream.use { it.write(requestBody) }
        } catch (_: IOException) {
            throw AzureSpeechException(AzureSpeechErrorKind.NETWORK, "Speech request could not be sent")
        }
    }

    private fun downloadAudio(connection: HttpURLConnection, temporary: File) {
        val input = try {
            connection.inputStream
        } catch (_: IOException) {
            throw AzureSpeechException(AzureSpeechErrorKind.NETWORK, "Speech audio could not be downloaded")
        }
        try {
            input.use {
                try {
                    temporary.outputStream().use { outputStream ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val count = try {
                                input.read(buffer)
                            } catch (_: IOException) {
                                throw AzureSpeechException(AzureSpeechErrorKind.NETWORK, "Speech audio could not be downloaded")
                            }
                            if (count < 0) break
                            if (count == 0) continue
                            total += count
                            if (total > MAX_AUDIO_BYTES) {
                                throw AzureSpeechException(AzureSpeechErrorKind.RESPONSE_TOO_LARGE, "Speech audio is too large")
                            }
                            try {
                                outputStream.write(buffer, 0, count)
                            } catch (_: IOException) {
                                throw AzureSpeechException(AzureSpeechErrorKind.OUTPUT_FAILURE, "Speech audio could not be saved")
                            }
                        }
                        try {
                            outputStream.fd.sync()
                        } catch (_: IOException) {
                            throw AzureSpeechException(AzureSpeechErrorKind.OUTPUT_FAILURE, "Speech audio could not be saved")
                        }
                    }
                } catch (error: AzureSpeechException) {
                    throw error
                } catch (_: IOException) {
                    throw AzureSpeechException(AzureSpeechErrorKind.OUTPUT_FAILURE, "Speech audio could not be saved")
                }
            }
        } catch (error: AzureSpeechException) {
            throw error
        } catch (_: IOException) {
            throw AzureSpeechException(AzureSpeechErrorKind.NETWORK, "Speech audio could not be downloaded")
        }
    }

    private fun commit(temporary: File, output: File) {
        try {
            try {
                Files.move(
                    temporary.toPath(),
                    output.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                throw AzureSpeechException(
                    AzureSpeechErrorKind.OUTPUT_FAILURE,
                    "Atomic audio replacement is unavailable",
                )
            }
        } catch (error: AzureSpeechException) {
            throw error
        } catch (_: IOException) {
            throw AzureSpeechException(AzureSpeechErrorKind.OUTPUT_FAILURE, "Speech audio could not be saved")
        }
    }

    private fun statusFailure(status: Int): AzureSpeechException = when (status) {
        400, 415 -> AzureSpeechException(AzureSpeechErrorKind.REQUEST_REJECTED, "Speech request was rejected")
        401 -> AzureSpeechException(AzureSpeechErrorKind.AUTHENTICATION, "Speech credentials were rejected")
        429 -> AzureSpeechException(AzureSpeechErrorKind.RATE_LIMITED, "Speech service quota was reached")
        502, 503 -> AzureSpeechException(AzureSpeechErrorKind.SERVICE_UNAVAILABLE, "Speech service is temporarily unavailable")
        in 300..399 -> AzureSpeechException(AzureSpeechErrorKind.SERVICE_ERROR, "Speech service returned an unexpected redirect")
        else -> AzureSpeechException(AzureSpeechErrorKind.SERVICE_ERROR, "Speech service returned an error")
    }

    private fun validateInputs(
        subscriptionKey: String,
        region: String,
        voice: String,
        rate: Float,
        text: String,
        output: File,
        timeoutMillis: Int,
    ) {
        if (subscriptionKey.isBlank() || subscriptionKey.length > MAX_KEY_LENGTH) {
            throw AzureSpeechException(AzureSpeechErrorKind.INVALID_INPUT, "Speech credentials are missing")
        }
        if (!REGION_PATTERN.matches(region)) {
            throw AzureSpeechException(AzureSpeechErrorKind.INVALID_INPUT, "Speech region is invalid")
        }
        if (!VOICE_PATTERN.matches(voice) || !voice.startsWith("ja-JP-")) {
            throw AzureSpeechException(AzureSpeechErrorKind.INVALID_INPUT, "Japanese speech voice is invalid")
        }
        if (!rate.isFinite() || rate !in MIN_RATE..MAX_RATE) {
            throw AzureSpeechException(AzureSpeechErrorKind.INVALID_INPUT, "Speech rate is invalid")
        }
        if (text.isBlank() || text.length > MAX_TEXT_CHARS || text.any { !isXmlCharacter(it) }) {
            throw AzureSpeechException(AzureSpeechErrorKind.INVALID_INPUT, "Speech text is invalid")
        }
        if (!output.name.endsWith(".wav", ignoreCase = true)) {
            throw AzureSpeechException(AzureSpeechErrorKind.INVALID_INPUT, "Speech output must be a WAV file")
        }
        if (timeoutMillis !in 1..MAX_TIMEOUT_MILLIS) {
            throw AzureSpeechException(AzureSpeechErrorKind.INVALID_INPUT, "Speech timeout is invalid")
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 30_000
        const val MAX_TIMEOUT_MILLIS = 120_000
        const val MAX_AUDIO_BYTES = 16L * 1024 * 1024
        private const val MAX_REQUEST_BYTES = 64 * 1024
        private const val MAX_TEXT_CHARS = 20_000
        private const val MAX_KEY_LENGTH = 512
        private const val BUFFER_SIZE = 8 * 1024
        private const val HTTP_OK = 200
        private const val MIN_RATE = 0.5f
        private const val MAX_RATE = 2f
        private const val USER_AGENT = "JapaneseAnki/1.0"
        private val REGION_PATTERN = Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")
        private val VOICE_PATTERN = Regex("ja-JP-[A-Za-z0-9][A-Za-z0-9-]{0,63}")

        private fun buildSsml(voice: String, rate: Float, text: String): String {
            val ratePercent = (rate - 1f) * 100f
            val rateAttribute = String.format(Locale.US, "%+.2f%%", ratePercent)
            return "<speak version=\"1.0\" xmlns=\"http://www.w3.org/2001/10/synthesis\" " +
                "xml:lang=\"ja-JP\"><voice name=\"$voice\"><prosody rate=\"$rateAttribute\">" +
                "${escapeXml(text)}</prosody></voice></speak>"
        }

        private fun isXmlCharacter(character: Char): Boolean =
            character == '\t' || character == '\n' || character == '\r' || character >= ' '

        private fun escapeXml(value: String): String = buildString(value.length) {
            value.forEach { character ->
                when (character) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&apos;")
                    else -> append(character)
                }
            }
        }

        private enum class WaveValidation { VALID, MALFORMED, UNREADABLE }

        private fun validateWaveFile(file: File): WaveValidation {
            if (!file.isFile || file.length() < RIFF_HEADER_BYTES) return WaveValidation.MALFORMED
            return try {
                java.io.RandomAccessFile(file, "r").use { input ->
                    if (readFourCc(input) != "RIFF" || input.readUnsignedIntLe() != file.length() - 8L) return WaveValidation.MALFORMED
                    if (readFourCc(input) != "WAVE") return WaveValidation.MALFORMED
                    val riffEnd = file.length()
                    var hasValidFormat = false
                    var hasAudioData = false
                    while (input.filePointer < riffEnd) {
                        if (riffEnd - input.filePointer < CHUNK_HEADER_BYTES) return WaveValidation.MALFORMED
                        val chunkId = readFourCc(input)
                        val chunkSize = input.readUnsignedIntLe()
                        val chunkDataStart = input.filePointer
                        val chunkDataEnd = chunkDataStart + chunkSize
                        val nextChunk = chunkDataEnd + (chunkSize and 1L)
                        if (chunkDataEnd < chunkDataStart || nextChunk < chunkDataEnd || nextChunk > riffEnd) return WaveValidation.MALFORMED
                        when (chunkId) {
                            "fmt " -> {
                                if (chunkSize < FORMAT_CHUNK_BYTES) return WaveValidation.MALFORMED
                                if (input.readUnsignedShortLe() != PCM_FORMAT ||
                                    input.readUnsignedShortLe() != MONO_CHANNELS ||
                                    input.readUnsignedIntLe() != SAMPLE_RATE ||
                                    input.readUnsignedIntLe() != BYTE_RATE ||
                                    input.readUnsignedShortLe() != BLOCK_ALIGN ||
                                    input.readUnsignedShortLe() != BITS_PER_SAMPLE
                                ) return WaveValidation.MALFORMED
                                hasValidFormat = true
                            }
                            "data" -> {
                                if (chunkSize == 0L || chunkSize % BLOCK_ALIGN != 0L) return WaveValidation.MALFORMED
                                hasAudioData = true
                            }
                        }
                        input.seek(nextChunk)
                    }
                    if (input.filePointer == riffEnd && hasValidFormat && hasAudioData) {
                        WaveValidation.VALID
                    } else {
                        WaveValidation.MALFORMED
                    }
                }
            } catch (_: IOException) {
                WaveValidation.UNREADABLE
            }
        }

        private fun readFourCc(input: java.io.RandomAccessFile): String =
            CharArray(4) { input.readUnsignedByte().toChar() }.concatToString()

        private fun java.io.RandomAccessFile.readUnsignedShortLe(): Int =
            readUnsignedByte() or (readUnsignedByte() shl 8)

        private fun java.io.RandomAccessFile.readUnsignedIntLe(): Long =
            readUnsignedByte().toLong() or
                (readUnsignedByte().toLong() shl 8) or
                (readUnsignedByte().toLong() shl 16) or
                (readUnsignedByte().toLong() shl 24)

        private const val RIFF_HEADER_BYTES = 12L
        private const val CHUNK_HEADER_BYTES = 8L
        private const val FORMAT_CHUNK_BYTES = 16L
        private const val PCM_FORMAT = 1
        private const val MONO_CHANNELS = 1
        private const val SAMPLE_RATE = 24_000L
        private const val BYTE_RATE = 48_000L
        private const val BLOCK_ALIGN = 2
        private const val BITS_PER_SAMPLE = 16

    }

    fun interface ConnectionFactory {
        fun open(url: URL): HttpURLConnection
    }
}

enum class AzureSpeechErrorKind {
    INVALID_INPUT,
    AUTHENTICATION,
    REQUEST_REJECTED,
    RATE_LIMITED,
    SERVICE_UNAVAILABLE,
    SERVICE_ERROR,
    NETWORK,
    RESPONSE_TOO_LARGE,
    INVALID_AUDIO,
    OUTPUT_FAILURE,
}

class AzureSpeechException(
    val kind: AzureSpeechErrorKind,
    message: String,
) : IOException(message)
