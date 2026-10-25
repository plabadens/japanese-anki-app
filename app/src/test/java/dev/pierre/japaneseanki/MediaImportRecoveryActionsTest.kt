package dev.pierre.japaneseanki

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import dev.pierre.japaneseanki.send.MediaImportRecoveryActions
import dev.pierre.japaneseanki.send.discardInterruptedMediaRecovery
import dev.pierre.japaneseanki.send.pendingMediaRecoveryOperation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [37])
class MediaImportRecoveryActionsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun seededWordAudioInterruptionOffersOneActionThatClearsOnlyFrozenRecovery() {
        val fields = AnkiFieldContract.requiredFields
        val frozen = FrozenSubmission(
            revision = 4L,
            modelId = 100L,
            deckId = 200L,
            modelFields = fields,
            orderedFields = fields.map { "" },
            sentence = "猫が走る",
            lemma = "猫",
            vocabularyReading = "ねこ",
            sentenceReading = "ねこ",
            definition = "cat",
            translation = "The cat runs.",
            notes = "keep these notes",
            tags = setOf("keep-this-tag"),
            pitchAccentText = "0",
            targetStart = 0,
            targetEnd = 1,
            media = null,
            importedFilename = null,
            noteId = null,
            sentenceAudio = FrozenAudioItem(MediaIdentity("/draft/sentence.mp3", 10L, 20L)),
            wordAudio = FrozenAudioItem(MediaIdentity("/draft/word.ogg", 11L, 21L)),
        )
        val seeded = PreferenceValues().changed(
            PreferenceChanges()
                .putString("sentence", frozen.sentence)
                .putString("vocabulary", frozen.lemma)
                .putString("notes", frozen.notes)
                .putString("tags", "keep-this-tag")
                .putFrozenSubmission(frozen, SubmissionPhase.MEDIA_IMPORTING)
                .putString(SubmissionRecoveryKeys.MEDIA_OPERATION, MediaImportOperation.WORD_AUDIO.name),
        )
        val storage = MemoryAppPreferenceStorage(initialDraft = seeded)
        runSuspendTest { storage.load() }
        assertEquals(MediaImportOperation.WORD_AUDIO, pendingMediaRecoveryOperation(storage.draft.value))
        assertNotNull(readFrozenSubmission(storage.draft.value))
        var terminal = false

        composeRule.setContent {
            MaterialTheme {
                MediaImportRecoveryActions(
                    operation = pendingMediaRecoveryOperation(storage.draft.value),
                    onRetry = {},
                    onDiscardAudio = {
                        discardInterruptedMediaRecovery(storage)
                        terminal = true
                    },
                    onRemoveImage = {},
                )
            }
        }

        val discardAction = ApplicationProvider.getApplicationContext<Context>()
            .getString(R.string.card_discard_interrupted_send_keep_draft)
        composeRule.onNodeWithText(discardAction)
            .assertIsDisplayed()
            .performClick()
        composeRule.runOnIdle {
            assertTrue(terminal)
            assertNull(SubmissionPhase.fromPersisted(storage.draft.value.getString(SubmissionRecoveryKeys.STATUS, null)))
            assertNull(readFrozenSubmission(storage.draft.value))
            assertEquals(frozen.sentence, storage.draft.value.getString("sentence", null))
            assertEquals(frozen.lemma, storage.draft.value.getString("vocabulary", null))
            assertEquals(frozen.notes, storage.draft.value.getString("notes", null))
            assertEquals("keep-this-tag", storage.draft.value.getString("tags", null))
        }
    }
}
