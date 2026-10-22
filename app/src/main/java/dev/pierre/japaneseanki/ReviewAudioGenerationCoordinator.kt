package dev.pierre.japaneseanki

/** The only entry cause that can authorize a fresh automatic Review audio attempt. */
enum class ReviewAudioEntryCause {
    EXPLICIT_CONTINUE,
    REVISIT,
    PROCESS_RESTORE,
}

/**
 * Input revision for audio declared before Continue, then pinned to that Continue while pending.
 * Once Continue completes, the next input edit or review visit can target the next sequence.
 */
internal fun declaredReviewAudioRevision(
    pendingAuthorization: Long?,
    lastAuthorization: Long,
): Long {
    require(lastAuthorization >= 0) { "Review audio authorization must not be negative" }
    pendingAuthorization?.let {
        require(it >= 0) { "Pending review audio authorization must not be negative" }
        return it
    }
    return runCatching { Math.addExact(lastAuthorization, 1L) }.getOrDefault(lastAuthorization)
}

/**
 * Turns the explicit preparation Continue into at-most-once per-revision audio attempts.
 *
 * The caller must declare the frozen revision's inputs before calling [onReviewEntry]. The marker
 * is committed before attempts are returned, so a process death or failed attempt cannot silently
 * reissue a provider request. Reopen/restore causes are deliberately inert. Cache hits remain inside
 * the controller and still count as an authorized attempt without requiring a provider call.
 */
class ReviewAudioGenerationCoordinator(
    private val controller: DraftAudioController,
    private val persistence: DraftAudioPersistence,
) {
    private val gate = Any()

    fun onReviewEntry(revision: Long, cause: ReviewAudioEntryCause): List<DraftAudioAttempt> {
        require(revision >= 0) { "Review revision must not be negative" }
        if (cause != ReviewAudioEntryCause.EXPLICIT_CONTINUE) return emptyList()
        return synchronized(gate) {
            val epoch = controller.epoch()
            controller.beginReviewEntry(revision) {
                val previous = persistence.read(ReviewAudioEntryPreferences.AUTHORIZED_REVISION)
                    ?.split(':')
                    ?.takeIf { it.size == 2 }
                    ?.let { parts ->
                        val savedEpoch = parts[0].toLongOrNull()
                        val savedRevision = parts[1].toLongOrNull()
                        if (savedEpoch != null && savedRevision != null && savedEpoch >= 0 && savedRevision >= 0) {
                            savedEpoch to savedRevision
                        } else null
                    }
                if (previous?.first == epoch && revision <= previous.second) {
                    false
                } else {
                    runCatching {
                        persistence.write(
                            mapOf(ReviewAudioEntryPreferences.AUTHORIZED_REVISION to "$epoch:$revision"),
                        )
                    }.getOrDefault(false)
                }
            }
        }
    }
}

object ReviewAudioEntryPreferences {
    /** Contains only draft epoch and numeric revision, never sentence text or an audio identity. */
    const val AUTHORIZED_REVISION = "review_audio_authorized_revision"
}
