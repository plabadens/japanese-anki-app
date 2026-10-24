package dev.pierre.japaneseanki.ui

import androidx.compose.runtime.Immutable
import dev.pierre.japaneseanki.FrozenSubmission

enum class RecoveryReason {
    None,
    MatchingNoteExists,
    SavedNoteMissing,
    DestinationUnavailable,
    Unverifiable,
    MediaInterrupted,
    MediaImported,
}

enum class SendPhase { Idle, Preparing, Submitting, Inserted, Verified }

@Immutable
internal data class SendState(
    val phase: SendPhase = SendPhase.Idle,
    val frozen: FrozenSubmission? = null,
    val completedNoteId: Long? = null,
    val duplicateNoteId: Long? = null,
    val recovery: RecoveryReason = RecoveryReason.None,
    val message: String? = null,
) {
    val locked: Boolean
        get() = phase == SendPhase.Preparing ||
            phase == SendPhase.Submitting ||
            phase == SendPhase.Inserted ||
            (phase == SendPhase.Verified && completedNoteId == null) ||
            recovery != RecoveryReason.None || duplicateNoteId != null

    val editingEnabled: Boolean
        get() = !locked && completedNoteId == null

    val recoveryRequired: Boolean
        get() = (recovery != RecoveryReason.None && phase != SendPhase.Idle) ||
            phase == SendPhase.Inserted ||
            (phase == SendPhase.Verified && completedNoteId == null)

    val retryReady: Boolean
        get() = phase == SendPhase.Preparing &&
            recovery == RecoveryReason.None &&
            duplicateNoteId == null &&
            completedNoteId == null

    val submitting: Boolean
        get() = phase == SendPhase.Submitting && recovery == RecoveryReason.None

    val duplicateBlocked: Boolean
        get() = phase == SendPhase.Idle &&
            (recovery == RecoveryReason.MatchingNoteExists || duplicateNoteId != null)

    val completedSubmission: FrozenSubmission?
        get() = frozen.takeIf { completedNoteId != null }
}
