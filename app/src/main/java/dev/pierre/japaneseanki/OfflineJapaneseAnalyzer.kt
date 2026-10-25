package dev.pierre.japaneseanki

import android.content.Context
import java.io.File

/**
 * MeCab over the bundled UniDic data. Extraction is a local, one-time unpack of an APK asset; the
 * analyzer never downloads anything.
 */
internal interface JapaneseAnalyzer {
    fun loadInstalled(): Boolean
    fun prepare()
    fun analyze(sentence: String): List<JapaneseToken>
}

internal class OfflineJapaneseAnalyzer(context: Context) : JapaneseAnalyzer {
    private val appContext = context.applicationContext
    @Volatile private var tagger: NativeMeCab? = null

    override fun loadInstalled(): Boolean {
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
    override fun prepare() {
        if (tagger != null) return
        synchronized(installationLock) {
            if (tagger != null) return
            BundledDictionary.install(appContext)
            tagger = NativeMeCab(BundledDictionary.directory(appContext.filesDir))
        }
    }

    override fun analyze(sentence: String): List<JapaneseToken> =
        checkNotNull(tagger) { "Offline Japanese analysis is not ready" }.analyze(sentence)

    companion object {
        private val installationLock = Any()
        @Volatile private var shared: OfflineJapaneseAnalyzer? = null

        fun shared(context: Context): OfflineJapaneseAnalyzer = shared ?: synchronized(installationLock) {
            shared ?: OfflineJapaneseAnalyzer(context).also { shared = it }
        }
    }
}

/** Production defaults to the real local services; tests can replace the startup data boundaries. */
internal object AppServices {
    @Volatile private var offlineAnalyzerOverride: ((Context) -> JapaneseAnalyzer)? = null
    @Volatile private var offlineDictionaryOverride: ((Context) -> OfflineDictionaryService)? = null
    @Volatile private var preferenceStorageOverride: AppPreferenceStorage? = null

    fun preferenceStorage(context: Context): AppPreferenceStorage =
        preferenceStorageOverride ?: (context.applicationContext as JapaneseAnkiApplication).preferenceStorage

    fun installPreferenceStorageForTest(storage: AppPreferenceStorage) {
        preferenceStorageOverride = storage
    }

    fun offlineAnalyzer(context: Context): JapaneseAnalyzer =
        offlineAnalyzerOverride?.invoke(context) ?: OfflineJapaneseAnalyzer.shared(context)

    fun offlineDictionary(
        context: Context,
        defaultProvider: (Context) -> OfflineDictionaryService,
    ): OfflineDictionaryService = offlineDictionaryOverride?.invoke(context) ?: defaultProvider(context)

    fun installOfflineAnalyzerForTest(factory: (Context) -> JapaneseAnalyzer) {
        offlineAnalyzerOverride = factory
    }

    fun installOfflineDictionaryForTest(factory: (Context) -> OfflineDictionaryService) {
        offlineDictionaryOverride = factory
    }

    fun clearTestOverrides() {
        offlineAnalyzerOverride = null
        offlineDictionaryOverride = null
        preferenceStorageOverride = null
    }
}
