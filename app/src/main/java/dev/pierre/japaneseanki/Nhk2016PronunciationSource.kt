package dev.pierre.japaneseanki

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * On-demand adapter for AJATT NHK-2016 index at commit b0207d240f0f28cdc2dd491dd83343c3a5630119.
 * The index repository is GPL-3.0; that index license does not establish the provenance or rights
 * of the linked NHK sound recordings. Recording use remains subject to verification by the integrator.
 */
class Nhk2016PronunciationSource(
    private val readIndexText: () -> String,
    private val spellingFor: (AudioSynthesisRequest) -> String = { it.text },
    private val connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) : AudioProvider {
    /** Keep one large bundled-index scan in flight at a time. */
    private val indexLock = Any()

    override fun synthesize(request: AudioSynthesisRequest, output: File): File {
        require(request.item == AudioItem.VOCABULARY && !request.entryIdentity.isNullOrBlank()) {
            "NHK recordings require a resolved vocabulary entry"
        }
        val spelling = spellingFor(request).trim()
        if (spelling.isEmpty()) throw IOException("NHK recording spelling is unavailable")
        val recording = findRecording(spelling) ?: throw IOException("No NHK recording matches this spelling")
        val parent = output.absoluteFile.parentFile ?: throw IOException("Invalid audio output path")
        if (!parent.exists() && !parent.mkdirs()) throw IOException("Could not create audio output directory")
        val temporary = File(parent, ".${UUID.randomUUID()}.ogg")
        val connection = connectionFactory(URL("$MEDIA_ROOT/$recording"))
        try {
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.instanceFollowRedirects = false
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("NHK recording is unavailable")
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

    /** Resolves an index record without network or storage access; only the recording is fetched. */
    internal fun findRecording(spelling: String): String? = synchronized(indexLock) {
        try {
            IndexHeadwords(readIndexText()).recording(spelling)
        } catch (_: IllegalArgumentException) {
            throw IOException("Bundled NHK pronunciation index is invalid")
        }
    }

    companion object {
        const val SOURCE_VERSION = "b0207d240f0f28cdc2dd491dd83343c3a5630119"
        const val SOURCE_LICENSE = "GPL-3.0 (index repository only; recording rights are not verified)"
        const val ASSET_NAME = "nhk-2016-b0207d240f0f.json"
        private const val MEDIA_ROOT = "https://raw.githubusercontent.com/Ajatt-Tools/nhk_2016_pronunciations_index/$SOURCE_VERSION/media"
        private const val TIMEOUT_MILLIS = 20_000
        private const val MAX_RECORDING_BYTES = 8L * 1024 * 1024
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
