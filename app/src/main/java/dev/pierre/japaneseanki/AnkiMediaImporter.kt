package dev.pierre.japaneseanki

import java.net.URI

/** Validation for the pinned AnkiDroid media-provider filename contract. */
internal object AnkiMediaImporter {
    fun sanitizeFilename(filename: String): String? {
        if (!SAFE_FILENAME.matches(filename) || filename.startsWith('.')) return null
        if (!filename.endsWith(".avif", ignoreCase = true)) return null
        return filename
    }

    fun importedFilename(uriString: String): String? {
        val uri = runCatching { URI(uriString) }.getOrNull() ?: return null
        if (!uri.scheme.equals("file", ignoreCase = true)) return null
        val path = uri.path?.takeIf(String::isNotEmpty) ?: return null
        if (path.split('/').any { it == "." || it == ".." }) return null
        val filename = path.substringAfterLast('/', "")
        return sanitizeFilename(filename)
    }

    private val SAFE_FILENAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
}
