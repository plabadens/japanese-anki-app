package dev.pierre.japaneseanki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreparationAuthorizationTest {
    @Test
    fun captureDoesNothingUntilSentenceCommitAndWordSelection() {
        val calls = mutableListOf<String>()
        var wordFetchAllowed = true
        val authorization = PreparationAuthorization(
            initialCommittedSentence = null,
            persistSentenceCommit = { true },
            authorizeTranslation = { calls += "translation:$it" },
            authorizeSentenceSpeech = { calls += "sentence-speech:$it" },
            authorizeWordRecording = { identity, target ->
                if (wordFetchAllowed) calls += "word-recording:$identity:${target.start}:${target.end}"
                wordFetchAllowed
            },
        )

        // Shutter, OCR completion with multiple choices, rotation and navigation have no API here.
        assertTrue(calls.isEmpty())
        assertTrue(authorization.commitSentence("今日は晴れです。"))
        assertFalse(authorization.commitSentence("今日は晴れです。"))
        assertEquals(
            listOf("translation:今日は晴れです。", "sentence-speech:今日は晴れです。"),
            calls,
        )

        val target = TargetSpan(0, 2)
        wordFetchAllowed = false
        assertFalse(authorization.selectWord("今日", target))
        wordFetchAllowed = true
        assertTrue(authorization.selectWord("今日", target))
        assertFalse(authorization.selectWord("今日", target))
        assertEquals(
            listOf(
                "translation:今日は晴れです。",
                "sentence-speech:今日は晴れです。",
                "word-recording:今日:0:2",
            ),
            calls,
        )
    }

    @Test
    fun editingAndCommittingAuthorizesReplacementAndRestorationDoesNot() {
        val calls = mutableListOf<String>()
        fun gate(initialSentence: String?) = PreparationAuthorization(
            initialCommittedSentence = initialSentence,
            persistSentenceCommit = { true },
            authorizeTranslation = { calls += "translation:$it" },
            authorizeSentenceSpeech = { calls += "sentence-speech:$it" },
            authorizeWordRecording = { identity, _ ->
                calls += "word-recording:$identity"
                true
            },
        )

        val firstProcess = gate(null)
        assertTrue(firstProcess.commitSentence("今日は晴れです。"))
        assertTrue(firstProcess.selectWord("晴れ", TargetSpan(3, 5)))
        assertEquals(3, calls.size)

        // Recreated process receives the durable committed sentence but no pending authorizations.
        val restored = gate("今日は晴れです。")
        assertFalse(restored.commitSentence("今日は晴れです。"))
        assertEquals(3, calls.size)
        assertTrue(restored.selectWord("晴れ", TargetSpan(3, 5)))
        assertEquals(4, calls.size)

        assertTrue(restored.commitSentence("今日は雨です。"))
        assertEquals(
            listOf(
                "translation:今日は晴れです。",
                "sentence-speech:今日は晴れです。",
                "word-recording:晴れ",
                "word-recording:晴れ",
                "translation:今日は雨です。",
                "sentence-speech:今日は雨です。",
            ),
            calls,
        )
    }
}
