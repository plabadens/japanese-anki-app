package dev.pierre.japaneseanki

import org.junit.Assert.assertEquals
import org.junit.Test

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
