package dev.pierre.japaneseanki

import dev.pierre.japaneseanki.speech.CloudVoice

/** Non-secret settings persisted in the app's protected settings preferences. */
internal data class SettingsValues(
    val modelId: Long? = null,
    val deckId: Long? = null,
    val defaultTags: String = "",
    val voiceName: String = "",
)

/** The catalog says whether its entries are merely cached or verified for the current key. */
internal enum class VoiceCatalogProvenance { NONE, CACHED, AUTHORITATIVE }

internal data class VoiceCatalogState(
    val voices: List<CloudVoice> = emptyList(),
    val provenance: VoiceCatalogProvenance = VoiceCatalogProvenance.NONE,
    val loading: Boolean = false,
    val attempted: Boolean = false,
    val error: String? = null,
) {
    val hasCachedList: Boolean get() = provenance == VoiceCatalogProvenance.CACHED && voices.isNotEmpty()

    fun selectedVoiceMissing(voiceName: String): Boolean =
        provenance == VoiceCatalogProvenance.AUTHORITATIVE &&
            voiceName.isNotBlank() && voices.none { it.name == voiceName }
}

/**
 * A fresh note inherits only the configured defaults. A non-null draft value (including an
 * intentionally empty one) always wins, so reopening a draft cannot replace its per-note edits.
 */
internal fun seedFreshNoteTags(draftTags: String?, defaultTags: String): String =
    draftTags ?: normalizeTagInput(defaultTags)

internal fun normalizeTagInput(tags: String): String =
    tags.trim().split(Regex("\\s+")).filter(String::isNotBlank).joinToString(" ")
