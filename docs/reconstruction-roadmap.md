# Source reconstruction roadmap

## Objective

Recreate the licensed Beta Blocker Android 1.67 behavior as a normal, human-editable Android project that compiles and can be tested independently. Raw APK, JADX, smali, model, native binaries, and vendor art stay in the adjacent private workspace.

## Recovered build facts

- Original package/version: `com.betablocker.lite`, 1.67 (16)
- Original minimum/target SDK: 26 / 36
- Original build stack: Android Gradle Plugin 8.2.0, Kotlin plugin 1.9.22, Gradle 8.5
- Original runtime libraries: AppCompat 1.6.1, Core 1.12.0, Lifecycle 2.7.0, Material 1.11.0, coroutines 1.7.3, ONNX Runtime 1.20.0
- Bundled model variants: `320n.onnx` and `320n_fp16.onnx`
- Native ABIs: arm64-v8a and x86_64

The reconstruction initially targets SDK 35 because that is the installed local platform. Raising target/compile SDK to 36 is a separate toolchain checkpoint.

## Parity milestones

Detailed evidence and completion status are maintained in [Feature Parity Matrix](feature-parity-matrix.md).

1. Compiling styled application shell. **Complete.**
2. Detector models, class mapping, preprocessing, ONNX execution, postprocessing, and unit tests. **Core implementation complete and validated on an API 35 emulator with the licensed FP16 model and NNAPI.**
3. MediaProjection capture, accessibility screenshot capture, object tracking, and overlay rendering. **Both consent-driven capture paths, tracking, and overlays are implemented and API 35 device-validated.**
4. Settings, censor styles, custom images, reverse censoring, and export rendering. **Complete on API 35 with a generated synthetic corpus covering all nine styles, four border effects, three reverse shapes, real private custom-image import/render/delete, destructive confirmation, and an actual MediaStore JPEG save/decode/cleanup cycle. Private positive-detection corpus testing remains a release QA task.**
5. Safe browser, tabs, bookmarks/incognito behavior, DOM censoring, ad blocking, downloads, and full-screen media. **Complete on API 35, including bounded download/header/status contracts, custom-view lifecycle coverage, and accurately disclosed private-tab limits; broader live-site compatibility remains release QA.**
6. Statistics, achievements, profiles, packs, diagnostics, popup features, help, onboarding, and languages. **Statistics, 20 milestones, 60 illustrated achievements (42 recovered conditions plus 18 SubHub-native feature milestones), profiles, hardened packs, local-only diagnostics, safely constrained Popup Storm, ten-section Help, permission repair, launcher shortcuts, and the recovered 11-choice language selector are implemented and API 35 validated. Localized shell copy is present for ten explicit languages; deeper copy falls back to English.**
7. Device/emulator parity testing and performance presets. **The API 35 automated suite now covers 43 instrumented contracts alongside unit, assembly, and lint checks. Accessibility capture prewarms its model while sleeping, avoids unnecessary source-frame copies for solid effects, and uses a rate-limit-safe cadence; physical-device, long-run performance, and private positive-detection corpus coverage remain release QA.**
8. Commitment pact. **Complete as an app-local configuration pact with a bounded timer, keeper-code release, visible countdown, Sub-mode stop lock, Dom/PIN release path, and boot re-arming. It does not activate Device Admin; Android uninstall remains outside the app.**
9. Consent-based PayPal Money Rules. **Implemented as a source-authored feature: opt-in detector entries, correction handling, hard local caps, explicit PayPal approval, Dom-only Sandbox/Live authorization boundaries, encrypted per-install credentials, Magnes risk data, Orders v2 create/capture, exact settlement correlation, saved-wallet capability handling, and unconditional unpaid release. Changing environment or Client ID cancels active checkout and clears vault authorization. A saved-wallet token is never treated as ready until PayPal returns both vault and customer IDs.**
10. Battery-aware Always-On/App Mode. **Complete with a styled launcher-app picker, exact package matching, all-app and selected-app modes, idle screenshot/inference suspension, IME transition handling, persisted last armed state, pact-aware non-exported boot restoration, a visible resume/disarm notification, and a fresh Android prompt for MediaProjection. Optional Hardcore Mode preserves the explicit service state and does not claim Device Admin grants capture or keepalive.**
11. Daily watched-app budgets. **Complete with independently optional per-app and combined limits, recognition-mode-independent foreground accounting, local-calendar reset, supported Android Home enforcement, adaptive Limits controls, and instrumentation coverage.**
12. SubHub adaptive shell. **Complete with independently enabled Censor/Limits/Wallet areas, an always-visible global Settings area, feature-aware bottom-pill navigation, active-state icons and capsules, contextual secondary tools, and phone/600 dp/840 dp resource breakpoints validated in portrait and landscape.**
13. Hardcore Mode. **Complete as an explicit Dom-only Device Admin opt-in with no declared device policies, Android-controlled activation/revocation, deactivate-before-uninstall friction, and no implicit service start when admin access is granted.**

The source baseline is now independently buildable, installable, and operational. Milestones 4–7 describe additional feature parity with the vendor build rather than prerequisites for editing or running the reconstructed protection pipeline.

## Artifact boundary

Model files are injected from a user-owned APK by `scripts/Import-PrivateModelAssets.ps1` and are not committed. The script's legacy private-badge extraction remains available for archival comparison only; the app uses tracked, original SubHub achievement medallions. The tracked project still runs unit tests and assembles without the detector binaries, while detector initialization reports a clear missing-model failure until they are supplied.
