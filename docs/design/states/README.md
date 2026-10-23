# Compose contract-state goldens

This directory contains 104 committed Roborazzi references: 13 real-screen states, each rendered in
light and dark themes, at font scales 1.0 and 2.0, at 390×844 dp and 360×828 dp. The previews call
`InputScreen`, `OnboardingScreen`, and `CardScreen`; they do not draw substitute screens.

## Naming

Each PNG is named:

`<state>__<theme>__font-<100|200>__<width>x<height>.png`

For example, `card-word-selected__dark__font-200__360x828.png` is the selected-word Card state in
dark theme, at 200% text, on the 360×828 dp viewport. The `font-100` and `font-200` qualifiers
mean Android font scales 1.0 and 2.0, respectively. The pixel image is rendered at xxhdpi; the
filename records the logical viewport in dp.

States rendered:

- `capture-camera-idle`
- `capture-camera-permission-prompt`
- `setup-dictionary-downloading`
- `capture-torch-on`
- `type-empty-field`
- `type-filled-sentence`
- `card-no-word-chosen`
- `card-word-selected`
- `card-pronunciation-preparing`
- `card-blocked-by-setup`
- `card-required-item-failed`
- `card-ready-to-send`
- `card-sent`

The setup-download state uses the real onboarding dictionary screen, the only production surface
that presents download progress. The Capture preview uses the production `InputScreen` with a static
camera-capability fixture; it does not bind CameraX in a JVM renderer.

## Regenerate

From the repository root, inside the pinned Nix development shell, run this one Gradle task:

```sh
nix develop --command bash -lc 'gradle --offline --no-daemon --max-workers=1 --console=plain -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/36.0.0/aapt2" :app:renderDesignStates'
```

It records the catalogue directly into this directory. Gradle's offline mode must be able to resolve
the pinned dependency set; the Nix environment supplies the toolchain and Noto Sans CJK JP. The
ordinary `testDebugUnitTest` lane compares the committed references rather than recording them.

## Review a change

1. Check the changed names and count under `docs/design/states/`; a complete catalogue has exactly
   104 PNGs. Confirm every changed image maps to an intentional state and qualifier.
2. Open the changed image and its previous committed version at native pixel size, side by side or
   with an image-diff viewer. A binary Git diff is not visual review. Check Japanese glyphs, text
   wrapping, control placement, contrast, and the fixed Card footer at both font scales.
3. If many references move together, identify the exact approved contract change that explains the
   shared movement in the change description. Do not accept a broad re-record as an explanation.
4. Run the comparison lane after recording and inspect the final diff. Keep generated images in this
   directory; do not replace them with browser captures or locally installed fonts.
