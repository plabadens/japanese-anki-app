package dev.pierre.japaneseanki

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class AnkiDroidGatewayTest {
    private companion object {
        const val AUTHORITY = "com.ichi2.anki.flashcards"
    }

    private data class InsertCall(val method: String, val uri: Uri, val values: ContentValues)

    /** ContentResolver.insert is final on current Android; Robolectric routes it to this fake provider. */
    private class FakeContentResolver(context: Context) : ContentResolver(context) {
        private val provider = FakeContentProvider()
        val calls get() = provider.calls
        var onInsert: (InsertCall) -> Uri?
            get() = provider.onInsert
            set(value) { provider.onInsert = value }
        var insertFailure: Exception?
            get() = provider.insertFailure
            set(value) { provider.insertFailure = value }

        init {
            org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(AUTHORITY, provider)
        }
    }

    private class FakeContentProvider : ContentProvider() {
        val calls = mutableListOf<InsertCall>()
        var onInsert: (InsertCall) -> Uri? = { Uri.parse("file:///AnkiDroid/collection.media/returned.mp3") }
        var insertFailure: Exception? = null

        override fun onCreate(): Boolean = true
        override fun getType(uri: Uri): String? = null
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

        override fun insert(uri: Uri, values: ContentValues?): Uri? {
            val call = InsertCall("insert", uri, ContentValues(requireNotNull(values)))
            calls += call
            insertFailure?.let { throw it }
            return onInsert(call)
        }
    }

    private data class GatewayFixture(
        val context: Context,
        val resolver: FakeContentResolver,
        val gateway: AnkiDroidGateway,
        val createdFiles: MutableList<File>,
    ) {
        fun mediaFile(filename: String): File = File(File(context.filesDir, "draft"), "${UUID.randomUUID()}-$filename")
            .apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(1, 2, 3))
                createdFiles += this
            }

        fun close() { createdFiles.forEach(File::delete) }
    }

    private fun withGateway(block: (GatewayFixture) -> Unit) {
        clearFileProviderCache()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val resolver = FakeContentResolver(context)
        val fixture = GatewayFixture(context, resolver, AnkiDroidGateway(resolver, context), mutableListOf())
        try {
            block(fixture)
        } finally {
            fixture.close()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun clearFileProviderCache() {
        val cache = FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }
        (cache.get(null) as MutableMap<String, Any>).clear()
    }

    private fun GatewayFixture.import(file: File, operation: MediaImportOperation): String = when (operation) {
        MediaImportOperation.IMAGE -> gateway.importProcessedImage(file)
        MediaImportOperation.AUDIO,
        MediaImportOperation.SENTENCE_AUDIO,
        MediaImportOperation.WORD_AUDIO -> gateway.importAudio(file, operation)
    }

    private fun assertSuccess(operation: MediaImportOperation, filename: String) = withGateway { fixture ->
        val file = fixture.mediaFile(filename)
        fixture.resolver.onInsert = { Uri.parse("file:///AnkiDroid/collection.media/${file.name}") }

        assertEquals(file.name, fixture.import(file, operation))

        val call = fixture.resolver.calls.single()
        assertEquals("insert", call.method)
        assertEquals("com.ichi2.anki.flashcards", call.uri.authority)
        assertEquals("content://com.ichi2.anki.flashcards/media", call.uri.toString())
        assertEquals(file.name, call.values.getAsString("preferred_name"))
        assertNotNull(call.values.getAsString("file_uri"))
        assertEquals("content", Uri.parse(call.values.getAsString("file_uri")).scheme)
        assertEquals("dev.pierre.japaneseanki.media", Uri.parse(call.values.getAsString("file_uri")).authority)
    }

    private fun assertProviderFailure(operation: MediaImportOperation, filename: String) = withGateway { fixture ->
        val file = fixture.mediaFile(filename)
        fixture.resolver.onInsert = { null }

        val error = org.junit.Assert.assertThrows(MediaImportException::class.java) {
            fixture.import(file, operation)
        }

        assertEquals(operation, error.operation)
        assertEquals("error=$error cause=${error.cause}", MediaImportFailureStage.PROVIDER_IMPORT, error.stage)
        assertNull(error.cause)
        assertEquals(1, fixture.resolver.calls.size)
    }

    private fun assertReturnedFilenameRejected(operation: MediaImportOperation, filename: String) = withGateway { fixture ->
        val file = fixture.mediaFile(filename)
        fixture.resolver.onInsert = { Uri.parse("file:///AnkiDroid/collection.media/not-supported.txt") }

        val error = org.junit.Assert.assertThrows(MediaImportException::class.java) {
            fixture.import(file, operation)
        }

        assertEquals(operation, error.operation)
        assertEquals(MediaImportFailureStage.RETURNED_MEDIA_VALIDATION, error.stage)
    }

    private fun assertSecurityFailurePreservesCause(operation: MediaImportOperation, filename: String) = withGateway { fixture ->
        val file = fixture.mediaFile(filename)
        val revoked = SecurityException("permission revoked")
        fixture.resolver.insertFailure = revoked

        val error = org.junit.Assert.assertThrows(MediaImportException::class.java) {
            fixture.import(file, operation)
        }

        assertEquals(operation, error.operation)
        assertEquals("error=$error cause=${error.cause}", MediaImportFailureStage.PROVIDER_IMPORT, error.stage)
        assertSame(revoked, error.cause)
    }

    @Test fun sentenceAudioInsertUsesProviderContractAndReturnsSafeFilename() =
        assertSuccess(MediaImportOperation.SENTENCE_AUDIO, "sentence.mp3")

    @Test fun wordAudioInsertUsesProviderContractAndReturnsSafeFilename() =
        assertSuccess(MediaImportOperation.WORD_AUDIO, "word.ogg")

    @Test fun imageInsertUsesProviderContractAndReturnsSafeFilename() =
        assertSuccess(MediaImportOperation.IMAGE, "image.webp")

    @Test fun sentenceAudioNullProviderResultIsProviderImportFailure() =
        assertProviderFailure(MediaImportOperation.SENTENCE_AUDIO, "sentence.mp3")

    @Test fun wordAudioNullProviderResultIsProviderImportFailure() =
        assertProviderFailure(MediaImportOperation.WORD_AUDIO, "word.ogg")

    @Test fun imageNullProviderResultIsProviderImportFailure() =
        assertProviderFailure(MediaImportOperation.IMAGE, "image.webp")

    @Test fun sentenceAudioRejectsUnsupportedReturnedFilename() =
        assertReturnedFilenameRejected(MediaImportOperation.SENTENCE_AUDIO, "sentence.mp3")

    @Test fun wordAudioRejectsUnsupportedReturnedFilename() =
        assertReturnedFilenameRejected(MediaImportOperation.WORD_AUDIO, "word.ogg")

    @Test fun imageRejectsUnsupportedReturnedFilename() =
        assertReturnedFilenameRejected(MediaImportOperation.IMAGE, "image.webp")

    @Test fun sentenceAudioSecurityExceptionIsProviderFailureWithCause() =
        assertSecurityFailurePreservesCause(MediaImportOperation.SENTENCE_AUDIO, "sentence.mp3")

    @Test fun wordAudioSecurityExceptionIsProviderFailureWithCause() =
        assertSecurityFailurePreservesCause(MediaImportOperation.WORD_AUDIO, "word.ogg")

    @Test fun imageSecurityExceptionIsProviderFailureWithCause() =
        assertSecurityFailurePreservesCause(MediaImportOperation.IMAGE, "image.webp")

    @Test
    fun wordAudioProviderFailureLeavesFrozenSubmissionAtWordStageAndKeepsSentenceReceipt() = withGateway { fixture ->
        val sentenceFile = fixture.mediaFile("sentence.mp3")
        val wordFile = fixture.mediaFile("word.ogg")
        val sentenceIdentity = MediaIdentity.fromFile(sentenceFile)
        val wordIdentity = MediaIdentity.fromFile(wordFile)
        val fields = AnkiFieldContract.requiredFields
        var frozen = FrozenSubmission(
            revision = 7L,
            modelId = 100L,
            deckId = 200L,
            modelFields = fields,
            orderedFields = fields.map { "" },
            sentence = "猫が走る",
            lemma = "猫",
            vocabularyReading = "ねこ",
            sentenceReading = "ねこ",
            definition = "cat",
            translation = "The cat runs.",
            notes = "",
            tags = emptySet(),
            pitchAccentText = "0",
            targetStart = 0,
            targetEnd = 1,
            media = null,
            importedFilename = null,
            noteId = null,
            sentenceAudio = FrozenAudioItem(sentenceIdentity),
            wordAudio = FrozenAudioItem(wordIdentity),
        )
        val storage = MemoryAppPreferenceStorage()
        assertTrue(
            storage.persistBlocking(
                PreferenceFile.DRAFT,
                PreferenceChanges().putFrozenSubmission(frozen, SubmissionPhase.MEDIA_IMPORTING),
            ),
        )
        var insertCount = 0
        fixture.resolver.onInsert = { call ->
            insertCount += 1
            if (insertCount == 1) Uri.parse("file:///AnkiDroid/collection.media/${call.values.getAsString("preferred_name")}")
            else null
        }

        val error = org.junit.Assert.assertThrows(MediaImportException::class.java) {
            importSendMedia(
                sentenceAudio = MediaImportRequest(sentenceIdentity),
                wordAudio = MediaImportRequest(wordIdentity),
                image = null,
                markImporting = { operation, source ->
                    storage.persistBlocking(
                        PreferenceFile.DRAFT,
                        PreferenceChanges()
                            .putFrozenSubmission(frozen, SubmissionPhase.MEDIA_IMPORTING)
                            .putString(SubmissionRecoveryKeys.MEDIA_OPERATION, operation.name)
                            .putString(SubmissionRecoveryKeys.MEDIA_SOURCE_PATH, source.path),
                    )
                },
                import = { operation, _ ->
                    val file = if (operation == MediaImportOperation.SENTENCE_AUDIO) sentenceFile else wordFile
                    fixture.import(file, operation)
                },
                persistReceipt = { receipt ->
                    val updated = applyImportedReceipt(frozen, receipt)
                    val saved = storage.persistBlocking(
                        PreferenceFile.DRAFT,
                        PreferenceChanges().putFrozenSubmission(updated, SubmissionPhase.MEDIA_IMPORTED)
                            .apply { putMediaReceipt(receipt) },
                    )
                    if (saved) frozen = updated
                    saved
                },
            )
        }

        assertEquals(MediaImportOperation.WORD_AUDIO, error.operation)
        assertEquals("error=$error cause=${error.cause}", MediaImportFailureStage.PROVIDER_IMPORT, error.stage)
        assertEquals(2, fixture.resolver.calls.size)
        val persisted = storage.durable(PreferenceFile.DRAFT)
        assertEquals(SubmissionPhase.MEDIA_IMPORTING.persisted, persisted.getString(SubmissionRecoveryKeys.STATUS, null))
        assertEquals(MediaImportOperation.WORD_AUDIO.name, persisted.getString(SubmissionRecoveryKeys.MEDIA_OPERATION, null))
        val restored = requireNotNull(readFrozenSubmission(persisted))
        assertEquals(sentenceIdentity, restored.sentenceAudio.identity)
        assertEquals(sentenceFile.name, restored.sentenceAudio.importedFilename)
        assertNull(restored.wordAudio.importedFilename)
        assertEquals(sentenceFile.name, persisted.getString(SENTENCE_AUDIO_RECEIPT_FILENAME, null))
    }
}
