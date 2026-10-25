package dev.pierre.japaneseanki

import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import dev.pierre.japaneseanki.speech.SpeechException
import dev.pierre.japaneseanki.speech.SpeechFailure

/**
 * Synchronous string persistence for draft audio state.
 *
 * The app adapter is a SharedPreferences editor that commits: `read` is
 * `getString(key, null)` and `write` applies every entry (`null` removes it) then returns
 * `commit()`. Tests use an in-memory map. No Android type appears here, so the controller is
 * exercised by the standalone Kotlin harness.
 */
interface DraftAudioPersistence {
    fun read(key: String): String?

    /** Applies [changes] atomically. Returns false when the commit did not land. */
    fun write(changes: Map<String, String?>): Boolean
}

/** Persisted keys and status values for the production draft-audio controller. */
object DraftAudioPreferences {
    const val VERSION = "draft_audio_version"
    const val VERSION_VALUE = "1"
    const val EPOCH = "draft_audio_epoch"

    fun identity(item: AudioItem): String = "${prefix(item)}_identity"
    fun status(item: AudioItem): String = "${prefix(item)}_status"
    fun path(item: AudioItem): String = "${prefix(item)}_path"
    fun size(item: AudioItem): String = "${prefix(item)}_size"
    fun modified(item: AudioItem): String = "${prefix(item)}_modified"
    fun format(item: AudioItem): String = "${prefix(item)}_format"

    /** Raw frozen-Send path kept outside the (possibly malformed) submission snapshot. */
    fun frozenPath(item: AudioItem): String = "${prefix(item)}_frozen_path"

    const val STATUS_READY = "ready"
    const val STATUS_PENDING = "pending"
    const val STATUS_FAILED = "failed"
    fun failureRetryable(item: AudioItem): String = "${prefix(item)}_failure_retryable"
    fun diagnostic(item: AudioItem, field: String): String = "${prefix(item)}_diagnostic_$field"

    fun prefix(item: AudioItem): String = when (item) {
        AudioItem.SENTENCE -> "draft_audio_sentence"
        AudioItem.VOCABULARY -> "draft_audio_word"
    }
}

/** Immutable provider-neutral inputs for one audio item. Identity must cover every input. */
data class DraftAudioInput(
    val item: AudioItem,
    val identity: String,
    val text: String,
    val revision: Long,
    val entryIdentity: String? = null,
    val cacheKey: AudioCacheKey,
    /** Container the configured provider writes (Google sentences: MP3, NHK words: OGG). */
    val expectedFormat: AudioFormat = AudioFormat.MP3,
    /**
     * Immutable provider request captured when the input is declared (for example a
     * `SentenceSpeechRequest` with its voice and phonetic hints, or an `AudioSynthesisRequest`).
     * The integrator's synthesizer casts it, so an attempt can never read recomposition state.
     */
    val providerPayload: Any? = null,
) {
    init {
        require(identity.isNotBlank()) { "Audio input identity must not be blank" }
        require(text.isNotBlank()) { "Audio input text must not be blank" }
        require(revision >= 0) { "Audio input revision must not be negative" }
    }
}

data class DraftAudioDiagnostic(
    val category: String,
    val httpStatus: Int?,
    val googleStatus: String?,
    val googleReason: String?,
    val exceptionClass: String?,
    val durationMillis: Long,
    val item: AudioItem,
    val revision: Long,
    val attemptId: Long,
    val inputBytes: Int?,
    val requestBytes: Int?,
    val voiceName: String?,
    val hintCount: Int?,
)

/** Per-item presentation state. Only [Ready] carries a durable, validated file. */
sealed interface DraftAudioState {
    val identity: String?

    data object Idle : DraftAudioState {
        override val identity: String? = null
    }

    /** Newly affected inputs wait for an explicit Continue before any billable work. */
    data class NeedsContinue(override val identity: String) : DraftAudioState

    data class Generating(override val identity: String, val attemptId: Long) : DraftAudioState

    data class Ready(override val identity: String, val file: DraftAudioFile) : DraftAudioState

    data class Failed(
        override val identity: String,
        val retryable: Boolean,
        val message: String,
        val diagnostic: DraftAudioDiagnostic? = null,
    ) : DraftAudioState
}

/** Attempt token: every acceptance must match draft epoch, item, input identity and attempt id. */
data class DraftAudioToken(
    val draftEpoch: Long,
    val item: AudioItem,
    val inputIdentity: String,
    val attemptId: Long,
)

/** Handed to a blocking worker. The worker owns [staging] until its run returns. */
class DraftAudioAttempt internal constructor(
    val token: DraftAudioToken,
    val input: DraftAudioInput,
    val staging: DraftAudioStaging,
)

sealed interface DraftAudioAttemptResult {
    data class Accepted(val file: DraftAudioFile, val fromCache: Boolean) : DraftAudioAttemptResult

    /** The attempt was invalidated (new input, cancel, discard, restart) before acceptance. */
    data object Stale : DraftAudioAttemptResult

    data class Failed(
        val message: String,
        val retryable: Boolean,
        val diagnostic: DraftAudioDiagnostic? = null,
    ) : DraftAudioAttemptResult
}

/** Immutable audio references pinned for one Send. */
data class DraftAudioFrozenSnapshot(
    val draftEpoch: Long,
    val files: Map<AudioItem, DraftAudioFile>,
    val identities: Map<AudioItem, String>,
)

sealed interface DraftAudioFreeze {
    data class Frozen(val snapshot: DraftAudioFrozenSnapshot) : DraftAudioFreeze
    data class Incomplete(val missing: List<AudioItem>) : DraftAudioFreeze
    data object Disposed : DraftAudioFreeze
}

data class DraftAudioSnapshot(
    val draftEpoch: Long,
    val items: Map<AudioItem, DraftAudioState>,
    val frozen: DraftAudioFrozenSnapshot?,
)

/**
 * The production draft-audio lifecycle: per-item independent jobs, one serialized transition
 * gate, explicit Continue/Retry, token-checked acceptance, durable publication before retirement
 * and a Send freeze that pins immutable files.
 *
 * Heavy work (cache copy, provider synthesis, file publish) happens in [runAttempt] outside the
 * gate. Only short state transitions and the persistence commit run inside it. Persistence must
 * therefore be a local synchronous commit, not a network or filesystem walk.
 */
class DraftAudioController(
    private val store: DraftAudioFileStore,
    private val persistence: DraftAudioPersistence,
    private val synthesize: (DraftAudioInput, File) -> Unit,
    private val cache: AudioCacheSource = AudioCacheSource.disabled,
    private val diagnose: (String) -> Unit = {},
    legacyPaths: Map<AudioItem, String?> = emptyMap(),
    private val initialEpoch: Long? = null,
) {
    private val gate = Any()
    private val inputs = HashMap<AudioItem, DraftAudioInput>()
    private val states = HashMap<AudioItem, DraftAudioState>()
    private val frozenLeases = mutableMapOf<AudioItem, AudioFileLease>()
    private var nextAttemptId = 0L
    private var draftEpoch: Long = initialEpoch?.coerceAtLeast(0L) ?: 0L
    private var frozen: DraftAudioFrozenSnapshot? = null
    private var disposed = false
    private var protectedAtRestore: Set<String> = emptySet()

    init {
        for (item in AudioItem.entries) states[item] = DraftAudioState.Idle
        restore(legacyPaths)
    }

    fun epoch(): Long = synchronized(gate) { draftEpoch }

    fun state(item: AudioItem): DraftAudioState = synchronized(gate) { states[item] ?: DraftAudioState.Idle }

    fun snapshot(): DraftAudioSnapshot = synchronized(gate) {
        DraftAudioSnapshot(draftEpoch, states.toMap(), frozen)
    }

    /**
     * Declares the current inputs for an item. A changed identity revokes any in-flight attempt,
     * retires the invalidated accepted file after the invalidation is persisted, and requires an
     * explicit Continue. Returns true when the visible state changed.
     */
    fun setInput(input: DraftAudioInput): Boolean = synchronized(gate) {
        if (disposed || frozen != null) return false
        val previous = states[input.item] ?: DraftAudioState.Idle
        if (inputs[input.item] == input) return refreshReady(input.item)
        inputs[input.item] = input
        nextAttemptId++
        val next = when {
            previous is DraftAudioState.Ready && previous.identity == input.identity ->
                if (previous.file.isIntact()) previous else DraftAudioState.Failed(
                    input.identity, retryable = true, message = AUDIO_FILE_MISSING,
                )
            previous is DraftAudioState.Failed && (previous.identity.isNullOrBlank() || previous.identity == input.identity) ->
                previous.copy(
                    identity = input.identity,
                    retryable = if (previous.identity.isNullOrBlank()) true else previous.retryable,
                )
            previous is DraftAudioState.NeedsContinue && previous.identity == input.identity -> previous
            else -> DraftAudioState.NeedsContinue(input.identity)
        }
        states[input.item] = next
        val persisted = persistLocked()
        // Retire only once the invalidation is durable: a failed commit must leave an orphan
        // file, never a persisted reference to audio that has been deleted.
        if (persisted && previous is DraftAudioState.Ready && next !is DraftAudioState.Ready) {
            retireAfterCommit(previous.file)
        }
        next != previous
    }

    /** Explicit Continue for a newly affected item. Returns null when no work is authorized. */
    fun confirm(item: AudioItem): DraftAudioAttempt? = synchronized(gate) {
        if (disposed || frozen != null) return null
        val input = inputs[item] ?: return null
        val state = states[item]
        if (state !is DraftAudioState.NeedsContinue || state.identity != input.identity) return null
        beginLocked(input)
    }

    /**
     * Atomically reserves once-only audio work for a fresh Continue into Review. Only inputs
     * already declared for [revision] in NeedsContinue are eligible; restored, Ready, Failed,
     * Generating, and stale-revision items are left alone. The authorization callback must persist
     * its marker synchronously and return false if that commit fails.
     */
    fun beginReviewEntry(revision: Long, authorize: () -> Boolean): List<DraftAudioAttempt> = synchronized(gate) {
        if (disposed || frozen != null || revision < 0) return emptyList()
        val pending = AudioItem.entries.mapNotNull { item ->
            val input = inputs[item] ?: return@mapNotNull null
            val state = states[item]
            input.takeIf {
                input.revision == revision && state is DraftAudioState.NeedsContinue && state.identity == input.identity
            }
        }
        if (pending.isEmpty() || !authorize()) return emptyList()
        pending.mapNotNull(::beginLocked)
    }

    /** Explicit per-item Retry for a failed item; a Ready sibling is left untouched. */
    fun retry(item: AudioItem): DraftAudioAttempt? = synchronized(gate) {
        if (disposed || frozen != null) return null
        val input = inputs[item] ?: return null
        val state = states[item]
        if (state !is DraftAudioState.Failed || !state.retryable || state.identity != input.identity) return null
        beginLocked(input)
    }

    /** Revokes acceptance for an in-flight attempt. Already-submitted provider calls still finish. */
    fun cancel(item: AudioItem): Boolean = synchronized(gate) {
        val state = states[item]
        if (state !is DraftAudioState.Generating) return false
        nextAttemptId++
        states[item] = DraftAudioState.NeedsContinue(state.identity)
        persistLocked()
        true
    }

    /**
     * Runs one authorized attempt on the calling (blocking) thread: cache copy or exactly one
     * provider call, validated staging, atomic publication, token-checked acceptance.
     */
    fun runAttempt(attempt: DraftAudioAttempt): DraftAudioAttemptResult {
        val staging = attempt.staging
        val startedAt = System.nanoTime()
        try {
            if (!isCurrent(attempt.token)) return DraftAudioAttemptResult.Stale
            var fromCache = false
            val lease = try {
                cache.open(attempt.input.cacheKey)
            } catch (cancelled: java.util.concurrent.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                diagnose("audio cache open failed: ${error.javaClass.simpleName}")
                null
            }
            if (lease != null) {
                try {
                    store.stageCopy(lease.file, staging)
                    fromCache = true
                } catch (cancelled: java.util.concurrent.CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    // A valid cache entry that cannot be copied is a local storage problem, not a
                    // miss: fail this attempt instead of paying for a redundant synthesis.
                    diagnose("audio cache copy failed: ${error.javaClass.simpleName}")
                    return failAttempt(
                        attempt,
                        "The saved audio could not be read from the cache. Tap Retry.",
                        retryable = true,
                    )
                } finally {
                    lease.close()
                }
            }
            if (!fromCache) {
                synthesize(attempt.input, staging.file)
                if (!staging.file.isFile || staging.file.length() == 0L) {
                    return failAttempt(attempt, "The audio provider returned no audio. Tap Retry.", retryable = true)
                }
                AudioFileValidator.requireContentFormat(staging.file)
                staging.sync()
                val stored = try {
                    cache.store(attempt.input.cacheKey, staging.file)
                } catch (cancelled: java.util.concurrent.CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    diagnose("audio cache store failed: ${error.javaClass.simpleName}")
                    AudioCacheStoreResult.Failed("cache store failed")
                }
                when (stored) {
                    is AudioCacheStoreResult.Failed -> diagnose("audio cache store failed: ${stored.reason}")
                    else -> Unit
                }
            }
            val published = store.publish(staging, attempt.token.draftEpoch, attempt.token.inputIdentity)
            return acceptLocked(attempt, published, fromCache)
        } catch (cancelled: java.util.concurrent.CancellationException) {
            revokeCancelledAttempt(attempt)
            throw cancelled
        } catch (error: Exception) {
            val diagnostic = diagnosticFor(error, attempt, elapsedMillis(startedAt))
            val retryable = retryable(error)
            return failAttempt(attempt, messageFor(error, diagnostic.category), retryable, diagnostic)
        } finally {
            // The worker owns the staging file until this point; a published file no longer
            // exists under the staging root, so this only removes failed/partial output.
            staging.close()
        }
    }

    /**
     * Freezes both ready items for Send, pinning their files and persisting the frozen paths.
     * An in-flight or unaccepted item makes Send incomplete instead of racing a completion.
     */
    fun freezeForSend(): DraftAudioFreeze = synchronized(gate) {
        if (disposed) return DraftAudioFreeze.Disposed
        frozen?.let { return DraftAudioFreeze.Frozen(it) }
        val missing = AudioItem.entries.filter { item ->
            val state = states[item]
            val input = inputs[item]
            state !is DraftAudioState.Ready || input == null || state.identity != input.identity || !state.file.isIntact()
        }
        if (missing.isNotEmpty()) return DraftAudioFreeze.Incomplete(missing)
        val files = LinkedHashMap<AudioItem, DraftAudioFile>()
        val identities = LinkedHashMap<AudioItem, String>()
        for (item in AudioItem.entries) {
            val state = states[item] as DraftAudioState.Ready
            val input = inputs.getValue(item)
            files[item] = state.file
            identities[item] = input.identity
            frozenLeases[item] = store.lease(state.file, AudioFileOwner.FROZEN_SUBMISSION)
        }
        val snapshot = DraftAudioFrozenSnapshot(draftEpoch, files, identities)
        frozen = snapshot
        val changes = persistChangesLocked()
        if (!persist(changes)) diagnose("could not persist frozen audio references")
        DraftAudioFreeze.Frozen(snapshot)
    }

    /**
     * Releases a Send freeze. `retainFiles` keeps the protection (uncertain Send) and the frozen
     * paths stay persisted for recovery; otherwise the frozen keys are removed.
     */
    fun releaseFreeze(retainFiles: Boolean): DraftAudioFrozenSnapshot? = synchronized(gate) {
        val snapshot = frozen ?: return null
        if (retainFiles) return snapshot
        frozenLeases.values.forEach(AudioFileLease::close)
        frozenLeases.clear()
        frozen = null
        persistLocked()
        snapshot
    }

    /**
     * Paths that must survive retirement: accepted files, frozen files and the files protected
     * at restore. [extra] carries raw recovery path strings (malformed values are ignored).
     */
    fun protectedPaths(extra: Collection<String> = emptyList()): Set<String> = synchronized(gate) {
        val paths = LinkedHashSet<String>(protectedAtRestore)
        for (state in states.values) {
            if (state is DraftAudioState.Ready) paths += state.file.path
        }
        frozen?.files?.values?.forEach { paths += it.path }
        for (raw in extra) {
            if (raw.isBlank()) continue
            runCatching { java.io.File(raw).canonicalPath }.getOrNull()?.let { paths += it }
        }
        paths
    }

    /** Discard: persist the empty state first, then retire accepted files that nobody owns. */
    fun discard(): List<DraftAudioRetirement> = synchronized(gate) {
        if (disposed || frozen != null) return emptyList()
        val accepted = states.values.filterIsInstance<DraftAudioState.Ready>().map { it.file }
        nextAttemptId++
        draftEpoch = maxOf(draftEpoch, persistenceEpoch()) + 1
        inputs.clear()
        for (item in AudioItem.entries) states[item] = DraftAudioState.Idle
        protectedAtRestore = emptySet()
        // A failed commit leaves the files as orphans for the startup sweep; deleting first
        // would leave persisted references pointing at nothing.
        if (!persistLocked()) return emptyList()
        accepted.map { store.retire(it) }
    }

    /**
     * Drops one item's declared input and accepted state, for a configuration change that leaves
     * no valid input at all (unresolved vocabulary, rejected reading). Retirement is gated on the
     * state commit, exactly like [setInput].
     */
    fun clear(item: AudioItem): Boolean = synchronized(gate) {
        if (disposed || frozen != null) return false
        val previous = states[item] ?: DraftAudioState.Idle
        val hadInput = inputs.remove(item) != null
        nextAttemptId++
        states[item] = DraftAudioState.Idle
        val persisted = persistLocked()
        if (persisted && previous is DraftAudioState.Ready) retireAfterCommit(previous.file)
        hadInput || previous !is DraftAudioState.Idle
    }

    /** Stops all later acceptance. Files are left to ownership-aware retirement/sweep. */
    fun dispose() {
        synchronized(gate) {
            disposed = true
            nextAttemptId++
            frozenLeases.values.forEach(AudioFileLease::close)
            frozenLeases.clear()
        }
    }

    private fun beginLocked(input: DraftAudioInput): DraftAudioAttempt? {
        val attemptId = ++nextAttemptId
        val token = DraftAudioToken(draftEpoch, input.item, input.identity, attemptId)
        val staging = try {
            store.beginAttempt(input.item, attemptId, draftEpoch, input.expectedFormat)
        } catch (error: Exception) {
            states[input.item] = DraftAudioState.Failed(
                input.identity,
                retryable = true,
                message = "Could not prepare draft audio storage. Tap Retry.",
            )
            persistLocked()
            diagnose("audio staging unavailable: ${error.javaClass.simpleName}")
            return null
        }
        states[input.item] = DraftAudioState.Generating(input.identity, attemptId)
        if (!persistLocked()) {
            states[input.item] = DraftAudioState.NeedsContinue(input.identity)
            staging.close()
            diagnose("audio attempt not started because its pending state could not be persisted")
            return null
        }
        return DraftAudioAttempt(token, input, staging)
    }

    private fun acceptLocked(attempt: DraftAudioAttempt, file: DraftAudioFile, fromCache: Boolean): DraftAudioAttemptResult {
        return synchronized(gate) {
            if (!isCurrentLocked(attempt.token)) {
                store.retire(file)
                return@synchronized DraftAudioAttemptResult.Stale
            }
            val previous = states[attempt.token.item]
            states[attempt.token.item] = DraftAudioState.Ready(attempt.token.inputIdentity, file)
            if (!persistLocked()) {
                states[attempt.token.item] = DraftAudioState.Failed(
                    attempt.token.inputIdentity, retryable = true, message = "Could not save audio state. Tap Retry.",
                )
                store.retire(file)
                return@synchronized DraftAudioAttemptResult.Failed("Could not save audio state. Tap Retry.", true)
            }
            if (previous is DraftAudioState.Ready && previous.file.path != file.path) {
                when (val retirement = store.retire(previous.file)) {
                    DraftAudioRetirement.RETIRED, DraftAudioRetirement.ALREADY_GONE ->
                        protectedAtRestore = protectedAtRestore - previous.file.path
                    else -> diagnose("previous draft audio not retired: $retirement")
                }
            }
            DraftAudioAttemptResult.Accepted(file, fromCache)
        }
    }

    private fun failAttempt(
        attempt: DraftAudioAttempt,
        message: String,
        retryable: Boolean,
        diagnostic: DraftAudioDiagnostic? = null,
    ): DraftAudioAttemptResult {
        val token = attempt.token
        return synchronized(gate) {
            if (!isCurrentLocked(token)) return@synchronized DraftAudioAttemptResult.Stale
            states[token.item] = DraftAudioState.Failed(token.inputIdentity, retryable, message, diagnostic)
            persistLocked()
            DraftAudioAttemptResult.Failed(message, retryable, diagnostic)
        }
    }

    private fun isCurrent(token: DraftAudioToken): Boolean = synchronized(gate) { isCurrentLocked(token) }

    /** Revokes acceptance at once when the worker itself is cancelled mid-flight. */
    private fun revokeCancelledAttempt(attempt: DraftAudioAttempt) = synchronized(gate) {
        val token = attempt.token
        if (!isCurrentLocked(token)) return@synchronized
        nextAttemptId++
        states[token.item] = DraftAudioState.NeedsContinue(token.inputIdentity)
        persistLocked()
    }

    private fun isCurrentLocked(token: DraftAudioToken): Boolean {
        if (disposed || token.draftEpoch != draftEpoch) return false
        val state = states[token.item]
        val input = inputs[token.item]
        return state is DraftAudioState.Generating && state.attemptId == token.attemptId &&
            state.identity == token.inputIdentity && input?.identity == token.inputIdentity
    }

    private fun refreshReady(item: AudioItem): Boolean {
        val state = states[item] ?: return false
        if (state !is DraftAudioState.Ready) return false
        if (state.file.isIntact()) return false
        states[item] = DraftAudioState.Failed(state.identity, retryable = true, message = AUDIO_FILE_MISSING)
        persistLocked()
        return true
    }

    private fun retireAfterCommit(file: DraftAudioFile) {
        when (val retirement = store.retire(file)) {
            DraftAudioRetirement.RETIRED, DraftAudioRetirement.ALREADY_GONE ->
                protectedAtRestore = protectedAtRestore - file.path
            else -> diagnose("draft audio not retired: $retirement")
        }
    }

    private fun restore(legacyPaths: Map<AudioItem, String?>) {
        val storedEpoch = persistence.read(DraftAudioPreferences.EPOCH)?.toLongOrNull()
        if (initialEpoch == null && storedEpoch != null && storedEpoch > draftEpoch) draftEpoch = storedEpoch
        val version = persistence.read(DraftAudioPreferences.VERSION)
        val protected = LinkedHashSet<String>()
        for (item in AudioItem.entries) {
            val identity = persistence.read(DraftAudioPreferences.identity(item)).orEmpty()
            val status = persistence.read(DraftAudioPreferences.status(item))
            val file = DraftAudioFile.decode(
                persistence.read(DraftAudioPreferences.path(item)),
                persistence.read(DraftAudioPreferences.format(item)),
                persistence.read(DraftAudioPreferences.size(item))?.toLongOrNull(),
                persistence.read(DraftAudioPreferences.modified(item))?.toLongOrNull(),
            )
            val frozenPath = persistence.read(DraftAudioPreferences.frozenPath(item))
            val persistedFailureRetryable = persistence.read(DraftAudioPreferences.failureRetryable(item))
                ?.toBooleanStrictOrNull()
            val restoredDiagnostic = readDiagnostic(item)
            if (!frozenPath.isNullOrBlank()) {
                runCatching { java.io.File(frozenPath).canonicalPath }.getOrNull()?.let { protected += it }
            }
            states[item] = when {
                version != DraftAudioPreferences.VERSION_VALUE -> {
                    if (legacyPaths[item].isNullOrBlank()) DraftAudioState.Idle
                    else DraftAudioState.Failed(identity, retryable = true, message = LEGACY_AUDIO_STATE)
                }
                status == DraftAudioPreferences.STATUS_READY && file != null && file.isIntact() &&
                    identity.isNotBlank() -> {
                    protected += file.path
                    DraftAudioState.Ready(identity, file)
                }
                status == DraftAudioPreferences.STATUS_READY ->
                    DraftAudioState.Failed(identity, retryable = true, message = AUDIO_FILE_MISSING)
                status == null && identity.isBlank() -> DraftAudioState.Idle
                else -> DraftAudioState.Failed(
                    identity,
                    retryable = persistedFailureRetryable ?: true,
                    message = INTERRUPTED_AUDIO_STATE,
                    diagnostic = restoredDiagnostic,
                )
            }
        }
        protectedAtRestore = protected
        // Normalise the snapshot once: pending/interrupted states become explicit retryable
        // failures, and no provider work is started here.
        persistLocked()
    }

    private fun persistenceEpoch(): Long =
        persistence.read(DraftAudioPreferences.EPOCH)?.toLongOrNull() ?: draftEpoch

    private fun persistLocked(): Boolean = persist(persistChangesLocked())

    private fun persist(changes: Map<String, String?>): Boolean =
        runCatching { persistence.write(changes) }.getOrElse { error ->
            diagnose("audio state persistence failed: ${error.javaClass.simpleName}")
            false
        }

    private fun persistChangesLocked(): Map<String, String?> {
        val changes = LinkedHashMap<String, String?>()
        changes[DraftAudioPreferences.VERSION] = DraftAudioPreferences.VERSION_VALUE
        changes[DraftAudioPreferences.EPOCH] = draftEpoch.toString()
        for (item in AudioItem.entries) {
            val state = states[item]
            val identity = when (state) {
                is DraftAudioState.Ready -> state.identity
                is DraftAudioState.Failed -> state.identity
                is DraftAudioState.Generating -> state.identity
                is DraftAudioState.NeedsContinue -> state.identity
                DraftAudioState.Idle, null -> inputs[item]?.identity
            }
            changes[DraftAudioPreferences.identity(item)] = identity
            when (state) {
                is DraftAudioState.Ready -> {
                    changes[DraftAudioPreferences.status(item)] = DraftAudioPreferences.STATUS_READY
                    changes[DraftAudioPreferences.path(item)] = state.file.path
                    changes[DraftAudioPreferences.format(item)] = state.file.format
                    changes[DraftAudioPreferences.size(item)] = state.file.size.toString()
                    changes[DraftAudioPreferences.modified(item)] = state.file.modified.toString()
                }
                is DraftAudioState.Generating -> {
                    changes[DraftAudioPreferences.status(item)] = DraftAudioPreferences.STATUS_PENDING
                    clearFileKeys(changes, item)
                }
                is DraftAudioState.Failed -> {
                    changes[DraftAudioPreferences.status(item)] = DraftAudioPreferences.STATUS_FAILED
                    clearFileKeys(changes, item)
                }
                else -> {
                    changes[DraftAudioPreferences.status(item)] = null
                    clearFileKeys(changes, item)
                }
            }
            if (state is DraftAudioState.Failed) {
                changes[DraftAudioPreferences.failureRetryable(item)] = state.retryable.toString()
                writeDiagnostic(changes, item, state.diagnostic)
            } else {
                changes[DraftAudioPreferences.failureRetryable(item)] = null
                writeDiagnostic(changes, item, null)
            }
            val frozenFile = frozen?.files?.get(item)
            changes[DraftAudioPreferences.frozenPath(item)] = frozenFile?.path
        }
        return changes
    }

    private fun clearFileKeys(changes: MutableMap<String, String?>, item: AudioItem) {
        changes[DraftAudioPreferences.path(item)] = null
        changes[DraftAudioPreferences.format(item)] = null
        changes[DraftAudioPreferences.size(item)] = null
        changes[DraftAudioPreferences.modified(item)] = null
    }

    private fun writeDiagnostic(
        changes: MutableMap<String, String?>,
        item: AudioItem,
        diagnostic: DraftAudioDiagnostic?,
    ) {
        val fields = mapOf(
            "category" to diagnostic?.category?.takeIf { category -> SpeechFailure.entries.any { it.name == category } },
            "http_status" to diagnostic?.httpStatus?.takeIf { it in 100..599 }?.toString(),
            "google_status" to diagnostic?.googleStatus?.takeIf(::isSafeDiagnosticToken),
            "google_reason" to diagnostic?.googleReason?.takeIf(::isSafeDiagnosticToken),
            "exception_class" to diagnostic?.exceptionClass?.takeIf { it.matches(SAFE_EXCEPTION_CLASS) },
            "duration_ms" to diagnostic?.durationMillis?.takeIf { it >= 0 }?.toString(),
            "revision" to diagnostic?.revision?.takeIf { it >= 0 }?.toString(),
            "attempt_id" to diagnostic?.attemptId?.takeIf { it >= 0 }?.toString(),
            "input_bytes" to diagnostic?.inputBytes?.takeIf { it >= 0 }?.toString(),
            "request_bytes" to diagnostic?.requestBytes?.takeIf { it >= 0 }?.toString(),
            "voice_name" to diagnostic?.voiceName?.takeIf { it.matches(SAFE_VOICE_NAME) },
            "hint_count" to diagnostic?.hintCount?.takeIf { it >= 0 }?.toString(),
        )
        fields.forEach { (field, value) ->
            changes[DraftAudioPreferences.diagnostic(item, field)] = value
        }
    }

    private fun readDiagnostic(item: AudioItem): DraftAudioDiagnostic? {
        fun read(field: String): String? = persistence.read(DraftAudioPreferences.diagnostic(item, field))
        val category = read("category")?.takeIf { it in SpeechFailure.entries.map(SpeechFailure::name) } ?: return null
        val duration = read("duration_ms")?.toLongOrNull()?.takeIf { it >= 0 } ?: return null
        val revision = read("revision")?.toLongOrNull()?.takeIf { it >= 0 } ?: return null
        val attemptId = read("attempt_id")?.toLongOrNull()?.takeIf { it >= 0 } ?: return null
        val httpStatus = read("http_status")?.toIntOrNull()?.takeIf { it in 100..599 }
        val hintCount = read("hint_count")?.toIntOrNull()?.takeIf { it >= 0 }
        val inputBytes = read("input_bytes")?.toIntOrNull()?.takeIf { it >= 0 }
        val requestBytes = read("request_bytes")?.toIntOrNull()?.takeIf { it >= 0 }
        val googleStatus = read("google_status")?.takeIf(::isSafeDiagnosticToken)
        val googleReason = read("google_reason")?.takeIf(::isSafeDiagnosticToken)
        val exceptionClass = read("exception_class")?.takeIf { it.matches(SAFE_EXCEPTION_CLASS) }
        val voiceName = read("voice_name")?.takeIf { it.matches(SAFE_VOICE_NAME) }
        return DraftAudioDiagnostic(
            category, httpStatus, googleStatus, googleReason, exceptionClass, duration,
            item, revision, attemptId, inputBytes, requestBytes, voiceName, hintCount,
        )
    }

    private fun isSafeDiagnosticToken(value: String): Boolean =
        value.length in 1..80 && value.all {
            it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it in "_.-"
        }

    private fun diagnosticFor(error: Exception, attempt: DraftAudioAttempt, elapsedMillis: Long): DraftAudioDiagnostic {
        val speechError = error as? SpeechException
        val speechDiagnostic = speechError?.diagnostic
        val request = speechDiagnostic?.request
        val category = speechError?.failure?.name ?: when (error) {
            is SocketTimeoutException -> "TIMEOUT"
            is UnknownHostException -> "DNS"
            is SSLException -> "TLS"
            is AudioSourceMissingException, is java.io.FileNotFoundException -> "SOURCE_MISSING"
            is IllegalArgumentException -> "INVALID_REQUEST"
            is IllegalStateException -> "SETUP"
            is IOException -> "NETWORK"
            else -> "UNKNOWN"
        }
        return DraftAudioDiagnostic(
            category = category,
            httpStatus = speechDiagnostic?.httpStatus?.takeIf { it in 100..599 },
            googleStatus = speechDiagnostic?.googleStatus?.takeIf(::isSafeDiagnosticToken),
            googleReason = speechDiagnostic?.googleReason?.takeIf(::isSafeDiagnosticToken),
            exceptionClass = if (speechDiagnostic != null) {
                speechDiagnostic.exceptionClass?.takeIf { it.matches(SAFE_EXCEPTION_CLASS) }
            } else {
                error.javaClass.simpleName.takeIf { it.matches(SAFE_EXCEPTION_CLASS) }
            },
            durationMillis = (speechDiagnostic?.durationMillis ?: elapsedMillis).coerceAtLeast(0L),
            item = attempt.input.item,
            revision = attempt.input.revision,
            attemptId = attempt.token.attemptId,
            inputBytes = request?.inputBytes?.takeIf { it >= 0 }
                ?: attempt.input.text.toByteArray(Charsets.UTF_8).size,
            requestBytes = request?.requestBytes?.takeIf { it >= 0 },
            voiceName = request?.voiceName?.takeIf { it.matches(SAFE_VOICE_NAME) }
                ?: attempt.input.cacheKey.voice.takeIf { it.matches(SAFE_VOICE_NAME) },
            hintCount = request?.hintCount?.takeIf { it >= 0 },
        )
    }

    private fun retryable(error: Exception): Boolean = when (error) {
        is SpeechException -> error.retryable
        is SocketTimeoutException, is UnknownHostException, is SSLException, is IOException ->
            error !is AudioSourceMissingException && error !is java.io.FileNotFoundException
        is IllegalArgumentException, is IllegalStateException -> false
        else -> false
    }

    private fun messageFor(error: Exception, category: String): String =
        (error as? SpeechException)?.message?.takeIf(String::isNotBlank) ?: when (category) {
            "TIMEOUT" -> "The audio service timed out. Tap Retry."
            "DNS" -> "The audio service host could not be resolved. Check the connection and settings."
            "TLS" -> "A secure connection to the audio service failed. Check the device date and network."
            "NETWORK" -> "The audio service could not be reached. Tap Retry when it is available."
            "SOURCE_MISSING" -> "No recording is available for this selected entry."
            "INVALID_REQUEST" -> "The audio request is invalid. Correct the input or settings."
            "SETUP", "AUTHORIZATION", "API_NOT_ENABLED", "BILLING" ->
                "Audio generation is not configured. Check the Google Cloud settings."
            "QUOTA" -> "The audio service quota was reached. Retry later."
            "INVALID_AUDIO", "INVALID_RESPONSE" -> "The audio service returned unusable audio. Tap Retry."
            else -> "Audio generation failed. Check the input and settings before retrying."
        }

    private fun elapsedMillis(started: Long): Long =
        ((System.nanoTime() - started).coerceAtLeast(0L) / 1_000_000L)

    private companion object {
        const val AUDIO_FILE_MISSING = "The saved audio file is missing. Tap Retry to generate it again."
        const val INTERRUPTED_AUDIO_STATE = "Audio generation was interrupted. Tap Retry to generate it again."
        const val LEGACY_AUDIO_STATE = "This audio was saved by an older version. Tap Retry to generate it again."
        val SAFE_EXCEPTION_CLASS = Regex("[A-Za-z0-9_\$]{1,128}")
        val SAFE_VOICE_NAME = Regex("[A-Za-z0-9_.-]{1,128}")
    }
}
