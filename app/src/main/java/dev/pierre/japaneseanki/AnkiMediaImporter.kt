package dev.pierre.japaneseanki

import java.net.URI

/** Validation for the pinned AnkiDroid media-provider filename contract. */
internal object AnkiMediaImporter {
    fun sanitizeFilename(filename: String): String? {
        if (!SAFE_FILENAME.matches(filename) || filename.startsWith('.')) return null
        if (!filename.endsWith(".webp", ignoreCase = true)) return null
        return filename
    }

    fun sanitizeAudioFilename(filename: String): String? {
        if (!SAFE_FILENAME.matches(filename) || filename.startsWith('.')) return null
        if (AudioFormat.fromFilename(filename) == null) return null
        return filename
    }

    fun importedFilename(uriString: String): String? = filenameFromFileUri(uriString, ::sanitizeFilename)

    fun importedAudioFilename(uriString: String): String? = filenameFromFileUri(uriString, ::sanitizeAudioFilename)

    private fun filenameFromFileUri(uriString: String, sanitize: (String) -> String?): String? {
        val uri = runCatching { URI(uriString) }.getOrNull() ?: return null
        if (!uri.scheme.equals("file", ignoreCase = true)) return null
        // Imported media must be a local file URI, not a remote file authority or a decorated URI.
        if (uri.rawAuthority != null || uri.rawQuery != null || uri.rawFragment != null) return null
        val path = uri.path?.takeIf(String::isNotEmpty) ?: return null
        if (path.split('/').any { it == "." || it == ".." }) return null
        val filename = path.substringAfterLast('/', "")
        return sanitize(filename)
    }

    private val SAFE_FILENAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
}
