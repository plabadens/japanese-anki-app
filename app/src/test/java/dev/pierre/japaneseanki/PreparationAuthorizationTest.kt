package dev.pierre.japaneseanki

import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
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
    fun rejectedPersistenceLeavesEditorAndPreparationAuthorizationUntouched() {
        val events = mutableListOf<String>()
        val authorization = PreparationAuthorization(
            initialCommittedSentence = "今日は晴れです。",
            persistSentenceCommit = { events += "persist:$it"; false },
            authorizeTranslation = { events += "translation:$it" },
            authorizeSentenceSpeech = { events += "speech:$it" },
            authorizeWordRecording = { _, _ -> error("No word authorization expected") },
        )

        assertFalse(authorization.commitSentence("今日は雨です。") {
            events += "editor-update"
        })

        assertEquals(listOf("persist:今日は雨です。"), events)
        assertFalse(authorization.commitSentence("今日は晴れです。"))
        assertEquals(listOf("persist:今日は雨です。"), events)
    }

    @Test
    fun sentenceEditInvalidationRunsBeforeSingleAuthorizationAndDoesNotCancelIt() {
        val events = mutableListOf<String>()
        var translationAutoAllowed = false
        var generationStarted = false
        val authorization = PreparationAuthorization(
            initialCommittedSentence = "今日は晴れです。",
            persistSentenceCommit = { events += "persist:$it"; true },
            authorizeTranslation = {
                events += "translation:$it"
                translationAutoAllowed = true
                generationStarted = true
            },
            authorizeSentenceSpeech = { events += "speech:$it" },
            authorizeWordRecording = { _, _ -> error("No word authorization expected") },
        )

        assertTrue(authorization.commitSentence("今日は雨です。") {
            events += "invalidate-editor-dependent-state"
            translationAutoAllowed = false
            generationStarted = false
        })

        assertEquals(
            listOf(
                "persist:今日は雨です。",
                "invalidate-editor-dependent-state",
                "translation:今日は雨です。",
                "speech:今日は雨です。",
            ),
            events,
        )
        assertTrue(translationAutoAllowed)
        assertTrue(generationStarted)
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

        // Recreated process receives the durable committed sentence but no pending authorizations.
        val restored = gate("今日は晴れです。")
        assertFalse(restored.commitSentence("今日は晴れです。"))
        assertTrue(restored.selectWord("晴れ", TargetSpan(3, 5)))

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
