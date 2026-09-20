# Pass 104 — bounded provisional extent and decoration ownership

## Changes

The provisional head/torso heuristic no longer projects unseen legs to eight head-heights
or widens every subject to 3.2 head-widths. It uses the observed head/torso union with a
small head-relative margin. This is intentionally only a temporary estimate: the person
model still supplies full-person coverage, including an extent larger than that estimate.
Original trigger coverage is always preserved. This removes one source of unsupported
growth on cropped portraits; it does not establish image-tile boundaries or prove that
every cross-tile association in the rejected recording came from this heuristic.

When an expanded person mask contains another source-compatible visual region, only one
owns its label and border. Equal expansions choose a stable ID, independent of input order.
All original fill coverage remains; tracking, raw associations and penalties are unchanged.
Partially overlapping regions, text, different source bases and ordinary unexpanded masks
keep their decorations. Work is bounded to 128 regions and skipped when person coverage
is inactive or reverse mode is selected.

The enclosing owner is painted last so duplicate fills cannot erase its border. Both the
ordinary Canvas and cached solid RenderNode paths use that order; cached solid layers are
re-recorded when decoration ownership changes. This does not consolidate every effect's
internal artwork or replace the raw render-region representation with a person tracker.

## Verification

Exact staged source tree `8e6cbe30bcb77946895d612109c64c8d687f7979` exported to
`C:/Users/user/Code/SubHub-pass104-presentation-274e` passed **813 unit tests**, `lintDebug`,
`assembleDebug`, and `assembleDebugAndroidTest` together with
`-PimmediateQualityExperiment=true`. Unrelated dirty prototypes were excluded.

A task-owned, headless, read-only emulator received the matching target/test APKs. The
declared `SubHubTestRunner` was checked; terminal result was **OK (6 tests)**:

- Native model repeated execution/cancellation.
- Settings default/round-trip/invalid value and PIN-locked UI.
- Provisional/full-model pixels, retained shape and stale-token rejection.
- Two parts sharing a person box produce one label, an intact outer border, and unchanged
  original track geometry (software Canvas pixel verification).
- Native Lab bundle includes person records and coverage mode, closing Pass 103's
  previously compile-only Lab-export check.

The hardware RenderNode path compiled and shares the tested ownership/order logic; a
separate hardware pixel capture was not performed. The emulator was stopped after testing.
No phone installation or new Pixel recording occurred during that test phase.

## Authorized Pixel installation

After the user requested installation, private signing run `35508892994` passed for
source `b92f124df819c9a43b2a448d48dcb247ce34c52d`. Artifact provenance confirms the
immediate-quality flag enabled and public release disabled. No release or tag was created.
The ARM64 APK checksum and compatible release certificate were verified; the bundled
static person model retained SHA-256
`d891b8c939ce062b13fc7440195d427eeecbec307c2417564d6908309b263e19`.

Replace-install on the Pixel 8 Pro succeeded, preserving app data. Independent installed
APK readback matched SHA-256
`55431cc3840c1321178acf701c2515db953d9041717f3e088c10174b2f461942`.
Android reported version `0.6.3` / code `19`, last update `2026-09-20 13:54:48`,
a running app process, and SubHub still listed among enabled Accessibility services.
This verifies deployment, not visual acceptance. No new recording was started.

## Remaining acceptance

There are no new live capture-age, queue, model-time, publication-latency, or scroll-error
measurements for these changes. Pass 102's negative user verdict remains authoritative.
Scroll displacement and browser-header transitions still need measured reproduction and
repair. The installed candidate now needs dense Pixel video/trace evaluation,
and explicit user acceptance. Do not claim the visual experience fixed from these tests.
