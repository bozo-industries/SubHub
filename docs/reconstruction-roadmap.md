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
4. Settings, censor styles, custom images, reverse censoring, and export rendering. **Implemented; expanded live visual-corpus validation remains.**
5. Safe browser, tabs, bookmarks/incognito behavior, DOM censoring, and ad blocking. **Implemented, including bounded downloads and accurately disclosed private-tab limits; broader live-site validation remains.**
6. Statistics, achievements, profiles, packs, diagnostics, and popup features. **Statistics, 20 milestones, 42 achievements, profiles, hardened packs, local-only diagnostics, and safely constrained Popup Storm are implemented and API 35 validated.**
7. Device/emulator parity testing and performance presets. **Baseline API 35 smoke test complete; physical-device and wider performance coverage remain.**
8. Consent-based commitment lock, only after the reconstructed app works.

The source baseline is now independently buildable, installable, and operational. Milestones 4–7 describe additional feature parity with the vendor build rather than prerequisites for editing or running the reconstructed protection pipeline.

## Artifact boundary

Model files and optional achievement badges are injected from a user-owned APK by `scripts/Import-PrivateModelAssets.ps1` and are not committed. The tracked project still runs unit tests and assembles without them; runtime detector initialization reports a clear missing-model failure until models are supplied, while the achievements catalog remains usable without badge binaries.
