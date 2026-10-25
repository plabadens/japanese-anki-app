package dev.pierre.japaneseanki.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SendStateTest {
    @Test
    fun lockAndEditingDerivationsCoverPhaseRecoveryDuplicateAndCompletedCombinations() {
        val phases = SendPhase.entries
        val recoveries = RecoveryReason.entries
        val duplicateIds = listOf(null, 17L)
        val completedIds = listOf(null, 23L)
        var rows = 0

        for (phase in phases) {
            for (recovery in recoveries) {
                for (duplicateId in duplicateIds) {
                    for (completedId in completedIds) {
                        val state = SendState(
                            phase = phase,
                            duplicateNoteId = duplicateId,
                            completedNoteId = completedId,
                            recovery = recovery,
                        )
                        val expectedLocked = phase in setOf(
                            SendPhase.Preparing,
                            SendPhase.Submitting,
                            SendPhase.Inserted,
                        ) || (phase == SendPhase.Verified && completedId == null) ||
                            recovery != RecoveryReason.None || duplicateId != null
                        val expectedRecovery = (recovery != RecoveryReason.None && phase != SendPhase.Idle) ||
                            phase == SendPhase.Inserted ||
                            (phase == SendPhase.Verified && completedId == null)

                        assertEquals("locked for $state", expectedLocked, state.locked)
                        assertEquals("editing for $state", !expectedLocked && completedId == null, state.editingEnabled)
                        assertEquals("recovery for $state", expectedRecovery, state.recoveryRequired)
                        rows++
                    }
                }
            }
        }

        assertTrue("Expected broad phase × recovery × id coverage", rows >= 12)
    }

}
