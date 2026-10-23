package dev.pierre.japaneseanki

import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TargetSpanTest {
    @Test fun selectionRetainsExactOccurrence() {
        val sentence = "猫と猫"
        assertEquals("猫", TargetSpan.fromSelection(sentence, 2, 3)?.textIn(sentence))
    }

    @Test fun invalidAndSurrogateSplittingSelectionsAreRejected() {
        val sentence = "A😀B"
        assertNull(TargetSpan.fromSelection(sentence, 1, 2))
        assertNull(TargetSpan.fromSelection(sentence, 4, 2))
        assertNull(TargetSpan.fromSelection(sentence, 1, 1))
    }
}
