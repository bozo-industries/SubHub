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

1. Compiling styled application shell.
2. Detector models, class mapping, preprocessing, ONNX execution, postprocessing, and unit tests.
3. MediaProjection capture, accessibility screenshot capture, object tracking, and overlay rendering.
4. Settings, censor styles, custom images, reverse censoring, and export rendering.
5. Safe browser, tabs, bookmarks/incognito behavior, DOM censoring, and ad blocking.
6. Statistics, achievements, profiles, packs, diagnostics, and popup features.
7. Device/emulator parity testing and performance presets.
8. Consent-based commitment lock, only after the reconstructed app works.

## Artifact boundary

Model files will be injected from a user-owned private location during local builds and will not be committed. The tracked project must remain buildable enough to run unit tests and assemble a shell without private assets; detector tasks will fail with a clear message until assets are supplied.
