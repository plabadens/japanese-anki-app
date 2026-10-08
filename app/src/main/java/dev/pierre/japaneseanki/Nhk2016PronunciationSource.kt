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
    private val dataDirectory: File,
    private val spellingFor: (AudioSynthesisRequest) -> String = { it.text },
    private val connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) : AudioProvider {
    override fun synthesize(request: AudioSynthesisRequest, output: File): File {
        require(request.item == AudioItem.VOCABULARY && !request.entryIdentity.isNullOrBlank()) {
            "NHK recordings require a resolved vocabulary entry"
        }
        val spelling = spellingFor(request).trim()
        if (spelling.isEmpty()) throw IOException("NHK recording spelling is unavailable")
        val recording = recordingFor(spelling) ?: throw IOException("No NHK recording matches this spelling")
        val parent = output.absoluteFile.parentFile ?: throw IOException("Invalid audio output path")
        if (!parent.exists() && !parent.mkdirs()) throw IOException("Could not create audio output directory")
        val temporary = File(parent, ".${UUID.randomUUID()}.ogg")
        val connection = connectionFactory(URL("$RAW_ROOT/media/$recording"))
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

    private fun recordingFor(spelling: String): String? {
        if (!dataDirectory.exists() && !dataDirectory.mkdirs()) throw IOException("NHK index storage is unavailable")
        val index = File(dataDirectory, "nhk-2016-b0207d240f0f.json")
        if (!index.isFile || index.length() !in 1..MAX_INDEX_BYTES) downloadIndex(index)
        return try {
            IndexHeadwords(index.readText(Charsets.UTF_8)).recordings(spelling)?.firstOrNull()
        } catch (_: IllegalArgumentException) {
            index.delete()
            throw IOException("NHK pronunciation index is invalid")
        }
    }

    private fun downloadIndex(destination: File) {
        val temporary = File(dataDirectory, ".${UUID.randomUUID()}.json")
        val connection = connectionFactory(URL("$RAW_ROOT/index.json"))
        try {
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.instanceFollowRedirects = false
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("NHK pronunciation index is unavailable")
            connection.inputStream.use { input -> temporary.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val size = input.read(buffer)
                    if (size < 0) break
                    total += size
                    if (total > MAX_INDEX_BYTES) throw IOException("NHK pronunciation index is too large")
                    output.write(buffer, 0, size)
                }
            } }
            IndexHeadwords(temporary.readText(Charsets.UTF_8)).validate()
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (error: IOException) {
            throw error
        } catch (error: Exception) {
            throw IOException("NHK pronunciation index could not be saved", error)
        } finally {
            temporary.delete()
            connection.disconnect()
        }
    }

    companion object {
        const val SOURCE_VERSION = "b0207d240f0f28cdc2dd491dd83343c3a5630119"
        const val SOURCE_LICENSE = "GPL-3.0 (index repository only; recording rights are not verified)"
        private const val RAW_ROOT = "https://raw.githubusercontent.com/Ajatt-Tools/nhk_2016_pronunciations_index/$SOURCE_VERSION"
        private const val TIMEOUT_MILLIS = 20_000
        private const val MAX_INDEX_BYTES = 64L * 1024 * 1024
        private const val MAX_RECORDING_BYTES = 8L * 1024 * 1024
    }
}

/** Narrow JSON reader for the source's top-level `headwords: { spelling: [filename, ...] }` map. */
internal class IndexHeadwords(private val json: String) {
    fun recordings(spelling: String): List<String>? = JsonCursor(json).readIndex(spelling)
    fun validate() { JsonCursor(json).readIndex(null) }
}

private class JsonCursor(private val input: String) {
    private var position = 0

    fun readIndex(targetSpelling: String?): List<String>? {
        expect('{')
        var headwords: List<String>? = null
        var foundHeadwords = false
        while (!consume('}')) {
            val name = string()
            expect(':')
            if (name == "headwords") {
                foundHeadwords = true
                headwords = readHeadwords(targetSpelling)
            } else skipValue()
            if (!consume(',')) { expect('}'); break }
        }
        whitespace()
        if (position != input.length || !foundHeadwords) invalid()
        return headwords
    }

    private fun readHeadwords(targetSpelling: String?): List<String>? {
        var matchingRecordings: List<String>? = null
        expect('{')
        while (!consume('}')) {
            val spelling = string()
            expect(':'); expect('[')
            val recordings = ArrayList<String>()
            while (!consume(']')) {
                val recording = string()
                if (!recording.matches(Regex("[0-9]{14}\\.ogg"))) invalid()
                recordings += recording
                if (!consume(',')) { expect(']'); break }
            }
            if (spelling == targetSpelling && recordings.isNotEmpty()) matchingRecordings = recordings
            if (!consume(',')) { expect('}'); break }
        }
        return matchingRecordings
    }

    private fun skipValue() {
        whitespace()
        when (peek()) {
            '"' -> string()
            '{' -> { expect('{'); while (!consume('}')) { string(); expect(':'); skipValue(); if (!consume(',')) { expect('}'); break } } }
            '[' -> { expect('['); while (!consume(']')) { skipValue(); if (!consume(',')) { expect(']'); break } } }
            else -> while (position < input.length && input[position] !in ",]} \t\r\n") position++
        }
    }

    private fun string(): String {
        expect('"')
        val result = StringBuilder()
        while (position < input.length) {
            val char = input[position++]
            when (char) {
                '"' -> return result.toString()
                '\\' -> {
                    if (position >= input.length) invalid()
                    when (val escape = input[position++]) {
                        '"', '\\', '/' -> result.append(escape)
                        'b' -> result.append('\b')
                        'f' -> result.append('\u000c')
                        'n' -> result.append('\n')
                        'r' -> result.append('\r')
                        't' -> result.append('\t')
                        'u' -> { if (position + 4 > input.length) invalid(); result.append(input.substring(position, position + 4).toIntOrNull(16)?.toChar() ?: invalid()); position += 4 }
                        else -> invalid()
                    }
                }
                else -> { if (char.code < 0x20) invalid(); result.append(char) }
            }
        }
        invalid()
    }

    private fun expect(char: Char) { whitespace(); if (position >= input.length || input[position++] != char) invalid() }
    private fun consume(char: Char): Boolean { whitespace(); if (position < input.length && input[position] == char) { position++; return true }; return false }
    private fun peek(): Char { whitespace(); if (position >= input.length) invalid(); return input[position] }
    private fun whitespace() { while (position < input.length && input[position] in " \t\r\n") position++ }
    private fun invalid(): Nothing = throw IllegalArgumentException("Invalid pronunciation index")
}
