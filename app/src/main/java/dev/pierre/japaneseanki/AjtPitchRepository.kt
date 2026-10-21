package dev.pierre.japaneseanki

import android.content.Context
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Why the process-wide AJT pitch index cannot answer lookups. */
enum class AjtPitchFailure { MISSING_INDEX, EMPTY_INDEX, INVALID_INDEX, OPEN_FAILED }

/** What the process-wide AJT pitch repository can answer right now. */
sealed interface AjtPitchAvailability {
    data object NotPrepared : AjtPitchAvailability
    data object Preparing : AjtPitchAvailability
    data object Ready : AjtPitchAvailability
    data class Unavailable(val failure: AjtPitchFailure) : AjtPitchAvailability
}

/**
 * The single owner of the bundled AJT pitch index for the life of the process.
 *
 * The activity surface prepares it on a background thread, observes [availability] and asks for
 * records; it never holds, replaces or closes the index handle itself. Opening is lazy and
 * serialised with queries, so a draft reset, a configuration change or a second caller cannot
 * swap a handle out from under a running query.
 *
 * Preparation is idempotent after success ([prepare] returns [AjtPitchAvailability.Ready] without
 * reopening) and explicitly retryable after failure: call [prepare] again. Nothing is closed on
 * activity destruction - one successful read-only handle is reused until the process exits, which
 * is why there is no reference counting and no close/query race.
 */
class AjtPitchRepository internal constructor(
    private val openStore: () -> AjtPitchStore?,
) {
    private val lock = Any()
    private var store: AjtPitchStore? = null

    private val _availability = MutableStateFlow<AjtPitchAvailability>(AjtPitchAvailability.NotPrepared)

    /** Observable availability for Compose (`collectAsState`) and any other reader. */
    val availability: StateFlow<AjtPitchAvailability> = _availability.asStateFlow()

    /**
     * Opens the index if it is not open yet. Blocks on I/O, so call it from a background
     * dispatcher. A failed probe or open closes the provisional handle and publishes
     * [AjtPitchAvailability.Unavailable]; a later call retries the open.
     */
    fun prepare(): AjtPitchAvailability {
        synchronized(lock) {
            if (store != null) return AjtPitchAvailability.Ready
            _availability.value = AjtPitchAvailability.Preparing
            val opened = try {
                openStore()
            } catch (cancelled: CancellationException) {
                // The opener closes anything it created before throwing; there is no handle here.
                _availability.value = AjtPitchAvailability.Unavailable(AjtPitchFailure.OPEN_FAILED)
                throw cancelled
            } catch (_: Exception) {
                return fail(AjtPitchFailure.OPEN_FAILED)
            } ?: return fail(AjtPitchFailure.MISSING_INDEX)

            val usable = try {
                opened.probe()
            } catch (cancelled: CancellationException) {
                closeQuietly(opened)
                _availability.value = AjtPitchAvailability.Unavailable(AjtPitchFailure.OPEN_FAILED)
                throw cancelled
            } catch (_: Exception) {
                closeQuietly(opened)
                return fail(AjtPitchFailure.INVALID_INDEX)
            }
            if (!usable) {
                closeQuietly(opened)
                return fail(AjtPitchFailure.EMPTY_INDEX)
            }
            store = opened
            _availability.value = AjtPitchAvailability.Ready
            return AjtPitchAvailability.Ready
        }
    }

    /**
     * Pitch records for one exact expression/reading pair. Serialised with [prepare]; an index
     * that is not ready yields no records instead of touching a closed handle.
     */
    fun pitch(expression: String, reading: String): List<AjtPitchRecord> {
        if (expression.isBlank() || reading.isBlank()) return emptyList()
        synchronized(lock) {
            return store?.pitch(expression, reading) ?: emptyList()
        }
    }

    /** Process-lifetime shutdown used by tests; the activity must never call this. */
    internal fun close() {
        synchronized(lock) {
            closeQuietly(store)
            store = null
            _availability.value = AjtPitchAvailability.NotPrepared
        }
    }

    private fun fail(failure: AjtPitchFailure): AjtPitchAvailability {
        store = null
        val unavailable = AjtPitchAvailability.Unavailable(failure)
        _availability.value = unavailable
        return unavailable
    }

    private fun closeQuietly(handle: AjtPitchStore?) {
        try {
            handle?.close()
        } catch (_: Exception) {
            // Closing a broken handle is best-effort; the failure is already reported.
        }
    }

    companion object {
        @Volatile private var shared: AjtPitchRepository? = null

        /** The process-wide repository. Never closed by an activity. */
        fun shared(context: Context): AjtPitchRepository {
            shared?.let { return it }
            // Capture the application context once: the opener must not retain an activity.
            val application = context.applicationContext
            return synchronized(this) {
                shared ?: AjtPitchRepository { AjtPitchIndex.open(application) }.also { shared = it }
            }
        }

        /** Drops the process-wide instance so a test can observe a fresh process lifetime. */
        internal fun resetForTests() {
            synchronized(this) {
                shared?.close()
                shared = null
            }
        }
    }
}
