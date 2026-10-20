# Live-provider checks still required

These rows could not be executed without Pierre's credentials, so they are **NOT RUN**, not failed.
Each item: what to do, and what counts as a pass. Record the date/voice/model with the result.

## A. Google Cloud Chirp 3 HD (R13, R28-live)

Precondition: Cloud Text-to-Speech API key entered in Settings; AnkiDroid permission granted.

1. Settings → **Refresh voices**. Expect a list containing only Japanese-capable Chirp 3 HD voices,
   with the returned names/IDs and metadata, plus a loading state and a working retry. No paid
   synthesis may happen — check the Google billing/usage page stays flat.
2. Pick a voice, **Save settings**, close and reopen Settings. The exact voice ID must persist.
3. Turn off Wi-Fi/data and Refresh again. The cached list must still be shown and clearly marked as
   cached; the saved selection must survive. With a fresh install and no cache, the selector must be
   unavailable, not silently empty or defaulted.
4. Synthesize these sentences with the chosen voice and listen:
   * 今日はいい天気ですね。 (今日 must be きょう, not こんにち)
   * 昨日は友だちと新しい喫茶店に行った。 (inflected 行った; 喫茶 きっさ)
   * コーヒーを二杯ください。 (counter 二杯 にはい)
   * ひらがなだけのぶん。 (kana-only: no hint payload, no spoken markup)
   Pass: readings are the selected ones, no bracket/ruby/`<b>` tokens are spoken, no omissions or
   additions, natural prosody, MP3 decodes and plays in Review. A rejected hint request must surface
   as a generation failure; it must not retry without hints.
5. Correct a reading in Edit (e.g. force 今日 → きょう on a second sentence), then regenerate. New
   audio must be requested with the corrected hint and the same voice; the old audio must not play.
6. Change the saved voice. Only sentence audio may be invalidated; translation, readings, recordings
   and image must survive.
7. Cost: confirm the actual per-character rate for the selected Chirp 3 HD voice and that two cards a
   day stays under USD 5/month, including retries.

## B. OpenRouter translation (R14)

Precondition: OpenRouter key entered; model shown read-only.

1. Translate 昨日は友だちと新しい喫茶店に行った。 Pass: faithful English, no invented detail, no
   photo or Notes content sent.
2. Edit the translation by hand, then press Retry on an unrelated failed field and re-send
   generation. Pass: the manual text survives; nothing overwrites it.
3. Force a failure: revoke the key in OpenRouter, press Retry. Pass: specific error, Send blocked,
   draft intact, no fallback model, no silent offline translation.
4. Confirm the pinned model string in Settings matches the model that actually answered (OpenRouter
   activity log).

## C. End-to-end export into a disposable collection (R24, R25, R23, R11-final)

Precondition: A and B pass; a **throwaway** AnkiDroid profile with the reference note type installed.

1. Mine 昨日は友だちと新しい喫茶店に行った。 with target 新しい, then **Send to AnkiDroid**.
   Pass: success only after note + destination + media are confirmed; the app returns to a blank
   capture with settings and last-sent tags kept.
2. Open the note in AnkiDroid's browser and check every field:
   `SentKanji` (only 新しい wrapped in `<b>`), `SentFurigana` (bracket furigana with the same
   highlight), `SentEng`, `SentAudio` (`[sound:…]`), `VocabKanji` 新しい, `VocabFurigana`,
   `VocabDef`, `VocabPitch` (one AJT `html_and_number` pattern, nested `nasal`/`handakuten` preserved),
   `VocabAudio`, `Image` (empty for Type input, filename only for Photo), `Notes`, and the three empty
   `fm_*` fields.
3. Look at the **rendered card**, not the field strings. Pass: pitch contour matches the numbers,
   the nasal dot on 悪逆/アクギャク renders as a mark and not as a mora, contracted kana and final
   drops are right, accent numbers ≥ 10 parse, audio and WebP image display.
4. Mine the same sentence+lemma again. Pass: duplicate is blocked with a clear reason; changing the
   deck or the sentence allows it.
5. Re-tap Send twice quickly. Pass: exactly one note; the second is serialized or refused.
6. Interrupt a send (force-stop between media import and insert if reproducible) and reopen. Pass:
   read-only reconciliation, no second insertion, no card moves, media receipts reused safely.

## D. Nice to have (not credentials)

* 200 % font + TalkBack walkthrough of Prepare → Review → Send (R21).
* Airplane-mode fresh install: local parsing/OCR work, network generation fails clearly (R03).
* Real gallery image share into a fresh draft (R06) — the adb-injected URI grant is not representative.
* Physical arm64 device: install, first-run unpack, one full journey (ARM64 runtime is only proven as
  packaging so far).
