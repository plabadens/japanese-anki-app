package dev.pierre.japaneseanki

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * Progress for the one-off NHK index download (37 MB for the pinned revision). Published from the
 * worker thread; `null` means nothing is in flight.
 */
data class NhkIndexProgress(val receivedBytes: Long, val totalBytes: Long?) {
    val determinate: Boolean get() = totalBytes != null && totalBytes > 0

    val fraction: Float?
        get() = totalBytes?.takeIf { it > 0 }?.let { total ->
            (receivedBytes.coerceAtMost(total).toFloat() / total).coerceIn(0f, 1f)
        }

    fun describe(): String = if (determinate) {
        String.format(
            java.util.Locale.US,
            "Downloading the NHK pronunciation index: %.1f of %.1f MB",
            receivedBytes / MB,
            totalBytes!! / MB,
        )
    } else {
        String.format(
            java.util.Locale.US,
            "Downloading the NHK pronunciation index: %.1f MB received",
            receivedBytes / MB,
        )
    }

    private companion object {
        const val MB = 1024.0 * 1024.0
    }
}

/**
 * On-demand adapter for AJATT NHK-2016 index at commit b0207d240f0f28cdc2dd491dd83343c3a5630119.
 * The index repository is GPL-3.0; that index license does not establish the provenance or rights
 * of the linked NHK sound recordings. Recording use remains subject to verification by the integrator.
 */
class Nhk2016PronunciationSource(
    private val dataDirectory: File,
    private val spellingFor: (AudioSynthesisRequest) -> String = { it.text },
    private val onIndexProgress: (NhkIndexProgress?) -> Unit = {},
    private val isIndexFetchCancelled: () -> Boolean = { false },
    private val readIndexText: (File) -> String = { it.readText(Charsets.UTF_8) },
    private val connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) : AudioProvider {
    /** Serialises the one-off 37 MB index download; a second word waits instead of fetching again. */
    private val indexLock = Any()

    override fun synthesize(request: AudioSynthesisRequest, output: File): File {
        require(request.item == AudioItem.VOCABULARY && !request.entryIdentity.isNullOrBlank()) {
            "NHK recordings require a resolved vocabulary entry"
        }
        val spelling = spellingFor(request).trim()
        if (spelling.isEmpty()) throw AudioSourceMissingException("NHK recording spelling is unavailable")
        val recording = recordingFor(spelling)
            ?: throw AudioSourceMissingException("No NHK recording matches this spelling")
        val parent = output.absoluteFile.parentFile ?: throw IOException("Invalid audio output path")
        if (!parent.exists() && !parent.mkdirs()) throw IOException("Could not create audio output directory")
        val temporary = File(parent, ".${UUID.randomUUID()}.ogg")
        val connection = connectionFactory(URL("$RAW_ROOT/media/$recording"))
        try {
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.instanceFollowRedirects = false
            val status = connection.responseCode
            if (status == HttpURLConnection.HTTP_NOT_FOUND || status == HttpURLConnection.HTTP_GONE) {
                throw AudioSourceMissingException("No NHK recording matches this spelling")
            }
            if (status != HttpURLConnection.HTTP_OK) throw IOException("NHK recording service is unavailable")
            connection.inputStream.use { input ->
                temporary.outputStream().use { stream ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val size = input.read(buffer)
                        if (size < 0) break
                        total += size
                        if (total > MAX_RECORDING_BYTES) throw IOException("NHK recording is too large")
                        stream.write(buffer, 0, size)
                    }
                }
            }
            AudioFileValidator.requireSupported(temporary)
            if (!output.name.endsWith(".ogg", ignoreCase = true)) throw IOException("NHK recording output must use Ogg extension")
            try {
                Files.move(temporary.toPath(), output.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (error: java.nio.file.AtomicMoveNotSupportedException) {
                throw IOException("Could not commit NHK recording", error)
            }
            return output
        } finally {
            temporary.delete()
            connection.disconnect()
        }
    }

    private fun recordingFor(spelling: String): String? = synchronized(indexLock) {
        if (!dataDirectory.exists() && !dataDirectory.mkdirs()) throw IOException("NHK index storage is unavailable")
        val index = File(dataDirectory, "nhk-2016-b0207d240f0f.json")
        if (!index.isFile || index.length() !in 1..MAX_INDEX_BYTES) {
            // A fresh download answers this lookup inside its own validation pass; the result is
            // released only after the index has been published atomically, so there is no second
            // read of the file we just wrote.
            downloadIndex(index, spelling)
        } else {
            try {
                IndexHeadwords(readIndexText(index)).recording(spelling)
            } catch (_: IllegalArgumentException) {
                index.delete()
                throw IOException("NHK pronunciation index is invalid")
            }
        }
    }

    private fun downloadIndex(destination: File, spelling: String): String? {
        val temporary = File(dataDirectory, ".${UUID.randomUUID()}.json")
        val connection = connectionFactory(URL("$RAW_ROOT/index.json"))
        var published = -PROGRESS_STEP_BYTES
        var total = 0L
        try {
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.instanceFollowRedirects = false
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("NHK pronunciation index is unavailable")
            val expected = connection.contentLengthLong.takeIf { it > 0 }
            onIndexProgress(NhkIndexProgress(0, expected))
            connection.inputStream.use { input -> temporary.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val size = input.read(buffer)
                    if (size < 0) break
                    total += size
                    if (total > MAX_INDEX_BYTES) throw IOException("NHK pronunciation index is too large")
                    if (isIndexFetchCancelled()) throw IOException("NHK pronunciation index download cancelled")
                    output.write(buffer, 0, size)
                    if (total - published >= PROGRESS_STEP_BYTES) {
                        published = total
                        onIndexProgress(NhkIndexProgress(total, expected))
                    }
                }
            } }
            onIndexProgress(NhkIndexProgress(total, expected ?: total))
            // One pass validates every headword and filename while capturing the requested
            // recording; the published index is then used without a second read or scan.
            val recording = IndexHeadwords(readIndexText(temporary)).recording(spelling)
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            return recording
        } catch (error: IOException) {
            throw error
        } catch (error: Exception) {
            throw IOException("NHK pronunciation index could not be saved", error)
        } finally {
            temporary.delete()
            connection.disconnect()
            onIndexProgress(null)
        }
    }

    companion object {
        const val SOURCE_VERSION = "b0207d240f0f28cdc2dd491dd83343c3a5630119"
        const val SOURCE_LICENSE = "GPL-3.0 (index repository only; recording rights are not verified)"
        private const val RAW_ROOT = "https://raw.githubusercontent.com/Ajatt-Tools/nhk_2016_pronunciations_index/$SOURCE_VERSION"
        private const val TIMEOUT_MILLIS = 20_000
        private const val MAX_INDEX_BYTES = 64L * 1024 * 1024
        private const val MAX_RECORDING_BYTES = 8L * 1024 * 1024

        /** Publish at most one progress event per this many bytes (about 64 updates for 37 MB). */
        private const val PROGRESS_STEP_BYTES = 256L * 1024
    }
}

/**
 * Narrow JSON reader for the source's top-level `headwords: { spelling: [filename, ...] }` map.
 *
 * Every lookup walks and validates the whole document - a malformed headword after the requested
 * one still fails the lookup - but it retains only the requested spelling's first recording. Key
 * names and recording filenames are compared/validated in place while decoding, so a scan does
 * not allocate one string per headword.
 */
internal class IndexHeadwords(private val json: String) {
    /** First recording for [spelling], or `null` when the document has no such headword. */
    fun recording(spelling: String): String? = JsonCursor(json).readIndex(spelling)

    /** Validates the whole document without retaining anything. */
    fun validate() { JsonCursor(json).readIndex(null) }
}

private class JsonCursor(private val input: String) {
    private var position = 0

    fun readIndex(targetSpelling: String?): String? {
        expect('{')
        var recording: String? = null
        var foundHeadwords = false
        while (!consume('}')) {
            val name = string()
            expect(':')
            if (name == "headwords") {
                foundHeadwords = true
                recording = readHeadwords(targetSpelling)
            } else skipValue()
            if (!consume(',')) { expect('}'); break }
        }
        whitespace()
        if (position != input.length || !foundHeadwords) invalid()
        return recording
    }

    /**
     * The last duplicate headword key wins, matching the previous reader; an empty recording list
     * leaves any earlier match in place. Only the first filename of the winning key is retained.
     */
    private fun readHeadwords(targetSpelling: String?): String? {
        var matchingRecording: String? = null
        expect('{')
        while (!consume('}')) {
            val isTarget = if (targetSpelling == null) {
                skipString()
                false
            } else {
                stringEquals(targetSpelling)
            }
            expect(':'); expect('[')
            var firstRecording: String? = null
            while (!consume(']')) {
                val recording = recordingName(retain = isTarget)
                if (recording != null && firstRecording == null) firstRecording = recording
                if (!consume(',')) { expect(']'); break }
            }
            if (isTarget && firstRecording != null) matchingRecording = firstRecording
            if (!consume(',')) { expect('}'); break }
        }
        return matchingRecording
    }

    /** Validates `[0-9]{14}\.ogg` on the decoded characters, allocating only when [retain]. */
    private fun recordingName(retain: Boolean): String? {
        expect('"')
        val builder = if (retain) StringBuilder(RECORDING_NAME_LENGTH) else null
        var index = 0
        var valid = true
        while (true) {
            val code = nextStringChar()
            if (code == END_OF_STRING) {
                if (!valid || index != RECORDING_NAME_LENGTH) invalid()
                return builder?.toString()
            }
            if (valid) {
                val accepted = when (index) {
                    in 0 until 14 -> code in '0'.code..'9'.code
                    14 -> code == '.'.code
                    15 -> code == 'o'.code
                    else -> code == 'g'.code
                }
                if (!accepted) valid = false
            }
            builder?.append(code.toChar())
            index++
        }
    }

    private fun skipValue() {
        whitespace()
        when (peek()) {
            '"' -> skipString()
            '{' -> { expect('{'); while (!consume('}')) { skipString(); expect(':'); skipValue(); if (!consume(',')) { expect('}'); break } } }
            '[' -> { expect('['); while (!consume(']')) { skipValue(); if (!consume(',')) { expect(']'); break } } }
            else -> while (position < input.length && input[position] !in ",]} \t\r\n") position++
        }
    }

    private fun string(): String {
        expect('"')
        val result = StringBuilder()
        while (true) {
            val code = nextStringChar()
            if (code == END_OF_STRING) return result.toString()
            result.append(code.toChar())
        }
    }

    private fun skipString() {
        expect('"')
        while (nextStringChar() != END_OF_STRING) {
            // Validation only: nothing is retained.
        }
    }

    private fun stringEquals(target: String): Boolean {
        expect('"')
        var index = 0
        var equal = true
        while (true) {
            val code = nextStringChar()
            if (code == END_OF_STRING) return equal && index == target.length
            if (equal) {
                if (index < target.length && target[index].code == code) index++ else equal = false
            }
        }
    }

    /** Decodes one character of the current string literal, or [END_OF_STRING] at its closing quote. */
    private fun nextStringChar(): Int {
        if (position >= input.length) invalid()
        return when (val char = input[position++]) {
            '"' -> END_OF_STRING
            '\\' -> {
                if (position >= input.length) invalid()
                when (val escape = input[position++]) {
                    '"', '\\', '/' -> escape.code
                    'b' -> '\b'.code
                    'f' -> '\u000c'.code
                    'n' -> '\n'.code
                    'r' -> '\r'.code
                    't' -> '\t'.code
                    'u' -> {
                        if (position + 4 > input.length) invalid()
                        val code = input.substring(position, position + 4).toIntOrNull(16) ?: invalid()
                        position += 4
                        code
                    }
                    else -> invalid()
                }
            }
            else -> {
                if (char.code < 0x20) invalid()
                char.code
            }
        }
    }

    private fun expect(char: Char) { whitespace(); if (position >= input.length || input[position++] != char) invalid() }
    private fun consume(char: Char): Boolean { whitespace(); if (position < input.length && input[position] == char) { position++; return true }; return false }
    private fun peek(): Char { whitespace(); if (position >= input.length) invalid(); return input[position] }
    private fun whitespace() { while (position < input.length && input[position] in " \t\r\n") position++ }
    private fun invalid(): Nothing = throw IllegalArgumentException("Invalid pronunciation index")

    private companion object {
        const val END_OF_STRING = -1
        const val RECORDING_NAME_LENGTH = 18
    }
}
