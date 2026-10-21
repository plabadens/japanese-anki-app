# Imported-media receipt wiring

The receipt code in `SubmissionRecovery.kt` is the single matcher for "this local export was
already imported into AnkiDroid, so reuse the imported filename". It exists twice on purpose,
because the two lifecycles answer different questions.

## LIVE receipts — fresh identity at the moment of use

```kotlin
val keys = ImportedReceiptKeys(
    path = IMAGE_RECEIPT_PATH,
    filename = IMAGE_RECEIPT_FILENAME,
    size = IMAGE_RECEIPT_SIZE,
    modified = IMAGE_RECEIPT_MODIFIED,
)

val identity = liveMediaIdentity(processedFile)            // one stat, once per phase
val imported = importedReceiptFromPreferences(
    preferences, keys, identity, AnkiMediaImporter::sanitizeFilename,
)
```

* `matchImportedReceipt(savedIdentity, savedFilename, currentMediaIdentity, sanitize)` is pure:
  same canonical path, same size, same mtime, and a filename the slot's sanitizer accepts.
  Anything else is `null` and the media gets imported again.
* `importedReceiptFromPreferences` only reads the four keys; it never stats. Pass the identity
  you already hold.
* `liveMediaIdentity(file)` is the one place that stats a live file: `null` for a missing path,
  a non-regular file, or a stat failure. Never fall back to re-stat'ing inside the matcher.

Call sites that must use it (all in `MainActivity.kt`, owned by the integrator):

1. Review-screen receipt (`IMAGE_RECEIPT_*` compare around `processedImageIdentity`).
2. Send, fresh identity: `media = liveMediaIdentity(processed)` then
   `imported = importedReceiptFromPreferences(...)`. The same `media` goes into the frozen
   snapshot, so the send path stats the export once.
3. Send, audio slots: compute the source identity once per phase, then use it for both the
   receipt lookup and the `FrozenAudioItem` — do not call `MediaIdentity.fromFile` twice for
   the same file.

Each slot keeps its own keys and its own sanitizer: `sanitizeFilename` for images,
`sanitizeAudioFilename` for audio.

## Post-import write — the other lifecycle

After `gateway.importProcessedImage(...)` (or `importAudio(...)`) succeeds, persist the receipt
of the identity you actually imported: path, size, mtime, and the returned filename. That write
is not a read path and must not be folded into the matcher.

## FROZEN receipts — separate policy, not duplication

A frozen submission has already been imported, so:

* a local export that no longer exists is still submittable;
* a local file that does exist must still be exactly the frozen media;
* the receipt must name the frozen identity and the frozen filename verbatim;
* a submission with no imported image needs no receipt at all.

That is `frozenImagePermitsImport(importedFilename, frozenIdentity, receiptIdentity,
receiptFilename, localFileExists, localIdentity)`. Both frozen checks in `MainActivity.kt`
(review-screen readiness and the Send-time recheck) call it. Audio keeps its own
`validateAudioReceipt` classifications; frozen policy does not change them.

No receipt format changes: the four keys per slot stay as they are, so existing preferences
continue to load.
