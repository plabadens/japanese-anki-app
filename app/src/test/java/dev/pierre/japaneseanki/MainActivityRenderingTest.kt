package dev.pierre.japaneseanki

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.lifecycle.Lifecycle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [37], qualifiers = "w390dp-h844dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainActivityRenderingTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @get:Rule
    val testName = TestName()

    private var scenario: ActivityScenario<MainActivity>? = null
    private var observedStorage: MemoryAppPreferenceStorage? = null
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        // Robolectric cannot initialise the bundled MeCab native dictionary or packaged SQLite asset.
        // These fakes sit behind the same service methods the activity uses to derive readiness.
        AppServices.installOfflineAnalyzerForTest { RenderingAnalyzer() }
        AppServices.installOfflineDictionaryForTest { RenderingDictionary() }
        seedPreferences(testName.methodName)
        if (testName.methodName == "preferenceCollectorsFollowTheActivityLifecycle") {
            observedStorage = MemoryAppPreferenceStorage(
                initialDraft = PreferenceValues(context.getSharedPreferences("draft", Context.MODE_PRIVATE).all.toMap()),
                initialSettings = PreferenceValues(context.getSharedPreferences("settings", Context.MODE_PRIVATE).all.toMap()),
            ).also(AppServices::installPreferenceStorageForTest)
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario?.close()
        scenario = null
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("draft", Context.MODE_PRIVATE).edit().clear().commit()
        AppServices.clearTestOverrides()
    }

    @Test
    fun onboardedLaunchShowsCaptureInsteadOfSetup() {
        waitForTag("capture-sentence-field")

        composeRule.onNodeWithTag("capture-sentence-field").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.choose_word)).assertIsDisplayed()
        composeRule.onAllNodesWithText(context.getString(R.string.card_sentence_audio_field))
            .assertCountEquals(0)
    }

    @Test
    fun preferenceCollectorsFollowTheActivityLifecycle() {
        val storage = checkNotNull(observedStorage)
        waitForTag("capture-sentence-field")
        composeRule.waitUntil { storage.draftCollectors == 1 && storage.settingsCollectors == 1 }

        scenario?.moveToState(Lifecycle.State.CREATED)
        composeRule.waitUntil { storage.draftCollectors == 0 && storage.settingsCollectors == 0 }
        storage.update(PreferenceFile.SETTINGS, PreferenceChanges().putString("while_stopped", "retained"))
        scenario?.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitUntil { storage.draftCollectors == 1 && storage.settingsCollectors == 1 }

        scenario?.recreate()
        waitForTag("capture-sentence-field")
        composeRule.waitUntil { storage.draftCollectors == 1 && storage.settingsCollectors == 1 }
        assertEquals("retained", storage.settings.value.getString("while_stopped", null))

        scenario?.close()
        scenario = null
        composeRule.waitUntil { storage.draftCollectors == 0 && storage.settingsCollectors == 0 }
    }

    @Test
    fun recreationAndActivityDestructionKeepTheApplicationWriterAndItsQueuedSaves() {
        waitForTag("capture-sentence-field")
        val storage = (context as JapaneseAnkiApplication).preferenceStorage
        scenario?.onActivity { assertSame(storage, AppServices.preferenceStorage(it)) }
        storage.update(PreferenceFile.SETTINGS, PreferenceChanges().putString("ownership_probe", "queued"))

        scenario?.recreate()
        waitForTag("capture-sentence-field")
        scenario?.onActivity { assertSame(storage, AppServices.preferenceStorage(it)) }
        scenario?.close()
        scenario = null

        assertTrue(runSuspendTest {
            storage.persist(PreferenceFile.SETTINGS, PreferenceChanges().putBoolean("ownership_barrier", true))
        })
        val restarted = AndroidPreferenceStorage(context)
        runSuspendTest { restarted.load() }
        assertEquals("queued", restarted.settings.value.getString("ownership_probe", null))
        assertTrue(restarted.settings.value.getBoolean("ownership_barrier", false))
    }

    @Test
    fun cardRouteRendersTheCommittedSentenceAndTargetWithSetupBlockedSend() {
        waitForTag("sentence-word-0-1")

        composeRule.onNodeWithTag("sentence-layout").assertIsDisplayed()
        composeRule.onNodeWithTag("sentence-word-0-1").assertIsDisplayed().assertIsSelected()
        composeRule.onNodeWithTag(
            "sentence-word-text-0-1",
            useUnmergedTree = true,
        ).assertIsDisplayed().assertTextEquals("猫")
        composeRule.onNodeWithTag(
            "sentence-word-text-1-2",
            useUnmergedTree = true,
        ).assertIsDisplayed().assertTextEquals("が")
        composeRule.onNodeWithTag(
            "sentence-word-text-2-4",
            useUnmergedTree = true,
        ).assertIsDisplayed().assertTextEquals("走る")
        // Word analysis and setup readiness arrive independently.
        waitForText(context.getString(R.string.audio_setup_needed))
        composeRule.onNodeWithText(context.getString(R.string.audio_setup_needed)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.finish_setup))
            .assertIsDisplayed()
            .assertIsEnabled()
    }

    @Test
    fun openSentenceEditRestoresItsWorkingTextAndSelectionAfterRecreation() {
        waitForTag("sentence-layout")

        val editSentence = context.getString(R.string.edit_sentence)
        composeRule.onNodeWithText(editSentence).performClick()
        val sentenceField = composeRule.onNodeWithText(context.getString(R.string.sentence))
        sentenceField.performTextReplacement("今日は雨です。")
        sentenceField.performTextInputSelection(androidx.compose.ui.text.TextRange(3, 4))

        scenario?.recreate()

        waitForText(editSentence)
        composeRule.onNodeWithText("今日は雨です。").assertIsDisplayed()
        val restoredField = composeRule.onNodeWithText(context.getString(R.string.sentence))
        assertEquals(
            androidx.compose.ui.text.TextRange(3, 4),
            restoredField.fetchSemanticsNode().config[
                androidx.compose.ui.semantics.SemanticsProperties.TextSelectionRange
            ],
        )
    }

    @Test
    fun dismissingSentenceEditPreservesAuthoritativeSentenceAndTargetSelection() {
        waitForTag("sentence-layout")
        composeRule.onNodeWithText(context.getString(R.string.edit_sentence)).performClick()
        val sentenceField = composeRule.onNodeWithText(context.getString(R.string.sentence))
        sentenceField.performTextReplacement("今日は雨です。")
        sentenceField.performTextInputSelection(androidx.compose.ui.text.TextRange(3, 4))

        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss))
            .performSemanticsAction(SemanticsActions.Dismiss) { dismiss -> dismiss() }

        sentenceField.assertDoesNotExist()
        composeRule.onNodeWithText("今日は雨です。").assertDoesNotExist()
        composeRule.onNodeWithTag("sentence-layout").assertIsDisplayed()
        composeRule.onNodeWithTag("sentence-word-text-0-1", useUnmergedTree = true)
            .assertTextEquals("猫")
        composeRule.onNodeWithTag("sentence-word-0-1").assertIsSelected()
        val draftPreferences = context.getSharedPreferences("draft", Context.MODE_PRIVATE)
        assertEquals(TEST_SENTENCE, draftPreferences.getString("sentence", null))
        assertEquals(0, draftPreferences.getInt("target_start", -1))
        assertEquals(1, draftPreferences.getInt("target_end", -1))
    }

    @Test
    fun interruptedWordAudioRecoveryDisplaysItsMessageAndEnabledDiscardAction() {
        waitForText(recoveryMessage())

        composeRule.onNodeWithText(recoveryMessage()).assertIsDisplayed()
        val discardAction = context.getString(R.string.card_discard_interrupted_send_keep_draft)
        waitForText(discardAction)
        assertEquals(1, composeRule.onAllNodesWithText(discardAction).fetchSemanticsNodes().size)
        composeRule.onNodeWithText(discardAction).assertIsEnabled()
    }

    @Test
    fun discardingInterruptedSendKeepsDraftAndClearsFrozenKeys() {
        waitForText(recoveryMessage())

        val discardAction = context.getString(R.string.card_discard_interrupted_send_keep_draft)
        waitForText(discardAction)
        assertEquals(1, composeRule.onAllNodesWithText(discardAction).fetchSemanticsNodes().size)
        composeRule.onNodeWithText(discardAction).assertIsEnabled()
            .performClick()

        val draftPreferences = context.getSharedPreferences("draft", Context.MODE_PRIVATE)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            clearFrozenSubmission().values.keys.none(draftPreferences::contains)
        }
        assertEquals(TEST_SENTENCE, draftPreferences.getString("sentence", null))
        assertEquals(TEST_NOTES, draftPreferences.getString("notes", null))
        assertEquals(TEST_TAGS, draftPreferences.getString("tags", null))
        assertTrue(clearFrozenSubmission().values.isNotEmpty())
    }

    @Test
    fun frozenRetryExplainsTheEditLockAndDiscardUnlocksTheVisibleDraft() {
        waitForText(recoveryMessage())
        composeRule.onNodeWithText(context.getString(R.string.card_retry_media_import_may_duplicate)).performClick()
        waitForText(context.getString(R.string.card_frozen_send_read_only))
        composeRule.onNodeWithText(context.getString(R.string.edit_sentence)).assertIsNotEnabled()
        composeRule.onAllNodesWithText(context.getString(R.string.card_still_needed_1_s, "Select a dictionary entry."))
            .assertCountEquals(0)
        val discard = context.getString(R.string.card_discard_interrupted_send_keep_draft)
        composeRule.onAllNodesWithText(discard).assertCountEquals(1)
        composeRule.onNodeWithText(discard).performClick()
        composeRule.onNodeWithText(context.getString(R.string.edit_sentence)).assertIsEnabled()
        val draft = context.getSharedPreferences("draft", Context.MODE_PRIVATE)
        composeRule.waitUntil { clearFrozenSubmission().values.keys.none(draft::contains) }
        assertEquals(TEST_SENTENCE, draft.getString("sentence", null))
        assertEquals(TEST_NOTES, draft.getString("notes", null))
    }

    private fun recoveryMessage(): String = context.getString(
        R.string.card_send_recovery_media_preparation_interrupted,
        MediaImportOperation.WORD_AUDIO.label.replaceFirstChar { it.uppercase() },
    )

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = SCREEN_READY_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForText(text: String) {
        try {
            composeRule.waitUntil(timeoutMillis = SCREEN_READY_TIMEOUT_MILLIS) {
                composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (timeout: Throwable) {
            dumpUi()
            throw AssertionError("Timed out waiting for text '$text'.", timeout)
        }
    }

    private fun dumpUi() {
        val output = buildString {
            fun appendNode(node: SemanticsNode, depth: Int) {
                append("  ".repeat(depth)).append(node.config).append('\n')
                node.children.forEach { appendNode(it, depth + 1) }
            }
            composeRule.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes()
                .forEach { appendNode(it, 0) }
        }
        java.io.File("build/main-activity-test-semantics.txt").writeText(output)
    }

    private fun seedPreferences(methodName: String) {
        val settings = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val draft = context.getSharedPreferences("draft", Context.MODE_PRIVATE)
        write(settings, PreferenceChanges().putBoolean("onboarding_complete", true))
        write(draft, when (methodName) {
            "cardRouteRendersTheCommittedSentenceAndTargetWithSetupBlockedSend",
            "openSentenceEditRestoresItsWorkingTextAndSelectionAfterRecreation",
            "dismissingSentenceEditPreservesAuthoritativeSentenceAndTargetSelection" -> cardDraft()
            "interruptedWordAudioRecoveryDisplaysItsMessageAndEnabledDiscardAction",
            "discardingInterruptedSendKeepsDraftAndClearsFrozenKeys",
            "frozenRetryExplainsTheEditLockAndDiscardUnlocksTheVisibleDraft" -> interruptedDraft()
            else -> PreferenceChanges()
                .putString("route", "Capture")
                .putString("capture_mode", "Type")
        })
    }

    private fun cardDraft(): PreferenceChanges = PreferenceChanges()
        .putString("route", "Card")
        .putString("capture_mode", "Type")
        .putString("sentence", TEST_SENTENCE)
        .putString("committed_sentence", TEST_SENTENCE)
        .putString("committed_sentence_voice", "")
        .putBoolean("card_invalidated", false)
        .putInt("target_start", 0)
        .putInt("target_end", 1)
        .putString("vocabulary", "猫")
        .putString("vocabulary_reading", "ねこ")
        .putString("definition", "cat")
        .putBoolean("definition_manual", true)
        .putString("translation", "The cat runs.")
        .putString("translation_sentence", TEST_SENTENCE)
        .putString("translation_source", TranslationSource.MANUAL.name)
        .putBoolean("translation_manual", true)
        .putBoolean("translation_stale", false)
        .putString("pitch_accent", "0")
        .putString("draft_origin", "Type")

    private fun interruptedDraft(): PreferenceChanges {
        val frozen = FrozenSubmission(
            revision = 7L,
            modelId = 100L,
            deckId = 200L,
            modelFields = AnkiFieldContract.requiredFields,
            orderedFields = AnkiFieldContract.requiredFields.map { "" },
            sentence = TEST_SENTENCE,
            lemma = "猫",
            vocabularyReading = "ねこ",
            sentenceReading = "ねこ",
            definition = "cat",
            translation = "The cat runs.",
            notes = TEST_NOTES,
            tags = setOf(TEST_TAGS),
            pitchAccentText = "0",
            targetStart = 0,
            targetEnd = 1,
            media = null,
            importedFilename = null,
            noteId = null,
            sentenceAudio = FrozenAudioItem(MediaIdentity("/draft/sentence.mp3", 10L, 20L)),
            wordAudio = FrozenAudioItem(MediaIdentity("/draft/word.ogg", 11L, 21L)),
        )
        return PreferenceChanges()
            .putString("route", "Card")
            .putString("capture_mode", "Type")
            .putString("sentence", TEST_SENTENCE)
            .putInt("target_start", 0)
            .putInt("target_end", 1)
            .putString("vocabulary", "猫")
            .putString("vocabulary_reading", "ねこ")
            .putString("notes", TEST_NOTES)
            .putString("tags", TEST_TAGS)
            .putString("draft_origin", "Type")
            .putFrozenSubmission(frozen, SubmissionPhase.MEDIA_IMPORTING)
            .putString(SubmissionRecoveryKeys.MEDIA_OPERATION, MediaImportOperation.WORD_AUDIO.name)
    }

    private fun write(preferences: android.content.SharedPreferences, changes: PreferenceChanges) {
        val editor = preferences.edit().clear()
        changes.values.forEach { (key, value) ->
            when (value) {
                null -> editor.remove(key)
                is String -> editor.putString(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toMutableSet())
                else -> error("Unsupported test preference value for $key")
            }
        }
        check(editor.commit()) { "Could not seed activity test preferences." }
    }

    private class RenderingDictionary : OfflineDictionaryService {
        override fun prepare(): Map<String, String> = mapOf("schema_version" to "2")
        override fun hasEntry(surface: String): Boolean = false
        override fun search(
            surface: String,
            lemma: String?,
            limit: Int,
            offset: Int,
        ): List<DictionaryEntry> = emptyList()
    }

    private class RenderingAnalyzer : JapaneseAnalyzer {
        override fun loadInstalled(): Boolean = true
        override fun prepare() = Unit

        override fun analyze(sentence: String): List<JapaneseToken> = if (sentence == TEST_SENTENCE) {
            listOf(
                JapaneseToken(TargetSpan(0, 1), "猫", "猫", "ねこ", "ねこ", "名詞-普通名詞"),
                JapaneseToken(TargetSpan(1, 2), "が", "が", "が", "が", "助詞-格助詞"),
                JapaneseToken(TargetSpan(2, 4), "走る", "走る", "はしる", "はしる", "動詞-一般"),
            )
        } else {
            emptyList()
        }
    }

    private companion object {
        const val SCREEN_READY_TIMEOUT_MILLIS = 20_000L
        const val TEST_SENTENCE = "猫が走る"
        const val TEST_NOTES = "keep these notes"
        const val TEST_TAGS = "keep-this-tag"
    }
}
