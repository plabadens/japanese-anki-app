package dev.pierre.japaneseanki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnkiFieldSerializerTest {
    @Test fun highlightsOnlySelectedOccurrenceAndEscapesInput() {
        val sentence = "<img src=x>猫と猫"
        val target = TargetSpan.fromSelection(sentence, 13, 14)!!
        assertEquals(
            "&lt;img src=x&gt;猫と<b>猫</b>",
            AnkiFieldSerializer.highlightedSentence(sentence, target),
        )
    }

    @Test fun plainNotesPreserveLineBreaksWithoutAcceptingHtml() {
        assertEquals("one&lt;br&gt;<br>two &amp; three", AnkiFieldSerializer.plainText("one<br>\ntwo & three"))
    }

    @Test fun fieldDelimiterCannotCorruptAnkiFieldSerialization() {
        assertEquals("a�b", AnkiFieldSerializer.plainText("a\u001fb"))
    }

    @Test fun definitionsKeepSenseBoundariesAndEscapeMarkup() {
        assertEquals(
            "<ol><li>one</li><li>&lt;two&gt;</li></ol>",
            AnkiFieldSerializer.definition(listOf("one", "<two>")),
        )
    }

    @Test fun mediaReferencesRejectPathsAndAnkiSoundDelimiters() {
        assertEquals("[sound:word.ogg]", AnkiFieldSerializer.audioReference("word.ogg"))
        assertNull(AnkiFieldSerializer.audioReference("../word.ogg"))
        assertNull(AnkiFieldSerializer.audioReference("word].ogg"))
        assertEquals("<img src=\"a&amp;b.avif\">", AnkiFieldSerializer.imageReference("a&b.avif"))
    }
}
