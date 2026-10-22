package dev.pierre.japaneseanki

import java.net.URI

/** Build-pinned data-only UniDic runtime artifact. The matching v0.4 release asset must ship with the APK. */
internal object UniDicResourceCatalog {
    const val VERSION = "3.1.1"
    const val ARCHIVE_SHA256 = "4963afe095a031b9a15e97325795cf28c772ff108138172e69c786cbc4c9b3fe"
    const val ARCHIVE_BYTES = 446_602_958L
    const val ARCHIVE_FILE_NAME = "unidic-cwj-3.1.1-runtime.zip"

    val manifest = FirstRunResourceManifest(
        listOf(
            FirstRunResource(
                id = "unidic-cwj",
                version = VERSION,
                uri = URI.create(
                    "https://github.com/plabadens/japanese-anki-app/releases/download/v0.4/$ARCHIVE_FILE_NAME",
                ),
                sha256 = ARCHIVE_SHA256,
                expectedBytes = ARCHIVE_BYTES,
                targetFileName = ARCHIVE_FILE_NAME,
                archiveEntries = listOf(
                    ResourceArchiveEntry("unidic-cwj-$VERSION/char.bin", 262_496L),
                    ResourceArchiveEntry("unidic-cwj-$VERSION/dicrc", 1_785L),
                    ResourceArchiveEntry("unidic-cwj-$VERSION/matrix.bin", 480_905_780L),
                    ResourceArchiveEntry("unidic-cwj-$VERSION/sys.dic", 243_418_052L),
                    ResourceArchiveEntry("unidic-cwj-$VERSION/unk.dic", 5_492L),
                ),
            ),
        ),
    )
}
