package dev.pierre.japaneseanki

import android.content.Context
import java.io.File

/**
 * MeCab over the bundled UniDic data. Extraction is a local, one-time unpack of an APK asset; the
 * analyzer never downloads anything.
 */
internal class OfflineJapaneseAnalyzer(context: Context) {
    private val appContext = context.applicationContext
    @Volatile private var tagger: NativeMeCab? = null

    fun loadInstalled(): Boolean {
        if (tagger != null) return true
        synchronized(installationLock) {
            if (tagger != null) return true
            val directory = BundledDictionary.directory(appContext.filesDir)
            if (!BundledDictionary.isInstalled(directory)) return false
            tagger = NativeMeCab(directory)
            return true
        }
    }

    /** Unpacks the bundled dictionary when needed, then opens it. Blocking: call off the main thread. */
    fun prepare() {
        if (tagger != null) return
        synchronized(installationLock) {
            if (tagger != null) return
            BundledDictionary.install(appContext)
            tagger = NativeMeCab(BundledDictionary.directory(appContext.filesDir))
        }
    }

    fun analyze(sentence: String): List<JapaneseToken> =
        checkNotNull(tagger) { "Offline Japanese analysis is not ready" }.analyze(sentence)

    companion object {
        private val installationLock = Any()
        @Volatile private var shared: OfflineJapaneseAnalyzer? = null

        fun shared(context: Context): OfflineJapaneseAnalyzer = shared ?: synchronized(installationLock) {
            shared ?: OfflineJapaneseAnalyzer(context).also { shared = it }
        }
    }
}
