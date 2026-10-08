# UI plan and outstanding visual work

Recorded after reviewing the built UI against `mockup/screens/*` and `mockup/shared.css`.

## Decision (user, this session)

A closer-to-Material-3 look is acceptable. Pixel fidelity to the mockups is **not**
required, but the **fundamental concepts from `SPEC.md` §4 and the mockups must
remain the same**. The mockups stay the reference for structure, screen inventory
and the neutral-ink/pitch-colour rule; Material 3 may supply the visual language
(type scale, component styling, motion).

## Fundamental concepts that must hold (checklist for review)

1. **Capture screen** is the entry surface: Photo/Type mode switch (Photo default,
   last used mode remembered) with **Settings only here**, in the same row to the
   right of the mode control. Photo mode is viewfinder-first (CameraX); Type mode
   is a plain field with an independent optional image.
2. **Target screen**: editable sentence and its tokens on one screen; tapping a
   token (or a contiguous span) selects the target; editing text re-tokenizes.
   Dictionary ambiguity appears as a sheet **over** this screen with sentence,
   image, tokens and selection retained beneath it.
3. **Review card is the ground truth** once a target resolves: one row per field —
   image, sentence (target highlighted, inline ruby, unresolved kanji dotted and
   unannotated), vocabulary (ruby + compact pitch diagram), definition,
   translation, **always-present Notes row**, tags chips with a trailing "+".
4. **Per-row affordances**: at most a small neutral play icon, a small neutral
   edit icon, and one neutral **flag** mark that is never the edit control and
   explains itself on tap in a non-modal, dismissible, row-associated way without
   obscuring Send or the edit controls.
5. **One bottom action area** on the card whose state changes in place: Send →
   disabled spinner "Sending…" → success (then blank capture) / duplicate-blocked
   (with Open in AnkiDroid) / uncertain-outcome recovery. The submitted card stays
   frozen and legible throughout, **including an empty Notes row**.
6. **Editing sheets**: change/remove image, Edit word (token reselection plus pitch
   alternatives in the full-scale diagram with kana), definition (sense checkboxes
   plus editable text), add tag (AnkiDroid tag list plus create-new). Notes edit
   inline. Discrete choices autosave; text editors offer Done; Back preserves.
7. **One neutral ink tone for every interactive control**; the only saturated
   colours anywhere are the four-way pitch coding. Missing/loading/pending values
   show a neutral spinner in place of the icon — never a blank or stale gap.
8. **Settings** is its own screen (note type, deck, Azure key/region/voice/rate,
   on-device TTS voice, audio cache, sources & licences), reachable only from
   capture; changing note type/deck invalidates export validation, not the draft.
9. **Accessibility**: ≥48 dp non-overlapping targets, row-specific TalkBack labels
   with selected/disabled state, 200% font, portrait and landscape without losing
   the draft, system-bar/IME insets reserved, 4.5:1 normal-text contrast.

## Mockup design tokens (available if closer fidelity is wanted later)

`mockup/shared.css` carries a complete light/dark token set we are not currently
adopting wholesale: paper `#F6F3EC`/`#16181B`, ink `#201F1D`/`#ECE8DF`, muted
`#6B6558`/`#B8B2A6`, rule `#DAD4C6`/`#6C7176`, screen `#FFFFFF`/`#1D2024`, field
`#FBFAF6`/`#22262B`, accent (墨) `#2A231C`/`#E4DAC5`, plus the four pitch hues;
radii 16/18/26 px and pill controls; 48 px icon targets; 14–22 px spacing; a mono
eyebrow style for small uppercase labels. The mockup screen inventory runs
01–32 (setup, share intake, capture, target, sheets, review states, settings).

## Current gaps (as of `main` @ b42abb6, verified on the Nix API 35 x86_64 emulator)

- **Verified on-device this pass**: capture screen (segmented Photo/Type pill,
  inlined-vector gear icon), target screen (token chips, trimmed instructional
  text), and the review card (no visible row labels, inlined vector edit/play
  icons, correct per-theme pitch hues) all render and screenshot cleanly in both
  light and dark. Two real bugs were found and fixed by this testing: shared text
  and typed text both left `route` stuck at `Capture`, so the auto-advance-to-
  review effect (gated on `route == Target`) never fired and the review card was
  unreachable from either entry point — see commits `edc7658`/`b42abb6`.
- Edit word now shows the sentence's tokens in its own sheet (A42) instead of
  redirecting to the target screen; Definition lists resolved senses as
  checkboxes plus editable text (§5.3); Notes expands inline on its own row
  instead of a shared sheet — **sheet behavior not yet re-verified on-device
  after this round's route fixes**; the Edit word sheet's empty-token-list path
  (a draft restored without re-tokenizing) is also unverified.
- The largest remaining "looks like a form" offender: note type, deck, and
  AnkiDroid permission setup still render **inline on the capture/target screen**
  rather than in Settings (§4.9, A54). The Settings `AlertDialog` has no note-type
  or deck picker at all today — relocating is mechanically a block move (all
  state already lives in the same composable scope) but was judged too risky to
  land and re-verify in this pass.
- Photo mode still opens the system picker, not a CameraX viewfinder; Settings is
  an `AlertDialog`, not its own screen; tags come from a text field, not
  AnkiDroid's tag list; play icons are wired to `onClick = {}` — audio generation
  exists but is not connected to the card.
- Audio and translation seams are implemented and reviewed but mostly **not
  wired** into the card; translation now visibly surfaces its own
  interrupted/retry state on-device, but no local-fallback flag path was
  exercised; A57 Speech-vs-Translator check not called from Settings.
- Submission states live in the form around the card rather than in the card's own
  bottom action area.
- No accessibility/responsive pass yet (48 dp audit, TalkBack, 200% font,
  landscape, insets). The new `semantics(mergeDescendants = false)` row labels in
  the review card are unverified under TalkBack.
- Mockup screens 01–06, 08, 10–12, 16, 19–26 (sheets and most error/setup states)
  are not built; 27–32 (settings) are not built.

## Plan for the next UI wave

One writer owns `MainActivity.kt` + `ui/*` at a time; stages commit separately.

1. **Theme + tokens**: single neutral ink scheme for light/dark, typography scale,
   shapes; audit for stray chroma (pitch-only rule). Verify both themes on the
   emulator with measured contrast.
2. **Review card structure**: rows, flags, dividers/density, the bottom action
   area state machine, frozen-card behaviour including empty Notes.
3. **Edit sheets**: image, Edit word (+ pitch picker), definition, tags; Notes
   inline.
4. **Wire enrichment**: audio play/pending/failed states from `AudioSourceChain`,
   translation with the local-fallback flag and manual-edit preservation, A57
   settings validation.
5. **Capture/target/settings surfaces**: mode chips, CameraX viewfinder, OCR
   ambiguity overlay, draft-replace prompt, Settings screen.
6. **Accessibility pass** plus device verification: drive the emulator through
   share → target → card → send states, capture dark/light screenshots, and compare
   structure against `mockup/screens/*` (structure, not pixels).

Verification for every stage: unit tests for extractable logic, emulator
single-session run (`nix run .#emulator-wrapper -- session <apk> <out-prefix>`),
screenshots kept with the handoff. Phone checks remain for camera, real TTS
voices and TalkBack behaviour.
