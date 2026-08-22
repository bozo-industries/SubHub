# BetaSafe

BetaSafe is a private Android reverse-engineering workspace for the licensed Beta Blocker 1.67 APK. The immediate goal is a reliable modification loop, not a premature source rewrite.

The repository contains only sanitized notes and repeatable tooling. The purchased APK, decompiled code, model files, signing keys, and rebuilt APKs stay outside version control in the adjacent private workspace:

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
- Device smoke test: pending; no ADB device was connected during setup

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

To sign, provide a private keystore path and process-scoped password variables. Password values are never passed as command-line arguments:

```powershell
$env:BETASAFE_KEYSTORE_PASSWORD = '<private value>'
$env:BETASAFE_KEY_PASSWORD = '<private value>'
.\scripts\New-LocalSigningKey.ps1 -KeystorePath 'C:\private\key.p12' -KeyAlias 'betasafe-local'
.\scripts\Build-PatchedApk.ps1 -KeystorePath 'C:\private\key.p12' -KeyAlias 'betasafe-local'
Remove-Item Env:\BETASAFE_KEYSTORE_PASSWORD, Env:\BETASAFE_KEY_PASSWORD
```

See [Architecture](docs/architecture.md) for code maps and [Static Evidence](docs/static-evidence.md) for the redacted extraction record.

## Boundaries

Do not commit or redistribute the vendor APK, code, art, models, user data, captures, or signing material. Confirm that any planned distribution and derivative work remains within the purchased license and applicable law.
