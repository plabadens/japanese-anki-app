package dev.pierre.japaneseanki

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import dev.pierre.japaneseanki.send.SendCoordinator
import dev.pierre.japaneseanki.send.SendOutcome
import dev.pierre.japaneseanki.send.SendRequest
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@Config(sdk = [37])
class AnkiDroidGatewayMediaImportTest {
    private class FakeAnkiProvider : ContentProvider() {
        val importedNames = mutableListOf<String>()
        val mediaOperations = mutableListOf<Pair<String, String>>()
        var noteInsertCount = 0
        var insertedFields: String? = null
        private var cardDeckId = 300L

        override fun onCreate(): Boolean = true

        override fun insert(uri: Uri, values: ContentValues?): Uri? = when (uri.lastPathSegment) {
            "media" -> {
                val source = requireNotNull(values?.getAsString("file_uri"))
                val preferredName = requireNotNull(values.getAsString("preferred_name"))
                mediaOperations += source to preferredName
                // Android's MIME mapping lets AnkiDroid normalize audio/ogg to .oga.
                val importedName = if (preferredName.endsWith(".ogg")) {
                    preferredName.removeSuffix(".ogg") + ".oga"
                } else preferredName
                importedNames += importedName
                Uri.parse("file:///collection.media/$importedName")
            }
            "notes" -> {
                noteInsertCount++
                insertedFields = values?.getAsString("flds")
                Uri.parse("content://com.ichi2.anki.flashcards/notes/501")
            }
            else -> error("Unexpected provider insert: $uri")
        }

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor = when {
            uri.path?.endsWith("notes_v2") == true -> MatrixCursor(arrayOf("_id", "flds"))
            uri.path?.endsWith("/cards") == true -> MatrixCursor(arrayOf("ord", "deck_id")).apply {
                addRow(arrayOf(0, cardDeckId))
            }
            else -> error("Unexpected provider query: $uri")
        }

        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
            if (uri.path?.contains("/cards/") != true) error("Unexpected provider update: $uri")
            cardDeckId = requireNotNull(values?.getAsLong("deck_id"))
            return 1
        }

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
            error("Unexpected provider delete: $uri")

        override fun getType(uri: Uri): String = "vnd.android.cursor.item/test"
    }

    @Test
    fun coordinatorPersistsAllThreeReceiptsAndInsertsTheNote() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<Context>()
        val suffix = UUID.randomUUID().toString()
        val draftDirectory = File(context.filesDir, "draft").apply { mkdirs() }
        val existingNames = draftDirectory.list()?.toSet().orEmpty()
        val provider = FakeAnkiProvider()
        ShadowContentResolver.registerProviderInternal("com.ichi2.anki.flashcards", provider)
        val storage = DataStorePreferenceStorage(context)
        val persistence = object : DraftAudioPersistence {
            private val values = mutableMapOf<String, String>()
            override fun read(key: String): String? = values[key]
            override fun write(changes: Map<String, String?>): Boolean {
                changes.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
                return true
            }
        }
        val audio = DraftAudioController(
            store = DraftAudioFileStore(
                draftDirectory,
                File(context.cacheDir, "audio-staging-$suffix"),
                AudioFileOperations.system(),
            ),
            persistence = persistence,
            synthesize = { input, output ->
                when (input.expectedFormat) {
                    AudioFormat.WAV -> AudioFixtures.wav(output)
                    AudioFormat.OGG -> AudioFixtures.ogg(output)
                    AudioFormat.MP3 -> AudioFixtures.mp3(output)
                }
                Unit
            },
        )
        val gateway = AnkiDroidGateway(context.contentResolver, context)
        val coordinator = SendCoordinator(
            gateway = gateway,
            storage = storage,
            audio = audio,
            ioDispatcher = Dispatchers.IO,
        )
        fun prepareAudio(item: AudioItem, format: AudioFormat, identity: String, text: String) {
            audio.setInput(
                DraftAudioInput(
                    item = item,
                    identity = identity,
                    text = text,
                    revision = 0,
                    cacheKey = AudioCacheKey("provider-send-test", text, "test-voice", 1f),
                    expectedFormat = format,
                ),
            )
            audio.runAttemptForTest(requireNotNull(audio.confirm(item)))
        }
        val image = File(draftDirectory, "image-$suffix.webp").apply {
            writeBytes(byteArrayOf(0x52, 0x49, 0x46, 0x46))
        }
        val draft = ManualNoteDraft(
            sentence = "猫が走る",
            target = TargetSpan(0, 1),
            sentenceReading = "ねこ",
            vocabulary = "猫",
            dictionaryEntryResolved = true,
            vocabularyReading = "ねこ",
            definition = "cat",
            translation = "The cat runs.",
            pitch = PitchAccent.create("ねこ", 0),
        )
        val request = SendRequest(
            draft = draft,
            fieldsByName = requireNotNull(draft.fieldsByName()),
            model = AnkiNoteType(100L, "Basic", AnkiFieldContract.requiredFields + AnkiFieldContract.optionalFields),
            deck = AnkiDeck(200L, "Default"),
            tags = setOf("provider-send-test"),
            pitchAccentText = "0",
            imageAttached = true,
            imageProcessing = false,
            processedImagePath = image.path,
            processedImageIdentity = liveMediaIdentity(image),
            imageReceiptFilename = null,
        )

        try {
            runSuspendTest {
                storage.load()
                prepareAudio(AudioItem.SENTENCE, AudioFormat.MP3, "sentence-$suffix", "猫が走る")
                prepareAudio(AudioItem.VOCABULARY, AudioFormat.OGG, "word-$suffix", "猫")
                assertEquals(SendOutcome.Created(501L), coordinator.submit(request))
            }
            assertEquals(3, provider.importedNames.size)
            assertEquals(listOf("mp3", "oga", "webp"), provider.importedNames.map { it.substringAfterLast('.') })
            assertEquals(listOf("mp3", "ogg", "webp"), provider.mediaOperations.map { it.second.substringAfterLast('.') })
            assertEquals(3, provider.mediaOperations.size)
            assertEquals(1, provider.noteInsertCount)
            val insertedFields = requireNotNull(provider.insertedFields)
            assertTrue(insertedFields.contains("[sound:${provider.importedNames[0]}]"))
            assertTrue(insertedFields.contains("[sound:${provider.importedNames[1]}]"))
            assertTrue(insertedFields.contains("<img src=\"${provider.importedNames[2]}\">"))
            assertEquals("done", storage.draft.value.getString(SubmissionRecoveryKeys.STATUS, null))
            assertEquals(501L, readFrozenSubmission(storage.draft.value)?.noteId)
            storage.close()
        } finally {
            audio.dispose()
            ShadowContentResolver.reset()
            draftDirectory.listFiles().orEmpty().filter { it.name !in existingNames }.forEach(File::delete)
            File(context.cacheDir, "audio-staging-$suffix").deleteRecursively()
        }
    }
}
