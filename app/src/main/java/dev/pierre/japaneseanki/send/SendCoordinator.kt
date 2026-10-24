package dev.pierre.japaneseanki.send

import android.content.SharedPreferences
import dev.pierre.japaneseanki.AnkiCardLocation
import dev.pierre.japaneseanki.AnkiDeck
import dev.pierre.japaneseanki.AnkiFieldContract
import dev.pierre.japaneseanki.AnkiMediaImporter
import dev.pierre.japaneseanki.AnkiNoteType
import dev.pierre.japaneseanki.AnkiProviderDefiniteInsertException
import dev.pierre.japaneseanki.AudioItem
import dev.pierre.japaneseanki.DraftAudioController
import dev.pierre.japaneseanki.DraftAudioFreeze
import dev.pierre.japaneseanki.DraftAudioFrozenSnapshot
import dev.pierre.japaneseanki.FrozenAudioItem
import dev.pierre.japaneseanki.FrozenSubmission
import dev.pierre.japaneseanki.ImportedMediaReceipt
import dev.pierre.japaneseanki.MediaIdentity
import dev.pierre.japaneseanki.MediaImportException
import dev.pierre.japaneseanki.MediaImportFailureStage
import dev.pierre.japaneseanki.MediaImportOperation
import dev.pierre.japaneseanki.MediaImportRequest
import dev.pierre.japaneseanki.ManualNoteDraft
import dev.pierre.japaneseanki.NoteInsertedRecoveryDecision
import dev.pierre.japaneseanki.SubmissionPhase
import dev.pierre.japaneseanki.SubmissionRecoveryKeys
import dev.pierre.japaneseanki.IMAGE_RECEIPT_FILENAME
import dev.pierre.japaneseanki.IMAGE_RECEIPT_KEYS
import dev.pierre.japaneseanki.IMAGE_RECEIPT_MODIFIED
import dev.pierre.japaneseanki.IMAGE_RECEIPT_PATH
import dev.pierre.japaneseanki.IMAGE_RECEIPT_SIZE
import dev.pierre.japaneseanki.SENTENCE_AUDIO_RECEIPT_FILENAME
import dev.pierre.japaneseanki.SENTENCE_AUDIO_RECEIPT_KEYS
import dev.pierre.japaneseanki.SENTENCE_AUDIO_RECEIPT_MODIFIED
import dev.pierre.japaneseanki.SENTENCE_AUDIO_RECEIPT_PATH
import dev.pierre.japaneseanki.SENTENCE_AUDIO_RECEIPT_SIZE
import dev.pierre.japaneseanki.WORD_AUDIO_RECEIPT_FILENAME
import dev.pierre.japaneseanki.WORD_AUDIO_RECEIPT_KEYS
import dev.pierre.japaneseanki.WORD_AUDIO_RECEIPT_MODIFIED
import dev.pierre.japaneseanki.WORD_AUDIO_RECEIPT_PATH
import dev.pierre.japaneseanki.WORD_AUDIO_RECEIPT_SIZE
import dev.pierre.japaneseanki.applyImportedReceipt
import dev.pierre.japaneseanki.clearFrozenSubmission
import dev.pierre.japaneseanki.frozenImagePermitsImport
import dev.pierre.japaneseanki.importSendMedia
import dev.pierre.japaneseanki.importedReceiptFromPreferences
import dev.pierre.japaneseanki.liveMediaIdentity
import dev.pierre.japaneseanki.mediaImportRequest
import dev.pierre.japaneseanki.noteInsertedRecoveryDecision
import dev.pierre.japaneseanki.nextSubmissionRevision
import dev.pierre.japaneseanki.putFrozenSubmission
import dev.pierre.japaneseanki.putMediaReceipt
import dev.pierre.japaneseanki.readFrozenSubmission
import dev.pierre.japaneseanki.receiptIdentity
import dev.pierre.japaneseanki.ui.CardAction
import dev.pierre.japaneseanki.ui.SendPhase
import dev.pierre.japaneseanki.ui.SendState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** The small AnkiDroid surface used by submission and recovery. */
internal interface AnkiNoteSink {
    fun hasDuplicate(model: AnkiNoteType, deck: AnkiDeck, fieldsByName: Map<String, String>): Long?
    fun hasMatchingNoteInAnyDeck(model: AnkiNoteType, fieldsByName: Map<String, String>): Long?
    fun insertNote(model: AnkiNoteType, orderedFields: List<String>, tags: Set<String>): Long
    fun ensureNoteInDeck(noteId: Long, deckId: Long): Boolean
    fun noteCards(noteId: Long): List<AnkiCardLocation>
    fun importAudio(file: File, operation: MediaImportOperation): String
    fun importProcessedImage(file: File): String
}

/** Side effects that belong to the completed draft rather than the Anki provider. */
internal interface FrozenSubmissionStore {
    fun persistLastSentTags(tags: Set<String>): Boolean
    fun clearDraft()
}

internal class SharedPreferencesFrozenSubmissionStore(
    private val preferences: SharedPreferences,
    private val settings: SharedPreferences,
    private val draftDirectory: File,
) : FrozenSubmissionStore {
    override fun persistLastSentTags(tags: Set<String>): Boolean =
        settings.edit().putString("last_note_tags", tags.joinToString(" ")).commit()

    override fun clearDraft() = clearPersistedDraft(preferences, draftDirectory)
}

internal data class SendRequest(
    val draft: ManualNoteDraft,
    val fieldsByName: Map<String, String>,
    val model: AnkiNoteType,
    val deck: AnkiDeck,
    val tags: Set<String>,
    val pitchAccentText: String,
    val imageAttached: Boolean,
    val imageProcessing: Boolean,
    val processedImagePath: String?,
    val processedImageIdentity: MediaIdentity?,
    val imageReceiptFilename: String?,
    val restoredFrozen: FrozenSubmission? = null,
)

internal enum class SendNotReadyReason { AUDIO_INCOMPLETE, AUDIO_DISPOSED, IMAGE_NOT_READY, INVALID_FIELDS }

internal enum class IndeterminateReason { OPERATION_FAILED, MISSING_NOTE_ID, NO_VERIFIABLE_CARDS, LOOKUP_FAILED }

internal sealed interface SendOutcome {
    data class Created(val noteId: Long) : SendOutcome
    data class Duplicate(val noteId: Long) : SendOutcome
    data class InsertFailed(val message: String) : SendOutcome
    data class MediaFailed(
        val operation: MediaImportOperation,
        val stage: MediaImportFailureStage,
        val message: String,
    ) : SendOutcome
    data class DestinationUnverified(val noteId: Long) : SendOutcome
    data class Indeterminate(
        val phase: SubmissionPhase?,
        val message: String,
        val reason: IndeterminateReason = IndeterminateReason.OPERATION_FAILED,
    ) : SendOutcome
    data class NotReady(val reason: SendNotReadyReason) : SendOutcome
    data object RetryAvailable : SendOutcome
    data object PendingRecovery : SendOutcome
}

/**
 * Runs the submission transaction outside composition. Preference keys, persisted phase names,
 * receipt writes, and provider operation order deliberately match the existing recovery contract.
 */
internal class SendCoordinator(
    private val gateway: AnkiNoteSink,
    private val preferences: SharedPreferences,
    private val audio: DraftAudioController,
    private val repository: FrozenSubmissionStore,
) {
    suspend fun submit(request: SendRequest, onState: (SendState) -> Unit): SendOutcome {
        val result = withContext(Dispatchers.IO) { submitBlocking(request) }
        result.frozen?.let { snapshot ->
            onState(SendState(phase = SendPhase.Submitting, frozen = snapshot))
        }
        return result.outcome
    }

    suspend fun recover(state: SendState): SendOutcome = withContext(Dispatchers.IO) {
        recoverBlocking(state)
    }

    private data class SubmissionRun(val outcome: SendOutcome, val frozen: FrozenSubmission?)

    private fun submitBlocking(request: SendRequest): SubmissionRun {
        val restored = request.restoredFrozen
        if (restored != null && (
                restored.modelId != request.model.id || restored.deckId != request.deck.id ||
                    restored.modelFields != request.model.fields
                )
        ) return SubmissionRun(SendOutcome.NotReady(SendNotReadyReason.INVALID_FIELDS), restored)

        val frozenAudio = if (restored == null) {
            when (val freeze = audio.freezeForSend()) {
                is DraftAudioFreeze.Frozen -> freeze.snapshot
                is DraftAudioFreeze.Incomplete -> return SubmissionRun(
                    SendOutcome.NotReady(SendNotReadyReason.AUDIO_INCOMPLETE),
                    null,
                )
                DraftAudioFreeze.Disposed -> return SubmissionRun(
                    SendOutcome.NotReady(SendNotReadyReason.AUDIO_DISPOSED),
                    null,
                )
            }
        } else null

        var frozen: FrozenSubmission? = restored
        var durableSnapshot: FrozenSubmission? = restored
        try {
            val fields = restored?.modelFields?.zip(restored.orderedFields)?.toMap() ?: request.fieldsByName
            val tagSet = restored?.tags ?: request.tags
            if (restored == null) {
                frozen = createInitialSnapshot(request, fields, tagSet)
                    ?: run {
                        audio.releaseFreeze(retainFiles = false)
                        return SubmissionRun(SendOutcome.NotReady(SendNotReadyReason.INVALID_FIELDS), null)
                    }
                durableSnapshot = frozen
            }
            val duplicateId = gateway.hasDuplicate(request.model, request.deck, fields)
            if (duplicateId != null) {
                preferences.edit().putString(SubmissionRecoveryKeys.STATUS, "blocked").commit()
                audio.releaseFreeze(retainFiles = false)
                if (restored != null) clearFrozenSubmission(preferences)
                return SubmissionRun(SendOutcome.Duplicate(duplicateId), frozen)
            }

            val startingSnapshot = restored ?: createFrozenSubmission(
                request,
                frozenAudio ?: error("Draft audio was not frozen."),
            )
            frozen = startingSnapshot
            durableSnapshot = startingSnapshot
            if (restored != null) {
                val localFile = restored.media?.let { File(it.path) }
                val localExists = localFile?.exists() == true
                check(
                    frozenImagePermitsImport(
                        importedFilename = restored.importedFilename,
                        frozenIdentity = restored.media,
                        receiptIdentity = receiptIdentity(preferences, IMAGE_RECEIPT_KEYS),
                        receiptFilename = preferences.getString(IMAGE_RECEIPT_FILENAME, null),
                        localFileExists = localExists,
                        localIdentity = if (localExists) liveMediaIdentity(localFile) else null,
                    ),
                ) { "Frozen image receipt is unavailable." }
            } else {
                check(preferences.edit()
                    .putLong(SubmissionRecoveryKeys.REVISION_SEQUENCE, startingSnapshot.revision)
                    .putFrozenSubmission(startingSnapshot, SubmissionPhase.MEDIA_IMPORTING)
                    .commit()) { "Could not save frozen submission for recovery." }
            }

            var snapshot = startingSnapshot
            fun persistReceipt(receipt: ImportedMediaReceipt): Boolean {
                val updated = try {
                    applyImportedReceipt(snapshot, receipt)
                } catch (_: Exception) {
                    return false
                }
                val saved = runCatching {
                    preferences.edit()
                        .putFrozenSubmission(updated, SubmissionPhase.MEDIA_IMPORTED)
                        .apply { putMediaReceipt(this, receipt) }
                        .remove(SubmissionRecoveryKeys.MEDIA_OPERATION)
                        .remove(SubmissionRecoveryKeys.MEDIA_SOURCE_PATH)
                        .remove(SubmissionRecoveryKeys.MEDIA_SOURCE_SIZE)
                        .remove(SubmissionRecoveryKeys.MEDIA_SOURCE_MODIFIED)
                        .commit()
                }.getOrDefault(false)
                if (saved) snapshot = updated
                return saved
            }

            val importedReceipts = importSendMedia(
                sentenceAudio = snapshot.sentenceAudio.takeUnless { it.omitted }?.let { item ->
                    val identity = item.identity ?: error("Sentence audio is missing its media identity.")
                    mediaImportRequest(
                        preferences,
                        SENTENCE_AUDIO_RECEIPT_KEYS,
                        MediaImportOperation.SENTENCE_AUDIO,
                        identity,
                        item.importedFilename,
                    )
                },
                wordAudio = snapshot.wordAudio.takeUnless { it.omitted }?.let { item ->
                    val identity = item.identity ?: error("Word audio is missing its media identity.")
                    mediaImportRequest(
                        preferences,
                        WORD_AUDIO_RECEIPT_KEYS,
                        MediaImportOperation.WORD_AUDIO,
                        identity,
                        item.importedFilename,
                    )
                },
                image = snapshot.media?.let { identity ->
                    mediaImportRequest(
                        preferences,
                        IMAGE_RECEIPT_KEYS,
                        MediaImportOperation.IMAGE,
                        identity,
                        snapshot.importedFilename,
                    )
                },
                markImporting = { operation, source ->
                    preferences.edit()
                        .putFrozenSubmission(snapshot, SubmissionPhase.MEDIA_IMPORTING)
                        .putString(SubmissionRecoveryKeys.MEDIA_OPERATION, operation.name)
                        .putString(SubmissionRecoveryKeys.MEDIA_SOURCE_PATH, source.path)
                        .putLong(SubmissionRecoveryKeys.MEDIA_SOURCE_SIZE, source.size)
                        .putLong(SubmissionRecoveryKeys.MEDIA_SOURCE_MODIFIED, source.modified)
                        .commit()
                },
                import = { operation, identity ->
                    val file = File(identity.path)
                    check(liveMediaIdentity(file) == identity) { "Frozen media changed before import." }
                    when (operation) {
                        MediaImportOperation.SENTENCE_AUDIO,
                        MediaImportOperation.WORD_AUDIO -> gateway.importAudio(file, operation)
                        MediaImportOperation.IMAGE -> gateway.importProcessedImage(file)
                        MediaImportOperation.AUDIO -> error("Generic audio is not a Send operation.")
                    }
                },
                persistReceipt = ::persistReceipt,
            )
            importedReceipts.forEach { receipt ->
                check(persistReceipt(receipt)) { "Could not save the ${receipt.operation.label} receipt." }
            }
            frozen = snapshot
            durableSnapshot = snapshot
            check(preferences.edit()
                .putFrozenSubmission(snapshot, SubmissionPhase.MEDIA_IMPORTED)
                .remove(SubmissionRecoveryKeys.MEDIA_OPERATION)
                .remove(SubmissionRecoveryKeys.MEDIA_SOURCE_PATH)
                .remove(SubmissionRecoveryKeys.MEDIA_SOURCE_SIZE)
                .remove(SubmissionRecoveryKeys.MEDIA_SOURCE_MODIFIED)
                .commit()) { "Could not save completed media recovery state." }

            val frozenFields = snapshot.modelFields.zip(snapshot.orderedFields).toMap()
            val frozenMapping = AnkiFieldContract.mapToModelOrder(request.model.fields, frozenFields)
            check(frozenMapping.isValid) { frozenMapping.errors.joinToString() }
            check(preferences.edit().putFrozenSubmission(snapshot, SubmissionPhase.NOTE_INSERTING).commit()) {
                "Could not save frozen submission before note insertion."
            }
            // No insert retry is safe after this point until recovery reconciles NOTE_INSERTING.
            val insertedNoteId = gateway.insertNote(request.model, frozenMapping.values, tagSet)
            val insertedSnapshot = snapshot.copy(noteId = insertedNoteId)
            check(preferences.edit().putFrozenSubmission(
                insertedSnapshot,
                SubmissionPhase.NOTE_INSERTED,
            ).commit()) { "Could not save the inserted note ID." }
            frozen = insertedSnapshot
            durableSnapshot = insertedSnapshot

            if (!gateway.ensureNoteInDeck(insertedNoteId, request.deck.id)) {
                audio.releaseFreeze(retainFiles = true)
                return SubmissionRun(SendOutcome.DestinationUnverified(insertedNoteId), insertedSnapshot)
            }
            check(preferences.edit().putFrozenSubmission(
                insertedSnapshot,
                SubmissionPhase.DESTINATION_VERIFIED,
            ).commit()) { "Could not save the verified destination." }
            check(repository.persistLastSentTags(insertedSnapshot.tags)) { "Could not save tags from the verified note." }
            check(preferences.edit().putString(SubmissionRecoveryKeys.STATUS, "done").commit())
            repository.clearDraft()
            audio.releaseFreeze(retainFiles = false)
            return SubmissionRun(SendOutcome.Created(insertedNoteId), insertedSnapshot)
        } catch (cancelled: java.util.concurrent.CancellationException) {
            val phase = SubmissionPhase.fromPersisted(preferences.getString(SubmissionRecoveryKeys.STATUS, null))
            audio.releaseFreeze(retainFiles = phase != null)
            throw cancelled
        } catch (error: AnkiProviderDefiniteInsertException) {
            audio.releaseFreeze(retainFiles = false)
            clearFrozenSubmission(preferences)
            return SubmissionRun(SendOutcome.InsertFailed(error.message.orEmpty()), frozen)
        } catch (error: MediaImportException) {
            audio.releaseFreeze(retainFiles = true)
            durableSnapshot = readFrozenSubmission(preferences) ?: durableSnapshot
            return SubmissionRun(
                SendOutcome.MediaFailed(error.operation, error.stage, error.message.orEmpty()),
                durableSnapshot,
            )
        } catch (error: Exception) {
            val phase = SubmissionPhase.fromPersisted(preferences.getString(SubmissionRecoveryKeys.STATUS, null))
            audio.releaseFreeze(retainFiles = phase != null)
            durableSnapshot = readFrozenSubmission(preferences) ?: durableSnapshot
            return if (phase != null) {
                SubmissionRun(SendOutcome.Indeterminate(phase, error.message.orEmpty()), durableSnapshot)
            } else {
                val reason = if (request.imageAttached) SendNotReadyReason.IMAGE_NOT_READY
                else SendNotReadyReason.INVALID_FIELDS
                SubmissionRun(SendOutcome.NotReady(reason), durableSnapshot)
            }
        }
    }

    private fun createFrozenSubmission(
        request: SendRequest,
        audioSnapshot: DraftAudioFrozenSnapshot,
    ): FrozenSubmission {
        val includeImage = request.imageAttached && !request.imageProcessing
        val processed = if (includeImage) request.processedImagePath?.let(::File)?.takeIf(File::isFile) else null
        if (includeImage && processed == null) throw ImageNotReadyException()
        val imageIdentity = liveMediaIdentity(processed)
        val importedImage = if (includeImage) {
            importedReceiptFromPreferences(
                preferences,
                IMAGE_RECEIPT_KEYS,
                imageIdentity,
                AnkiMediaImporter::sanitizeFilename,
            )
        } else null
        val sentenceEntry = audioSnapshot.files.getValue(AudioItem.SENTENCE)
        val wordEntry = audioSnapshot.files.getValue(AudioItem.VOCABULARY)
        val sentenceIdentity = MediaIdentity(sentenceEntry.path, sentenceEntry.size, sentenceEntry.modified)
        val wordIdentity = MediaIdentity(wordEntry.path, wordEntry.size, wordEntry.modified)
        val sentenceImported = importedReceiptFromPreferences(
            preferences,
            SENTENCE_AUDIO_RECEIPT_KEYS,
            sentenceIdentity,
            AnkiMediaImporter::sanitizeAudioFilename,
        )
        val wordImported = importedReceiptFromPreferences(
            preferences,
            WORD_AUDIO_RECEIPT_KEYS,
            wordIdentity,
            AnkiMediaImporter::sanitizeAudioFilename,
        )
        val baseFields = request.draft.copy(
            imageFilename = importedImage,
            sentenceAudioFilename = sentenceImported,
            vocabularyAudioFilename = wordImported,
        ).fieldsByName() ?: error("Note changed before Send was frozen.")
        val baseMapping = AnkiFieldContract.mapToModelOrder(request.model.fields, baseFields)
        check(baseMapping.isValid) { baseMapping.errors.joinToString() }
        return FrozenSubmission(
            revision = nextSubmissionRevision(
                preferences.getLong(SubmissionRecoveryKeys.REVISION_SEQUENCE, 0L),
                preferences.getLong(SubmissionRecoveryKeys.REVISION, 0L),
            ),
            modelId = request.model.id,
            deckId = request.deck.id,
            modelFields = request.model.fields,
            orderedFields = baseMapping.values,
            sentence = request.draft.sentence,
            lemma = request.draft.vocabulary,
            vocabularyReading = request.draft.vocabularyReading,
            sentenceReading = request.draft.sentenceReading,
            definition = request.draft.definition,
            translation = request.draft.translation,
            notes = request.draft.notes,
            tags = request.tags,
            pitchAccentText = request.pitchAccentText,
            targetStart = request.draft.target?.start ?: -1,
            targetEnd = request.draft.target?.end ?: -1,
            media = imageIdentity,
            importedFilename = importedImage,
            noteId = null,
            sentenceAudio = FrozenAudioItem(sentenceIdentity, sentenceImported),
            wordAudio = FrozenAudioItem(wordIdentity, wordImported),
        )
    }

    private fun createInitialSnapshot(
        request: SendRequest,
        fieldsByName: Map<String, String>,
        tags: Set<String>,
    ): FrozenSubmission? {
        val mapping = AnkiFieldContract.mapToModelOrder(request.model.fields, fieldsByName)
        if (!mapping.isValid) return null
        return FrozenSubmission(
            revision = nextSubmissionRevision(
                preferences.getLong(SubmissionRecoveryKeys.REVISION_SEQUENCE, 0L),
                preferences.getLong(SubmissionRecoveryKeys.REVISION, 0L),
            ),
            modelId = request.model.id,
            deckId = request.deck.id,
            modelFields = request.model.fields,
            orderedFields = mapping.values,
            sentence = request.draft.sentence,
            lemma = request.draft.vocabulary,
            vocabularyReading = request.draft.vocabularyReading,
            sentenceReading = request.draft.sentenceReading,
            definition = request.draft.definition,
            translation = request.draft.translation,
            notes = request.draft.notes,
            tags = tags,
            pitchAccentText = request.pitchAccentText,
            targetStart = request.draft.target?.start ?: -1,
            targetEnd = request.draft.target?.end ?: -1,
            media = request.processedImageIdentity,
            importedFilename = request.imageReceiptFilename,
            noteId = null,
        )
    }

    private fun recoverBlocking(state: SendState): SendOutcome {
        val phase = SubmissionPhase.fromPersisted(preferences.getString(SubmissionRecoveryKeys.STATUS, null))
            ?: return SendOutcome.Indeterminate(null, "No pending submission phase.")
        if (phase == SubmissionPhase.MEDIA_IMPORTING || phase == SubmissionPhase.MEDIA_IMPORTED) {
            return SendOutcome.PendingRecovery
        }
        val snapshot = state.frozen ?: readFrozenSubmission(preferences)
            ?: return SendOutcome.Indeterminate(phase, "Frozen submission is unavailable.")
        return try {
            if (phase == SubmissionPhase.NOTE_INSERTED || phase == SubmissionPhase.DESTINATION_VERIFIED) {
                val noteId = snapshot.noteId
                    ?: return SendOutcome.Indeterminate(phase, "Saved note ID is missing.", IndeterminateReason.MISSING_NOTE_ID)
                when (noteInsertedRecoveryDecision(noteId, gateway.noteCards(noteId), snapshot.deckId)) {
                    NoteInsertedRecoveryDecision.COMPLETE -> {
                        check(preferences.edit().putString(
                            SubmissionRecoveryKeys.STATUS,
                            SubmissionPhase.DESTINATION_VERIFIED.persisted,
                        ).commit())
                        check(repository.persistLastSentTags(snapshot.tags))
                        check(preferences.edit().putString(SubmissionRecoveryKeys.STATUS, "done").commit())
                        SendOutcome.Created(noteId)
                    }
                    NoteInsertedRecoveryDecision.NEEDS_REVIEW -> SendOutcome.DestinationUnverified(noteId)
                    NoteInsertedRecoveryDecision.INDETERMINATE -> SendOutcome.Indeterminate(
                        phase,
                        "Saved note has no verifiable cards.",
                        IndeterminateReason.NO_VERIFIABLE_CARDS,
                    )
                    NoteInsertedRecoveryDecision.FAIL -> SendOutcome.Indeterminate(
                        phase,
                        "Saved note destination is invalid.",
                        IndeterminateReason.LOOKUP_FAILED,
                    )
                }
            } else if (phase == SubmissionPhase.NOTE_INSERTING) {
                val model = AnkiNoteType(snapshot.modelId, "", snapshot.modelFields)
                val values = snapshot.modelFields.zip(snapshot.orderedFields).toMap()
                val duplicateId = gateway.hasMatchingNoteInAnyDeck(model, values)
                if (duplicateId != null) SendOutcome.Duplicate(duplicateId) else SendOutcome.RetryAvailable
            } else {
                SendOutcome.PendingRecovery
            }
        } catch (cancelled: java.util.concurrent.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            SendOutcome.Indeterminate(phase, error.message.orEmpty(), IndeterminateReason.LOOKUP_FAILED)
        }
    }

    private class ImageNotReadyException : IllegalStateException()
}

/** Route the Card's primary click without ever retaining a forward-assigned placeholder lambda. */
internal fun cardActionClickHandler(
    action: CardAction,
    sendState: SendState,
    frozenRetryReady: Boolean,
    onSubmit: () -> Unit,
    onRetryAnalysis: () -> Unit,
    onRecovery: () -> Unit,
): () -> Unit = when (action) {
    CardAction.None -> ({})
    CardAction.RetryAnalysis -> onRetryAnalysis
    CardAction.RetrySend -> ({ if (!sendState.locked || frozenRetryReady) onSubmit() })
    CardAction.Recovery -> onRecovery
    CardAction.Submit -> ({ if (!sendState.locked || frozenRetryReady) onSubmit() })
}

private fun draftAudioPreferenceKeys(): List<String> = buildList {
    add(dev.pierre.japaneseanki.DraftAudioPreferences.VERSION)
    add(dev.pierre.japaneseanki.DraftAudioPreferences.EPOCH)
    for (item in AudioItem.entries) {
        add(dev.pierre.japaneseanki.DraftAudioPreferences.identity(item))
        add(dev.pierre.japaneseanki.DraftAudioPreferences.status(item))
        add(dev.pierre.japaneseanki.DraftAudioPreferences.path(item))
        add(dev.pierre.japaneseanki.DraftAudioPreferences.format(item))
        add(dev.pierre.japaneseanki.DraftAudioPreferences.size(item))
        add(dev.pierre.japaneseanki.DraftAudioPreferences.modified(item))
        add(dev.pierre.japaneseanki.DraftAudioPreferences.frozenPath(item))
    }
}

internal fun clearPersistedDraft(preferences: SharedPreferences, draftDirectory: File? = null) {
    val ownedDirectory = draftDirectory?.canonicalFile
    val ownedPaths = listOfNotNull(
        preferences.getString("photo_source_path", null),
        preferences.getString("note_image_path", null),
        preferences.getString("processed_image_path", null),
        preferences.getString("pending_replacement_image_path", null),
    )
    ownedPaths.distinct().forEach { path ->
        val file = File(path)
        if (ownedDirectory != null && file.exists() && file.canonicalFile.parentFile == ownedDirectory) file.delete()
    }
    val draftKeys = listOf(
        "sentence", "selection_start", "selection_end", "target_start", "target_end",
        "vocabulary", "vocabulary_reading", "pitch_accent", "definition", "definition_manual_stale", "sentence_reading",
        "sentence_reading_corrections",
        "leave_sentence_target_unannotated", "leave_vocabulary_unannotated", "dependent_fields_stale",
        "definition_manual", "definition_manual_stale", "translation", "translation_sentence", "translation_source",
        "translation_manual", "translation_stale", "translation_pending", "translation_error",
        "translation_suggestion", "translation_suggestion_source", "translation_suggestion_error",
        "translation_attempted_sentence", "notes", "tags", "submission_status", "pending_model_id", "pending_deck_id",
        "pending_sentence", "pending_lemma", "dictionary_entry_id", "dictionary_sense_indices",
        "sentence_furigana_corrections", "photo_source_path", "note_image_path", "processed_image_path",
        dev.pierre.japaneseanki.IMAGE_RECEIPT_PATH, dev.pierre.japaneseanki.IMAGE_RECEIPT_FILENAME,
        dev.pierre.japaneseanki.IMAGE_RECEIPT_SIZE, dev.pierre.japaneseanki.IMAGE_RECEIPT_MODIFIED,
        dev.pierre.japaneseanki.SENTENCE_AUDIO_RECEIPT_PATH, dev.pierre.japaneseanki.SENTENCE_AUDIO_RECEIPT_FILENAME,
        dev.pierre.japaneseanki.SENTENCE_AUDIO_RECEIPT_SIZE, dev.pierre.japaneseanki.SENTENCE_AUDIO_RECEIPT_MODIFIED,
        dev.pierre.japaneseanki.WORD_AUDIO_RECEIPT_PATH, dev.pierre.japaneseanki.WORD_AUDIO_RECEIPT_FILENAME,
        dev.pierre.japaneseanki.WORD_AUDIO_RECEIPT_SIZE, dev.pierre.japaneseanki.WORD_AUDIO_RECEIPT_MODIFIED,
        SubmissionRecoveryKeys.MEDIA_OPERATION, SubmissionRecoveryKeys.MEDIA_SOURCE_PATH,
        SubmissionRecoveryKeys.MEDIA_SOURCE_SIZE, SubmissionRecoveryKeys.MEDIA_SOURCE_MODIFIED,
        "pending_replacement_image_path", "draft_origin",
        SubmissionRecoveryKeys.REVISION, SubmissionRecoveryKeys.MODEL_FIELDS,
        SubmissionRecoveryKeys.ORDERED_FIELDS, SubmissionRecoveryKeys.VOCABULARY_READING,
        SubmissionRecoveryKeys.SENTENCE_READING, SubmissionRecoveryKeys.DEFINITION,
        SubmissionRecoveryKeys.TRANSLATION, SubmissionRecoveryKeys.NOTES, SubmissionRecoveryKeys.TAGS,
        SubmissionRecoveryKeys.PITCH, SubmissionRecoveryKeys.TARGET_START, SubmissionRecoveryKeys.TARGET_END,
        SubmissionRecoveryKeys.MEDIA_PATH, SubmissionRecoveryKeys.MEDIA_SIZE,
        SubmissionRecoveryKeys.MEDIA_MODIFIED, SubmissionRecoveryKeys.IMPORTED_FILENAME,
        SubmissionRecoveryKeys.NOTE_ID,
    ) + draftAudioPreferenceKeys()
    preferences.edit().also { editor -> draftKeys.forEach(editor::remove) }.commit()
}
