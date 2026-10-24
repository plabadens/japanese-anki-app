package dev.pierre.japaneseanki.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.printToString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.pierre.japaneseanki.JapaneseToken
import dev.pierre.japaneseanki.TargetSpan
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [37], qualifiers = "w390dp-h844dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SentenceSelectionLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    private data class Scene(
        val sentence: String,
        val tokens: List<JapaneseToken>,
        val selected: TargetSpan? = null,
        val dark: Boolean = false,
        val fontScale: Float = 1f,
        val width: Int = 320,
    )

    @Test
    fun selectionTickIsCommittedOnceAndRetappingTheSameWordIsSilent() {
        val scene = mutableStateOf(repeatedScene())
        val events = mutableListOf<TouchFeedbackEvent>()
        val dispatcher = TouchFeedbackDispatcher { event -> events += event }
        composeRule.setContent {
            val current = scene.value
            CompositionLocalProvider(
                LocalMotionEnabled provides false,
                LocalTouchFeedbackDispatcher provides dispatcher,
            ) {
                MaterialTheme(colorScheme = lightColorScheme()) {
                    sentencePane(current) { span -> scene.value = scene.value.copy(selected = span) }
                }
            }
        }

        composeRule.onNodeWithTag("sentence-word-text-0-1", useUnmergedTree = true)
            .performTouchInput { click(Offset(center.x, center.y)) }
        composeRule.waitForIdle()
        assertEquals(listOf(TouchFeedbackEvent.WORD_SELECTION), events)
        composeRule.onNodeWithTag("sentence-word-0-1").performClick()
        assertEquals("Re-selecting the current target must not tick", listOf(TouchFeedbackEvent.WORD_SELECTION), events)
        composeRule.onNodeWithTag("sentence-word-text-2-3", useUnmergedTree = true)
            .performTouchInput { click(Offset(center.x, center.y)) }
        composeRule.waitForIdle()
        assertEquals(
            listOf(TouchFeedbackEvent.WORD_SELECTION, TouchFeedbackEvent.WORD_SELECTION),
            events,
        )
    }

    @Test
    fun aPressThatRepaintsTheWordBeforeReleaseStillSelectsIt() {
        // A real finger stays down across several frames while the pressed highlight repaints the
        // word. The tap must survive that recomposition; keyed gestures used to cancel themselves.
        val scene = mutableStateOf(regularScene())
        val events = mutableListOf<TouchFeedbackEvent>()
        val dispatcher = TouchFeedbackDispatcher { event -> events += event }
        composeRule.setContent {
            val current = scene.value
            CompositionLocalProvider(
                LocalMotionEnabled provides false,
                LocalTouchFeedbackDispatcher provides dispatcher,
            ) {
                MaterialTheme(colorScheme = lightColorScheme()) {
                    sentencePane(current) { span -> scene.value = scene.value.copy(selected = span) }
                }
            }
        }

        val word = composeRule.onNodeWithTag("sentence-word-text-4-5", useUnmergedTree = true)
        word.performTouchInput { down(center) }
        composeRule.waitForIdle()
        word.performTouchInput { up() }
        composeRule.waitForIdle()

        assertEquals("A finger-down must survive the press highlight", TargetSpan(4, 5), scene.value.selected)
        assertEquals(listOf(TouchFeedbackEvent.WORD_SELECTION), events)
    }

    @Test
    fun reducedMotionProgressRemainsStaticAccessibleAndSilent() {
        val events = mutableListOf<TouchFeedbackEvent>()
        val dispatcher = TouchFeedbackDispatcher { event -> events += event }
        composeRule.setContent {
            CompositionLocalProvider(
                LocalMotionEnabled provides false,
                LocalTouchFeedbackDispatcher provides dispatcher,
            ) {
                Box {
                    MotionAwareProgressIndicator(
                        modifier = Modifier.testTag("reduced-motion-progress"),
                        color = Color.Blue,
                        progressSize = ProgressSize.Medium,
                        stateDescription = "Working",
                    )
                }
            }
        }
        val progress = composeRule.onNodeWithTag("reduced-motion-progress").fetchSemanticsNode().config
        assertEquals(ProgressBarRangeInfo.Indeterminate, progress[SemanticsProperties.ProgressBarRangeInfo])
        assertEquals("Working", progress[SemanticsProperties.StateDescription])
        assertTrue("Progress must not dispatch haptics", events.isEmpty())
        capture(motionEvidenceDirectory(), "reduced-motion-progress.png")
    }

    @Test
    fun selectionIsOccurrenceSpecificAndDoesNotChangeAnyMeasuredBounds() {
        val repeated = repeatedScene()
        val scene = mutableStateOf(repeated)
        composeRule.setContent {
            val current = scene.value
            CompositionLocalProvider(
                LocalDensity provides Density(composeRule.density.density, current.fontScale),
            ) {
                MaterialTheme(colorScheme = lightColorScheme()) {
                    sentencePane(current) { span -> scene.value = scene.value.copy(selected = span) }
                }
            }
        }

        val root = composeRule.onNodeWithTag("sentence-layout")
        val wordTags = listOf(
            "sentence-word-0-1",
            "sentence-word-1-2",
            "sentence-word-2-3",
            "sentence-word-3-4",
            "sentence-word-4-5",
        )
        val beforeRoot = root.fetchSemanticsNode().boundsInRoot
        val beforeWords = wordTags.map { composeRule.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot }
        val minTargetHeightPx = 48f * composeRule.density.density
        beforeWords.forEachIndexed { index, bounds ->
            assertTrue("Word target $index is under 48dp: $bounds", bounds.height >= minTargetHeightPx - 1f)
        }
        beforeWords.forEachIndexed { index, bounds ->
            beforeWords.drop(index + 1).forEach { other ->
                val overlaps = bounds.left < other.right && bounds.right > other.left &&
                    bounds.top < other.bottom && bounds.bottom > other.top
                assertTrue("Adjacent word targets overlap: $bounds / $other", !overlaps)
            }
        }

        composeRule.onNodeWithTag("sentence-word-text-4-5", useUnmergedTree = true).assertHasNoClickAction()
        composeRule.onNodeWithTag("sentence-word-2-3").assertHasClickAction()
        val selectedTextNode = composeRule.onNodeWithTag("sentence-word-text-2-3", useUnmergedTree = true)
        val selectedLayoutResults = mutableListOf<TextLayoutResult>()
        val selectedTextAction = selectedTextNode.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult]
        assertTrue("Could not inspect selectable word", selectedTextAction.action?.invoke(selectedLayoutResults) == true)
        val selectedGlyph = selectedLayoutResults.single().getBoundingBox(0)
        selectedTextNode.performTouchInput {
            click(Offset(selectedGlyph.center.x, selectedGlyph.center.y))
        }
        composeRule.waitForIdle()
        assertEquals("Touch selection did not update the owner state", TargetSpan(2, 3), scene.value.selected)
        composeRule.onNodeWithTag("sentence-word-0-1").assertIsNotSelected()
        composeRule.onNodeWithTag("sentence-word-2-3").assertIsSelected()
        assertEquals("Sentence bounds changed on selection", beforeRoot, root.fetchSemanticsNode().boundsInRoot)
        assertEquals(
            "Per-token bounds changed on selection",
            beforeWords,
            wordTags.map { composeRule.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot },
        )

        val traversalValues = wordTags.map { tag ->
            composeRule.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.TraversalIndex]
        }
        assertEquals(listOf(0f, 1f, 2f, 3f, 4f), traversalValues)
        val traversalTree = composeRule.onRoot(useUnmergedTree = true).printToString()
        val evidenceDirectory = evidenceDirectory()
        File(evidenceDirectory, "talkback-traversal-semantics.txt").writeText(traversalTree)
        assertTrue("Traversal tree evidence is empty", traversalTree.isNotBlank())

        composeRule.onNodeWithTag("sentence-word-0-1").performClick()
        composeRule.waitForIdle()
        assertEquals("Accessibility activation did not select the focused word", TargetSpan(0, 1), scene.value.selected)
        composeRule.onNodeWithTag("sentence-word-0-1").assertIsSelected()
        scene.value = repeated.copy(selected = TargetSpan(2, 3))
        composeRule.waitForIdle()

        val regular = regularScene()
        scene.value = regular
        composeRule.waitForIdle()
        capture(evidenceDirectory, "sentence-no-selection-light.png")
        scene.value = regular.copy(selected = TargetSpan(4, 5))
        composeRule.waitForIdle()
        capture(evidenceDirectory, "sentence-selected-light.png")

        scene.value = regular.copy(dark = true)
        composeRule.waitForIdle()
        capture(evidenceDirectory, "sentence-no-selection-dark.png")
        scene.value = regular.copy(dark = true, selected = TargetSpan(4, 5))
        composeRule.waitForIdle()
        capture(evidenceDirectory, "sentence-selected-dark.png")

        scene.value = repeated.copy(selected = TargetSpan(2, 3))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("sentence-word-2-3").assertIsSelected()
        composeRule.onNodeWithTag("sentence-word-0-1").assertIsNotSelected()
        capture(evidenceDirectory, "sentence-repeated-second-occurrence.png")

        val kana = kanaScene()
        scene.value = kana
        composeRule.waitForIdle()
        capture(evidenceDirectory, "sentence-kana-only.png")
        val kanaBounds = listOf("sentence-word-0-2", "sentence-word-2-3", "sentence-word-3-5")
            .map { composeRule.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot }
        assertEquals("Kana-only words must share a baseline when on one line", 1, kanaBounds.map { it.bottom }.distinct().size)

        val long = longWordScene()
        scene.value = long
        composeRule.waitForIdle()
        val longTextNode = composeRule.onNodeWithTag("sentence-word-text-0-${long.sentence.length - 1}", useUnmergedTree = true)
        val layoutResults = mutableListOf<TextLayoutResult>()
        val getTextLayout = longTextNode.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult]
        assertTrue("Could not inspect wrapped word layout", getTextLayout.action?.invoke(layoutResults) == true)
        val textLayout = layoutResults.single()
        assertTrue("Long source word did not wrap inside its own target", textLayout.lineCount > 1)
        composeRule.onNodeWithText(long.sentence, useUnmergedTree = true).assertExists()
        val finalBaseOffset = long.sentence.length - 2
        val trailingPunctuationOffset = long.sentence.length - 1
        assertEquals(
            "Japanese kinsoku must keep trailing punctuation with the final word line",
            textLayout.getLineForOffset(finalBaseOffset),
            textLayout.getLineForOffset(trailingPunctuationOffset),
        )
        val punctuationBounds = textLayout.getBoundingBox(trailingPunctuationOffset)
        longTextNode.performTouchInput {
            click(Offset(punctuationBounds.center.x, punctuationBounds.center.y))
        }
        composeRule.onNodeWithTag("sentence-word-0-${long.sentence.length - 1}").assertIsNotSelected()
        longTextNode.assertHasNoClickAction()
        capture(evidenceDirectory, "sentence-font-scale-200-wrap-light.png")
        scene.value = long.copy(dark = true)
        composeRule.waitForIdle()
        capture(evidenceDirectory, "sentence-font-scale-200-wrap-dark.png")

        val expectedFiles = listOf(
            "sentence-no-selection-light.png",
            "sentence-selected-light.png",
            "sentence-no-selection-dark.png",
            "sentence-selected-dark.png",
            "sentence-repeated-second-occurrence.png",
            "sentence-kana-only.png",
            "sentence-font-scale-200-wrap-light.png",
            "sentence-font-scale-200-wrap-dark.png",
            "talkback-traversal-semantics.txt",
        )
        expectedFiles.forEach { name ->
            val file = File(evidenceDirectory, name)
            assertTrue("Missing evidence file: $file", file.isFile && file.length() > 0L)
        }
        android.util.Log.i("SentenceSelectionEvidence", "Captured ${expectedFiles.size} files under ${evidenceDirectory.absolutePath}")
    }

    @Composable
    private fun sentencePane(scene: Scene, onSelect: (TargetSpan) -> Unit) {
        MaterialTheme(colorScheme = if (scene.dark) darkColorScheme() else lightColorScheme()) {
            Surface(
                modifier = Modifier.width(scene.width.dp),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Box(Modifier.fillMaxWidth().padding(12.dp)) {
                    SelectableSentence(
                        sentence = scene.sentence,
                        tokens = scene.tokens,
                        selectedSpan = scene.selected,
                        selectionEnabled = true,
                        onSelectSpan = onSelect,
                        furigana = emptyList(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    private fun regularScene(): Scene {
        val sentence = "図書館で本を読みました。"
        return Scene(sentence, listOf(
            token(sentence, 0, 3, "としょかん"),
            token(sentence, 3, 4, "で"),
            token(sentence, 4, 5, "ほん"),
            token(sentence, 5, 6, "を"),
            token(sentence, 6, 11, "よみました"),
            token(sentence, 11, 12, "", "補助記号-句点"),
        ))
    }

    private fun repeatedScene(): Scene {
        val sentence = "本で本です。"
        return Scene(sentence, listOf(
            token(sentence, 0, 1, "ほん"),
            token(sentence, 1, 2, "で"),
            token(sentence, 2, 3, "ほん"),
            token(sentence, 3, 4, "で"),
            token(sentence, 4, 5, "す"),
            token(sentence, 5, 6, "", "補助記号-句点"),
        ))
    }

    private fun kanaScene(): Scene {
        val sentence = "あめがふる。"
        return Scene(sentence, listOf(
            token(sentence, 0, 2, "あめ"),
            token(sentence, 2, 3, "が"),
            token(sentence, 3, 5, "ふる"),
            token(sentence, 5, 6, "", "補助記号-句点"),
        ))
    }

    private fun longWordScene(): Scene {
        val word = "非常に長い日本語の単語です"
        val sentence = "$word。"
        return Scene(
            sentence = sentence,
            tokens = listOf(
                token(sentence, 0, word.length, "ひじょうにながいにほんごのたんごです"),
                token(sentence, word.length, sentence.length, "", "補助記号-句点"),
            ),
            fontScale = 2f,
            width = 164,
        )
    }

    private fun token(sentence: String, start: Int, end: Int, reading: String, pos: String = "名詞") =
        JapaneseToken(
            span = TargetSpan(start, end),
            surface = sentence.substring(start, end),
            lemma = sentence.substring(start, end),
            surfaceReading = reading,
            lemmaReading = reading,
            partOfSpeech = pos,
        )

    private fun motionEvidenceDirectory(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File("build/robolectric-evidence/motion-feedback").apply {
            deleteRecursively()
            assertTrue("Could not create motion feedback evidence directory", mkdirs())
        }
    }

    private fun evidenceDirectory(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File("build/robolectric-evidence/sentence-selection").apply {
            deleteRecursively()
            assertTrue("Could not create sentence-selection evidence directory", mkdirs())
        }
    }

    private fun capture(directory: File, name: String) {
        composeRule.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val file = File(directory, name)
        file.outputStream().use { output ->
            assertTrue("Could not encode screenshot $name", bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        assertTrue("Screenshot was not written: $file", file.isFile && file.length() > 0L)
    }
}
