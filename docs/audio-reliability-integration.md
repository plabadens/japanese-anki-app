# Audio reliability: integration handoff (items 1, 5, 4)

This bundle delivers the audio storage fix, the production draft-audio controller and the
ownership-aware retirement machinery. `MainActivity.kt` and `SubmissionRecovery.kt` are
deliberately untouched: this document is the exact wiring recipe for the integration owner.

Everything below is compiled and exercised by JVM unit tests plus an on-device playback test.
Nothing in this bundle runs in production until the wiring below lands.

## 1. Owned modules and APIs

| File | API | Purpose |
| --- | --- | --- |
| `AudioProvider.kt` | `AudioFormat` (`WAV`, `OGG`, `MP3`; `extensions`, `fromFilename`) | Single list of supported containers. |
| `AudioProvider.kt` | `AudioFileValidator.detect/requireContentFormat/requireFormat/requireSupported` | Content-first validation; name extension must agree with the bytes. |
| `AudioGeneration.kt` | `AudioFileOperations` | Injectable move/delete/fsync seam (deterministic failure tests). |
| `AudioGeneration.kt` | `ReusableAudioCache.get/open/put/store/clear` | MP3 lookup fixed; `open` returns a lease that pins the entry against clear/eviction; `store` is best-effort. |
| `AudioGeneration.kt` | `AudioCacheLease`, `AudioCacheStoreResult`, `AudioCacheSource` | Cache ownership and the controller's cache boundary. |
| `AudioGeneration.kt` | `AudioDraftMaterializer.materialize` | Validated temp -> fsync -> validate -> atomic publish. |
| `DraftAudioStore.kt` | `DraftAudioFileStore` | Finals under `filesDir/draft` (direct children), private staging, publish, retire, sweep. |
| `DraftAudioStore.kt` | `DraftAudioFile`, `DraftAudioStaging`, `AudioFileOwner`, `AudioOwnershipRegistry.process` | Persistable file metadata, worker-owned staging, process-visible leases. |
| `DraftAudioController.kt` | `DraftAudioController` | Per-item state machine, explicit Continue/Retry, token-checked acceptance, persistence, Send freeze. |
| `DraftAudioController.kt` | `DraftAudioPersistence`, `DraftAudioPreferences`, `DraftAudioInput`, `DraftAudioState`, `DraftAudioAttempt`, `DraftAudioAttemptResult`, `DraftAudioFreeze`, `DraftAudioSnapshot` | Persistence seam, key/value contract and immutable UI/worker types. |

`RevisionedAudioResult` in `AudioGeneration.kt` is now `@Deprecated`. **Delete it and the two
revision tests in `AudioGenerationTest` only after `MainActivity` is wired to
`DraftAudioController`.** The controller carries strictly stronger revision/retry/restart rules,
so keeping the old class afterwards is duplicated lifecycle code.

## 2. Persistence

`DraftAudioPersistence` is a synchronous string map. The adapter must commit, not `apply()`:

```kotlin
class SharedPreferencesDraftAudioPersistence(
    private val preferences: SharedPreferences,
) : DraftAudioPersistence {
    override fun read(key: String): String? = preferences.getString(key, null)

    override fun write(changes: Map<String, String?>): Boolean {
        val editor = preferences.edit()
        for ((key, value) in changes) {
            if (value == null) editor.remove(key) else editor.putString(key, value)
        }
        return editor.commit()
    }
}
```

Keys (`DraftAudioPreferences`), all top-level prefs strings:

```
draft_audio_version                 "1"
draft_audio_epoch                   monotonic draft epoch
draft_audio_sentence_identity       DraftAudioIdentity.sentence(...)
draft_audio_sentence_status         ready | pending | failed
draft_audio_sentence_path           canonical absolute path (ready only)
draft_audio_sentence_format         wav | ogg | mp3 (ready only)
draft_audio_sentence_size           decimal bytes (ready only)
draft_audio_sentence_modified       decimal epoch millis (ready only)
draft_audio_sentence_frozen_path    frozen-Send raw path (uncertain Send only)
draft_audio_word_*                  same seven keys for the vocabulary recording
```

Migration: pass the old path-only keys as `legacyPaths` when constructing the controller:

```kotlin
val controller = DraftAudioController(
    store = draftAudioStore,
    persistence = audioPersistence,
    cache = reusableAudioCache,
    synthesize = ::synthesizeDraftAudio,
    legacyPaths = mapOf(
        AudioItem.SENTENCE to preferences.getString("sentence_audio_path", null),
        AudioItem.VOCABULARY to preferences.getString("word_audio_path", null),
    ),
)
```

A missing version marker (or a non-ready status, pending generation, corrupt/missing file) is
restored as `DraftAudioState.Failed(retryable = true)`. Restore never calls a provider. After
wiring, delete the `sentence_audio_path` / `word_audio_path` writes and reads; the controller is
the only writer of draft audio state.

## 3. MainActivity wiring

### 3.1 Construct once per draft generation

```kotlin
val draftAudioStore = remember {
    DraftAudioFileStore(
        finalsDirectory = File(filesDir, "draft"),        // unchanged: FileProvider + Anki import root
        stagingDirectory = File(filesDir, "audio-staging"), // private, NOT under draft/
    )
}
val audioPersistence = remember { SharedPreferencesDraftAudioPersistence(preferences) }
val audioController = remember(draftGeneration) {
    DraftAudioController(
        store = draftAudioStore,
        persistence = audioPersistence,
        cache = reusableAudioCache,          // ReusableAudioCache implements AudioCacheSource
        synthesize = ::synthesizeDraftAudio,
        legacyPaths = mapOf(/* as above */),
    )
}
```

One controller per draft generation. `discard()` reuses the same instance (new epoch, cleared
state); disposing the controller on Activity recreation is fine because a new instance restores
from persistence with zero provider calls.

### 3.2 Declare inputs instead of generating

Replace the `LaunchedEffect(...)` block at `MainActivity.kt` ~1276-1460 with input declaration.
Everything needed by the provider must be captured here, not read later:

```kotlin
LaunchedEffect(generationStarted, route, submissionLocked, currentSentenceIdentity.value,
               currentWordIdentity.value) {
    if (!generationStarted || submissionLocked) return@LaunchedEffect

    // Sentence: request + key exactly as today, but stored in the input.
    val sentenceVoice = /* resolved voice or null */
    if (sentenceVoice != null && hintResult is HintResult.Ready) {
        val request = SentenceSpeechRequest(sentenceText, sentenceVoice, hintResult.hints)
        audioController.setInput(
            DraftAudioInput(
                item = AudioItem.SENTENCE,
                identity = currentSentenceIdentity.value,
                text = sentenceText,
                revision = 0,
                cacheKey = AudioCacheKey(
                    source = "google-chirp3-hd",
                    text = sentenceText,
                    voice = sentenceVoice.name,
                    rate = request.speakingRate.toFloat(),
                    hintIdentity = ReadingHintBuilder.cacheFragment(hintResult.hints),
                ),
                expectedFormat = AudioFormat.MP3,
                providerPayload = request,
            ),
        )
    }

    // Vocabulary: NHK Ogg, entry identity as today.
    if (entry != null) {
        audioController.setInput(
            DraftAudioInput(
                item = AudioItem.VOCABULARY,
                identity = currentWordIdentity.value,
                text = vocabulary,
                revision = 0,
                entryIdentity = wordEntryIdentity,
                cacheKey = AudioCacheKey(
                    source = "nhk-2016", text = vocabulary, voice = "NHK-2016", rate = 1f,
                    entryIdentity = wordEntryIdentity,
                ),
                expectedFormat = AudioFormat.OGG,
                providerPayload = AudioSynthesisRequest(
                    AudioItem.VOCABULARY, vocabulary, 0, wordEntryIdentity,
                ),
            ),
        )
    }
}
```

`setInput` never generates. Voice/hint/entity errors currently surfaced as
`sentenceAudio.error` become a `DraftAudioState.Failed` (or no input) owned by the caller: keep
setting the existing message state when `voice == null` / `HintResult.Impossible` / `entry ==
null`, because those are configuration errors rather than provider failures.

### 3.3 Continue and Retry

The new explicit authorization is a UI affordance you must add. Current behaviour auto-generated
on every affected input; the controller requires Continue for each affected item and Retry for a
failure.

```kotlin
fun onContinue(item: AudioItem) {
    val attempt = audioController.confirm(item) ?: return
    coroutineScope.launch { withContext(Dispatchers.IO) { audioController.runAttempt(attempt) } }
}

fun onRetry(item: AudioItem) {
    val attempt = audioController.retry(item) ?: return
    coroutineScope.launch { withContext(Dispatchers.IO) { audioController.runAttempt(attempt) } }
}
```

`runAttempt` is blocking by design: one provider call at most, cache copy, atomic publish and
acceptance all happen on the IO dispatcher. A cache *miss* (including an unreadable cache) calls
the provider once; a valid cache entry that fails to copy fails the item with a local error
instead of triggering a billable synthesis. The `synthesize` lambda must write the completed
audio into the `File` it is given and must not invent its own destination; the controller owns
that staging file and publishes it. Coroutine cancellation does not release the staging
file early; the worker returns, then acceptance is rejected because the token was revoked by
`cancel`/`setInput`/`discard`.

Map state to the existing presentation (no UI redesign):

```kotlin
fun present(state: DraftAudioState, playing: Boolean): ReviewAudioPresentation = when (state) {
    is DraftAudioState.Ready -> if (playing) ReviewAudioPresentation.Playing else ReviewAudioPresentation.Ready
    is DraftAudioState.Generating -> ReviewAudioPresentation.Generating
    is DraftAudioState.NeedsContinue -> ReviewAudioPresentation.Failed("Tap Generate to create this audio.")
    is DraftAudioState.Failed -> ReviewAudioPresentation.Failed(state.message)
    DraftAudioState.Idle -> ReviewAudioPresentation.Unavailable
}
```

Enable Continue for `NeedsContinue`, Retry for `Failed(retryable = true)`. Never call `confirm`
from recomposition or navigation; only from the user's tap.

### 3.4 Playback

Play from `DraftAudioState.Ready.file.path` and hold a lease while the player is alive:

```kotlin
fun playAudio(item: AudioItem, file: DraftAudioFile) {
    stopAudioPlayback()                       // releases the previous lease inside stopAudioPlayback

    val lease = draftAudioStore.lease(file, AudioFileOwner.PLAYBACK)
    val player = MediaPlayer()
    try {
        player.setDataSource(file.path)
        player.setOnCompletionListener { stopAudioPlayback() }
        player.prepare()
        player.start()
        audioPlayer = player
        audioLease = lease
    } catch (_: Exception) {
        lease.close()
        player.release()
        // surface the existing "could not be played" message
    }
}

fun stopAudioPlayback() {
    audioPlayer?.runCatching { if (isPlaying) stop(); release() }
    audioPlayer = null
    audioLease?.close()
    audioLease = null
}
```

`onDestroy` must keep calling `stopAudioPlayback()` (now also releasing the lease).

### 3.5 Send: freeze before import

Replace every read of mutable Compose audio state inside the Send job with the frozen snapshot:

```kotlin
val frozen = when (val freeze = audioController.freezeForSend()) {
    is DraftAudioFreeze.Frozen -> freeze.snapshot
    is DraftAudioFreeze.Incomplete -> {
        submitMessage = "Wait for or retry the missing audio before sending."
        return@launch
    }
    DraftAudioFreeze.Disposed -> return@launch
}

val sentenceFile = frozen.files.getValue(AudioItem.SENTENCE).file()
val wordFile = frozen.files.getValue(AudioItem.VOCABULARY).file()
```

`freezeForSend` validates both files and takes `FROZEN_SUBMISSION` leases, so an import cannot
read a file that is being retired. `FrozenSubmission.sentenceAudio/wordAudio` should be built
from the same immutable snapshot, not from `sentenceAudio.path`.

* Successful note completion: `audioController.releaseFreeze(retainFiles = false)`.
* Uncertain Send / recovery pending: `audioController.releaseFreeze(retainFiles = true)` and keep
  the normal `SubmissionRecovery` snapshot. The frozen leases stay held and
  `draft_audio_*_frozen_path` stays persisted, so the source survives retirement.
* Verified completion or explicit local discard: release and then `audioController.discard()` if
  the draft is being thrown away.

While a freeze is active the controller refuses `setInput`/`confirm`/`retry`/`discard`; this is
intentional. Do not add audio editing paths that bypass it.

### 3.6 Discard and startup sweep

Replace the manual audio resets at `MainActivity.kt` 602-616 / 972 / 992 / 1018 / 1055 with:

```kotlin
audioController.discard()   // persists the empty state first, then retires unowned files
```

Run the orphan sweep once at startup, after the controller is constructed (so it can supply live
references) and on `Dispatchers.IO`, before any new generation:

```kotlin
val report = draftAudioStore.sweep(
    audioController.protectedPaths(
        listOfNotNull(
            preferences.getString("sentence_audio_path", null),
            preferences.getString("word_audio_path", null),
            preferences.getString(SubmissionRecoveryKeys.SENTENCE_AUDIO_PATH, null),
            preferences.getString(SubmissionRecoveryKeys.WORD_AUDIO_PATH, null),
            preferences.getString(DraftAudioPreferences.frozenPath(AudioItem.SENTENCE), null),
            preferences.getString(DraftAudioPreferences.frozenPath(AudioItem.VOCABULARY), null),
        ),
    ),
)
Log.i("DraftAudio", "retired=${report.deleted.size} kept=${report.protectedFiles.size} " +
    "unknown=${report.retainedUnknown.size} failed=${report.failures.size}")
```

The sweep is ownership-based, not age-based: it only touches direct children of
`filesDir/draft` and `filesDir/audio-staging` whose names it recognises. Images, unknown names,
symlinks, files outside those roots and AnkiDroid media are never deleted. A failed delete is
reported and retried on the next start; a failed preference commit leaves an orphan, never a
reference to deleted audio. Do not enable the sweep until the wiring above is in place and the
controller tests are green.

## 4. Test harness handoff (integrator: core manifest)

`scripts/test-core.sh` is owned by integration. Add these sources to the `K2JVMCompiler` list and
these classes to `JUnitCore`:

```
# sources, after app/src/main/java/dev/pierre/japaneseanki/AudioGeneration.kt
  app/src/main/java/dev/pierre/japaneseanki/DraftAudioStore.kt \
  app/src/main/java/dev/pierre/japaneseanki/DraftAudioController.kt \

# test sources, after .../Nhk2016PronunciationSourceTest.kt
  app/src/test/java/dev/pierre/japaneseanki/AudioFixtures.kt \
  app/src/test/java/dev/pierre/japaneseanki/AudioReliabilityTest.kt \
  app/src/test/java/dev/pierre/japaneseanki/DraftAudioStoreTest.kt \
  app/src/test/java/dev/pierre/japaneseanki/DraftAudioControllerTest.kt \

# runner classes
  dev.pierre.japaneseanki.AudioReliabilityTest \
  dev.pierre.japaneseanki.DraftAudioStoreTest \
  dev.pierre.japaneseanki.DraftAudioControllerTest \
```

These sources use only `java.io`, `java.nio.file`, `java.security` and JUnit: no Android,
coroutines or Compose types. `DraftAudioController` deliberately exposes a blocking
`runAttempt` so the standalone harness can drive completions deterministically.

## 5. Native acceptance

`app/src/androidTest/java/dev/pierre/japaneseanki/DraftAudioPlaybackTest.kt` runs two tests on a
real device:

1. For WAV, Ogg and MP3, a fixture provider writes the container into the private staging file,
   the controller publishes it, and `MediaPlayer.prepare()`/`start()` must report a non-zero
   duration and play it.
2. A `PLAYBACK` lease blocks retirement while a real `MediaPlayer` is playing; after release the
   file is retired.

Fixtures: the WAV is generated on device; `app/src/androidTest/assets/tone.ogg` and `tone.mp3`
are 0.6 s 440 Hz tones produced locally with ffmpeg (`libvorbis` and `libmp3lame`) and checked in
with the test APK. No network, credential, paid call or NHK download is involved.

```
sha256(tone.ogg) = dd9a23e882552c2f2b555d8d34108ace474629c80bf51c20cb838016c76dfee1
sha256(tone.mp3) = d17bf96be1a23cb75b4d407bd91cf3fff77b667ad26e0c5d16322229e1cc38a9
```

## 6. Deliberately not done here

* No `MainActivity.kt`, `SubmissionRecovery.kt`, Gradle, flake, script or `test-core.sh` edits.
* No production call to `sweep`, `publish` or the controller yet: retirement stays disabled
  until the wiring above exists.
* No UI redesign: `NeedsContinue` is presented through the existing failed/unavailable control
  with a Generate action; that is the only new affordance.
* No Android `SharedPreferences` adapter is checked in (it would be an unused stub); the adapter
  is the eight lines in §2 for the integrator to add beside `MainActivity`.

## 7. Integration status (consumed)

This recipe has been applied in the integration branch:

* `SharedPreferencesDraftAudioPersistence` lives beside `MainActivity` (as specified in §2).
* One controller per draft generation, rebuilt after `discard()`; `DisposableEffect` disposes it.
* `MainActivity` declares inputs instead of generating, wires Continue/Retry, plays through a
  `PLAYBACK` lease, freezes before import, releases the freeze on every Send outcome (retained on
  an uncertain Send), runs the startup sweep and calls `discard()` on a draft reset.
* `RevisionedAudioResult`, `AudioGenerationState` and `AudioRequestIdentity`, and their two
  `AudioGenerationTest` cases, were deleted after the wiring landed.
* `DraftAudioStore.sweep()` now filters the staging root by recognised names.
* `discard()`/`setInput()` only retire files after the state commit succeeded; `clear(item)` was
  added for an item that has no input at all.
* `scripts/test-core.sh` carries the source/test/runner entries from §4 (183 tests).

