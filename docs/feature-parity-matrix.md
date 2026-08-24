# Beta Blocker 1.67 feature parity matrix

This matrix separates behavior recovered from the user-owned APK from the maintained SubHub implementation. Decompiled output and licensed binary assets remain in `C:\Users\user\Code\SubHub-private`; tracked source is independently written and human-editable.

## Protection pipeline

| Feature | APK evidence | SubHub status |
| --- | --- | --- |
| 18-class NudeNet-compatible ONNX detector | `DetectionEngine`, two 320-series models | Working and API 35 device-validated |
| Low/Medium/High/Ultra performance presets | `DetectionPresets`, `PresetInfo` | Working from recovered quality/cadence values with bounded 1/2/3/4-thread ONNX kernel budgets and preset-sized custom-image memory pools; capture remains single-flight; settings and policy are emulator-validated |
| MediaProjection whole-screen capture | `ScreenCaptureService` | Working and device-validated |
| Accessibility screenshot capture and selected-app mode | `ScreenshotAccessibilityService`, `canTakeScreenshot=true` | Working on Android 11+ with all-app or exact selected-package foreground rules. Selected mode keeps only the user-enabled event binding alive and suspends screenshot scheduling, inference, tracking, and overlays outside watched apps; keyboards, notifications, System UI, and permission sheets do not replace the foreground app; persistence, styled picker, boot/disarm contracts, API 35 binding, NNAPI initialization, and first 1080×2400 frame are validated |
| Object tracking and motion prediction | `ObjectTracker`, `TrackedObject` | Working with preset-specific velocity tuning |
| Touch-through overlay censoring | `OverlayController`, `CensorBoxView` | Working with tracked frame-region rendering |
| Solid, pixelate, blur, custom, static, glitch, tape, error-popup, and bar styles | `CensorEffects`, settings resource IDs | Working in live/export renderers; all nine types pass API 35 continuous live-draw, opacity, phrase-glyph, synthetic-corpus pixel-change, and region-boundary contracts, and settings UI is emulator-validated |
| Border classic/glow/gradient/rainbow and animation | `CensorBoxView`, border resources/preferences | Working in live/export renderers; all four effects and animation configuration pass the API 35 synthetic-corpus suite |
| Phrase categories and custom phrases | `CensorPhrases`, settings preferences | Implemented with seven recovered categories and custom phrases |
| Visible explicit-text detection without OCR | New maintained App Mode feature | Working from bounded Accessibility node text with strict/balanced/broad sensitivity and category switches; common obfuscation and broader explicit/D/s wording are normalized locally, aggregate post descriptions cannot enlarge precise child lines, matched spans project to line-height regions, and fusion/stable tracking prevent repeat counting |
| Reverse censor mode and shaped cutouts | `ReverseCensorConfig`, `ReverseCensorView` | Working with rectangle, rounded, and ellipse cutouts; each shape passes API 35 preserved-center/censored-field corpus contracts |
| Custom censor-image library | `CustomImageManager`, `CustomImagePool` | Working private import/enable/disable/preview/delete flow with 25 MiB/64-image storage bounds; the live pool decodes off-thread, uses preset-specific image/dimension caps, precomputes 129 aspect crops, and retains stable track assignments; real PNG rendering and library UI/empty-state are API 35 validated |
| Censored multi-image export and optional source deletion | `CensorRenderer`, export tab | Working with cancellable SAF/MediaStore flow and second confirmation before post-save source deletion; style rendering, destructive confirmation, actual JPEG MediaStore save/decode/cleanup, and UI are API 35 validated |

## Browser

| Feature | APK evidence | SubHub status |
| --- | --- | --- |
| Hardened WebView, mixed-content/file isolation, Safe Browsing | `MainActivity.setupWebView` | Working |
| DOM pre-blur, selector filter, background stripping | `DomController` | Working with per-feature shield toggles; API 35 dialog and injection flow emulator-validated |
| Request ad blocking | `AdBlocker` | Working baseline |
| Multiple tabs and per-tab close/switch | `BrowserTab`, tab strip | Working; three-tab normal/private switch flow emulator-validated |
| Incognito tab behavior | `BrowserTab.isIncognito` | Implemented as accurately labeled private tabs with DOM storage/form/cache/history retention disabled; Android WebView cookie-jar limitation is disclosed |
| Bookmarks | `browser_bookmarks` preferences | Working local add/open/remove/deduplicate store; instrumented persistence contract added |
| Search suggestions | Google Firefox suggestion endpoint | Implemented as opt-in only, HTTPS, 3-second timeouts, 64 KiB response cap, and five-result cap |
| File and image downloads; censor-before-save | WebView download handler | Working with DownloadManager and a 25 MiB bounded on-device censor-before-save route; API 35 loopback HTTP contracts verify 2xx enforcement, declared/streamed size bounds, body integrity, cookies, and user-agent forwarding, with cleartext restricted to localhost by a debug-only network-security config |
| Full-screen media | WebChromeClient custom-view methods | Working; API 35 custom-view enter/exit, content visibility, parent attachment, and callback cleanup contract validated |

## Configuration and extensions

| Feature | APK evidence | SubHub status |
| --- | --- | --- |
| Named settings profiles | `ProfileManager` | Working with typed save/load/delete and a 50-profile bound; API 35 screen emulator-validated |
| Versioned settings backup | Profile/configuration preferences | Working JSON export/import with a 256 KiB input bound; private images, models, browser data, and secrets excluded |
| `.bbpack` import/install/activate/deactivate/delete | `PackManager`, `PacksActivity` | Working in private storage with canonical path, entry-count, per-entry, archive, and extracted-size limits sized for image-heavy packs (256 MiB archive, 96 MiB entry, 512 MiB extracted); traversal and limit behavior are instrumented and the screen is API 35 emulator-validated |
| Pack manifest integrity verification | `PackVerifier`, `PackManifest` | Working with recovered canonical SHA-256 behavior and re-verification of installed manifests; accurately labeled as an integrity digest, not publisher authentication |
| Pack-controlled locked settings and restoration | `LockedSettings`, preference backups | Working with typed pre-activation backups and deactivation restoration; this is configuration locking, not uninstall resistance |
| Diagnostics counters and performance overlay | `DiagnosticsCollector`, port-8765 server | Working as an in-app, process-memory-only screen plus optional touch-through overlay; provider/model, frame, latency, service, config, permission, and sanitized failure state are exposed without URLs, phrases, paths, secrets, exports, sockets, or LAN access |
| Language selection | `LocaleHelper`, language resource names | Working with the recovered 11-choice set (`system`, English, French, Spanish, Portuguese, German, Japanese, Simplified/Traditional Chinese, Korean, Russian), Android-native per-app locale persistence, and localized navigation/onboarding/help shell; deeper feature copy intentionally falls back to English pending reviewed translations; German switch/recreation API 35 validated |

## Statistics and engagement

| Feature | APK evidence | SubHub status |
| --- | --- | --- |
| Lifetime/session blocks, time, sessions, peaks | `StatsRepository`, `StatsData` | Working with unique-track accounting, persisted active-session start/block state, and a live one-second Home timer |
| Active dates, streak, history, browser/export/style/profile counters | `StatsRepository` | Working, including a bounded 30-session history and legacy numeric preference compatibility |
| Session trend visualization | `SessionTrendView` | Working custom on-device chart with accessible content description; API 35 UI emulator-validated |
| Milestones | `MilestoneManager` | All 20 recovered thresholds implemented, including major-milestone notices |
| Achievements, progress, and unlock presentation | `AchievementManager`, generated badge art | All 42 recovered conditions plus 18 SubHub-native feature milestones are implemented with persistent unlock/pending-notice state. Every one of the 60 entries has original illustrated medallion art, grouped progress cards, a compact Home preview, and a themed unlock card. Marathon targets one continuous protected day, Mega Marathon targets seven days, and four Wallet tiers use only cumulative confirmed paid settlements; API 35 catalog UI emulator-validated |
| Popup Storm with folders, presets, bursts, bounce, denial effects | `popup/*`, `PopupStormActivity` | Working with all four recovered presets, bounded SAF/private-folder scanning, a generated built-in sample, fade/rotation/bounce, burst ramps, denial blur/pixelation/captions, detection cover/avoid positioning, separate touch-modal popup windows, explicit photosensitivity acknowledgement, and an independent always-visible stop control; API 35 live overlay workflow validated |
| Help/onboarding/permission repair/app shortcuts | `MainActivity` and layout/resource IDs | Working with non-blocking first-run onboarding, ten collapsible source-authored help sections, current overlay/notification status, guided repair, accessibility settings entry, and dynamic Start Protection/Open Browser launcher shortcuts; API 35 UI and instrumented contracts validated |
| Commitment pact | New maintained feature; intentionally not Device Admin | Working with 30-minute through 7-day bounded pacts, keeper-code release, censor/browser-shield configuration gating, countdown, Sub-mode protection-stop locking, Dom/PIN release, and boot re-arming. Android uninstall/data clearing remain outside the app |
| Battery-aware Always-On/App Mode | New maintained feature | Working with all-app and selected-app recognition, a launcher-scoped styled picker, IME-safe foreground transitions, persisted last armed state, idle detector suspension, pact-aware non-exported boot handling, visible resume/disarm controls, and a fresh Android prompt for MediaProjection. Device Admin activation preserves the explicit armed/disarmed choice and grants neither capture authority nor keepalive |
| Watched-app daily budgets | New maintained feature | Working with opt-in per-app and combined limits independent of censor-recognition mode, foreground-only accounting independent of detector readiness, local-midnight reset, immediate Home enforcement, adaptive in-style controls, and policy instrumentation |
| Dom/Sub presentation boundary | New maintained feature | Sub mode is the process-start default and exposes one clean aggregate page with only enabled module state and Start/Stop Protection. It fully hides configuration, links, and bottom navigation. A controller PIN enters Dom mode, which exposes all feature-aware tabs and settings until explicit return to Sub mode or process end; focused API 35 contracts cover the boundary and optional-module visibility |
| Hardcore Mode | New maintained feature | Dom-only, opt-in Android Device Admin activation adds the platform's deactivate-before-uninstall step without starting service. Already-active protection keeps its persisted state, while idle protection stays idle. It requests no wipe, password, camera, force-lock, or monitoring policy; Android Settings still controls revocation |
| Sub Wallet settlement | New maintained feature | Sub mode shows balance/history plus explicit PayPal review, resume, and cancellation while hiding rule costs, caps, corrections, debug controls, Dom navigation, and the mode switch |

## Licensed visual assets

The maintained app now includes original, generated SubHub artwork: a purple demoness guardian, a wide grungy gothic header, a horned-shield launcher icon, a Popup Storm guardian-shield sample, and 60 distinct illustrated achievement medallions. These files are project-owned and tracked. Related milestone tiers use coordinated visual families rather than repeating one category image. `Import-PrivateModelAssets.ps1` still retains the private badge-extraction path as archival tooling, but the runtime achievement catalog no longer reads or redistributes those files. The two detector models remain separately imported and are required only when protection or image analysis is started.

## Completion rule

A row moves to complete only after source implementation, unit or contract coverage where applicable, successful `assembleDebug`/lint, and an emulator workflow for user-visible behavior. Physical-device-only platform behavior remains explicitly labeled until tested on hardware.
