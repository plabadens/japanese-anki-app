# Japanese Anki Card Creator v0.3 — install and setup

Personal sideloaded app. It mines one Japanese sentence and one vocabulary item into a new note in
AnkiDroid. It does not install note types, edit existing notes, or sync.

## Package

| | |
|---|---|
| File | `japanese-anki-0.3-debug.apk` |
| Size | 546 904 852 bytes (UniDic + JMdict + ML Kit model + AJT pitch index are bundled) |
| SHA-256 | `2cc3c053003af15de44050f350a3758d0c25155910b31a83ff0d6beedf37a15e` |
| Version | `versionName 0.3`, `versionCode 3` (v0.2 shipped as 2) |
| SDK | `minSdk 37`, `targetSdk 37`, `compileSdk 37` (Android 17) |
| ABIs | `arm64-v8a` (phone) and `x86_64` (emulator) |
| Signature | debug identity `CN=Android Debug`, cert SHA-256 `1446fc8e57cc68f3e585f6f6dfa31ab60c5346644f4f1df53f4133915285635d` — the same key as the delivered v0.2 APK |

The signing key is a local debug key, not a Play/release identity. It is kept outside the repository
at `/srv/hermes/.android/debug.keystore`; the build picks it up through
`JAPANESE_ANKI_DEBUG_KEYSTORE` so later builds keep the same update identity. Anyone rebuilding must
use that keystore, or the APK will not install over this one.

## Install / update

Phone (arm64):

```
adb install -r -g japanese-anki-0.3-debug.apk
```

or copy the APK to the device and open it (allow install from that source).

Updating an installed v0.2 works in place — no uninstall, no data loss — because the certificate
matches. `-g` grants the camera permission up front; without it the app asks normally and denial
keeps Type/image-picker entry usable.

## First run

UniDic is unpacked on first launch: 725 MB, progress shown in the app, several minutes on a phone.
Keep about 1 GB free. The dictionary and pitch index work offline afterwards. No network is needed
for capture, OCR, tokenization, dictionary/meaning choice, pitch or word audio apart from the
one-off NHK recording index download (37 MB) the first time a word recording is fetched.

## Setup order

1. Install **AnkiDroid 2.24.0** (or newer) and create/choose a collection.
2. In this app: **Settings** (gear, top right on the capture screen) → grant the AnkiDroid API
   permission when prompted → choose **Note type** and **Destination deck**. A model without the
   fourteen contract fields is reported by name and is not silently substituted.
3. Install the reference note type once, by hand: see `docs/v0.3-reference-template/README.md` in the
   repository. The app never modifies your note type; the template is what renders the pitch spans,
   nasal/handakuten annotations and bracket furigana.
4. **Google Cloud speech:** paste a Cloud Text-to-Speech API key into Settings → tap
   **Refresh voices** → pick a Japanese **Chirp 3 HD** voice → **Save settings**. The key is stored
   with Android Keystore protection, is never shown in errors or logs, and is only sent to Google.
   Voice listing is metadata only and synthesizes nothing.
5. **OpenRouter translation:** paste a separate OpenRouter key. The model is fixed in the build and
   shown read-only (`google/gemini-2.5-flash`). A Google key cannot authenticate OpenRouter and vice
   versa.

No credentials are embedded in this APK. Without them the local half of the workflow still works;
sentence audio and translation stay blocked and Send stays disabled with a specific reason.

## Using it

Capture (Photo) or paste/type/share one sentence → pick the target word → pick the dictionary entry
and meaning → **Continue to generate** → review the composed card (sentence with ruby and highlighted
target, vocabulary with pitch, word playback, translation), play the word audio, add notes/tags, then
**Send to AnkiDroid**. Edit opens targeted corrections; Back never destroys the draft.

## Known issues in this build

* **Stale vocabulary display:** if you tap a word with several dictionary entries and dismiss the
  "Choose dictionary entry" sheet, the screen keeps the previous word's vocabulary, definition and
  pitch until you pick an entry. Generation is blocked meanwhile (no entry selected), so a wrong note
  cannot be exported.
* **Settings is one scrollable dialog**, not a settings screen. All sections are there but the
  Google/OpenRouter/cache controls sit below the fold.
* The first word-audio fetch downloads a 37 MB index with no progress indicator; the card shows
  "Generating Vocabulary audio" for as long as that takes.
* After process death, Review shows sentence audio as unavailable until you tap the info icon (the
  previous failure text is not restored verbatim).
* `nix build .#apk` (the flake derivation) is not verified; the supported clean build is Gradle
  inside `nix develop`, see `ACCEPTANCE.md`.

## Verifying this file

```
sha256sum japanese-anki-0.3-debug.apk
aapt2 dump badging japanese-anki-0.3-debug.apk | head -4
apksigner verify --print-certs japanese-anki-0.3-debug.apk | grep 'SHA-256'
```

Expected: the hash above, `versionCode='3' versionName='0.3'`, `minSdkVersion:'37'`,
`native-code: 'arm64-v8a' 'x86_64'`, and the `1446fc8e…` certificate.
