# Beta Blocker 1.67 feature parity matrix

This matrix separates behavior recovered from the user-owned APK from the maintained BetaSafe implementation. Decompiled output and licensed binary assets remain in `C:\Users\user\Code\BetaSafe-private`; tracked source is independently written and human-editable.

## Protection pipeline

| Feature | APK evidence | BetaSafe status |
| --- | --- | --- |
| 18-class NudeNet-compatible ONNX detector | `DetectionEngine`, two 320-series models | Working and API 35 device-validated |
| Low/Medium/High/Ultra performance presets | `DetectionPresets`, `PresetInfo` | Working from recovered values; settings UI emulator-validated |
| MediaProjection whole-screen capture | `ScreenCaptureService` | Working and device-validated |
| Accessibility screenshot capture | `ScreenshotAccessibilityService`, `canTakeScreenshot=true` | Working on Android 11+ with the platform-safe 500 ms screenshot cadence; API 35 service binding, NNAPI initialization, and first 1080×2400 frame emulator-validated |
| Object tracking and motion prediction | `ObjectTracker`, `TrackedObject` | Working with preset-specific velocity tuning |
| Touch-through overlay censoring | `OverlayController`, `CensorBoxView` | Working with tracked frame-region rendering |
| Solid, pixelate, blur, custom, static, glitch, tape, and error-popup styles | `CensorEffects`, settings resource IDs | Implemented; settings UI emulator-validated, live visual corpus check pending |
| Border classic/glow/gradient/rainbow and animation | `CensorBoxView`, border resources/preferences | Implemented; live visual corpus check pending |
| Phrase categories and custom phrases | `CensorPhrases`, settings preferences | Implemented with seven recovered categories and custom phrases |
| Reverse censor mode and shaped cutouts | `ReverseCensorConfig`, `ReverseCensorView` | Implemented; live visual corpus check pending |
| Custom censor-image library | `CustomImageManager`, `CustomImagePool` | Working private import/enable/delete/pool flow; emulator picker check pending |
| Censored multi-image export and optional source deletion | `CensorRenderer`, export tab | Implemented with cancellable SAF/MediaStore flow and second confirmation before post-save source deletion; UI emulator-validated, gallery corpus check pending |

## Browser

| Feature | APK evidence | BetaSafe status |
| --- | --- | --- |
| Hardened WebView, mixed-content/file isolation, Safe Browsing | `MainActivity.setupWebView` | Working |
| DOM pre-blur, selector filter, background stripping | `DomController` | Working with per-feature shield toggles; API 35 dialog and injection flow emulator-validated |
| Request ad blocking | `AdBlocker` | Working baseline |
| Multiple tabs and per-tab close/switch | `BrowserTab`, tab strip | Working; three-tab normal/private switch flow emulator-validated |
| Incognito tab behavior | `BrowserTab.isIncognito` | Implemented as accurately labeled private tabs with DOM storage/form/cache/history retention disabled; Android WebView cookie-jar limitation is disclosed |
| Bookmarks | `browser_bookmarks` preferences | Working local add/open/remove/deduplicate store; instrumented persistence contract added |
| Search suggestions | Google Firefox suggestion endpoint | Implemented as opt-in only, HTTPS, 3-second timeouts, 64 KiB response cap, and five-result cap |
| File and image downloads; censor-before-save | WebView download handler | Implemented with DownloadManager and a 25 MiB bounded on-device censor-before-save route; live remote-download check pending |
| Full-screen media | WebChromeClient custom-view methods | Implemented; live media-site check pending |

## Configuration and extensions

| Feature | APK evidence | BetaSafe status |
| --- | --- | --- |
| Named settings profiles | `ProfileManager` | Working with typed save/load/delete and a 50-profile bound; API 35 screen emulator-validated |
| Versioned settings backup | Profile/configuration preferences | Working JSON export/import with a 256 KiB input bound; private images, models, browser data, and secrets excluded |
| `.bbpack` import/install/activate/deactivate/delete | `PackManager`, `PacksActivity` | Working in private storage with canonical path, entry-count, per-entry, archive, and extracted-size limits; traversal rejection instrumented and API 35 screen emulator-validated |
| Pack manifest integrity verification | `PackVerifier`, `PackManifest` | Working with recovered canonical SHA-256 behavior and re-verification of installed manifests; accurately labeled as an integrity digest, not publisher authentication |
| Pack-controlled locked settings and restoration | `LockedSettings`, preference backups | Working with typed pre-activation backups and deactivation restoration; this is configuration locking, not uninstall resistance |
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
