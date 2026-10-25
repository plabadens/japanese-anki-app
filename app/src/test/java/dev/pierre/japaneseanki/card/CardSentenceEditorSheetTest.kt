package dev.pierre.japaneseanki.card

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pierre.japaneseanki.TargetSpan
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardSentenceEditorSheetTest {
    @Test
    fun rejectedAndBlankEditsLeaveCommittedEditorAndSelectionUntouched() {
        val committedEditor = TextFieldValue("今日は晴れです。", TextRange(3, 5))
        var editor = committedEditor
        var target: TargetSpan? = TargetSpan(3, 5)
        var commitCalls = 0
        var closeCalls = 0

        commitCardSentenceEdit(
            edited = TextFieldValue("  "),
            updateEditor = { editor = it },
            commitSentence = { _, _ -> commitCalls++; false },
            onCommitted = { closeCalls++ },
        )
        commitCardSentenceEdit(
            edited = TextFieldValue("別の文です。"),
            updateEditor = { editor = it },
            commitSentence = { _, _ -> commitCalls++; false },
            onCommitted = { closeCalls++ },
        )
        useCardSentenceSelectionAsTarget(
            edited = TextFieldValue("   ", TextRange(0, 2)),
            updateEditor = { editor = it },
            commitSentence = { _, _ -> commitCalls++; false },
            selectTarget = { target = it },
            onCommitted = { closeCalls++ },
        )
        useCardSentenceSelectionAsTarget(
            edited = TextFieldValue("別の文です。", TextRange(0, 2)),
            updateEditor = { editor = it },
            commitSentence = { _, _ -> commitCalls++; false },
            selectTarget = { target = it },
            onCommitted = { closeCalls++ },
        )

        assertEquals(committedEditor, editor)
        assertEquals(TargetSpan(3, 5), target)
        assertEquals(2, commitCalls)
        assertEquals(0, closeCalls)
    }

    @Test
    fun successfulDoneEditUpdatesEditorOnlyAfterCommit() {
        val edited = TextFieldValue("今日は雨です。")
        var editor = TextFieldValue("今日は晴れです。")
        val events = mutableListOf<String>()

        commitCardSentenceEdit(
            edited = edited,
            updateEditor = { editor = it; events += "editor" },
            commitSentence = { text, beforeUpdate ->
                events += "persist:$text"
                beforeUpdate()
                true
            },
            onCommitted = { events += "close" },
        )

        assertEquals(listOf("persist:今日は雨です。", "editor", "close"), events)
        assertEquals(edited, editor)
    }

    @Test
    fun successfulEditUpdatesEditorAndSelectionOnlyAfterCommit() {
        val edited = TextFieldValue("今日は雨です。", TextRange(3, 4))
        var editor = TextFieldValue("今日は晴れです。", TextRange(3, 5))
        var target: TargetSpan? = TargetSpan(3, 5)
        val events = mutableListOf<String>()

        useCardSentenceSelectionAsTarget(
            edited = edited,
            updateEditor = { editor = it; events += "editor" },
            commitSentence = { text, beforeUpdate ->
                events += "persist:$text"
                beforeUpdate()
                true
            },
            selectTarget = { target = it; events += "target" },
            onCommitted = { events += "close" },
        )

        assertEquals(listOf("persist:今日は雨です。", "editor", "target", "close"), events)
        assertEquals(edited, editor)
        assertEquals(TargetSpan(3, 4), target)
    }

}
