# Screens

These notes state product intent and state boundaries. App copy, layout, accessibility semantics, and measurements live in code, resources, and tests.

## Capture

Purpose: acquire a sentence from a photograph or direct text entry.

States: camera permission needed, camera ready, image reading, recoverable capture/reading failure, empty text, entered text.

Primary action: request camera access when needed; capture when ready; proceed from nonempty typed text. Empty text cannot proceed.

Forbidden: screen title, instructional prose, a competing second primary action, or visible internal processing details.

## Card

Purpose: choose the learning target, inspect the composed note, and deliver it.

States: no target, target selected, preparation pending, setup blocked, one required operation failed, ready, sending, uncertain external write.

Primary action: select a word; wait while preparation is active; use the one safe recovery for a blocker; send when ready; reconcile an uncertain write without inserting again.

Forbidden: separate Target or Review screens, a preparation page, stacked hint lines, per-field progress, generated-field repair forms, or duplicate sentence displays.

## Dictionary setup

Purpose: make the local Japanese dictionary available for analysis.

States: not started, transferring, waiting, paused, verifying, installing, failed, ready.

Primary action: start, pause/resume, retry, or continue according to the current task state.

Forbidden: claiming readiness from transferred bytes alone, fake progress, or a second competing transfer task.

## Camera setup

Purpose: request camera access while preserving typing as a usable alternative.

States: permission not requested, granted, denied.

Primary action: request access when eligible; continue after the decision. Typing remains available if access is declined.

Forbidden: repeated permission prompts or a camera requirement for local text entry.

## Audio setup

Purpose: configure the credential and voice needed for paid audio work.

States: credential missing, voice list loading, voice list unavailable, valid selection saved.

Primary action: save valid setup and continue; reload voice metadata only when explicitly requested.

Forbidden: paid audition or synthesis on open, keystroke, or revisit; exposing the credential; an automatic retry.

## AnkiDroid setup

Purpose: connect the real AnkiDroid provider and choose a compatible destination.

States: unavailable, disconnected, connected without a destination, configured, deferred.

Primary action: connect and resolve the destination, continue when valid, or defer setup.

Forbidden: a fake production provider, silently invented destination, or claiming a successful connection without verification.

## Readiness

Purpose: summarize which prerequisites are usable and let the learner enter the app.

States: required setup incomplete, dictionary and audio ready, optional camera or AnkiDroid unavailable.

Primary action: finish when required setup is valid; return to the incomplete prerequisite otherwise.

Forbidden: treating optional camera or AnkiDroid setup as a blocker for local capture.

## Settings

Purpose: change durable connection, credential, voice, destination, and tagging preferences.

States: saved, editing, loading metadata, validation failure, persistence failure.

Primary action: change the relevant preference or retry a failed save.

Forbidden: paid work on open or typing, duplicate credential surfaces, a global save workflow, or losing edits after a persistence failure.

## Downloads

Purpose: inspect and control the same durable dictionary task used during setup.

States: idle, transferring, waiting, paused, verifying, installing, failed, ready, setup incomplete.

Primary action: start, pause/resume, retry, cancel, or return to unfinished setup as the task permits.

Forbidden: starting a duplicate task, hiding a required mobile-data decision, or discarding a local draft when the task changes.

## Passage choice

Purpose: identify the intended sentence when image analysis finds multiple plausible passages.

States: candidates available, selection committed, dismissed without selection.

Primary action: choose one passage; dismissal preserves the source and commits nothing.

Forbidden: automatic passage choice when ambiguity remains or a second action that sends the note.

## Sentence editor

Purpose: correct the source sentence before dependent analysis is committed.

States: unchanged, edited, empty, committed, dismissed.

Primary action: commit nonempty text; dismissal preserves the prior committed sentence.

Forbidden: partial edits changing prepared content or an empty commit.

## Meaning choice

Purpose: resolve a genuine ambiguity between viable meanings of the selected word.

States: choices available, choice committed, dismissed with the prior choice retained.

Primary action: select one meaning.

Forbidden: exposing a choice list for an unambiguous result or splitting one decision into multiple pickers.

## Full definition

Purpose: expose complete source meaning text when a preview is insufficient.

States: open, closed.

Primary action: close and return to the prior choice.

Forbidden: changing the selected meaning while viewing text.

## Photo viewer

Purpose: inspect the retained source photograph without altering it.

States: open, closed.

Primary action: close and return to the prior surface.

Forbidden: replacing, editing, or discarding the source from the viewer.

## Note editor

Purpose: add or change personal note text for this card.

States: unchanged, edited, committed, dismissed.

Primary action: commit this note's value; dismissal preserves the prior committed value.

Forbidden: changing a default or generated field as a side effect.

## Tags editor

Purpose: add or remove tags for this card.

States: unchanged, edited, committed, dismissed.

Primary action: commit this card's tag set; dismissal preserves the prior committed value.

Forbidden: changing default tags or another card's tags.

## Voice picker

Purpose: select one available voice for future authorized audio work.

States: list available, selection committed, dismissed.

Primary action: choose one voice.

Forbidden: billable preview or changing the saved choice on dismissal.

## Note-type picker

Purpose: choose a compatible Anki note type when more than one is available.

States: choices available, selection committed, dismissed.

Primary action: choose one compatible type.

Forbidden: silently substituting an incompatible type.

## Deck picker

Purpose: choose the destination deck for future notes.

States: choices available, selection committed, dismissed.

Primary action: choose one deck.

Forbidden: silently inventing a destination.

## Mobile-data consent

Purpose: authorize or postpone a dictionary transfer over mobile data.

States: consent granted, waiting for Wi-Fi, dismissed.

Primary action: authorize the disclosed transfer or wait for Wi-Fi.

Forbidden: starting mobile transfer before explicit consent or hiding a required restart disclosure.

## Draft-replacement confirmation

Purpose: prevent accidental loss when a new shared or captured input would replace a nonempty draft.

States: replacement confirmed, existing draft retained.

Primary action: confirm replacement or keep the current draft.

Forbidden: replacing the draft without an explicit decision.

## Cache-clear confirmation

Purpose: confirm removal of recreatable cache data without affecting learner work.

States: clear confirmed, action cancelled.

Primary action: clear cache or cancel.

Forbidden: deleting the active draft, saved notes, or AnkiDroid media.

## Sources and licenses

Purpose: provide the notices required for bundled and downloaded materials.

States: content available, returned to Settings.

Primary action: inspect a notice or return.

Forbidden: setup prerequisites, credentials, or unrelated product controls.
