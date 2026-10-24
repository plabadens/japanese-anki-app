package dev.pierre.japaneseanki.send

import android.content.Context
import android.content.SharedPreferences
import dev.pierre.japaneseanki.MediaImportOperation
import dev.pierre.japaneseanki.R
import dev.pierre.japaneseanki.SubmissionPhase
import dev.pierre.japaneseanki.SubmissionRecoveryKeys
import dev.pierre.japaneseanki.readFrozenSubmission
import dev.pierre.japaneseanki.ui.RecoveryReason
import dev.pierre.japaneseanki.ui.SendPhase
import dev.pierre.japaneseanki.ui.SendState

internal data class SendOutcomeUiUpdate(
    val state: SendState,
    val submitMessage: String? = null,
    val updateSubmitMessage: Boolean = false,
    val deliveryVerified: Boolean = false,
)

/** Translate pipeline outcomes into the existing Card state and copy. */
internal fun SendOutcome.toUiUpdate(
    current: SendState,
    context: Context,
    preferences: SharedPreferences,
    imageAttached: Boolean,
): SendOutcomeUiUpdate {
    var state = current
    var submitMessage: String? = null
    var updateSubmitMessage = false
    var deliveryVerified = false

    fun updateRecovery(message: String, reason: RecoveryReason, phase: SendPhase) {
        state = state.copy(message = message, recovery = reason, phase = phase)
    }

    when (this) {
        is SendOutcome.Duplicate -> {
            state = state.copy(
                phase = SendPhase.Idle,
                recovery = RecoveryReason.MatchingNoteExists,
                duplicateNoteId = noteId,
                completedNoteId = null,
            )
            submitMessage = context.getString(R.string.card_blocked_a_note_with_this_sentence_and_voca)
            updateSubmitMessage = true
        }
        is SendOutcome.Created -> {
            deliveryVerified = true
            val completed = current.frozen ?: readFrozenSubmission(preferences)
            state = state.copy(
                phase = SendPhase.Verified,
                frozen = completed,
                completedNoteId = completed?.noteId ?: noteId,
                duplicateNoteId = null,
                recovery = RecoveryReason.None,
            )
            submitMessage = context.getString(R.string.card_note_sent_to_ankidroid)
            updateSubmitMessage = true
        }
        is SendOutcome.InsertFailed -> {
            state = state.copy(
                phase = SendPhase.Idle,
                frozen = null,
                completedNoteId = null,
                duplicateNoteId = null,
                recovery = RecoveryReason.None,
            )
            submitMessage = context.getString(R.string.card_send_retry_suffix, message)
            updateSubmitMessage = true
        }
        is SendOutcome.MediaFailed -> {
            val phase = SubmissionPhase.fromPersisted(
                preferences.getString(SubmissionRecoveryKeys.STATUS, null),
            )
            val reason = if (phase == SubmissionPhase.MEDIA_IMPORTED) {
                RecoveryReason.MediaImported
            } else RecoveryReason.MediaInterrupted
            val stage = stage.name.lowercase().replace('_', ' ')
            updateRecovery(
                context.getString(
                    R.string.card_send_recovery_media_import_stage_uncertain,
                    operation.label,
                    stage,
                ),
                reason,
                SendPhase.Inserted,
            )
        }
        is SendOutcome.DestinationUnverified -> updateRecovery(
            context.getString(R.string.card_send_recovery_note_assignment_interrupted),
            RecoveryReason.None,
            SendPhase.Inserted,
        )
        is SendOutcome.Indeterminate -> {
            if (phase == null) {
                state = state.copy(phase = SendPhase.Idle)
                submitMessage = context.getString(
                    if (imageAttached) {
                        R.string.card_could_not_import_the_selected_image_the_dr
                    } else {
                        R.string.card_could_not_send_the_note_check_ankidroid_ap
                    },
                )
                updateSubmitMessage = true
            } else {
                val reason = when (phase) {
                    SubmissionPhase.MEDIA_IMPORTING -> RecoveryReason.MediaInterrupted
                    SubmissionPhase.MEDIA_IMPORTED -> RecoveryReason.MediaImported
                    SubmissionPhase.NOTE_INSERTING,
                    SubmissionPhase.NOTE_INSERTED,
                    SubmissionPhase.DESTINATION_VERIFIED -> RecoveryReason.None
                }
                val message = when (phase) {
                    SubmissionPhase.MEDIA_IMPORTING -> {
                        val operation = preferences.getString(SubmissionRecoveryKeys.MEDIA_OPERATION, null)
                            ?.let { name -> runCatching { MediaImportOperation.valueOf(name) }.getOrNull() }
                        val label = operation?.label ?: context.getString(R.string.card_media_label)
                        context.getString(R.string.card_send_recovery_media_import_uncertain, label)
                    }
                    SubmissionPhase.MEDIA_IMPORTED -> context.getString(R.string.card_send_recovery_media_receipts_saved)
                    SubmissionPhase.NOTE_INSERTING -> context.getString(R.string.card_send_recovery_note_insertion_uncertain)
                    SubmissionPhase.NOTE_INSERTED -> context.getString(R.string.card_send_recovery_note_assignment_interrupted)
                    SubmissionPhase.DESTINATION_VERIFIED -> context.getString(R.string.card_send_recovery_destination_verified)
                }
                updateRecovery(message, reason, SendPhase.Inserted)
            }
        }
        is SendOutcome.NotReady -> {
            state = state.copy(phase = SendPhase.Idle)
            when (reason) {
                SendNotReadyReason.AUDIO_INCOMPLETE -> {
                    submitMessage = context.getString(R.string.card_wait_for_or_retry_the_missing_audio_before)
                    updateSubmitMessage = true
                }
                SendNotReadyReason.IMAGE_NOT_READY -> {
                    submitMessage = context.getString(R.string.card_selected_image_not_ready_for_import)
                    updateSubmitMessage = true
                }
                SendNotReadyReason.AUDIO_DISPOSED,
                SendNotReadyReason.INVALID_FIELDS -> Unit
            }
        }
        SendOutcome.RetryAvailable,
        SendOutcome.PendingRecovery -> Unit
    }

    return SendOutcomeUiUpdate(state, submitMessage, updateSubmitMessage, deliveryVerified)
}
