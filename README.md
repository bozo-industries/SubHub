# BetaSafe

BetaSafe is a private Android reconstruction workspace for the licensed Beta Blocker 1.67 APK. The primary goal is a maintainable source implementation that preserves the original app's useful on-device behavior and visual language.

The primary workstream is now a clean, human-editable Android source reconstruction under `app/`. Raw JADX output remains private reference material; tracked code is written and maintained as ordinary source. See [Reconstruction Roadmap](docs/reconstruction-roadmap.md).

The repository contains sanitized notes, repeatable tooling, maintained source, and original project-owned art. The purchased APK, decompiled code, vendor art, model files, signing keys, and rebuilt APKs stay outside version control in the adjacent private workspace:

```text
C:\Users\user\Code\BetaSafe-private\
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
- Clean source baseline: builds, installs, and runs independently as `com.betasafe.app`
- API 35 emulator suite: 18 instrumented contracts plus unit/build/lint checks pass for UI/settings, browser workflows, both consent-driven capture modes, NNAPI inference, censor overlays/export, profiles/packs, diagnostics, statistics/achievements, Popup Storm, Help/onboarding/locales/shortcuts, commitment safety, and clean stop behavior
- Help and language surface: ten expandable source-authored guides, guided permission repair, first-run disclosure, two launcher shortcuts, and the recovered 11-choice locale selector are working; shell copy is localized and deeper untranslated copy falls back to English
- Consent-first commitment: bounded 30-minute to 7-day keeper-code pacts seal app configuration with an explicit countdown and unconditional safety release; they never use Device Admin, block uninstall/data clearing, or delay stopping protection
- Visual identity: original generated purple-demoness guardian, gothic header, horned-shield launcher icon, and Popup Storm sample are tracked project assets and appear throughout the maintained shell; vendor badges and detector models remain private imports

A patched APK signed with a new local key cannot update an installation signed by the vendor key. Export any settings you need before uninstalling the original, or change the package ID so both builds can coexist. Keep the same private local key for every later BetaSafe update.

## Working with the APK

Browse reconstructed logic under `BetaSafe-private\jadx\sources\com\betablocker\lite`. Make round-trip changes in `BetaSafe-private\apktool`, using the JADX tree only as a guide.

Create a fresh private workspace from a licensed APK:

```powershell
.\scripts\Initialize-Workspace.ps1 -ApkPath 'C:\path\to\licensed.apk'
```

Rebuild and align after editing smali or resources:

```powershell
.\scripts\Build-PatchedApk.ps1
```

The build runs `Test-NoMonetizationGate.ps1` first and stops if known billing, premium, paywall, purchase-token, or entitlement code indicators appear. Informational itch.io purchase/update text and Kotlin coroutine subscriptions are not monetization gates.

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

The editable debug APK is written to `app\build\outputs\apk\debug\app-debug.apk` with package ID `com.betasafe.app`, so it can coexist with the vendor-signed installation.

See [API 35 Device Smoke Test](docs/device-smoke-test.md) for the verified runtime path and the remaining physical-device coverage.

To sign, provide a private keystore path and process-scoped password variables. Password values are never passed as command-line arguments:

```powershell
$env:BETASAFE_KEYSTORE_PASSWORD = '<private value>'
$env:BETASAFE_KEY_PASSWORD = '<private value>'
.\scripts\New-LocalSigningKey.ps1 -KeystorePath 'C:\private\key.p12' -KeyAlias 'betasafe-local'
.\scripts\Build-PatchedApk.ps1 -KeystorePath 'C:\private\key.p12' -KeyAlias 'betasafe-local'
Remove-Item Env:\BETASAFE_KEYSTORE_PASSWORD, Env:\BETASAFE_KEY_PASSWORD
```

See [Architecture](docs/architecture.md) for code maps, [Generated Visual Assets](docs/generated-assets.md) for art provenance and prompt summaries, and [Static Evidence](docs/static-evidence.md) for the redacted extraction record.

## Boundaries

Do not commit or redistribute the vendor APK, code, art, models, user data, captures, or signing material. Confirm that any planned distribution and derivative work remains within the purchased license and applicable law.
