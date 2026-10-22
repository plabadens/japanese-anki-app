package dev.pierre.japaneseanki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UniDicResourceCatalogTest {
    @Test fun manifestPinsOnlyTheRuntimeArchiveAndItsExactFiles() {
        val resources = UniDicResourceCatalog.manifest.resources
        assertEquals(1, resources.size)
        val resource = resources.single()
        assertEquals("unidic-cwj", resource.id)
        assertEquals("3.1.1", resource.version)
        assertEquals("https://github.com/plabadens/japanese-anki-app/releases/download/v0.4/unidic-cwj-3.1.1-runtime.zip", resource.uri.toString())
        assertEquals(446_602_958L, resource.expectedBytes)
        assertEquals("4963afe095a031b9a15e97325795cf28c772ff108138172e69c786cbc4c9b3fe", resource.sha256)
        assertEquals(UniDicResourceCatalog.ARCHIVE_FILE_NAME, resource.targetFileName)
        assertEquals(
            setOf(
                "unidic-cwj-3.1.1/char.bin",
                "unidic-cwj-3.1.1/dicrc",
                "unidic-cwj-3.1.1/matrix.bin",
                "unidic-cwj-3.1.1/sys.dic",
                "unidic-cwj-3.1.1/unk.dic",
            ),
            resource.archiveEntries.map { it.path }.toSet(),
        )
        assertEquals(724_593_605L, resource.archiveEntries.sumOf { it.sizeBytes })
        assertTrue(resource.uri.scheme.equals("https", ignoreCase = true))
    }
}
