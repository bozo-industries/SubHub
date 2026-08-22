# API 35 device smoke test

- Date: 2026-08-22
- Device: dedicated `betasafe_api35` Android Emulator, Google APIs x86_64
- Package: `com.betasafe.app`

## Result

The clean source reconstruction builds, installs, launches, persists settings, opens its hardened WebView, obtains explicit Android screen-capture consent, processes captured frames with the licensed local ONNX model, draws a touch-through overlay, and stops cleanly.

## Evidence

- `testDebugUnitTest`, `assembleDebug`, and `lintDebug` completed successfully.
- A streamed `adb install -r` completed successfully.
- Main, Settings, and Browser screens launched through normal in-app navigation with no fatal exception.
- The browser loaded Google over HTTPS, applied its protected footer, and remained below Android's system-bar insets.
- Changing the UI to `pixelate`, moving intensity to `83%`, and disabling the pink border produced the expected values in `betablocker_settings.xml`.
- Android displayed its standard MediaProjection consent sheet. The whole-screen option was selected for the test; no production consent path was bypassed.
- The foreground service and ongoing notification were present while protection ran.
- ONNX Runtime loaded `320n_fp16.onnx` through NNAPI.
- The first captured 324×720 frame completed inference in 97 ms on the emulator.
- Stopping from the app removed `ScreenCaptureService` and persisted the protected-session duration.
- Log review found no `FATAL EXCEPTION`, pipeline-start failure, or frame-processing failure.

Notification and overlay grants were pre-authorized with ADB on this dedicated emulator so the test could focus on capture consent and runtime behavior. A normal installation still uses the explicit in-app permission flow.

## Remaining coverage

- Repeat on at least one physical Android 15/16 device, including rotation and display-size changes.
- Validate the alternate accessibility screenshot service if that parity path is implemented.
- Benchmark CPU, heat, and battery use over a long session and tune capture cadence/presets.
- Exercise positive detections with a private, consented test corpus; do not add that corpus to version control.
