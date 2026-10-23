package dev.pierre.japaneseanki

import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsModelTest {
    @Test
    fun freshNotesReceiveConfiguredDefaultsEveryTime() {
        val defaults = "  grammar   japan beginner "

        assertEquals("grammar japan beginner", seedFreshNoteTags(null, defaults))
        assertEquals("grammar japan beginner", seedFreshNoteTags(null, defaults))
    }

    @Test
    fun existingPerNoteEditsIncludingIntentionalClearArePreserved() {
        val defaults = "grammar japan"

        assertEquals("custom   tags", seedFreshNoteTags("custom   tags", defaults))
        assertEquals("", seedFreshNoteTags("", defaults))
        assertEquals("grammar japan", defaults)
    }
}
