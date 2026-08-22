# Beta Blocker 1.67 feature parity matrix

This matrix separates behavior recovered from the user-owned APK from the maintained BetaSafe implementation. Decompiled output and licensed binary assets remain in `C:\Users\user\Code\BetaSafe-private`; tracked source is independently written and human-editable.

## Protection pipeline

| Feature | APK evidence | BetaSafe status |
| --- | --- | --- |
| 18-class NudeNet-compatible ONNX detector | `DetectionEngine`, two 320-series models | Working and API 35 device-validated |
| Low/Medium/High/Ultra performance presets | `DetectionPresets`, `PresetInfo` | Implemented from recovered values; emulator recheck pending |
| MediaProjection whole-screen capture | `ScreenCaptureService` | Working and device-validated |
| Accessibility screenshot capture | `ScreenshotAccessibilityService`, `canTakeScreenshot=true` | To implement |
| Object tracking and motion prediction | `ObjectTracker`, `TrackedObject` | Working with preset-specific velocity tuning |
| Touch-through overlay censoring | `OverlayController`, `CensorBoxView` | Working core |
| Solid, pixelate, blur, custom, static, glitch, tape, and error-popup styles | `CensorEffects`, settings resource IDs | Box/pixelate/blur/bar baseline; remaining real effects to implement |
| Border classic/glow/gradient/rainbow and animation | `CensorBoxView`, border resources/preferences | To implement |
| Phrase categories and custom phrases | `CensorPhrases`, settings preferences | To implement |
| Reverse censor mode and shaped cutouts | `ReverseCensorConfig`, `ReverseCensorView` | To implement |
| Custom censor-image library | `CustomImageManager`, `CustomImagePool` | To implement |
| Censored multi-image export and optional source deletion | `CensorRenderer`, export tab | To implement with explicit SAF/MediaStore consent |

## Browser

| Feature | APK evidence | BetaSafe status |
| --- | --- | --- |
| Hardened WebView, mixed-content/file isolation, Safe Browsing | `MainActivity.setupWebView` | Working |
| DOM pre-blur, selector filter, background stripping | `DomController` | Pre-blur working; toggles/remaining filters to implement |
| Request ad blocking | `AdBlocker` | Working baseline |
| Multiple tabs and per-tab close/switch | `BrowserTab`, tab strip | To implement |
| Incognito tab behavior | `BrowserTab.isIncognito` | To implement without claiming OS-level profile isolation |
| Bookmarks | `browser_bookmarks` preferences | To implement |
| Search suggestions | Google Firefox suggestion endpoint | To implement with bounded/redacted requests |
| File and image downloads; censor-before-save | WebView download handler | To implement |
| Full-screen media | WebChromeClient custom-view methods | To implement |

## Configuration and extensions

| Feature | APK evidence | BetaSafe status |
| --- | --- | --- |
| Named settings profiles | `ProfileManager` | To implement |
| `.bbpack` import/install/activate/deactivate/delete | `PackManager`, `PacksActivity` | To implement with Zip Slip/size/path validation |
| Signed-pack verification and manifests | `PackVerifier`, `PackManifest` | To implement and label digest verification accurately |
| Pack-controlled locked settings and restoration | `LockedSettings`, preference backups | To implement; this is configuration locking, not uninstall resistance |
| Diagnostics counters and performance overlay | `DiagnosticsCollector`, port-8765 server | To implement in-app/loopback-only; unsafe unauthenticated LAN exposure will not be reproduced |
| Language selection | `LocaleHelper`, language resource names | To implement for recovered app-owned translations |

## Statistics and engagement

| Feature | APK evidence | BetaSafe status |
| --- | --- | --- |
| Lifetime/session blocks, time, sessions, peaks | `StatsRepository`, `StatsData` | Working baseline |
| Active dates, streak, history, browser/export/style/profile counters | `StatsRepository` | To implement |
| Session trend visualization | `SessionTrendView` | To implement |
| Milestones | `MilestoneManager` | To implement |
| Achievements, progress, unlock dialogs, badge saving | `AchievementManager`, badge assets | To implement |
| Popup Storm with folders, presets, bursts, bounce, denial effects | `popup/*`, `PopupStormActivity` | To implement with photosensitivity acknowledgement and a persistent stop control |
| Help/onboarding/permission repair/app shortcuts | `MainActivity` and layout/resource IDs | To implement |

## Licensed visual assets

The APK contains the logo/background, category/style/tab/status icons, and eight badge images. `Import-PrivateModelAssets.ps1` will be expanded into a private asset importer. Runtime code will tolerate missing decorative assets so ordinary source builds remain reproducible; detector models remain required only when protection or image analysis is started.

## Completion rule

A row moves to complete only after source implementation, unit or contract coverage where applicable, successful `assembleDebug`/lint, and an emulator workflow for user-visible behavior. Physical-device-only platform behavior remains explicitly labeled until tested on hardware.
