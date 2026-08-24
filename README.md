# SubHub

SubHub is a private Android reconstruction workspace based on the licensed Beta Blocker 1.67 APK. The primary goal is a maintainable, human-editable source implementation that preserves the original app's useful on-device behavior while providing a cohesive purple control-center interface.

The maintained source, Gradle project, and Android package now use the SubHub identity throughout; the application ID is `com.subhub.app`.

The primary workstream is now a clean, human-editable Android source reconstruction under `app/`. Raw JADX output remains private reference material; tracked code is written and maintained as ordinary source. See [Reconstruction Roadmap](docs/reconstruction-roadmap.md).

The repository contains sanitized notes, repeatable tooling, maintained source, and original project-owned art. The purchased APK, decompiled code, vendor art, model files, signing keys, and rebuilt APKs stay outside version control in the adjacent private workspace:

```text
C:\Users\user\Code\SubHub-private\
  apktool\   # authoritative, rebuildable resources + smali
  jadx\      # readable Java reconstruction for navigation
  build\     # unsigned/aligned/signed local APKs and local signing key
```

## Current state

- Original: Beta Blocker 1.67 (`com.betablocker.lite`, version code 16)
- APKTool 3.0.2 decode/rebuild: passing
- Zip alignment: passing
- Local v2/v3 signing and verification: passing
- JADX 1.5.5: usable, with method-level failures that require smali fallback
- Monetization-gate audit: no billing/paywall/entitlement code found; every scripted build rechecks this invariant
- Clean source baseline: builds, installs, and runs independently as `com.subhub.app`
- Clean release variant: unsigned release APK assembly and release lint pass; release manifest has no cleartext override and its optional Device Admin receiver declares no device policies
- API 35 emulator suite: 53 unit and 59 instrumented checks cover the adaptive SubHub shell, UI/settings, browser/download/full-screen workflows, both consent-driven capture modes, native-text classification, app-mode persistence/boot/disarm behavior, daily app budgets, NNAPI inference, every censor style/border/reverse shape, custom-image import, MediaStore export, profiles/packs, diagnostics, statistics/achievements, Popup Storm, Help/onboarding/locales/shortcuts, commitment safety, money-rule safety, and clean stop behavior
- Help and language surface: ten expandable source-authored guides, guided permission repair, first-run disclosure, two launcher shortcuts, and the recovered 11-choice locale selector are working; shell copy is localized and deeper untranslated copy falls back to English
- Commitment Pact: 1-hour, 24-hour, 7-day, or 30-day keeper-code pacts seal configuration, keep protection armed in Sub mode, require Dom mode to stop it, and re-arm App Mode after reboot
- Battery-aware app mode: the user-enabled Accessibility service can recognize in every external app or only selected launcher apps; selected mode keeps only the platform event binding alive and suspends screenshot scheduling, inference, tracking, and overlays everywhere else. Visible native text can be classified locally without OCR and fused into the same stable tracker as visual detections
- Opt-in daily app limits: watched apps can have an independent per-app budget, one combined budget, or both, independently of the censor-recognition mode; only foreground time accrues, usage resets at local midnight, and a spent app is returned to Android Home
- Predictable restart behavior: normal use restores the last armed/disarmed state; a sealed pact or Hardcore Mode forces App Mode armed after reboot, while MediaProjection still needs Android’s capture prompt
- Consent-first Money Rules: new stable detector tracks can add locally bounded EUR entries with Every-N batching, daily/weekly caps, and a correction window. Dom Settings can select Sandbox or Live, stores the selected installation's credentials with Android Keystore encryption, collects PayPal device risk data, and strictly verifies every Orders v2 capture
- Dom/Sub boundary: Sub mode presents one clean active-state dashboard with no configuration navigation; the controller PIN enters Dom mode for every rule and setting, while Start/Stop Protection and explicit Wallet settlement remain ordinary Sub actions
- Hardcore Mode: an optional Dom-only toggle launches Android's own Device Admin approval, declares no wipe/password/camera/lock/monitoring policies, and adds only the supported deactivate-before-uninstall step plus boot re-arming of App Mode intent
- Preset-aware rendering: Low/Medium/High/Ultra bound ONNX kernel concurrency to 1/2/3/4 threads and scale the predecoded custom-censor pool; capture remains single-flight so stale inference frames cannot queue
- Modular shell: Censor, Limits, and Wallet can each be enabled independently from the always-available Settings page. Disabled areas disappear from the centered bottom pill; visible destinations use a violet active capsule, purpose-built icons, dense utility cards, and adaptive phone/tablet gutters. Censor tools use centered icon-over-label tiles, and secondary tools retain the same palette and card language without crowding the primary navigation

A patched APK signed with a new local key cannot update an installation signed by the vendor key. Export any settings you need before uninstalling the original, or change the package ID so both builds can coexist. Keep the same private local key for every later SubHub update.

## Working with the APK

Browse reconstructed logic under `SubHub-private\jadx\sources\com\betablocker\lite`. Make round-trip changes in `SubHub-private\apktool`, using the JADX tree only as a guide.

Create a fresh private workspace from a licensed APK:

```powershell
.\scripts\Initialize-Workspace.ps1 -ApkPath 'C:\path\to\licensed.apk'
```

Rebuild and align after editing smali or resources:

```powershell
.\scripts\Build-PatchedApk.ps1
```

The patched-vendor build runs `Test-NoMonetizationGate.ps1` first and stops if known billing, premium, paywall, purchase-token, or entitlement gate indicators appear in the decoded purchased APK. This audit remains about removing vendor access gates; it does not scan or disable the clean source project's separate, opt-in Money Rules feature.

## Building the editable source project

The clean source tree builds without proprietary model assets, which keeps tests and UI work reproducible:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

To produce a detector-capable local APK, import the two licensed models directly from your purchased APK. They are ignored by Git:

```powershell
.\scripts\Import-PrivateModelAssets.ps1 -ApkPath 'C:\path\to\licensed.apk'
.\gradlew.bat clean testDebugUnitTest assembleDebug
```

The editable debug APK is written to `app\build\outputs\apk\debug\app-debug.apk` with package ID `com.subhub.app`, so it can coexist with the vendor-signed installation.

See [API 35 Device Smoke Test](docs/device-smoke-test.md) and [Visual Parity Audit](docs/visual-parity.md) for the verified runtime path, matched-screen review, and remaining physical-device coverage.

To sign, provide a private keystore path and process-scoped password variables. Password values are never passed as command-line arguments:

```powershell
$env:SUBHUB_KEYSTORE_PASSWORD = '<private value>'
$env:SUBHUB_KEY_PASSWORD = '<private value>'
.\scripts\New-LocalSigningKey.ps1 -KeystorePath 'C:\private\key.p12' -KeyAlias 'subhub-local'
.\scripts\Build-PatchedApk.ps1 -KeystorePath 'C:\private\key.p12' -KeyAlias 'subhub-local'
Remove-Item Env:\SUBHUB_KEYSTORE_PASSWORD, Env:\SUBHUB_KEY_PASSWORD
```

See [Architecture](docs/architecture.md) for code maps, [Generated Visual Assets](docs/generated-assets.md) for art provenance and prompt summaries, and [Static Evidence](docs/static-evidence.md) for the redacted extraction record.

PayPal setup, saved-wallet eligibility, and the payment-security boundary are documented in [PayPal Money Rules Integration](docs/paypal-penance.md). Credentials are entered per installation and are not compiled into the APK.

## Boundaries

Do not commit or redistribute the vendor APK, code, art, models, user data, captures, or signing material. Confirm that any planned distribution and derivative work remains within the purchased license and applicable law.
