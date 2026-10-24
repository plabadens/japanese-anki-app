package dev.pierre.japaneseanki.send


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
import dev.pierre.japaneseanki.AppPreferenceStorage
import dev.pierre.japaneseanki.PreferenceChanges
import dev.pierre.japaneseanki.PreferenceFile
import dev.pierre.japaneseanki.receiptIdentity
import dev.pierre.japaneseanki.ui.CardAction
import dev.pierre.japaneseanki.ui.SendPhase
import dev.pierre.japaneseanki.ui.SendState
import kotlinx.coroutines.CoroutineDispatcher
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
    private val storage: AppPreferenceStorage,
    private val audio: DraftAudioController,
    private val repository: FrozenSubmissionStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val preferences get() = storage.draft.value

    suspend fun submit(request: SendRequest, onState: (SendState) -> Unit): SendOutcome {
        val result = withContext(ioDispatcher) { submitBlocking(request) }
        result.frozen?.let { snapshot ->
            onState(SendState(phase = SendPhase.Submitting, frozen = snapshot))
        }
        return result.outcome
    }

    suspend fun recover(state: SendState): SendOutcome = withContext(ioDispatcher) {
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
                storage.persistBlocking(
                    PreferenceFile.DRAFT,
                    PreferenceChanges().putString(SubmissionRecoveryKeys.STATUS, "blocked"),
                )
                audio.releaseFreeze(retainFiles = false)
                if (restored != null) storage.persistBlocking(PreferenceFile.DRAFT, clearFrozenSubmission())
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
                check(storage.persistBlocking(PreferenceFile.DRAFT, PreferenceChanges()
                    .putLong(SubmissionRecoveryKeys.REVISION_SEQUENCE, startingSnapshot.revision)
                    .putFrozenSubmission(startingSnapshot, SubmissionPhase.MEDIA_IMPORTING))) {
                    "Could not save frozen submission for recovery."
                }
            }

            var snapshot = startingSnapshot
            fun persistReceipt(receipt: ImportedMediaReceipt): Boolean {
                val updated = try {
                    applyImportedReceipt(snapshot, receipt)
                } catch (_: Exception) {
                    return false
                }
                val saved = runCatching {
                    storage.persistBlocking(PreferenceFile.DRAFT, PreferenceChanges()
                        .putFrozenSubmission(updated, SubmissionPhase.MEDIA_IMPORTED)
                        .apply { putMediaReceipt(receipt) }
                        .remove(SubmissionRecoveryKeys.MEDIA_OPERATION)
                        .remove(SubmissionRecoveryKeys.MEDIA_SOURCE_PATH)
                        .remove(SubmissionRecoveryKeys.MEDIA_SOURCE_SIZE)
                        .remove(SubmissionRecoveryKeys.MEDIA_SOURCE_MODIFIED)
                    )
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
                    storage.persistBlocking(PreferenceFile.DRAFT, PreferenceChanges()
                        .putFrozenSubmission(snapshot, SubmissionPhase.MEDIA_IMPORTING)
                        .putString(SubmissionRecoveryKeys.MEDIA_OPERATION, operation.name)
                        .putString(SubmissionRecoveryKeys.MEDIA_SOURCE_PATH, source.path)
                        .putLong(SubmissionRecoveryKeys.MEDIA_SOURCE_SIZE, source.size)
                        .putLong(SubmissionRecoveryKeys.MEDIA_SOURCE_MODIFIED, source.modified)
                    )
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
            check(storage.persistBlocking(PreferenceFile.DRAFT, PreferenceChanges()
                .putFrozenSubmission(snapshot, SubmissionPhase.MEDIA_IMPORTED)
                .remove(SubmissionRecoveryKeys.MEDIA_OPERATION)
                .remove(SubmissionRecoveryKeys.MEDIA_SOURCE_PATH)
                .remove(SubmissionRecoveryKeys.MEDIA_SOURCE_SIZE)
                .remove(SubmissionRecoveryKeys.MEDIA_SOURCE_MODIFIED)
            )) { "Could not save completed media recovery state." }

            val frozenFields = snapshot.modelFields.zip(snapshot.orderedFields).toMap()
            val frozenMapping = AnkiFieldContract.mapToModelOrder(request.model.fields, frozenFields)
            check(frozenMapping.isValid) { frozenMapping.errors.joinToString() }
            check(storage.persistBlocking(
                PreferenceFile.DRAFT,
                PreferenceChanges().putFrozenSubmission(snapshot, SubmissionPhase.NOTE_INSERTING),
            )) {
                "Could not save frozen submission before note insertion."
            }
            // No insert retry is safe after this point until recovery reconciles NOTE_INSERTING.
            val insertedNoteId = gateway.insertNote(request.model, frozenMapping.values, tagSet)
            val insertedSnapshot = snapshot.copy(noteId = insertedNoteId)
            check(storage.persistBlocking(
                PreferenceFile.DRAFT,
                PreferenceChanges().putFrozenSubmission(insertedSnapshot, SubmissionPhase.NOTE_INSERTED),
            )) { "Could not save the inserted note ID." }
            frozen = insertedSnapshot
            durableSnapshot = insertedSnapshot

            if (!gateway.ensureNoteInDeck(insertedNoteId, request.deck.id)) {
                audio.releaseFreeze(retainFiles = true)
                return SubmissionRun(SendOutcome.DestinationUnverified(insertedNoteId), insertedSnapshot)
            }
            check(storage.persistBlocking(
                PreferenceFile.DRAFT,
                PreferenceChanges().putFrozenSubmission(insertedSnapshot, SubmissionPhase.DESTINATION_VERIFIED),
            )) { "Could not save the verified destination." }
            check(repository.persistLastSentTags(insertedSnapshot.tags)) { "Could not save tags from the verified note." }
            check(storage.persistBlocking(
                PreferenceFile.DRAFT,
                PreferenceChanges().putString(SubmissionRecoveryKeys.STATUS, "done"),
            ))
            repository.clearDraft()
            audio.releaseFreeze(retainFiles = false)
            return SubmissionRun(SendOutcome.Created(insertedNoteId), insertedSnapshot)
        } catch (cancelled: java.util.concurrent.CancellationException) {
            val phase = SubmissionPhase.fromPersisted(preferences.getString(SubmissionRecoveryKeys.STATUS, null))
            audio.releaseFreeze(retainFiles = phase != null)
            throw cancelled
        } catch (error: AnkiProviderDefiniteInsertException) {
            audio.releaseFreeze(retainFiles = false)
            storage.persistBlocking(PreferenceFile.DRAFT, clearFrozenSubmission())
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
                        check(storage.persistBlocking(
                            PreferenceFile.DRAFT,
                            PreferenceChanges().putString(
                                SubmissionRecoveryKeys.STATUS,
                                SubmissionPhase.DESTINATION_VERIFIED.persisted,
                            ),
                        ))
                        check(repository.persistLastSentTags(snapshot.tags))
                        check(storage.persistBlocking(
                            PreferenceFile.DRAFT,
                            PreferenceChanges().putString(SubmissionRecoveryKeys.STATUS, "done"),
                        ))
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
