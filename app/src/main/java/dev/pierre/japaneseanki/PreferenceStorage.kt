package dev.pierre.japaneseanki

import kotlinx.coroutines.flow.StateFlow
import java.io.File

internal enum class PreferenceFile { DRAFT, SETTINGS }

/** Immutable committed snapshot. No optimistic second source of truth over DataStore. */
internal class PreferenceValues internal constructor(private val values: Map<String, Any?> = emptyMap()) {
    fun contains(key: String): Boolean = values.containsKey(key)
    fun getString(key: String, default: String?): String? = values[key] as? String ?: default
    fun getInt(key: String, default: Int): Int = values[key] as? Int ?: default
    fun getLong(key: String, default: Long): Long = values[key] as? Long ?: default
    fun getBoolean(key: String, default: Boolean): Boolean = values[key] as? Boolean ?: default
    fun getStringSet(key: String, default: Set<String>?): Set<String>? =
        (values[key] as? Set<*>)?.filterIsInstance<String>()?.toSet() ?: default
    internal fun sameValues(other: PreferenceValues): Boolean = values == other.values
    internal fun changed(changes: PreferenceChanges): PreferenceValues {
        val updated = if (changes.clearFirst) mutableMapOf() else values.toMutableMap()
        changes.values.forEach { (key, value) -> if (value == null) updated.remove(key) else updated[key] = value }
        return PreferenceValues(updated)
    }
}

internal class PreferenceChanges {
    internal val values = linkedMapOf<String, Any?>()
    internal var clearFirst = false
        private set
    fun clear(): PreferenceChanges = apply { clearFirst = true; values.clear() }
    fun putString(key: String, value: String?): PreferenceChanges = set(key, value)
    fun putInt(key: String, value: Int): PreferenceChanges = set(key, value)
    fun putLong(key: String, value: Long): PreferenceChanges = set(key, value)
    fun putBoolean(key: String, value: Boolean): PreferenceChanges = set(key, value)
    fun putStringSet(key: String, value: Set<String>?): PreferenceChanges = set(key, value?.toSet())
    fun remove(key: String): PreferenceChanges = set(key, null)
    private fun set(key: String, value: Any?): PreferenceChanges = apply { values[key] = value }
}

internal interface AppPreferenceStorage {
    val draft: StateFlow<PreferenceValues>
    val settings: StateFlow<PreferenceValues>
    suspend fun load()
    fun update(file: PreferenceFile, changes: PreferenceChanges)
    suspend fun persist(file: PreferenceFile, changes: PreferenceChanges): Boolean
    fun persistBlocking(file: PreferenceFile, changes: PreferenceChanges): Boolean
    /** Blocking adapters are confined to existing audio/provider boundaries, always off main. */
    fun readBlocking(file: PreferenceFile): PreferenceValues = when (file) {
        PreferenceFile.DRAFT -> draft.value
        PreferenceFile.SETTINGS -> settings.value
    }
}

/** Publication of an audio file must actually be durable before its predecessor is retired. */
internal class PreferenceDraftAudioPersistence(private val storage: AppPreferenceStorage) : DraftAudioPersistence {
    override fun read(key: String): String? = storage.readBlocking(PreferenceFile.DRAFT).getString(key, null)
    override fun write(changes: Map<String, String?>): Boolean = storage.persistBlocking(
        PreferenceFile.DRAFT,
        PreferenceChanges().also { editor -> changes.forEach { (key, value) -> editor.putString(key, value) } },
    )
}

internal fun clearPersistedDraftBlocking(storage: AppPreferenceStorage, draftDirectory: File?) {
    val snapshot = storage.readBlocking(PreferenceFile.DRAFT)
    val directory = draftDirectory?.canonicalFile
    val files = listOf("photo_source_path", "note_image_path", "processed_image_path", "pending_replacement_image_path")
        .mapNotNull { snapshot.getString(it, null) }.distinct().map(::File)
        .filter { directory != null && it.canonicalFile.parentFile == directory }
    check(storage.persistBlocking(PreferenceFile.DRAFT, draftClearChanges())) { "Could not clear the saved draft." }
    // Never delete a file still referenced by a failed persistence transaction.
    files.forEach { it.delete() }
}

/** The draft has its own store. Clear it atomically instead of maintaining a list of retired keys. */
internal fun draftClearChanges(): PreferenceChanges = PreferenceChanges().clear()
