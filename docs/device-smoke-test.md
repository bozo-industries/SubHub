# API 35 device smoke test

- Date: 2026-08-23
- Device: dedicated `betasafe_api35` Android Emulator, Google APIs x86_64
- Package: `com.betasafe.app`

## Result

The clean source reconstruction builds, installs, launches, persists settings, opens its hardened WebView, obtains explicit Android screen-capture consent, processes captured frames with the licensed local ONNX model, draws a touch-through overlay, and stops cleanly.

## Evidence

- A clean `testDebugUnitTest`, `assembleDebug`, `lintDebug`, 43-test `connectedDebugAndroidTest`, `assembleRelease`, and `lintRelease` run completed successfully.
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
- The new purple-demoness App Mode screen rendered correctly with its Android-service status, all/selected mode controls, auto-resume disclosure, save action, real launcher icons, and exact package labels.
- App-mode instrumentation verified persisted selected-package state, auto-resume enabled/disabled boot behavior, explicit notification Disarm clearing both Accessibility and MediaProjection intent, launcher-only package discovery, and a non-exported boot receiver.
- With Calendar as the sole watched package, the live Accessibility service activated recognition on Calendar, loaded the licensed FP16 detector through NNAPI, processed a 1080×2400 frame in 77 ms, ignored its own overlay window event, and suspended immediately when Android Settings became foreground.

## 2026-08-23 SubHub shell and timer pass

- The maintained shell was reorganized into three fixed primary destinations—Censor, Limits, and Money—with a bounded bottom pill, visible active capsule, purpose-built icons, contextual secondary tools, and consistent dark/violet header and card treatment.
- A 15-activity visual smoke run launched and captured every user-facing surface without a crash.
- The same 15-screen pass completed at a forced 1600×2560/240 dpi tablet profile in portrait and landscape. Phone, 600 dp, and 840 dp resource breakpoints keep content bounded with adaptive gutters and column counts.
- Every editable form field now retains a label and short explanation above its value, including Limits budgets, Money Rules amounts, commitment codes, profile names, custom phrases, and the browser address/search field.
- Home's protection action switched to its active state, the restored current-session timer advanced once per second, and active session state survived activity recreation.
- Manual MediaProjection loaded `320n_fp16.onnx` through NNAPI and processed its first 486×1080 frame in 131 ms.
- Selected-app Accessibility mode activated on Calendar, loaded the detector through NNAPI, processed its first 1080×2400 frame in 45 ms, and suspended on the unselected Clock package.
- App-timer contracts cover per-app, combined, unselected-app, disabled, and local-day-reset behavior. Timers use the watched package set independently of censor recognition mode. In the live service, Android Settings was selected with a one-minute per-app budget; at the boundary the service logged `PER_APP` enforcement and Android reported the Nexus Launcher as `topResumedActivity`. Reopening a spent app follows the same pre-recognition budget check.
- The clean Accessibility declaration was corrected so Android can bind the signature-permission-protected service on target 35. A live all-app cycle prewarmed `320n_fp16.onnx` under NNAPI while capture slept, activated on Android Settings, produced its first processed 1080×2400 frame about 0.58 seconds after the foreground transition (138 ms inference), and suspended on return to SubHub. Window-manager inspection found no Accessibility overlay remaining after suspension.
- Automatic capture now invalidates in-flight frames on every accepted package transition, uses immediate overlay/popup teardown, and explicitly hides cleared reverse-mode content. The Accessibility event reaction timeout is 100 ms; screenshot cadence uses a 350 ms floor to avoid Android's rate-limit failures. Default solid boxes render as alpha-255 black and no longer retain an unnecessary second full-screen bitmap.

## 2026-08-23 renderer, controller, and responsive QA pass

- A clean 53-test unit suite, 59-test connected instrumentation suite, focused 31-test device regression pass, lint, and debug assembly completed with zero failures.
- The live overlay corpus rendered all nine censor effects continuously into a generated contact sheet. Consecutive source-frame refreshes remained opaque, custom phrases produced visible glyph pixels, and distant pixels outside each tracked region stayed transparent.
- Source-dependent effects now transfer the owned capture frame to the overlay without a second full-screen copy. Custom images decode asynchronously, reuse 129 precomputed aspect-crop buckets, retain deterministic track assignments, and are bounded by the selected performance preset.
- Low/Medium/High/Ultra use bounded 1/2/3/4-thread ONNX kernel budgets while the capture loop remains single-flight. This allows useful inference parallelism without overlapping captures or queued stale frames.
- Android System UI, notification, and permission-controller windows are rejected as transient foreground events. Unit regression coverage proves those events cannot replace the watched package; ordinary app transitions remain accepted.
- The controller EDIT boundary now covers Money, Limits, censor configuration, browser shields, custom images, profiles, packs, Popup Storm, diagnostics, commitment setup, permission repair, and destructive export settings. Locked pages remain readable; safety releases and non-configuration operations remain reachable.
- Money Rules now distinguishes Lifetime Blocks from post-enable rule events, previews Every-N math and caps live, preserves the saved remainder across ordinary rule edits, resets it only when the batch threshold changes or rules are disabled, and explicitly reports when the daily or weekly cap makes an otherwise eligible event worth zero.
- App Mode classifies bounded visible Accessibility text locally without OCR, merges it with overlapping model detections, and sends the fused boxes through the existing stable tracker so the same post does not create repeated money events.
- Image-heavy `.bbpack` inputs use explicit archive, entry, extracted-size, and entry-count bounds with actionable rejection messages instead of the former 50 MiB blanket failure.
- A 20-page real screenshot map passed at the phone profile and 1600×2560/240 dpi tablet profile. The final review verified centered icon-over-label Censor tools, vertically centered pill navigation, readable shared buttons/inputs, the compact three-row app picker, reduced main-flow copy, and a text-only EDIT control.

Notification and overlay grants were pre-authorized with ADB on this dedicated emulator so the test could focus on capture consent and runtime behavior. A normal installation still uses the explicit in-app permission flow.

## Remaining coverage

- Repeat on at least one physical Android 15/16 device, including rotation and display-size changes.
- Repeat the already working alternate accessibility screenshot path on physical hardware and across rotation/display-size changes.
- Benchmark CPU, heat, and battery use over a long session and tune capture cadence/presets.
- Exercise positive detections with a private, consented test corpus; do not add that corpus to version control.
