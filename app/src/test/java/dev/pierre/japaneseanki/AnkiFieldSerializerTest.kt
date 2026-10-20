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

    @Test fun sentenceFuriganaAnnotatesKnownSpansAndKeepsOkuriganaInPlace() {
        val sentence = "昨日食べた。"
        val markup = AnkiFieldSerializer.highlightedFuriganaSentence(
            sentence,
            TargetSpan(2, 5),
            "たべた",
            listOf(FuriganaSpan(TargetSpan(0, 2), "昨日", "きのう")),
        )
        assertEquals("昨日[きのう]<b>食[た]べた</b>。", markup)
    }

    @Test fun wordFuriganaAlignsOkuriganaAndSkipsKanaOnlyRuby() {
        assertEquals("食[た]べる", AnkiFieldSerializer.furiganaWord("食べる", "たべる"))
        assertEquals("お金[かね]", AnkiFieldSerializer.furiganaWord("お金", "おかね"))
        assertEquals("引[ひ]き出[だ]し", AnkiFieldSerializer.furiganaWord("引き出し", "ひきだし"))
        assertEquals("猫[ねこ]。", AnkiFieldSerializer.furiganaWord("猫。", "ねこ"))
        assertEquals("たべる", AnkiFieldSerializer.furiganaWord("たべる", "たべる"))
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
        assertEquals("<img src=\"a&amp;b.webp\">", AnkiFieldSerializer.imageReference("a&b.webp"))
    }
}
