# API 35 device smoke test

- Date: 2026-08-22
- Device: dedicated `betasafe_api35` Android Emulator, Google APIs x86_64
- Package: `com.betasafe.app`

## Result

The clean source reconstruction builds, installs, launches, persists settings, opens its hardened WebView, obtains explicit Android screen-capture consent, processes captured frames with the licensed local ONNX model, draws a touch-through overlay, and stops cleanly.

## Evidence

- A clean `testDebugUnitTest`, `assembleDebug`, `lintDebug`, 26-test `connectedDebugAndroidTest`, `assembleRelease`, and `lintRelease` run completed successfully.
- A streamed `adb install -r` completed successfully.
- Main, Settings, and Browser screens launched through normal in-app navigation with no fatal exception.
- The first-run disclosure, gothic/demoness shell, ten-section Help screen, permission status/repair controls, and complete 11-choice language dialog rendered correctly.
- Selecting German recreated Help with localized title, permissions, language status, and section labels; the default locale was restored after validation.
- Dynamic launcher shortcuts were registered for Start Protection and Open Browser.
- The Commitment Pact setup, matching keeper-code entry, consent checkbox, sealed countdown, wrong/correct-code contracts, and two-step safety release were exercised. An active pact routed configuration to the pact screen while the main protection control remained enabled.
- A generated gradient corpus exercised all nine censor types, four border effects, three reverse cutout shapes, and a real imported private custom-censor PNG.
- Export wrote a synthetic censored JPEG through MediaStore, decoded it from the returned gallery URI, and removed the test artifact. Source-deletion enablement required its second confirmation.
- Browser contracts exercised bounded HTTP image retrieval with session headers and oversize rejection, plus full-screen custom-view entry/exit and callback cleanup. Cleartext is restricted to localhost by a debug-only network-security config and is absent from release.
- The browser loaded Google over HTTPS, applied its protected footer, and remained below Android's system-bar insets.
- Changing the UI to `pixelate`, moving intensity to `83%`, and disabling the pink border produced the expected values in `betablocker_settings.xml`.
- Android displayed its standard MediaProjection consent sheet. The whole-screen option was selected for the test; no production consent path was bypassed.
- The foreground service and ongoing notification were present while protection ran.
- ONNX Runtime loaded `320n_fp16.onnx` through NNAPI.
- The first captured 324×720 frame completed inference in 97 ms on the emulator.
- Stopping from the app removed `ScreenCaptureService` and persisted the protected-session duration.
- Log review found no `FATAL EXCEPTION`, pipeline-start failure, or frame-processing failure.
- The release merged manifest contained neither a cleartext override nor any Device Admin surface; the debug APK verified with its normal v2 debug signature.

Notification and overlay grants were pre-authorized with ADB on this dedicated emulator so the test could focus on capture consent and runtime behavior. A normal installation still uses the explicit in-app permission flow.

## Remaining coverage

- Repeat on at least one physical Android 15/16 device, including rotation and display-size changes.
- Repeat the already working alternate accessibility screenshot path on physical hardware and across rotation/display-size changes.
- Benchmark CPU, heat, and battery use over a long session and tune capture cadence/presets.
- Exercise positive detections with a private, consented test corpus; do not add that corpus to version control.
