package dev.pierre.japaneseanki

import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnkiMediaImporterTest {
    @Test fun acceptsOnlySimpleAvifFilenames() {
        assertEquals("image-1.webp", AnkiMediaImporter.sanitizeFilename("image-1.webp"))
        assertNull(AnkiMediaImporter.sanitizeFilename("../image.webp"))
        assertNull(AnkiMediaImporter.sanitizeFilename(".hidden.webp"))
        assertNull(AnkiMediaImporter.sanitizeFilename("image.jpg"))
    }

    @Test fun acceptsOnlySafeWavOggAndMp3AudioNames() {
        assertEquals("sentence-1.wav", AnkiMediaImporter.sanitizeAudioFilename("sentence-1.wav"))
        assertEquals("word.OGG", AnkiMediaImporter.sanitizeAudioFilename("word.OGG"))
        assertEquals("sentence.mp3", AnkiMediaImporter.sanitizeAudioFilename("sentence.mp3"))
        assertNull(AnkiMediaImporter.sanitizeAudioFilename("../word.ogg"))
        assertNull(AnkiMediaImporter.sanitizeAudioFilename(".hidden.wav"))
        assertNull(AnkiMediaImporter.sanitizeAudioFilename("audio.m4a"))
        assertNull(AnkiMediaImporter.sanitizeFilename("word.wav"))
    }

    @Test fun extractsSafeAudioReferencesOnlyFromLocalFileUris() {
        assertEquals("voice.wav", AnkiMediaImporter.importedAudioFilename("file:///data/collection.media/voice.wav"))
        assertEquals("voice.ogg", AnkiMediaImporter.importedAudioFilename("file:///data/collection.media/voice.ogg"))
        assertEquals("voice.mp3", AnkiMediaImporter.importedAudioFilename("file:///data/collection.media/voice.mp3"))
        assertNull(AnkiMediaImporter.importedAudioFilename("file:///data/collection.media/voice.m4a"))
        assertNull(AnkiMediaImporter.importedAudioFilename("content://provider/voice.wav"))
        assertNull(AnkiMediaImporter.importedAudioFilename("file://remote/data/voice.wav"))
    }

    @Test fun providerReturnValidationUsesTheImportedMediaType() {
        listOf("voice.mp3", "voice.ogg", "voice.wav").forEach { filename ->
            val uri = "file:///data/collection.media/$filename"
            assertEquals(filename, AnkiMediaImporter.importedFilename(uri, AnkiMediaKind.AUDIO))
            assertNull(AnkiMediaImporter.importedFilename(uri, AnkiMediaKind.IMAGE))
        }
        assertEquals(
            "image.webp",
            AnkiMediaImporter.importedFilename("file:///data/collection.media/image.webp", AnkiMediaKind.IMAGE),
        )
        assertNull(
            AnkiMediaImporter.importedFilename("file:///data/collection.media/image.webp", AnkiMediaKind.AUDIO),
        )
        listOf(
            "file://remote/data/collection.media/voice.mp3",
            "file:///data/collection.media/voice.mp3?download=1",
            "file:///data/collection.media/voice.mp3#fragment",
            "file:///data/collection.media/../voice.mp3",
            "file:///data/collection.media/%2e%2e/voice.mp3",
            "content://provider/voice.mp3",
        ).forEach { uri ->
            assertNull(AnkiMediaImporter.importedFilename(uri, AnkiMediaKind.AUDIO))
        }
    }

    @Test fun extractsActualFilenameOnlyFromFileUris() {
        assertEquals(
            "actual-name.webp",
            AnkiMediaImporter.importedFilename("file:///data/anki/collection.media/actual-name.webp"),
        )
        assertNull(AnkiMediaImporter.importedFilename("content://provider/media/actual-name.webp"))
        assertNull(AnkiMediaImporter.importedFilename("file://remote-host/data/collection.media/actual-name.webp"))
        assertNull(AnkiMediaImporter.importedFilename("file:///data/collection.media/actual-name.webp?download=1"))
        assertNull(AnkiMediaImporter.importedFilename("file:///data/collection.media/actual-name.webp#fragment"))
        assertNull(AnkiMediaImporter.importedFilename("file:///data/anki/collection.media/../secret.webp"))
        assertNull(AnkiMediaImporter.importedFilename("file:///data/anki/collection.media/../../secret.webp"))
        assertNull(AnkiMediaImporter.importedFilename("file:///data/anki/collection.media/audio.mp3"))
    }
}
