package dev.pierre.japaneseanki

import dev.pierre.japaneseanki.speech.CloudVoice

/** Non-secret settings persisted in the app's protected settings preferences. */
internal data class SettingsValues(
    val modelId: Long? = null,
    val deckId: Long? = null,
    val defaultTags: String = "",
    val voiceName: String = "",
)

/** The catalog records whether entries are saved, stale for a changed key, or verified for the current key. */
internal enum class VoiceCatalogProvenance { NONE, CACHED, STALE, AUTHORITATIVE }

internal data class VoiceCatalogState(
    val voices: List<CloudVoice> = emptyList(),
    val provenance: VoiceCatalogProvenance = VoiceCatalogProvenance.NONE,
    val loading: Boolean = false,
    val attempted: Boolean = false,
    val error: String? = null,
) {
    val hasCachedList: Boolean get() =
        (provenance == VoiceCatalogProvenance.CACHED || provenance == VoiceCatalogProvenance.STALE) && voices.isNotEmpty()

    fun selectedVoiceForSynthesis(voiceName: String): CloudVoice? =
        if (voiceName.isNotBlank() &&
            (provenance == VoiceCatalogProvenance.CACHED || provenance == VoiceCatalogProvenance.AUTHORITATIVE)
        ) {
            voices.firstOrNull { it.name == voiceName }
        } else {
            null
        }

    fun selectedVoiceMissing(voiceName: String): Boolean =
        provenance == VoiceCatalogProvenance.AUTHORITATIVE &&
            voiceName.isNotBlank() && voices.none { it.name == voiceName }
}

internal fun sentenceVoiceForSynthesis(
    credentialStored: Boolean,
    credentialUnreadable: Boolean,
    voiceName: String,
    catalog: VoiceCatalogState,
): CloudVoice? =
    if (credentialStored && !credentialUnreadable) catalog.selectedVoiceForSynthesis(voiceName) else null

/**
 * A fresh note inherits only the configured defaults. A non-null draft value (including an
 * intentionally empty one) always wins, so reopening a draft cannot replace its per-note edits.
 */
internal fun seedFreshNoteTags(draftTags: String?, defaultTags: String): String =
    draftTags ?: normalizeTagInput(defaultTags)

internal fun normalizeTagInput(tags: String): String =
    tags.trim().split(Regex("\\s+")).filter(String::isNotBlank).joinToString(" ")
