package dev.pierre.japaneseanki

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowContentResolver

/**
 * Independent provider fake for the public v2.24.0 contract, not gateway implementation constants.
 * Source: Anki-Android ebcf8e0e34921628b9b8a496c66ffd4adbb3705f:
 * api/.../FlashCardsContract.kt and AnkiDroid/.../provider/CardContentProvider.kt.
 * See ../ANKIDROID_CONTRACT.md for the source links and validation boundary.
 */
@RunWith(AndroidJUnit4::class)
class AnkiDroidContractTest {
    private val provider = ContractProvider()
    private lateinit var gateway: AnkiDroidGateway
    private val model = AnkiNoteType(42, "Sentence", listOf("Extra", "VocabKanji", "SentKanji", "Notes"))

    @Before
    fun registerProvider() {
        ShadowContentResolver.registerProviderInternal("com.ichi2.anki.flashcards", provider)
        gateway = AnkiDroidGateway(ApplicationProvider.getApplicationContext<Context>().contentResolver)
    }

    @Test
    fun discoversOrderedFieldsAndExcludesFilteredDecks() {
        assertEquals(listOf(model), gateway.noteTypes())
        assertEquals(listOf(AnkiDeck(200, "Japanese")), gateway.decks())
    }

    @Test
    fun insertsOnlyNoteColumnsPreservingFieldOrderAndTrailingEmptyField() {
        val fields = listOf("extra", "猫", "猫が走る", "")
        assertEquals(501L, gateway.insertNote(model, fields, linkedSetOf("日本語", "study")))
        assertEquals(1, provider.notes.size)
        assertEquals(fields, provider.notes.single().fields)
        assertEquals(setOf("日本語", "study"), provider.notes.single().tags)
        assertTrue("Insertion uses the model default deck, not an invented note column",
            provider.cards.all { it.deckId == 300L })
    }

    @Test
    fun movesEveryGeneratedCardByTemplateOrdinalNotCardIdAndVerifiesTheResult() {
        val note = gateway.insertNote(model, listOf("", "猫", "猫が走る", ""), emptySet())
        assertTrue(gateway.ensureNoteInDeck(note, 200))
        assertEquals(listOf("/notes/501/cards/0", "/notes/501/cards/3"), provider.moves)
        assertEquals(listOf(AnkiCardLocation(0, 200), AnkiCardLocation(3, 200)), gateway.noteCards(note))
        assertEquals(1, provider.notes.size)
        assertTrue("The moved deck must be re-queried", provider.cardQueries >= 3)
    }

    @Test
    fun leavesCardsAlreadyInTheChosenDeckAlone() {
        gateway.insertNote(model, listOf("", "猫", "猫が走る", ""), emptySet())
        provider.cards[0].deckId = 200
        assertTrue(gateway.ensureNoteInDeck(501, 200))
        assertEquals(listOf("/notes/501/cards/3"), provider.moves)
    }

    @Test
    fun duplicateScanUsesSqlModelFilterAndThenChecksTheCardsDecks() {
        val fields = listOf("", "猫", "猫が走る", "")
        gateway.insertNote(model, fields, emptySet())
        val values = model.fields.zip(fields).toMap()
        assertEquals(null, gateway.hasDuplicate(model, AnkiDeck(200, "Japanese"), values))
        assertEquals(501L, gateway.hasMatchingNoteInAnyDeck(model, values))
        gateway.ensureNoteInDeck(501, 200)
        assertEquals(501L, gateway.hasDuplicate(model, AnkiDeck(200, "Japanese"), values))
        assertEquals(null, gateway.hasMatchingNoteInAnyDeck(model.copy(id = 99), values))
    }

    @Test
    fun absentCardsAndUnsuccessfulMovesNeverVerifyTheDestination() {
        gateway.insertNote(model, listOf("", "猫", "猫が走る", ""), emptySet())
        provider.moveCount = 0
        assertThrows(IllegalArgumentException::class.java) { gateway.ensureNoteInDeck(501, 200) }
        provider.cards.clear()
        assertFalse(gateway.ensureNoteInDeck(501, 200))
        assertEquals("An unverified destination must not cause another insert", 1, provider.notes.size)
    }

    @Test
    fun permissionRevocationAndUncertainProviderWritesAreNotSuccess() {
        provider.denyAccess = true
        assertThrows(IllegalStateException::class.java) { gateway.noteTypes() }
        assertThrows(IllegalStateException::class.java) {
            gateway.insertNote(model, listOf("", "猫", "猫が走る", ""), emptySet())
        }
        assertTrue(provider.notes.isEmpty())
        provider.denyAccess = false
        gateway.insertNote(model, listOf("", "猫", "猫が走る", ""), emptySet())
        provider.failMove = true
        assertThrows(AnkiProviderUncertainException::class.java) { gateway.ensureNoteInDeck(501, 200) }
        assertEquals(1, provider.notes.size)
    }

    private class ContractProvider : ContentProvider() {
        data class Note(val id: Long, val modelId: Long, val fields: List<String>, val tags: Set<String>)
        data class Card(val id: Long, val ordinal: Int, var deckId: Long)
        val notes = mutableListOf<Note>()
        val cards = mutableListOf<Card>()
        val moves = mutableListOf<String>()
        var cardQueries = 0
        var moveCount = 1
        var denyAccess = false
        var failMove = false

        override fun onCreate() = true
        override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.com.ichi2.anki.note"
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
            error("Deletion is not part of a safe send/recovery: $uri")

        private fun checkAccess(uri: Uri) {
            check(uri.authority == "com.ichi2.anki.flashcards")
            if (denyAccess) throw SecurityException("AnkiDroid API access denied")
        }

        override fun insert(uri: Uri, values: ContentValues?): Uri {
            checkAccess(uri)
            check(uri.path == "/notes" && uri.query == null)
            val note = requireNotNull(values)
            check(note.keySet() == setOf("mid", "flds", "tags"))
            check(note.getAsLong("mid") == 42L)
            val fields = note.getAsString("flds").split('\u001f')
            require(fields.size == 4)
            val id = 501L + notes.size
            notes += Note(id, 42, fields, note.getAsString("tags").split(' ').filter(String::isNotBlank).toSet())
            // Non-contiguous ordinals and unrelated card IDs catch URI/id confusion.
            cards += Card(9001, 0, 300)
            cards += Card(9002, 3, 300)
            return Uri.parse("content://com.ichi2.anki.flashcards/notes/$id")
        }

        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
            checkAccess(uri)
            check(sortOrder == null)
            val columns = requireNotNull(projection)
            val rows: List<Map<String, Any>> = when (uri.path) {
                "/models" -> listOf(mapOf("_id" to 42L, "name" to "Sentence", "field_names" to "Extra\u001fVocabKanji\u001fSentKanji\u001fNotes"))
                "/decks" -> listOf(
                    mapOf("deck_id" to 200L, "deck_name" to "Japanese", "deck_dyn" to 0),
                    mapOf("deck_id" to 900L, "deck_name" to "Filtered", "deck_dyn" to 1),
                )
                "/notes_v2" -> {
                    check(selection == "mid=?")
                    val modelId = requireNotNull(selectionArgs).single().toLong()
                    notes.filter { it.modelId == modelId }.map { mapOf("_id" to it.id, "flds" to it.fields.joinToString("\u001f")) }
                }
                "/notes/501/cards" -> {
                    check(selection == null && selectionArgs == null)
                    cardQueries++
                    cards.map { mapOf("_id" to it.id, "ord" to it.ordinal, "deck_id" to it.deckId) }
                }
                else -> error("Unsupported contract URI: $uri")
            }
            return MatrixCursor(columns).apply {
                rows.forEach { row -> addRow(columns.map { requireNotNull(row[it]) { "Unknown contract column $it" } }) }
            }
        }

        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
            checkAccess(uri)
            check(selection == null && selectionArgs == null)
            val segments = uri.pathSegments
            check(segments.take(3) == listOf("notes", "501", "cards") && segments.size == 4)
            val change = requireNotNull(values)
            check(change.keySet() == setOf("deck_id"))
            if (failMove) throw IllegalStateException("Provider disconnected during move")
            val card = cards.single { it.ordinal == segments[3].toInt() }
            moves += requireNotNull(uri.path)
            if (moveCount == 1) card.deckId = change.getAsLong("deck_id")
            return moveCount
        }
    }
}
