package dev.pierre.japaneseanki

import java.io.File

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

    data class Failed(val message: String, val retryable: Boolean) : DraftAudioAttemptResult
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
                previous.copy(identity = input.identity, retryable = true)
            previous is DraftAudioState.NeedsContinue && previous.identity == input.identity -> previous
            else -> DraftAudioState.NeedsContinue(input.identity)
        }
        states[input.item] = next
        persistLocked()
        if (previous is DraftAudioState.Ready && next !is DraftAudioState.Ready) retireAfterCommit(previous.file)
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
            return failAttempt(attempt, messageFor(error), retryable = true)
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
        persistLocked()
        accepted.map { store.retire(it) }
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
        persistLocked()
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

    private fun failAttempt(attempt: DraftAudioAttempt, message: String, retryable: Boolean): DraftAudioAttemptResult {
        val token = attempt.token
        return synchronized(gate) {
            if (!isCurrentLocked(token)) return@synchronized DraftAudioAttemptResult.Stale
            states[token.item] = DraftAudioState.Failed(token.inputIdentity, retryable, message)
            persistLocked()
            DraftAudioAttemptResult.Failed(message, retryable)
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
                else -> DraftAudioState.Failed(identity, retryable = true, message = INTERRUPTED_AUDIO_STATE)
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

    private fun messageFor(error: Exception): String {
        val message = error.message?.trim().orEmpty()
        if (message.isEmpty() || message.length > MAX_MESSAGE) return "Audio generation failed. Tap Retry."
        return message
    }

    private companion object {
        const val AUDIO_FILE_MISSING = "The saved audio file is missing. Tap Retry to generate it again."
        const val INTERRUPTED_AUDIO_STATE = "Audio generation was interrupted. Tap Retry to generate it again."
        const val LEGACY_AUDIO_STATE = "This audio was saved by an older version. Tap Retry to generate it again."
        const val MAX_MESSAGE = 300
    }
}
