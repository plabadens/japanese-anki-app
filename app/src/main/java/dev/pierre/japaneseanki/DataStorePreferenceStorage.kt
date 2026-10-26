package dev.pierre.japaneseanki

import android.content.Context
import android.os.Looper
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** DataStore owns serialization, atomic writes and its cache. UI flows contain committed data only. */
internal class DataStorePreferenceStorage(
    context: Context,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AppPreferenceStorage, AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val stores: Map<PreferenceFile, DataStore<Preferences>> = PreferenceFile.entries.associateWith { file ->
        val name = file.name.lowercase()
        PreferenceDataStoreFactory.create(
            migrations = listOf(SharedPreferencesMigration(context.applicationContext, name)),
            scope = scope,
            produceFile = { context.applicationContext.preferencesDataStoreFile(name) },
        )
    }
    override val draft = values(PreferenceFile.DRAFT)
    override val settings = values(PreferenceFile.SETTINGS)

    private fun values(file: PreferenceFile) = stores.getValue(file).data
        .map { preferences -> PreferenceValues(preferences.asMap().mapKeys { it.key.name }) }
        // load()/readBlocking() propagate failures to the caller. Observation must not crash the process.
        .catch { }
        .stateIn(scope, SharingStarted.Eagerly, PreferenceValues())

    override suspend fun load() {
        // Reading the native flows also propagates read/corruption errors instead of inventing an empty draft.
        stores.values.forEach { it.data.first() }
        // These stateIn subscriptions were started before load; await their initial committed snapshots.
        draft.first { it.sameValues(snapshot(PreferenceFile.DRAFT)) }
        settings.first { it.sameValues(snapshot(PreferenceFile.SETTINGS)) }
    }

    private suspend fun snapshot(file: PreferenceFile) =
        PreferenceValues(stores.getValue(file).data.first().asMap().mapKeys { it.key.name })

    override fun readBlocking(file: PreferenceFile): PreferenceValues {
        check(Looper.myLooper() != Looper.getMainLooper()) { "Read persistent snapshots off the main thread." }
        return runBlocking { snapshot(file) }
    }

    override fun update(file: PreferenceFile, changes: PreferenceChanges) {
        // Enter updateData before returning; its own actor orders requests. No second queue or optimistic cache.
        scope.launch(start = CoroutineStart.UNDISPATCHED) { persist(file, changes) }
    }

    override suspend fun persist(file: PreferenceFile, changes: PreferenceChanges): Boolean = try {
        stores.getValue(file).edit { preferences ->
            if (changes.clearFirst) preferences.clear()
            changes.values.forEach { (name, value) ->
                if (value == null) {
                    preferences.asMap().keys.firstOrNull { it.name == name }?.let { preferences.remove(it) }
                } else when (value) {
                    is String -> preferences[stringPreferencesKey(name)] = value
                    is Int -> preferences[intPreferencesKey(name)] = value
                    is Long -> preferences[longPreferencesKey(name)] = value
                    is Boolean -> preferences[booleanPreferencesKey(name)] = value
                    is Set<*> -> preferences[stringSetPreferencesKey(name)] = value.filterIsInstance<String>().toSet()
                    else -> error("Unsupported preference type")
                }
            }
        }
        true
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (_: java.io.IOException) {
        false
    }

    override fun persistBlocking(file: PreferenceFile, changes: PreferenceChanges): Boolean {
        check(Looper.myLooper() != Looper.getMainLooper()) { "Preference commits must not run on the main thread." }
        return runBlocking { persist(file, changes) }
    }

    override fun close() { runBlocking { scope.coroutineContext.job.cancelAndJoin() } }
}
