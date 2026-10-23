# Settings state goldens

These Roborazzi references exercise the production `SettingsScreen`, not a mock layout. Five states
are recorded: no key, key saved, voice list loading, voice list authoritative, and voice list failed.
Each state has light/dark renders at font scales 1.0/2.0 and viewports 390×844/360×828 dp (40 PNGs).

File names are `<state>__<theme>__font-<100|200>__<width>x<height>.png`. For example,
`settings-voice-list-failed__dark__font-200__390x844.png` is the failed voice-list state at 200% text
in dark theme.

The saved-key fixtures use a synthetic, masked value only. The suite makes no network call and uses
no credential. The no-key voice picker explicitly shows the existing key-required guidance; a failed
voice load is visible on the Settings surface. Behavior tests also exercise the key/tag input fields,
note-type/deck/voice pickers, and voice selection persistence through flush/reopen. A separate device
test checks that both editable fields raise the IME in the production-style full-size dialog window.

Generate these plus the main real-screen catalogue from the repository root:

```sh
nix develop --command bash -lc 'gradle --offline --no-daemon --max-workers=1 --console=plain -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/36.0.0/aapt2" :app:renderDesignStates'
```

The main real-screen catalogue is documented in [`../states/README.md`](../states/README.md).
