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
