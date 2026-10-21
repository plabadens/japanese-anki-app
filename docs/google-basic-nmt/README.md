# Google Cloud Translation Basic v2 NMT with one shared Google Cloud key

Implementation evidence for SPEC §3, §4.7, §7.2, §10, §11, §14 R14/R27/R30, §15.

## Revision and environment

- Branch `japanese-anki-app/t_e7982c2a-use-google-translation-basic-nmt-and-one`.
- Commits: `6dce438` adapter, shared credential, failure classification and tests; `cb655fa`
  full-screen Settings pane; evidence on top.
- Base: `main` at `02ef946` (approved SPEC change) merged with the verified reading/NHK fixes
  (`7e37e79`, merge `24769dc`). Parent card `t_430ab378` was removed from the board and shipped no
  Settings-screen commit; its branch contained none of that work, so the shared-key Settings wiring
  required by this card was implemented here.
- Build environment: NixOS, `nix develop` (JDK 17, Gradle 8.14.4, Android build-tools 36.0.0,
  platform 37.0).
- Emulator: API-37.0 `google_apis` x86_64 AVD (`japanese-anki-basic-nmt-api37`, headless,
  1080x2400, `-port 5560`). Radio/wifi disabled for every failure-state capture so no request could
  reach Google; `Active default network: none` was confirmed first.

## What changed

| Area | Change |
|---|---|
| `TranslationService.kt` | `OpenRouterTranslator` replaced by `GoogleTranslationClient`: `POST https://translation.googleapis.com/language/translate/v2`, body `q`/`source=ja`/`target=en`/`format=text`/`model=nmt`, credential only in `X-Goog-Api-Key`, redirects refused, 256 KB bounded response, 20 s default timeout. Parses exactly one non-empty `data.translations[].translatedText`, unescapes Google's HTML entities into plain text, and maps failures to `NETWORK`/`AUTHORIZATION`/`API_NOT_ENABLED`/`BILLING`/`QUOTA`/`INVALID_REQUEST`/`INVALID_RESPONSE` with fixed sanitized messages. `TranslationService` now takes a credential provider instead of a stored string. |
| `GoogleApiError.kt` (new) | One tested classifier for Google Cloud API error bodies (key invalid, `SERVICE_DISABLED`, billing, quota, invalid request), shared by the speech and translation adapters so both report the same project problem in their own status. |
| `GoogleCloudCredential.kt` (new) | The single shared key. Keeps the existing storage id (`google_tts_key_encrypted`) so an already-saved Text-to-Speech key is reused without re-entry; `write` is local-only, and the obsolete `openrouter_key_encrypted` ciphertext is deleted, never read as a Google key. |
| `speech/GoogleSpeech.kt`, `speech/SentenceSpeech.kt` | Speech failures reuse the shared classifier (`API_NOT_ENABLED`, `BILLING` added) and map a dropped connection to a specific network failure instead of a generic one. Chirp 3 HD, exact selected voice, MP3 validation, and yomigana hints are unchanged. |
| `MainActivity.kt` | One Google Cloud key entry supplies both adapters. Settings is now a full-screen pane (top app bar with Back and Save, one scrolling body) instead of an `AlertDialog`. OpenRouter key entry, storage, and prompt/model plumbing removed. Speech and translation have separate status blocks; translation shows the read-only Basic/NMT path, an explicit, disclosed, possibly billable `Test translation` action, and the project requirements (billing, both APIs, key restricted to them). Saving writes local state only. `translationResponseIsCurrent` keeps in-flight results from mutating a newer revision. |
| `TranslationServiceTest.kt` | Rewritten for the Google contract (see matrix). |
| `scripts/test-core.sh` | Compiles and runs the new adapter, classifier, and tests. |
| `README.md` | Current setup text describes Google Translation Basic NMT and the shared key. |

Historical v0.3 documents (`docs/v0.3-candidate`, `docs/v0.3-final`, `docs/redesign-*`,
`docs/acceptance-test-matrix.md`) still describe OpenRouter because that is what shipped then; they
were left as history.

## Commands and results

```
nix develop --command ./scripts/test-core.sh
    OK (113 tests)                                   (evidence/core-tests.txt)

nix develop --command bash -lc \
  'gradle -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/36.0.0/aapt2" \
          testDebugUnitTest assembleDebug'
    BUILD SUCCESSFUL                                 (evidence/gradle-unit-tests.txt)
    153 unit tests, 0 failures, 0 errors

nix build .#checks.x86_64-linux.core -L
    OK (113 tests)                                   (evidence/nix-build.txt)

nix build .#apk -L
    BUILD SUCCESSFUL                                 (evidence/nix-build.txt)
    unsigned APK in the Nix store, arm64-v8a and x86_64 libs packaged

sha256sum app/build/outputs/apk/debug/app-debug.apk
    ca0b06ec1935e62d6081d8c90d163685b208f35fada187d9b547cf55d1681b9c
```

Installable candidates from this revision:

```
debug-signed (installed and exercised on the emulator in this run)
/srv/hermes/workspace/japanese-anki-app/.worktrees/t_e7982c2a/app/build/outputs/apk/debug/app-debug.apk
548,139,889 bytes, sha256 ca0b06ec…681b9c

Nix build output (unsigned; signing is a release gate)
/nix/store/nap4c61q1h7dksxmlvnbqgx8br5ic2ib-japanese-anki-card-creator-0.1.0/japanese-anki-unsigned.apk
531,756,791 bytes, sha256 2b6504ad…0605fa
```

Version name/signing/ABI release gates belong to integration card `t_7349d4d0`; these are
candidates, not a release.

## Acceptance matrix

Legend: **PASS** = executed on this revision and observed; **PARTIAL** = the listed part ran, the
remainder is named; **NOT RUN** = not executed.

| ID | Result | Evidence |
|---|---|---|
| Build and packaging | **PASS** (this card's scope) | `./scripts/test-core.sh` 113 tests; Gradle `testDebugUnitTest assembleDebug` 153 tests, 0 failures; `nix build .#checks.x86_64-linux.core` OK (113); `nix build .#apk` succeeds and the unsigned APK packages `arm64-v8a` and `x86_64`. Debug APK installed and exercised on the API-37 x86_64 AVD. |
| R14 request contract | **PASS** (controlled transport) | Serialized body is exactly `{"q","source":"ja","target":"en","format":"text","model":"nmt"}`; URL is the Basic v2 endpoint with no query string; credential arrives only as the transport's `X-Goog-Api-Key` argument; body contains no prompt, meaning, reading, notes, image, or key. `basicV2NmtRequestIsSerializedExactly`, `payloadCarriesOnlyTheSentenceAndTheCredentialStaysInTheHeader`. |
| R14 response handling | **PASS** (controlled transport) | HTML-escaped `translatedText` becomes plain text and `SentEng` re-escapes it (`&lt;b&gt;` exported); empty, malformed, non-string, and multi-result payloads block. `htmlEscapedResponseBecomesPlainTextAndExportEscapesIt`, `emptyMalformedAndAmbiguousResponsesBlock`. |
| R14 failure isolation | **PASS** (controlled transport) | 302/401/403/403-`SERVICE_DISABLED`/403-`BILLING_DISABLED`/400-`API_KEY_INVALID`/429/500/IOException produce distinct retained messages with no body, key, or sentence text; no fallback path exists. `failuresAreSpecificSanitizedAndNeverFallBack`, `timeoutAndNetworkFailuresAreRetryableFailuresNotResults`. |
| R14 cancellation / revision ownership | **PASS** (unit + code path) | `CancellationException` propagates instead of becoming a failure; `translationResponseIsCurrent` rejects a changed sentence, a newer revision, and a locked submission. `cancellationIsNotConvertedIntoAFailedTranslation`, `obsoleteResponsesCannotMutateANewerRevision`. |
| R14 meaning-only preservation and manual edits | **PASS** | Translation is keyed on sentence text only, so target/meaning edits leave it valid; a manual correction survives a stale sentence as a suggestion and Retry only fills the suggestion. `meaningOnlyChangesKeepTheSentenceTranslation`, `manualCorrectionsSurviveAStaleSentenceAndRetry`. |
| R14 export blocking | **PASS** | A blank/failed translation yields `fieldsByName() == null` with "A completed English translation is required." `failedOrMissingTranslationBlocksExport`. |
| R27 shared credential, header-only | **PASS** (controlled transport) | One credential provider feeds both adapters; both requests carry the same key only as the header argument, never in URL or body; constructing the adapters sends nothing. `bothAdaptersUseTheSameProtectedCredentialInHeadersOnly`. |
| R27 protected storage | **PARTIAL** (device observed, no interception proxy) | On device the stored value is Keystore ciphertext: `google_tts_key_encrypted=ltMdEye/mZRr…` in `shared_prefs/settings.xml`; the key field renders masked (`•••••••••••`) until "Show key" is tapped; `openrouter_key_encrypted` is absent. No capture proxy was run to prove TLS header content on the wire. |
| R27 no credential in errors | **PASS** | Failure messages are fixed strings; tests assert the key, response body, and sentence never appear. |
| R29/R30 one key, separate checks | **PASS** (controlled transport) | Speech listing can fail with the translation succeeding and vice versa; `SERVICE_DISABLED`/billing are reported per service. `speechAndTranslationChecksFailIndependently`, `speechNetworkFailureIsSpecific`. |
| R30 key entry triggers no paid request | **PASS** | Adapter construction and save perform no network call (unit: zero transport calls); on device, saving with the radio off produced no request and only local state changed. Voice listing is a separate, non-synthesizing action. |
| R30 no-key local preparation | **PASS** (emulator) | With no key the app starts, the dictionary installs, typing and settings work; Settings shows "No key saved. Local preparation works; speech and translation need a key." and `Test translation` is disabled. `screens/settings-no-key.png`, `screens/settings-no-key-translation.png`. |
| R30 service-failure state | **PASS** (emulator, no network) | With a deliberately fake key (`TESTONLYKEY`) and the emulator radio off: key status "Key saved for speech and translation.", speech status "Voice listing service is unavailable." (red), and a separate translation status "Translation service could not be reached." after tapping `Test translation`. `screens/settings-shared-key-speech-failure.png`, `screens/translation-test-network-failure.png`. |
| Draft retention through Settings | **PASS** (emulator) | A typed draft survives opening the Settings pane and pressing Back. `screens/draft-retained-after-settings.png`. |
| Obsolete OpenRouter storage removed | **PASS** (device + unit) | `shared_prefs/settings.xml` after launching and saving contains only `google_tts_voice` and `google_tts_key_encrypted`; `removeObsoleteOpenRouterStorage` deletes the pref and never reads it. |
| Native Settings screen | **PASS** (emulator) | Settings is a full-screen pane with a top app bar (Back, title, Save) and one scrolling body; Back returns to the draft unchanged. `screens/settings-shared-key-speech-failure.png`, `screens/settings-no-key.png`. |
| Live Google translation quality / listing / synthesis / listening | **NOT RUN** | No live credentials were requested, read, or used, and no paid Google call was made (every failure capture ran with the emulator radio off, `Active default network: none`). Mocks and the offline device states above are integration evidence only, not quality evidence. Live-service verification stays with SPEC §15 / integration card `t_7349d4d0`. |

## Notes and deliberate exclusions

- Settings is a full-screen pane rendered over the current draft inside `MainActivity` (top app bar,
  Back, Save, one scrolling body). It is not a separate navigation route: the app has no settings
  destination, and adding one was the removed parent card's scope. All controls and the per-service
  status live in that pane.
- No provider/model picker, prompt, fallback, or automatic retry was added; a failure keeps the
  draft and offers the existing explicit Retry.
- `TranslationSource` is now `GOOGLE`/`MANUAL`. A draft restored from a build that stored
  `OPENROUTER` resolves to a manual translation (unknown source name), so it is kept rather than
  silently re-billed — conservative by design.
- The translation HTML-entity unescaper handles named, decimal, and hex references; anything it
  does not recognise is preserved literally rather than dropped.
- No new dependency was added; the existing `MiniJson` reader serves the new response and error
  shapes.
