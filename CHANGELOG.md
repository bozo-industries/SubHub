# SubHub changelog

User-facing changes from every tagged SubHub release.

<details open>
<summary><strong>SubHub 0.6.3</strong></summary>

## What’s new in SubHub 0.6.3

### New

- **Add Atmosphere and trusted arrangement updates** — Brings Whispers and Popup Storm into one discoverable Atmosphere hub, and lets Sub Space apply an active Studio pack update only when its pack, creator-device, name, and author identity still match.

### Improved

- **Unify SubHub around a purple accent system** — Replaces the remaining hot-pink default chrome with a consistent violet palette across buttons, tabs, switches, selection tiles, quality icons, borders, and Whisper artwork.

### Quality

- **Publish the complete SubHub screen map** — Expands the device screenshot suite to cover both spaces and every major settings, Wallet, Studio, Atmosphere, diagnostics, update, achievement, and service-lock surface.

</details>

<details>
<summary><strong>SubHub 0.6.2</strong></summary>

## What’s new in SubHub 0.6.2

### Fixed

- **Simplify Home progress summary** — Present one focused current-service row and a compact two-row lifetime summary so Home stays useful without duplicating the detailed Statistics page.

</details>

<details>
<summary><strong>SubHub 0.6.1</strong></summary>

## What’s new in SubHub 0.6.1

### New

- **Complete app-wide service progress** — Expand Home and Statistics beyond censor counts with durable feature-aware service history and retuned achievements; preserve timed service state across process and Accessibility interruptions; let Dom Space release timed service…

</details>

<details>
<summary><strong>SubHub 0.6.0</strong></summary>

## What’s new in SubHub 0.6.0

### New

- **Add portable SubHub Studio arrangements** — Introduce the always-available Studio surface and bounded .subhubpack format with drafts, previews, embedded art, role-aware sharing, reviewed section merges, reversible backups, optional locks, and legacy .bbpack import.

</details>

<details>
<summary><strong>SubHub 0.5.10</strong></summary>

## What’s new in SubHub 0.5.10

### Fixed

- **Restore the full Hardcore guard** — Require both Accessibility and Device Admin before Hardcore protection is considered ready, surface persistent repair guidance after revocation or updates, and keep the Clear Storage target blocked while leaving Clear Cache usable.

</details>

<details>
<summary><strong>SubHub 0.5.9</strong></summary>

## What’s new in SubHub 0.5.9

### Fixed

- **Guard Clear Storage in Hardcore mode** — Resolve the real Android application window while the accessibility concealment overlay is present, so navigating from App info to Storage cannot make the guard inspect its own overlay and clear itself.

</details>

<details>
<summary><strong>SubHub 0.5.8</strong></summary>

## What’s new in SubHub 0.5.8

### Fixed

- **Refine arrangement labels** — Replace the remaining Ass and Anus category copy with Butt / Cheeks and Anal, and render ordinary censor, detection, and text-filter values in Title Case instead of arbitrary all-caps text.

</details>

<details>
<summary><strong>SubHub 0.5.7</strong></summary>

## What’s new in SubHub 0.5.7

### Fixed

- **State active filters on the Home card** — Label the compact Beta Filter summary explicitly as Image filter on/off and Text filter on/off.
- **Reuse Dom labels in arrangement details** — Render censor style, border, detection, text-filter, and phrase values from the same string resources used by Dom settings so the Sub-space summary cannot drift into alternate names.
- **Capitalize arrangement option labels** — Use consistent Title Case for body-area, text-filter, capture, phrase, and arrangement labels in both Dom settings and the Home detail sheets.

</details>

<details>
<summary><strong>SubHub 0.5.6</strong></summary>

## What’s new in SubHub 0.5.6

### New

- **Add premium motion and frosted plum surfaces** — Give tab changes, back navigation, and dialogs restrained directional motion while layering translucent plum surfaces and supported Android 12 blur for a calmer premium finish.
- **Add dated achievement detail cards** — Make every visible achievement tappable for a larger badge, description, progress, and unlock-date view, persist future unlock timestamps, and backfill existing unlocks to the 2026-08-27 update date.
- **Clarify Home arrangement and progress summaries** — Home now uses exact Dom labels, reports image/text state and enabled tribute-rule counts, shows SubHub's app icon with SUB SPACE or DOM SPACE, and puts the statistics button below session metrics.

### Documentation

- **Clarify cumulative release-note generation** — Document the distinction between the single-release changelog output and the cumulative history output so local release preparation cannot replace older changelog entries.

### Build & release

- **Bundle SubHub 0.5.5 release history** — Embed the complete collapsible 0.5.5 changelog in both repository Markdown and the in-app release history.
- **Stage immutable releases before publication** — Upload verified release assets one at a time to a reusable draft, compare the complete remote asset set with local build outputs, and only then publish the immutable release.
- **Publish SubHub 0.5.6** — Roll the complete tested 0.5.5 feature set forward after GitHub's immutable-release policy permanently consumed the unpublished v0.5.5 tag.

</details>

<details>
<summary><strong>SubHub 0.5.4</strong></summary>

## What’s new in SubHub 0.5.4

### New

- **Add browsable release and arrangement details** — Make every tagged changelog available as compact expandable update cards and let Home arrangement cards reveal their full active selections without exposing edit controls.
- **Give SubHub a new launcher identity** — Replace the fragile horned shield with an approved demonic-heart lock mark that remains legible inside Android's adaptive safe zone.

### Build & release

- **Add release-run recovery** — Allow an existing immutable tag to be rebuilt through a manual workflow dispatch when GitHub leaves the original tag event stuck before job creation.

</details>

<details>
<summary><strong>SubHub 0.5.3</strong></summary>

## What’s new in SubHub 0.5.3

### New

- **Rebuild the Home dashboard** — Replace the stacked Home cards with a responsive command, arrangement, and progress hierarchy.
- **Rebuild the Home command deck** — Home now uses a cohesive dark command deck with compact single-line arrangement cards and calmer service controls.

### Faster & smoother

- **Keep Ultra's hot path bounded** — Expired censor tracks now leave the tracker instead of accumulating for an entire protection session, while low-confidence model candidates are rejected before class metadata work and small per-frame collection allocations are reduced.

### Build & release

- **Generate meaningful release changelogs** — GitHub releases and the in-app updater now show categorized user-visible changes before separate APK guidance.

</details>

<details>
<summary><strong>SubHub 0.5.2</strong></summary>

## What’s new in SubHub 0.5.2

### Fixed

- **Stop app limits with protection** — Gate both daily-limit usage accounting and foreground eviction on SubHub's armed protection state.

</details>

<details>
<summary><strong>SubHub 0.5.1</strong></summary>

## What’s new in SubHub 0.5.1

### Fixed

- **Complete protected interactions and update handoff** — Start service-duration locks only after the selected capture path is available, and clear stale timed state when protection is no longer engaged.

</details>

<details>
<summary><strong>SubHub 0.5.0</strong></summary>

## What’s new in SubHub 0.5.0

### New

- **Add on-device subliminal messaging** — Add an independent Subliminal module with Dom-only configuration, per-app assignment, safe touch-through Accessibility overlays, phrase packs, profile persistence, Sub-mode status, aggregate statistics, and four tiered achievements.

</details>

<details>
<summary><strong>SubHub 0.4.0</strong></summary>

## What’s new in SubHub 0.4.0

### New

- **Polish core control surfaces and payment flow** — Refresh Home, achievements, and censor-style controls with stronger hierarchy, truthful per-app limit summaries, larger milestone artwork, and a confined animated border renderer.
- **Rework Help and Wallet configuration** — Move Help & Safety to a dedicated Home card available in Dom and Sub views, place PayPal second-last in Settings, and expand the in-app guide to the current feature model.
- **Add signed GitHub release updates** — Discover stable and preview SubHub releases every six hours and expose a Help-linked maintenance screen in both Dom and Sub modes.

### Improved

- **Add GNU GPL v3 license** — Added the GNU General Public License version 3 to the project.

### Documentation

- **Refresh the SubHub product guide** — Replace the dense README with a concise visual product page, document saved-wallet auto-pay boundaries, update the 58-badge catalog, and remove every Safe Browser reference and stale capture.
- **Explain PayPal Wallet setup** — Add a current setup guide for Sandbox and Live REST apps, merchant eligibility, Save payment methods and Vault capabilities, payer-wallet authorization, and Germany-safe handling of PayPal's combined PayPal and Venmo label.

</details>

<details>
<summary><strong>SubHub 0.3.0</strong></summary>

## What’s new in SubHub 0.3.0

### New

- **Polish protection settings and milestones**

</details>

<details>
<summary><strong>SubHub 0.2.0</strong></summary>

## What’s new in SubHub 0.2.0

### New

- **Add illustrated SubHub achievement collection** — Expand the catalog from 42 to 56 state-backed milestones covering App Mode, limits, pacts, Hardcore, text filtering, packs, Wallet, PayPal vaulting, and paid pauses.
- **Give every achievement unique tiered artwork**

### Fixed

- **Preserve protection state when enabling Hardcore** — Treat Device Admin as a capability grant instead of implicitly arming App Mode.

### Documentation

- **Give SubHub a polished visual README** — Replace the reconstruction-led presentation with a product-first story, paired device screenshots, scene-aware copy, and compact build details.

### Build & release

- **Make the Gradle wrapper executable** — GitHub's Linux runners could not start gradlew from a fresh checkout.

</details>

<details>
<summary><strong>SubHub 0.1.0</strong></summary>

## What’s new in SubHub 0.1.0

### New

- **Establish private APK modification workflow**
- **Add compiling source reconstruction foundation**
- **Reconstruct detector and tracking core**
- **Add consent-first capture and overlay pipeline**
- **Add live censor settings and styles**
- **Rebuild the hardened safe browser**
- **Restore local protection statistics**
- **Restore detector performance presets**
- **Restore advanced censor effects**
- **Add export and accessibility capture**
- **Restore advanced browser workflow**
- **Add profiles and hardened packs**
- **Add local runtime diagnostics**
- **Restore statistics and achievements**
- **Restore safely constrained popup storm**
- **Add gothic help and locale shell**
- **Add consent-first commitment pact**
- **Add consent-first penance ledger**
- **Add PayPal settlement service**
- **Add battery-aware app recognition mode**
- **Restore visual parity and working app limits**
- **Rebrand app as SubHub with adaptive navigation**
- **Polish SubHub controls and capture stability**
- **Complete controller-gated SubHub surfaces**
- **Complete detection and interface reliability pass**
- **Add modular capture and wallet settings**
- **Add Dom and Sub presentation modes**
- **Add consensual Hardcore Mode and Sub checkout**
- **Finish SubHub control and payment pass**
- **Add timed pacts and guarded hardcore payments**
- **Complete SubHub home and paid pause pass**
- **Add local smut model and Ultra OCR**
- **Rebuild text censoring around exact OCR lines**
- **Finish wallet authorization and copy pass**
- **Polish the SubHub control experience** — Refresh the visual system, add live censor previews, make App Mode the new-install default, gate tribute events behind active service, and add rate-limited Hardcore tamper tributes.

### Fixed

- **Audit every decoded DEX tree**
- **Validate API 35 runtime behavior**
- **Restore persistent automatic censoring**
- **Ignore transient system windows in app mode**
- **Improve native text detection and alignment**
- **Track censors during continuous scrolling**
- **Simplify settings navigation controls**
- **Enforce Hardcore protection boundary**
- **Charge censor taps at live overlay bounds**
- **Clean adaptive launcher icon**
- **Align text censors and conceal guarded controls**
- **Put Hardcore first and normalize Censor header**
- **Align text bars and stabilize video linger**
- **Anchor censor overlays to the full display**
- **Stop censoring outside assigned apps**
- **Enforce censor scope from live app windows**
- **Detect censor taps and verify PayPal credentials**
- **Fall back to standard PayPal checkout**
- **Clarify German PayPal vault eligibility**
- **Finalize PayPal wallet after browser fallback**

### Faster & smoother

- **Precompute and bound live censor rendering**
- **Keep accessibility censors aligned while scrolling**
- **Decouple Ultra capture from inference**
- **Accelerate Ultra detection pipeline**
- **Move censors with accessibility scroll events**
- **Keep Ultra inference active while scrolling**
- **Run text detection concurrently**
- **Cut APK size with ABI splits and compact assets**

### Improved

- **Refine home sessions and detection behavior**

### Documentation

- **Explain patched APK signing constraints**
- **Define full feature parity scope**
- **Record renderer and responsive QA**
- **Clarify Android geometry test placement**

### Build & release

- **Enforce monetization-free APK builds**
- **Automate signed Android releases** — Centralize Android version metadata, build universal and per-ABI artifacts, validate signing inputs, and publish verified APKs with checksums.

### Quality

- **Expand end-to-end Android coverage**

### Maintenance

- **Ignore local Jolli integration state**

</details>
