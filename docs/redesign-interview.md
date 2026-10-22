# Core-workflow redesign interview

Status: historical interview rationale, not a second specification. The approved [sentence-mining screen/copy contract](sentence-mining-flow.md), progress A and aligned SPEC.md R01–R39 are current. Do not implement historical proposals below as additional requirements.

Baseline checkpoint: `cd1c27a` (main checkout only; worktrees excluded).

## Confirmed by Pierre

- Personal sentence mining in Japan and from textbooks: photograph an interesting sentence, or type a sentence and take a picture.
- Select the target vocabulary (n+1 approach) and intended dictionary meaning; dictionary entries can be redundant.
- Generate sentence/vocabulary furigana, definition, translation, pitch, recorded word pronunciation, and high-quality sentence TTS, retaining the image.
- Notes and Tags are optional. Image is preferred but optional for typed input (clarification superseding the earlier all-fields-required statement); Photo input retains its photograph. The empty `fm_*` compatibility fields are not learning content.
- Word pronunciation should come from NHK 2016 or a similar recorded source. The exact sole source remains to be selected; this is not authorization for a fallback chain.
- Review should be a quick visual confirmation, perhaps listening to the word pronunciation. Normally Pierre will not audition sentence audio per note.
- Confirm and send a complete note to AnkiDroid. Do not export an incomplete or known-bad note for later rework.
- Network access is acceptable and required for the chosen high-quality sentence TTS. Sentence TTS budget: no more than USD 5/month at a couple of cards per day.
- On generation failure, retain contents and offer explicit retry or discard. No lower-quality provider substitution.
- v0.1 felt like a painful, slow web form. Seen v0.2 screenshots still feel form-like; v0.2 remains unfinished because implementation is bogged down in the specification.
- Reduce maintenance surface and focus on the complete creation journey rather than broad resilience/features.
- The photograph and remembered situation are the mnemonic; do not add generated mnemonic content.
- Missing required recorded pronunciation or pitch makes the note unexportable. Log lookup misses locally.
- Targeted corrections before export are acceptable.
- Keep photographs local. The desired low-latency phase is camera → local OCR → target choice → definition choice, followed by a higher-latency network phase.
- Pierre delegated the sentence-selection interaction choice to the assistant, prioritizing simplicity and user-friendliness.

## Adopted workflow direction

1. Capture a photo (or type, preferably with a photo); identify/correct the sentence.
2. Select the target and intended dictionary sense.
3. Generate the complete note through one selected path per operation.
4. Review a composed learning artifact, not an always-editable field list; optional notes/tags remain secondary.
5. Send only when required content is ready; retain draft and successful current results on failure.

Use a native Compose/Material interactive prototype as the UI authority. Retain HTML only for optional exploratory composition. This proposal does not authorize a WebView card-template renderer or removal of editing.

Sentence-selection design choice: show readable recognized text beneath a small photo reference. A single clear region populates the editable sentence directly; multiple regions are selectable text excerpts before editing. Permit trimming/correcting the chosen text before target selection. Do not build draggable OCR bounding boxes, zoom-coordinate selection, or a second on-image selection mode. Choosing text does not crop the retained photo. Validate with real textbook/sign examples in the prototype.

Lookup-miss logging proposal: a bounded local diagnostic log recording operation/source version, selected spelling/reading, and reason. Distinguish absent source entries from network failures. No photo bytes, credentials, remote log upload, or analytics dashboard.

Frontend decision: keep Kotlin/Compose; no Tauri, Dioxus, Flutter, or HTML frontend migration. Use native Material 3 Expressive conventions, color, and appropriate animation instead of bespoke restrictions.

## Expanded rewrite mandate

- Standard orientation-aware camera; landscape capture then portrait continuation must work. Remove custom screen-lock/counter-rotation choreography.
- One path per operation. Sentence TTS is Google Cloud Chirp 3 HD with required phonetic hints; OpenRouter remains translation-only. Google voices are fetched from the cloud and selectable in Settings. Keep a small injectable provider boundary, not multiple production adapters or a fallback chain. Do not request secrets in chat.
- Choose AVIF or WebP based on implementation simplicity and actual compression tests. Maximum long edge is 1080 px, not 1920×1080; keep photos local.
- Nasalization remains required. Mirror AJT Japanese HTML pitch output and relevant furigana/word-audio behavior, verified against upstream code/data.
- API 37 only: ARM64 device, x86_64 emulator. No broad API/ABI matrix.
- Buildable in Nix is sufficient; byte-identical APKs are not a goal.
- Further simplification is authorized. SPEC selects one platform WebP encoder, removes first-install tag seeds and fixed UI placement rules, and uses a deliberate rebuild with confirmation instead of competing stale/manual/suggestion branches.

Remaining technical selections and evidence are tracked only in SPEC §15: AJT annotated source/recording consistency, Google hinted-speech/voice-list validation and OpenRouter translation-model selection, image preset validation, and actual API-37 end-to-end acceptance. This is not approval to implement several providers while deciding.

## Quality boundary

Completeness, source identity, reading matches, and playable files can be checked mechanically. Arbitrary Japanese pronunciation, pitch appropriateness, furigana and translation cannot be guaranteed correct merely by receiving successful API responses. Provider selection needs representative Japanese listening/linguistic evaluation before trusting a glance-and-send workflow. Do not invent an automatic quality score or add a second AI verification pipeline by default.
