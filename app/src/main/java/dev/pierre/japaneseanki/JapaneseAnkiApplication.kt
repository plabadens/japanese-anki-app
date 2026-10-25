package dev.pierre.japaneseanki

import android.app.Application

/** One preference snapshot and ordered writer queue for the lifetime of this process. */
class JapaneseAnkiApplication : Application() {
    internal val preferenceStorage: AppPreferenceStorage by lazy { AndroidPreferenceStorage(this) }
}
