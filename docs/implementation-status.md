# Implementation status — 2026-10-14

**Pre-redesign evidence:** the revised `SPEC.md` changes platform, providers, pitch data, media format, UI, and export eligibility. This report does not establish implementation or acceptance of that redesign; removed fallbacks and byte-identical builds are no longer work to complete.

Non-normative status snapshot for baseline commit `88c52525f9264afaaaab55d8100f47493c97fa76`. Requirements live in [../SPEC.md](../SPEC.md). This document records evidence and historical claims, not feature acceptance.

## Checks performed on this checkout

- `nix build --offline .#checks.x86_64-linux.core --no-link --print-build-logs` succeeded: dictionary builder, bundled dictionary asset, and UniDic packager self-checks passed; JUnit reported **OK (44 tests)**.
- Direct `./scripts/test-core.sh` could not run outside the development shell because Gradle was unavailable. `nix develop --command ./scripts/test-core.sh` timed out before a result was obtained.
- The Nix debug APK attempt fetched declared Maven inputs and was stopped before compilation. It did not establish APK build success or failure, full Android unit-test results, or APK reproducibility. Nix `--offline` did not prevent fixed-output input fetches in this run; it is not itself a network-isolation demonstration.
- No device/emulator acceptance checks were performed in this review.
- Static inspection found audio source/cache backends and Review audio presentation interfaces, but no production instantiation of the source chain/cache and no audio arguments at the MainActivity ReviewCard call. Audio receipt validation also had no production caller.
- `nix/gradle/deps.json` is populated and includes `lint-gradle:31.13.2`; the old claim that the lock is unpopulated is stale. Full lock coverage and reproducible APK output remain unverified here.

## Specification decisions not yet implemented by this documentation change

The 2026-10-14 specification review removes image cropping, requires no card moves during recovery, defines protected translation suggestions and Review-backed reselection sheets, permits documented SDK-default telemetry without user content/credentials, and expands the API/ABI release matrix. Existing code and mockups may still reflect earlier requirements. No application code was changed in this pass.

## Historical implementation report preserved from SPEC.md

The following paragraph was present at baseline commit `88c5252`. Its original verification date and underlying device/build logs were not established in this review. Preserve it as historical reporting, not newly reproduced evidence; see the caveats above, especially the stale dependency-lock statement.

**Implementation status (existing implementation, not proof of conformance to the revised UI below):** The Compose editor, durable manual draft fields, target-span validation/selection, Android text-entry intents, AnkiDroid setup, controlled field serialization, duplicate blocking, manual pitch editing, and stale-value protection are implemented. Pinned JMdict/Kanjium data is indexed in SQLite for offline candidate/sense and pitch lookup. UniDic CWJ 3.1.1 and MeCab 0.996 are pinned and packaged; the editor wires token selection, orthographic-lemma lookup for supported inflections, editable target/vocabulary reading suggestions, and best-effort full-sentence furigana with per-token corrections. Settings now cover default tags, optional Azure configuration with Android Keystore encryption, Android TTS engine/installed offline Japanese voice discovery, and speech rates; speech generation and audio-cache controls are not yet connected. NDK CMake builds succeeded for `arm64-v8a` and `x86_64`, and a host JNI smoke test verified native feature output. The debug APK builds and has been smoke-tested on a Pixel 7/API 37: first-launch UniDic extraction, MeCab tokenization, and offline SQLite dictionary lookup work. The native library and APK pass 16 KB alignment checks. The Nix flake exposes core-test, unsigned APK, and debug APK targets with a Gradle dependency-lock bootstrap; the Gradle artifact lock is not yet populated, so Nix APK output and reproducible offline build verification remain pending. Audio generation, media workflows, remaining settings, and acceptance testing remain pending. AnkiDroid integration is wired against the v2.24.0 source contract (commit `ebcf8e0e34921628b9b8a496c66ffd4adbb3705f`, the release available on Google Play) but untested on-device; provider behavior with trailing empty fields remains a release gate.
