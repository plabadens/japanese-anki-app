package dev.pierre.japaneseanki

import org.json.JSONArray
import java.io.File

internal class NativeMeCab(dictionaryDirectory: File) : AutoCloseable {
    private var handle = nativeOpen(dictionaryDirectory.absolutePath)

    init {
        check(handle != 0L) { "MeCab could not open the bundled UniDic data" }
    }

    @Synchronized
    fun analyze(sentence: String): List<JapaneseToken> {
        check(handle != 0L) { "MeCab is closed" }
        val rows = JSONArray(nativeAnalyze(handle, sentence))
        var searchFrom = 0
        return List(rows.length()) { index ->
            val row = rows.getJSONArray(index)
            val surface = row.getString(0)
            val start = sentence.indexOf(surface, searchFrom)
            check(start >= 0) { "MeCab returned a token outside the input sentence" }
            val end = start + surface.length
            searchFrom = end
            val lemma = row.getString(1)
            val lemmaReading = katakanaToHiragana(row.getString(3))
            val surfaceReading = if (surface == lemma && lemmaReading.isNotBlank()) {
                lemmaReading
            } else {
                katakanaToHiragana(row.getString(2))
            }
            JapaneseToken(
                span = TargetSpan(start, end),
                surface = surface,
                lemma = lemma,
                surfaceReading = surfaceReading,
                lemmaReading = lemmaReading,
                partOfSpeech = row.getString(4),
            )
        }
    }

    @Synchronized
    override fun close() {
        if (handle != 0L) {
            nativeClose(handle)
            handle = 0L
        }
    }

    private external fun nativeOpen(dictionaryPath: String): Long
    private external fun nativeAnalyze(handle: Long, sentence: String): String
    private external fun nativeClose(handle: Long)

    companion object {
        init {
            System.loadLibrary("japaneseanki_mecab")
        }
    }
}
