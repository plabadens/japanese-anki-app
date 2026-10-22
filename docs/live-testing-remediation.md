# Live-testing remediation contract

## Authority and scope

**Reconciled 22 October 2026.** The approved [sentence-mining screen/copy contract](sentence-mining-flow.md) is the UI authority; SPEC.md holds aligned technical requirements and acceptance R01–R39. This remediation document retains diagnostic/technical detail, not a competing workflow or authority over those files. Preserve unrelated safety, privacy, source provenance, complete-note and credential-dependent verification requirements. Never introduce a second pitch backend or a fallback chain. Do not access credentials or invoke paid services for tests.

## Settled behavior

Production Target has **one selectable sentence**, not a hero plus a duplicate pill rendering; Edit sentence is left-aligned. **Progress A** is the selected action-centred design, unrelated to the historical Target A/B prototype names. No daily stepper or per-item generation spinners. The Japanese ML Kit model is bundled. Only UniDic is provisioned separately with build-pinned integrity gates; index/dictionary/pitch/OCR data remain bundled. Onboarding is Dictionary → Camera → Audio → AnkiDroid → Ready with durable background provisioning and honest failures. No recognition-backend change or privacy/telemetry waiver is authorized. Remove prototype/debug text.

### Capture

Use a full-width uncropped orientation-correct 4:3 sensor view (upright portrait viewport 3:4). No synthetic black masks, inset card frame or corner clipping. Ordinary chrome/extra space below is acceptable. A Pixel-camera-inspired bottom Camera/Type toggle is welcome; do not copy zoom/camera-switch features that are not required. Preview position and dimensions must remain identical through shutter idle/pending/error and background processing states. Keep all transient feedback in reserved/overlay slots. Fixed shutter slot; all sibling capture actions honor operation locks. Capture save errors must retain safe diagnostics and clean partials.

OCR candidate selection and dictionary ambiguity dismiss consistently via scrim/swipe/system Back. Keep the image/target and expose a visible reopen action. Never silently choose an ambiguous entry. Invalidate old choices when image/draft changes. Copy picker streams off the UI thread. Type attachment retry must not start OCR or replace typed text. Show failures and recovery on the currently visible screen.

### Target

Use the single sentence-native set of contextual word-selection units. A tap selects a whole word and updates local lookup; no initial target, duplicated hero, Extend/Trim controls or separate handle mode. Preserve raw morphemes/grouped units, occurrence offsets, source sentence, Unicode and accessible selection. Resolve supported readings/meanings automatically; ask only about genuine ambiguity through one combined meaning sheet, not consecutive entry/sense forms. Edit sentence sits left-aligned directly underneath. Sheets are left-aligned; definition previews ellipsize one line while complete stored/exported meaning remains accessible.

### Review and generation

Only Notes and Tags are editable in Review, through separate content-linked editors. No combined metadata control. There is no generated-field correction workbench in preparation or elsewhere; sentence editing and word/meaning selection in Target are the correction path. Translation has no heading; each surface has one top heading only. Deck selection is in Settings. Remove impossible photo actions/internal policy explanations. Defaults seed fresh notes only; per-note tags never become defaults.

Fresh explicit Review card authorizes missing recorded-word download, sentence TTS and translation for that revision. Review opens immediately, with one grouped progress A indicator in the stable footer; no Generate page, per-item spinners or processing checklist. Restoring/recomposing/revisiting never repeats paid work or automatically retries interrupted/failed work. Explicit safe retry affects only the failed operation; permanent errors use the contracted input/Settings recovery. Preserve successful siblings/caches. Speakers have 48 dp non-overlapping effective targets and accessible Play/Stop/pending state. No auto-play.

No word contour after the last mora, no extra final-drop indicator; preserve source accent metadata/export semantics. Katakana diagram labels only (do not alter canonical readings/source HTML). Equal outer diameters for solid/hollow markers, connectors clipped outside hollow interiors. Preserve source-only nasal/devoicing marks.

### Settings

Reachable via top-right gear on Capture, Target and Review without draft loss. Shared theme, icon Back, keyboard/system insets. No Save button: autosave all settings consistently; secure/debounced credential persistence, error visibility and flush behavior must be tested. Defaults apply only to fresh notes. Destination/voice changes must not retarget a locked Send. Load voices on Settings open with a usable key and Reload action; key changes invalidate list provenance, refreshes are revision/credential fenced, cached state is honest and missing authoritative voice blocks synthesis. No paid calls on open or field changes. Keep one shared protected Google key.

### Send and diagnostics

Populate `VocabPartOfSpeech` from the selected JMdict senses' available part-of-speech data, respecting inherited POS, deduplicating stably and preserving sense/entry identity. Do not substitute MeCab's segmentation POS or invent missing data. Recognize/export this field when the chosen note type has it without requiring template changes; absent JMdict POS remains empty. Add serializer, selected-sense, escaping and invalidation tests. Linguistic worker owns the pure mapping helper; integrator owns draft/field/export wiring.

Fix returned-URI validation by media type; gateway currently runs image-only importedFilename for audio too. Whole Send imports sentence/word audio before image, and generic image error can actually be audio failure. Preserve strict file/URI security checks. Record operation-specific safe failures and receipts. Guard uncertain post-write/pre-receipt outcomes; no blind reimport/reinsert, collection-media rollback or recovery card moves. A matching note's existence is not proof of verified destination/authorship. Recovery state/actions must not depend on human message strings.

Retain safe TTS status/reason/transport class/duration/input-byte-count/attempt identity through controller/UI; no keys, raw response bodies or media bytes. Distinguish timeout/DNS/TLS/5xx/input/setup/source misses. Local ordinary linguistic text logging remains permitted by SPEC privacy policy. Validate provider byte limits; do not silently remove hints or change model/voice/provider. Manual retry remains the policy.

### Resources and onboarding

Bundle assets by default. The APK contains the OCR model, NHK-2016 index, dictionary and pitch dataset; only the 446,602,958-byte UniDic runtime ZIP is provisioned, with build-pinned SHA-256, exact archive and extracted-size checks. Dictionary starts early in the wizard and persists independently of screens/process recreation. Downloads/onboarding/notification own the one phase indicator, with real transfer bytes, then verification/installation; no Camera progress panel. Offer applicable pause/resume/cancel/retry, honest restart disclosure and Wi-Fi/mobile consent. Drafts survive interruption/cancellation. Headword lookup is offline; selected recording media is on demand. Keep native code/notices packaged. Missing versioned resource delivery remains a release blocker, not permission for fallback URLs.

## Reference images

- Compact pills/speaker inspiration: https://storage.ghost.io/c/7a/33/7a33d0f4-927d-4fe8-a6bf-96131b5e76d4/content/images/2026/01/Screenshot-repeat.png
- Camera inspiration: https://lh3.googleusercontent.com/3RrCLi_CSYe-NyjhqzZFkZHpJhgpHlN_XjuvncRgGMUGCeTjwYDfQn8KN-ckW-YdaFi05h8nJj-akKNOODd5s4mdv3zS_kK2YXg=w978-h1280-rw-nu

## Pitch investigation before migration

Confirmed root cause: our builder imports ONLY `pitch_accents_formatted.3.csv`, while AJT iterates parts 1, 2 and 3. Part 1 has 受付/ウケツケ with devoiced ツ (line 29579) and できる (line 202749); part 2 contains 出きる,出來る,出来る and 受付. This supersedes the earlier incorrect conclusion that these readings are absent from the full pinned upstream dataset. Repair full multi-shard ingestion; no Kanjium migration is indicated by these examples.

Reproduce 受付 / うけつけ against downloaded DB, all original AJT records, our builder/filter, validation and AJT plugin matching rules. Required reference HTML is `<span class="low_rise">ウ</span><span class="high">ケ<span class="devoiced">ツ</span>ケ</span> <span class="pitch_number">0</span>`. Trace aliases/readings/alternative spelling and rejected HTML, not just exact row absence. Preserve deterministic order/deduplication, complete shard/source hashes and supplied phonetics. Keep a single AJT backend. If a genuinely unresolved incompatibility later requires migration, provide a draft comparing a single Kanjium backend's coverage, marks, licenses, export/recording semantics and losses; no migration without approval.

## Work boundaries and verification

One UI owner integrates shared MainActivity.kt/ui files after consuming prerequisite branches. Component workers do not independently add screens, strings, headings or recovery actions; every element traces to the approved flow/copy IDs. Feature work starts from committed dev and lands on dev after its gates, following docs/branching.md. A stale main-based worktree must explicitly incorporate the approved contract and prerequisite commits. No main promotion, force-push or release is authorized by the design approval. Report branch/SHA, exact tests and limits; remote writes require the task's explicit publication scope.

Gate ladder: targeted compile/tests, full JVM once per branch, lint once, one coordinated API-37 x86_64 emulator lane. No competing emulators or heavyweight builds. Core tests alone do not verify CameraX, provider import, modal Back or native rendering. Exercise the connected journey and every contracted exception, absence of removed UI, copy-ID coverage, light/dark/200% text, motion/haptic settings, durable resource interruption/consent and no paid calls on restore/open. Import verification requires a separate dummy provider APK across app UIDs plus a real AnkiDroid happy path; follow SPEC R38/R39 and the design contract. Live Google quality needs authorized credentials; mark NOT RUN honestly. Do not claim delivered fixes before integrated execution.
