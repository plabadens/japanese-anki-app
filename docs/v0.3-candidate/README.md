# Japanese Anki Card Creator v0.3 (candidate)

Debug-signed, sideloadable candidate for the v0.3 mining workflow. This is not a Play
release and it does not claim update compatibility with any earlier install: it is signed with
the local Android debug key (`CN=Android Debug`), so installing it over a build signed with a
different key fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Uninstall the old app first if
that happens (uninstalling drops the app's local draft data only, not AnkiDroid's collection).

## Artifact

| Item | Value |
|---|---|
| APK | `releases/v0.3/japanese-anki-0.3-debug.apk` |
| SHA-256 | see `releases/v0.3/SHA256SUMS.txt` |
| package | `dev.pierre.japaneseanki` |
| versionName / versionCode | `0.3` / `2` |
| minSdk / targetSdk / compileSdk | 37 / 37 / 37 |
| ABIs packaged | `arm64-v8a`, `x86_64` (all `arm64-v8a/*.so` are ELF64 AArch64) |
| signing | Android debug key, not a release identity |
| built from | branch `japanese-anki-app/t_845e4675-implement-v0.3-complete-mining-workflow`, commit `d11bbb5` on baseline `9f6aba3`, built from a fresh clone of that commit |

The documentation commit on top of `d11bbb5` adds only `docs/v0.3-candidate/` (this text and the acceptance
record); no app source differs from `d11bbb5`, which is what the APK was built from.

Size is ~548 MB because the full UniDic CWJ 3.1.1 archive (~450 MB) and the JMdict/AJT indexes
ship inside the APK. First launch unpacks UniDic to app storage (about 700 MB, several minutes
on the emulator). Keep that in mind on a phone with little free space.

## Install

```
adb install -r -g releases/v0.3/japanese-anki-0.3-debug.apk
```

`-g` grants camera and AnkiDroid API permissions up front; without it the app asks normally.
Android 17 (API 37) is required. AnkiDroid 2.24.0 is the integration reference.

## First-run setup

1. Open AnkiDroid once so it creates its collection, and allow "All files access" when asked.
2. In the app, open **Settings** (gear, top right):
   * **AnkiDroid** — grant API access, then pick the note type and destination deck. The app
     needs the reference `Sentence (JA)` note type; `docs/v0.3-reference-template/README.md`
     has the install procedure, including the corrected front template for nested nasal marks
     and accent numbers of ten or more. A note type without the required fields is rejected
     with an explicit missing-field list; nothing is substituted.
   * **Google Cloud speech** — paste a Google Cloud API key with Text-to-Speech access, save,
     then **Refresh voices** and pick a Japanese Chirp 3 HD voice. Without a key the voice list
     stays empty and sentence audio cannot be generated.
   * **OpenRouter translation** — paste a separate OpenRouter key. The translation model is
     fixed and shown read-only.
   * **Audio cache** — shows the reusable cache size and clears it; draft audio is untouched.
3. Take a photo (or pick one), fix the OCR excerpt, tap the target word, choose the sense, then
   **Continue to generate**.

Keys are encrypted with an Android Keystore-backed key, are never logged, never leave the
device except as authentication headers to their own service, and are excluded from backup.

## Verification status

`ACCEPTANCE.md` in this directory is the R01–R29 matrix with real results, including what could
not be run: live Google listing/synthesis/listening and live OpenRouter translation are
**NOT RUN** because no credentials were available, and therefore no note was sent to AnkiDroid
in this pass. `screens/` holds the emulator screenshots quoted there.

## Build from a clean checkout

```
git clone <repo> && cd japanese-anki-app
nix develop --command ./scripts/test-core.sh          # 62 core tests
nix develop --command gradle :app:testDebugUnitTest \
  -Pandroid.aapt2FromMavenOverride=$ANDROID_HOME/build-tools/36.0.0/aapt2   # 110 unit tests
nix develop --command gradle :app:assembleDebug \
  -Pandroid.aapt2FromMavenOverride=$ANDROID_HOME/build-tools/36.0.0/aapt2   # app-debug.apk
```

The `-Pandroid.aapt2FromMavenOverride` flag is required on NixOS: AGP's Maven-hosted AAPT2 is a
generic-linux binary and cannot start there. `nix build .#apk` was not exercised in this pass;
the Gradle path above is the verified one.
