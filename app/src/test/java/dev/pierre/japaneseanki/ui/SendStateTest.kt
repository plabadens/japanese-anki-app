package dev.pierre.japaneseanki.ui

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun eachRecoveryReasonIsAssignedAtItsOutcomeScenario() {
        val root = projectRoot()
        val source = read(root.resolve("app/src/main/java/dev/pierre/japaneseanki/MainActivity.kt")) +
            read(root.resolve("app/src/main/java/dev/pierre/japaneseanki/send/SendOutcomeUi.kt"))
        val scenarios = listOf(
            Triple("is SendOutcome.Duplicate ->", "RecoveryReason.MatchingNoteExists", 700),
            Triple("IndeterminateReason.MISSING_NOTE_ID ->", "RecoveryReason.SavedNoteMissing", 300),
            Triple("if (model == null) {", "RecoveryReason.DestinationUnavailable", 400),
            Triple("card_could_not_verify_the_saved_note_id_retry_t", "RecoveryReason.Unverifiable", 300),
            Triple(
                "this@MainActivity.getString(R.string.card_no_matching_note_was_found_for_the_frozen)",
                "RecoveryReason.None",
                220,
            ),
            Triple(
                "SubmissionPhase.MEDIA_IMPORTING -> RecoveryReason.MediaInterrupted",
                "RecoveryReason.MediaInterrupted",
                120,
            ),
            Triple(
                "SubmissionPhase.MEDIA_IMPORTED -> RecoveryReason.MediaImported",
                "RecoveryReason.MediaImported",
                120,
            ),
        )

        scenarios.forEach { (scenario, reason, contextLength) ->
            val start = source.indexOf(scenario)
            assertTrue("Missing recovery scenario: $scenario", start >= 0)
            val context = source.substring(start, (start + contextLength).coerceAtMost(source.length))
            assertTrue("$scenario does not produce $reason", context.contains(reason))
        }
    }

    @Test
    fun recoveryAffordancesDoNotBranchOnDisplayCopy() {
        val source = read(projectRoot().resolve("app/src/main/java/dev/pierre/japaneseanki/MainActivity.kt"))
        assertFalse(source.contains("recoveryMessage?.contains("))
        assertFalse(source.contains("recoveryMessage?.startsWith("))
    }

    private fun read(file: File): String = Files.readString(file.toPath(), StandardCharsets.UTF_8)

    private fun projectRoot(): File {
        var current = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (true) {
            if (File(current, "app/src/main/res/values/strings.xml").isFile) return current
            current = current.parentFile ?: break
        }
        error("Could not locate the repository root from ${System.getProperty("user.dir")}")
    }
}
