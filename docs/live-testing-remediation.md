# Live-testing remediation contract

## Authority and scope

This document supersedes conflicting workflow/UI/resource rules in SPEC.md. Preserve all unrelated safety, privacy, source provenance, complete-note and credential-dependent verification requirements. Implement independent obvious fixes now; target alternatives, pitch-source migration and unresolved distribution choices require separate proposals/decisions. Never introduce a second pitch backend or a fallback chain. Do not publish or push anything without explicit authorization. Do not access credentials or invoke paid services for tests.

## Settled behavior

Latest user decisions: production Target is **A, hero sentence with compact Material word pills**; B remains a prototype only. **Google Play Services-managed Japanese ML Kit model download without an app-pinned model hash is explicitly approved.** This OCR-only exception supersedes strict model-hash wording below; all app-managed datasets remain build-hash-verified and no model payload belongs in the release APK. Onboarding must wait for usable OCR, handle download/check failures and retries honestly, and verify offline recognition after delivery. Use indeterminate progress if the API cannot expose bytes. No recognition-backend change, privacy/telemetry waiver, push or publication is authorized. Remove prototype debug text from production.

### Capture

Use a full-width uncropped orientation-correct 4:3 sensor view (upright portrait viewport 3:4). No synthetic black masks, inset card frame or corner clipping. Ordinary chrome/extra space below is acceptable. A Pixel-camera-inspired bottom Camera/Type toggle is welcome; do not copy zoom/camera-switch features that are not required. Preview position and dimensions must remain identical through shutter idle/pending/error and background processing states. Keep all transient feedback in reserved/overlay slots. Fixed shutter slot; all sibling capture actions honor operation locks. Capture save errors must retain safe diagnostics and clean partials.

OCR candidate selection and dictionary ambiguity dismiss consistently via scrim/swipe/system Back. Keep the image/target and expose a visible reopen action. Never silently choose an ambiguous entry. Invalidate old choices when image/draft changes. Copy picker streams off the UI thread. Type attachment retry must not start OCR or replace typed text. Show failures and recovery on the currently visible screen.

### Target

Propose two interactive alternatives: (1) prominent exact original sentence plus compact Material-styled word pills, inspired only by the density of the supplied Duolingo reference; (2) sentence-native independently draggable snapped selection handles. Auto-tap means a tap snaps to a whole word and updates lookup automatically; handles adjust either boundary. Do not select an initial target without a tap or force-open a sheet merely because a word was tapped. Remove visible Extend/Trim controls in either design. Preserve raw morphemes separately from grouped units, exact occurrence offsets and source sentence, explicit entry/sense choice, Unicode safety and accessible selection. Use compact sentence editing, not a standalone full row. No duplicate instructions. All lookup sheets are left-aligned; each definition preview is one line with ellipsis. Full meaning remains accessible after selection or in a detail surface, never truncated in stored/exported data.

### Review and generation

Only Notes and Tags are editable in Review; reading/meaning/translation/pitch/photo corrections belong in retained preparation. Deck selection is in Settings, not Review. Remove impossible photo actions/internal policy explanations. Defaults seed every fresh note; per-note tag edits do not modify defaults or carry forward as implicit defaults.

Fresh explicit Continue from preparation authorizes automatic work upon entering Review for that revision: recorded word download, sentence TTS and translation; no extra Generate taps. Small inline per-item spinners, not a blocking loading screen. Restoring/recomposing/revisiting Review must not cause a duplicate paid request or automatically restart failed/interrupted work. Explicit retry only for appropriate transient failures; settings/input correction for permanent errors. Preserve caches and successful siblings. Big speaker-icon word/sentence buttons with at least 48 dp effective non-overlapping targets, accessible Play/Stop labels and loading/playing state. Do not auto-play.

No word contour after the last mora, no extra final-drop indicator; preserve source accent metadata/export semantics. Katakana diagram labels only (do not alter canonical readings/source HTML). Equal outer diameters for solid/hollow markers, connectors clipped outside hollow interiors. Preserve source-only nasal/devoicing marks.

### Settings

Reachable via top-right gear on Capture, Target and Review without draft loss. Shared theme, icon Back, keyboard/system insets. No Save button: autosave all settings consistently; secure/debounced credential persistence, error visibility and flush behavior must be tested. Defaults apply only to fresh notes. Destination/voice changes must not retarget a locked Send. Load voices on Settings open with a usable key and Reload action; key changes invalidate list provenance, refreshes are revision/credential fenced, cached state is honest and missing authoritative voice blocks synthesis. No paid calls on open or field changes. Keep one shared protected Google key.

### Send and diagnostics

Populate `VocabPartOfSpeech` from the selected JMdict senses' available part-of-speech data, respecting inherited POS, deduplicating stably and preserving sense/entry identity. Do not substitute MeCab's segmentation POS or invent missing data. Recognize/export this field when the chosen note type has it without requiring template changes; absent JMdict POS remains empty. Add serializer, selected-sense, escaping and invalidation tests. Linguistic worker owns the pure mapping helper; integrator owns draft/field/export wiring.

Fix returned-URI validation by media type; gateway currently runs image-only importedFilename for audio too. Whole Send imports sentence/word audio before image, and generic image error can actually be audio failure. Preserve strict file/URI security checks. Record operation-specific safe failures and receipts. Guard uncertain post-write/pre-receipt outcomes; no blind reimport/reinsert, collection-media rollback or recovery card moves. A matching note's existence is not proof of verified destination/authorship. Recovery state/actions must not depend on human message strings.

Retain safe TTS status/reason/transport class/duration/input-byte-count/attempt identity through controller/UI; no keys, raw response bodies or media bytes. Distinguish timeout/DNS/TLS/5xx/input/setup/source misses. Local ordinary linguistic text logging remains permitted by SPEC privacy policy. Validate provider byte limits; do not silently remove hints or change model/voice/provider. Manual retry remains the policy.

### Resources and onboarding

Release APK must not bundle offline datasets/models. First-run onboarding covers resource downloads with individual progress bars, permissions and Google API-key/voice setup. Key and voice setup are REQUIRED before finishing onboarding; do not offer a skip-key completion route. Downloads default to Wi-Fi; mobile data requires showing download size and explicit confirmation. The distribution target is versioned assets in this project's GitHub Releases, subject to source/license verification; publication itself is not yet authorized. Build includes trusted immutable hashes; downloads are versioned, bounded, validated before atomic activation, cancellable/retryable and robust to interruption/storage shortage/archive traversal. Once provisioned, resources work offline. Updating an installed app must preserve/adopt valid existing resources rather than needlessly redownload them. Keep native executable code and mandatory license notices in APK; no runtime executable-code download. Clarify OCR model delivery if platform-managed integrity cannot satisfy build-pinned hashes. Do not disable privacy protections to enable downloads. Hosting direction and onboarding key requirements are settled above; publishing verified assets still needs explicit authorization before rollout. Start pure verified resource-manager components independently; no placeholder URLs/hashes or pretending a local fixture is a published artifact.

## Reference images

- Compact pills/speaker inspiration: https://storage.ghost.io/c/7a/33/7a33d0f4-927d-4fe8-a6bf-96131b5e76d4/content/images/2026/01/Screenshot-repeat.png
- Camera inspiration: https://lh3.googleusercontent.com/3RrCLi_CSYe-NyjhqzZFkZHpJhgpHlN_XjuvncRgGMUGCeTjwYDfQn8KN-ckW-YdaFi05h8nJj-akKNOODd5s4mdv3zS_kK2YXg=w978-h1280-rw-nu

## Pitch investigation before migration

Confirmed root cause: our builder imports ONLY `pitch_accents_formatted.3.csv`, while AJT iterates parts 1, 2 and 3. Part 1 has 受付/ウケツケ with devoiced ツ (line 29579) and できる (line 202749); part 2 contains 出きる,出來る,出来る and 受付. This supersedes the earlier incorrect conclusion that these readings are absent from the full pinned upstream dataset. Repair full multi-shard ingestion; no Kanjium migration is indicated by these examples.

Reproduce 受付 / うけつけ against downloaded DB, all original AJT records, our builder/filter, validation and AJT plugin matching rules. Required reference HTML is `<span class="low_rise">ウ</span><span class="high">ケ<span class="devoiced">ツ</span>ケ</span> <span class="pitch_number">0</span>`. Trace aliases/readings/alternative spelling and rejected HTML, not just exact row absence. Preserve deterministic order/deduplication, complete shard/source hashes and supplied phonetics. Keep a single AJT backend. If a genuinely unresolved incompatibility later requires migration, provide a draft comparing a single Kanjium backend's coverage, marks, licenses, export/recording semantics and losses; no migration without approval.

## Work boundaries and verification

Independent component workers do not edit MainActivity.kt, SPEC.md or other workers' owned files. One integration owner wires production after merging their branches. Local commits and local integration are explicitly authorized; push and publication are not. Worktree baseline is committed main; existing workers MUST incorporate the contract commit or read this absolute shared-checkout contract, never assume stale worktree SPEC controls. Report branch/commit SHA, files, exact test counts and limitations. No push/publication.

Gate ladder: targeted compile/tests, full JVM once per branch, lint once, one coordinated API-37 emulator lane for necessary runtime tests. Do not boot competing emulators or run many heavyweight builds simultaneously. Core tests alone do not verify CameraX, provider import, modal Back or native rendering. Final integration and independent acceptance must test real supplied symptom paths, absence of removed UI, light/dark/200% text, resource hash/interruption cases and no paid calls on restore/open. Live Google quality requires authorized credentials; label NOT RUN honestly. Do not claim APK fixes delivered until integrated and exercised.
