package dev.pierre.japaneseanki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnkiMediaImporterTest {
    @Test fun acceptsOnlySimpleAvifFilenames() {
        assertEquals("image-1.avif", AnkiMediaImporter.sanitizeFilename("image-1.avif"))
        assertNull(AnkiMediaImporter.sanitizeFilename("../image.avif"))
        assertNull(AnkiMediaImporter.sanitizeFilename(".hidden.avif"))
        assertNull(AnkiMediaImporter.sanitizeFilename("image.jpg"))
    }

    @Test fun acceptsOnlySafeWavAndOggAudioNames() {
        assertEquals("sentence-1.wav", AnkiMediaImporter.sanitizeAudioFilename("sentence-1.wav"))
        assertEquals("word.OGG", AnkiMediaImporter.sanitizeAudioFilename("word.OGG"))
        assertNull(AnkiMediaImporter.sanitizeAudioFilename("../word.ogg"))
        assertNull(AnkiMediaImporter.sanitizeAudioFilename(".hidden.wav"))
        assertNull(AnkiMediaImporter.sanitizeAudioFilename("audio.mp3"))
        assertNull(AnkiMediaImporter.sanitizeFilename("word.wav"))
    }

    @Test fun extractsSafeAudioReferencesOnlyFromLocalFileUris() {
        assertEquals("voice.wav", AnkiMediaImporter.importedAudioFilename("file:///data/collection.media/voice.wav"))
        assertEquals("voice.ogg", AnkiMediaImporter.importedAudioFilename("file:///data/collection.media/voice.ogg"))
        assertNull(AnkiMediaImporter.importedAudioFilename("file:///data/collection.media/voice.mp3"))
        assertNull(AnkiMediaImporter.importedAudioFilename("content://provider/voice.wav"))
        assertNull(AnkiMediaImporter.importedAudioFilename("file://remote/data/voice.wav"))
    }

    @Test fun extractsActualFilenameOnlyFromFileUris() {
        assertEquals(
            "actual-name.avif",
            AnkiMediaImporter.importedFilename("file:///data/anki/collection.media/actual-name.avif"),
        )
        assertNull(AnkiMediaImporter.importedFilename("content://provider/media/actual-name.avif"))
        assertNull(AnkiMediaImporter.importedFilename("file://remote-host/data/collection.media/actual-name.avif"))
        assertNull(AnkiMediaImporter.importedFilename("file:///data/collection.media/actual-name.avif?download=1"))
        assertNull(AnkiMediaImporter.importedFilename("file:///data/collection.media/actual-name.avif#fragment"))
        assertNull(AnkiMediaImporter.importedFilename("file:///data/anki/collection.media/../secret.avif"))
        assertNull(AnkiMediaImporter.importedFilename("file:///data/anki/collection.media/../../secret.avif"))
        assertNull(AnkiMediaImporter.importedFilename("file:///data/anki/collection.media/audio.mp3"))
    }
}
