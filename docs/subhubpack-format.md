# SubHub pack format

`.subhubpack` is SubHub Studio's portable, account-free arrangement format. It is a bounded ZIP archive designed for local creation, review, sharing, and reversible activation. Schema version 1 is introduced with SubHub 0.6.0.

## Archive layout

```text
manifest.json
sections/modules.json
sections/censor.json
sections/limits.json
sections/wallet.json
sections/subliminal.json
sections/popup.json
assets/censor/*
assets/popup/*
assets/cover/*
```

Only sections declared by `includedSections` are required. The manifest records the format and schema versions, arrangement identity and author metadata, minimum SubHub version, included sections, optional lock groups, non-binding recommendations, per-asset SHA-256 values, and an integrity digest covering the manifest and section data.

Studio rejects duplicate or unsafe paths, unknown sections, missing entries, mismatched hashes, malformed metadata, oversized entries, archives that expand beyond the total limit, and arrangements requiring a newer SubHub build.

## Portable sections

| Section | Included data |
|---|---|
| `modules` | Feature-area enablement for Censor, Limits, Wallet, and Subliminal Messaging |
| `censor` | Detection preset, image/body and text filters, phrases, censor appearance, border/effect settings, and capture preference |
| `limits` | Generic per-app and shared allowance defaults only |
| `wallet` | Local tribute triggers, prices, batching, grace, dwell, caps, tamper cooldown, and paid-break configuration |
| `subliminal` | Preset, timing, opacity, text size, phrase groups, and custom phrases |
| `popup` | Popup Storm behavior and embedded popup images |

The schema is an explicit allowlist. Unknown fields are discarded rather than copied into application preferences.

## Deliberately excluded

An arrangement never carries:

- PayPal client IDs, secrets, access tokens, saved payer or wallet identifiers, approval state, or transaction history.
- Controller PIN material, permission state, Accessibility or Device Admin state, or Hardcore activation state.
- App package names, app assignments, per-app usage, or per-app allowance overrides.
- Current service state, release time, session data, statistics, achievements, ledger history, update state, or private filesystem paths.

Hardcore and service-duration fields are recommendations shown during activation. Studio never applies them automatically.

## Activation and locks

Only Dom Space can activate an arrangement. The review flow chooses sections, shows the proposed changes, writes a recovery journal, backs up affected keys, commits the new values, installs verified assets into private storage, and then records the active arrangement. An interrupted activation is rolled back at next startup.

Lock groups use stable section names rather than individual UI widgets. Locked groups stay read-only until Dom Space deactivates or replaces the arrangement. Replacing an arrangement restores the previous backup before applying the next one. Sub Space may create, import, duplicate, export, and share arrangements, but cannot activate, replace, or deactivate one.

Legacy `.bbpack` archives use their existing verifier and activation path. They remain import-only and are shown in the Studio Library; they are not silently converted to `.subhubpack`.
