package dev.pierre.japaneseanki

import android.app.Application

/** Application-owned DataStores; screen state belongs to ViewModels. */
class JapaneseAnkiApplication : Application() {
    internal val preferenceStorage: AppPreferenceStorage by lazy { DataStorePreferenceStorage(this) }
    internal val audioCache by lazy { ReusableAudioCache(java.io.File(cacheDir, "audio")) }
}
