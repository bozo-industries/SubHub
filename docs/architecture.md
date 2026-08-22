# Architecture map

This map is statically derived from Beta Blocker Android 1.67. JADX paths are readable navigation aids; APKTool smali is authoritative when the reconstructions disagree.

## Runtime pipeline

```text
MainActivity
  |-- MediaProjection consent -> ScreenCaptureService
  |                              -> ScreenCaptureManager
  |-- App Mode configuration -> AppModeManager
  |-- Accessibility events   -> ScreenshotAccessibilityService
                                 |-- all external apps, or
                                 |-- selected launcher packages
                                  |
                                  v
                           DetectionEngine
                           ONNX Runtime + 320n model
                                  |
                                  v
                            ObjectTracker
                                  |
                                  v
              OverlayController / CensorRenderer / ReverseCensorView
```

`DetectionEngine` accepts screen bitmaps, runs an 18-class detector, applies confidence filtering/NMS, and produces bounding boxes. The tracker stabilizes detections between frames. Rendering is split between system overlays and image/export rendering.

The clean source implements both capture branches as ordinary Java source under `app/src/main/java/com/betasafe/app`. Android's own consent dialog authorizes every MediaProjection session, while Android's accessibility settings control the alternate screenshot service. Export rendering and reverse censoring are also maintained source. The app does not silently grant or retain platform capture authority.

`AppModeManager` persists whether automatic recognition is armed, whether every external app or only a selected package set is watched, and whether that armed preference should survive reboot. `ScreenshotAccessibilityService` consumes only window-state package transitions. In selected-app mode, leaving the active package cancels screenshot scheduling and closes inference-adjacent UI/state; the Accessibility binding stays available for the next package transition without running the detector. Input-method transitions are ignored so opening a keyboard does not falsely suspend recognition.

`BootReceiver` is non-exported and performs no capture. At boot or package replacement it restores only the user's armed preference and, when a prior MediaProjection session was desired, posts a visible notification that returns to the app for fresh Android approval. Its Disarm action clears both automatic recognition and pending session intent.

## High-value code areas

| Area | Readable JADX path | Rebuildable APKTool path |
| --- | --- | --- |
| App shell and browser | `jadx/sources/com/betablocker/lite/MainActivity.java` | `apktool/smali/com/betablocker/lite/MainActivity.smali` |
| MediaProjection service | `jadx/.../service/ScreenCaptureService.java` | `apktool/smali/.../service/ScreenCaptureService.smali` |
| Accessibility capture | `jadx/.../service/ScreenshotAccessibilityService.java` | `apktool/smali/.../service/ScreenshotAccessibilityService.smali` |
| ONNX inference | `jadx/.../detection/DetectionEngine.java` | `apktool/smali/.../detection/DetectionEngine.smali` |
| Class/category mapping | `jadx/.../detection/NudeNetClasses.java` | `apktool/smali/.../detection/NudeNetClasses.smali` |
| Tracking | `jadx/.../detection/ObjectTracker.java` | `apktool/smali/.../detection/ObjectTracker.smali` |
| Overlay rendering | `jadx/.../overlay/OverlayController.java` | `apktool/smali/.../overlay/OverlayController.smali` |
| Export rendering | `jadx/.../capture/CensorRenderer.java` | `apktool/smali/.../capture/CensorRenderer.smali` |
| Settings packs | `jadx/.../pack/PackManager.java` | `apktool/smali/.../pack/PackManager.smali` |
| Local statistics | `jadx/.../stats/StatsRepository.java` | `apktool/smali/.../stats/StatsRepository.smali` |

The private roots shown in the table are relative to `C:\Users\user\Code\BetaSafe-private`.

## Bundled inference assets

- `assets/320n.onnx`: approximately 11.6 MiB
- `assets/320n_fp16.onnx`: approximately 5.8 MiB
- ONNX Runtime JNI libraries: arm64-v8a and x86_64
- 18 detector outputs cover exposed/covered genitalia, breasts, buttocks, anus, torso, feet, armpits, and male/female faces

## Capture and platform surface

The manifest requests MediaProjection foreground-service, overlay, notification, Internet, and battery-optimization permissions. It also declares a non-exported accessibility service. The minimum SDK encoded in the APK is 26; the target/compile SDK is 36, although the storefront recommends Android 15 or newer for correct behavior.

## Network behavior

No Retrofit-style product backend or remote license API was found in the application package. Network use is primarily the built-in WebView, Google search suggestions, image download/export, and external support/social links.

The app also contains a diagnostics HTTP server on TCP port 8765. It constructs `ServerSocket(8765)`, exposes `/`, `/data`, and `/reset`, and has no apparent authentication. Because the socket is not explicitly bound to loopback, treat it as LAN-exposed whenever started. A future maintained implementation should remove it from release builds or bind it to loopback with explicit opt-in and authentication.

The clean source reconstruction intentionally omits that diagnostics server. The purchased APK's own app code did not contain a billing, premium, entitlement, or remote-license gate in the audited DEX trees. The maintained source now adds an original, optional PayPal settlement feature: detector events write only to an on-device bounded ledger, while a separately deployed backend creates/captures PayPal orders and verifies webhooks. No merchant credential is compiled into Android.

## Penance and payment boundary

`PenanceManager` stores opt-in rules, capped strike entries, mercy state, settlement state, and payment history in private app preferences. Both capture services forward only the count of newly observed tracker IDs; they never initiate a network request. The UI can forgive a false positive during mercy or release every unpaid entry at any time.

`payment-server/` owns PayPal OAuth, Orders v2 calls, capture, persistent order correlation, and webhook verification. Android sends a random settlement ID, exact bounded amount, and currency. PayPal approval returns through the backend to the app's `betasafe://paypal` route, but the backend does not capture on that redirect. Android requests capture only while the same local checkout remains active and accepts completion only when order ID, settlement ID, amount, and currency all match. Release builds permit HTTPS backend URLs only.

## JADX limitations

JADX emitted method-level errors in application code including `MainActivity`, `DetectionEngine`, both capture services, `CensorRenderer`, `ScreenCaptureManager`, `CensorBoxView`, pack parsing/management, and popup management. The Java files remain useful around those failures, but edits to affected methods must be derived from their smali implementation.

## App shell and language surface

`MainActivity` owns a non-blocking first-run card and two dynamic launcher shortcuts routed through its exported entry point. `HelpActivity` owns current permission status, one-step overlay/notification repair, the accessibility-settings handoff, language selection, and ten source-authored expandable guides. `LocaleHelper` persists the recovered 11-choice language set through AppCompat per-app locales. Navigation, onboarding, permission repair, and Help section labels have localized resources; untranslated detail deliberately uses Android's English resource fallback.

The maintained header and Help surfaces use the project-owned gothic header and demoness guardian art. They do not depend on the purchased APK's decorative assets.

## Consent-first commitment pact

`CommitmentManager` stores only pact timestamps, bounded duration, a random salt, and a PBKDF2-HMAC-SHA256 keeper-code hash in the app's private settings. `CommitmentActivity` provides the setup ceremony, live countdown, keeper release, and a permanently reachable two-step safety release. While active, entry to censor settings and browser-shield configuration routes to the pact screen. Protection stopping remains fully operational through the main UI and foreground-service notification.

This is intentionally an app-local consent ritual rather than a device-security primitive. The manifest has no Device Admin receiver. Android uninstall, app-data clearing, system controls, and immediate protection stop remain available, and the UI states those boundaries before activation and while the pact is active.

## Device Admin and restart boundary

Device Admin is deliberately absent. It is an enterprise policy surface, not a keepalive mechanism, foreground-app signal, or capture grant. Adding uninstall friction would not make recognition more reliable and would weaken BetaSafe's unconditional safety release. Modern Android also requires a new MediaProjection consent token for every capture session and prevents target-35 apps from starting a MediaProjection foreground service from `BOOT_COMPLETED`.

The maintained design therefore separates restartable intent from non-restartable capture authority: Accessibility app mode may resume because the user enabled that service in Android settings, while whole-screen MediaProjection resumes only after the user taps the visible notification and approves Android's system dialog again.

## Recommended next phase

Use the clean source tree for maintained changes. Complete broad emulator and physical Android 15/16 validation, including private visual corpora and long-running performance, before treating the reconstruction as release-ready.
