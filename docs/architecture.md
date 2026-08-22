# Architecture map

This map is statically derived from Beta Blocker Android 1.67. JADX paths are readable navigation aids; APKTool smali is authoritative when the reconstructions disagree.

## Runtime pipeline

```text
MainActivity
  |-- MediaProjection consent -> ScreenCaptureService
  |                              -> ScreenCaptureManager
  |-- Accessibility capture  -> ScreenshotAccessibilityService
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

The clean source reconstruction intentionally omits that diagnostics server. It also contains no billing, payment, premium, entitlement, or remote-license implementation; the purchased APK's own app code did not contain such a gate in the audited DEX trees.

## JADX limitations

JADX emitted method-level errors in application code including `MainActivity`, `DetectionEngine`, both capture services, `CensorRenderer`, `ScreenCaptureManager`, `CensorBoxView`, pack parsing/management, and popup management. The Java files remain useful around those failures, but edits to affected methods must be derived from their smali implementation.

## Recommended next phase

Use the clean source tree for maintained changes. Continue the remaining parity work—help/onboarding, localization, and broader integration of the new project-owned visual identity—then complete broad emulator and physical Android 15/16 validation before adding the separately designed consent-based commitment lock.
