# AnkiDroid provider contract

The gateway targets the standard `com.ichi2.anki` package, not parallel installations.
Contract reviewed against AnkiDroid v2.24.0, commit
`ebcf8e0e34921628b9b8a496c66ffd4adbb3705f`, and current upstream equivalents:

- [API overview](https://github.com/ankidroid/Anki-Android/wiki/AnkiDroid-API)
- [FlashCardsContract](https://github.com/ankidroid/Anki-Android/blob/ebcf8e0e34921628b9b8a496c66ffd4adbb3705f/api/src/main/java/com/ichi2/anki/FlashCardsContract.kt)
- [Provider implementation](https://github.com/ankidroid/Anki-Android/blob/ebcf8e0e34921628b9b8a496c66ffd4adbb3705f/AnkiDroid/src/main/java/com/ichi2/anki/provider/CardContentProvider.kt)
- [Official add-note wrapper](https://github.com/ankidroid/Anki-Android/blob/main/api/src/main/java/com/ichi2/anki/api/AddContentApi.kt)

## Contract exercised

`AnkiDroidContractTest` runs the real ContentResolver gateway against an independent,
strict provider fake. It does not import production URI/column constants.

- `models`: `_id`, `name`, unit-separator-delimited `field_names` in model order.
- `decks`: `deck_id`, `deck_name`, `deck_dyn`; exclude filtered decks.
- `notes_v2`: SQL selection (`mid=?`) with bound arguments, not browser search syntax.
- Insert `notes` with `mid`, `flds`, `tags`; preserve field order and empty trailing fields.
  A note has no writable deck column: its generated cards initially use the default deck.
- Query `notes/{noteId}/cards`; move cards via `notes/{noteId}/cards/{ord}` using `deck_id`.
  `ord` is a template ordinal, not a card database ID. Re-query to verify all destinations.
- Missing cards, unsuccessful updates, revoked permission and uncertain writes must not be
  reported as successful, retried inserts, or destructive rollback.

`AnkiDroidGatewayTest` checks media column/URI contracts and failure classifications.
`AnkiDroidGatewayMediaImportTest` exercises receipt persistence, media import, note insertion
and destination verification together. `SendCoordinatorTest` and recovery tests verify the
durable phase boundary before insertion and prevention of blind reinsertion.

These are JVM/provider-boundary checks, not proof of integration with an installed device.
Device acceptance still requires a compatible note type/deck, API permission, actual media
playback, and verification of the resulting cards in AnkiDroid. A disabled Send button is a
local readiness/recovery problem before the provider insert, not an API response.
