# Motion and haptics evidence

Captured on the attached API 37 x86_64 emulator (Android 17). For both runs, Android system settings were set to `system/haptic_feedback_enabled=0` and `global/animator_duration_scale=0`; window and transition animation scales were also set to `0`. The original values were restored after each run (`haptic_feedback_enabled=1`, animation scale settings returned to their prior defaults).

- `system-reduced-motion-capture-progress.png` — `FlowTheme` read the system animator setting as disabled. The instrumentation test asserted `LocalMotionEnabled == false`, an indeterminate progress range, the “Reading text…” state description, and successful switching to Type mode. The indicator is static; the progress state remains labelled and the screen remains operable.
- `system-touch-feedback-disabled-type-mode.png` — the real `FlowTheme` dispatcher was active while system touch feedback was disabled. Switching to Type and displaying the sentence editor still worked. Physical haptic sensation was not assessed.

Instrumentation coverage also checks one platform feedback request for each committed event, no request while the lifecycle is backgrounded, no feedback for OCR progress/errors, and no duplicate tick when reselecting the current mode or word. The in-memory dispatcher assertions verify event routing; they do not claim to measure physical vibration.
