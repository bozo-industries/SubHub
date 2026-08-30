# Censor performance lab

This directory is the durable memory for real-time censor experiments. Chat history, loose logs,
and aggregate averages are not pass records. Every numbered build gets one record under `passes/`
and one row in [the registry](registry.md).

## Pass contract

Before implementation, a pass record must name:

1. exactly one primary hypothesis and vector;
2. a frozen baseline or rollback anchor;
3. objective success and regression thresholds;
4. the evidence that can falsify the hypothesis offline;
5. the evidence that still requires the physical Pixel and the Emiru Google Images gate.

After testing, the same record must contain the APK and evidence hashes, objective comparison,
human verdict, regressions, disposition, and the condition under which a rejected idea may be
reconsidered. Missing evidence is written as `not captured`; it is never interpreted as a win.

Pass states are `planned`, `candidate`, `accepted`, `rollback-anchor`, `rejected`, or
`inconclusive`. Only `accepted` and `rollback-anchor` may be used as performance baselines.

## Anti-loop rules

- Read [the registry](registry.md) and the relevant pass records before choosing a vector.
- Do not combine architecture, tracking thresholds, and rendering motion into one device pass.
- Do not repeat a rejected vector unless its recorded re-entry condition is now true.
- Emulator results prove API, lifecycle, race, and directional performance behavior only. They do
  not override Pixel NNAPI measurements or the user's visual verdict.
- Aggregate scroll displacement cannot hide per-event amplification, reversal, or viewport lead.
- A quality result never gains render authority merely because it is more detailed. It must pass
  epoch, motion-generation, fast-submission, and identity handoff fences.
- Detector omission is not evidence that content disappeared. Existing coverage ends through
  positive replacement evidence, offscreen clipping, or the asynchronous wall-clock cap.
- Prefer a censor one frame too long over one frame too short.
- Freeze and hash every APK offered for device testing. Later source edits get a new pass ID.

## Required record fields

Each pass file contains: status, date, baseline, commit/worktree identity, artifact hashes, device
and capture mode, hypothesis, exact vector, changed files, objective evidence, human verdict,
regression flags, rollback anchor, disposition, and next-eligibility condition.

Generated APKs, traces, and videos remain outside Git. Records store their filenames and SHA-256
hashes so a surviving artifact can be identified without committing it.

## Visual-oracle runtime

`scripts/analyze_scroll_alignment.py` requires NumPy and OpenCV; the headless OpenCV package is
sufficient. Keep those dependencies task-local or in a dedicated development environment rather
than installing them globally merely to analyze one pass. Its legacy page-wide flow fields are
retained for comparison, while `correctedAlignment` replaces disagreements with accepted local
background evidence around each censor; lazy-loaded masonry reflow must not be scored as censor
detachment. The trace analyzer accepts both Android `threadtime` and `time` logcat prefixes; a zero
gesture/motion count must be checked against raw tag counts before it is treated as evidence.

## Remote Censor Lab bundles

Diagnostics can start an explicit, bounded telemetry session without MediaProjection. The user
records video with Android's system recorder, stops the session, optionally selects that video,
and shares one ZIP. The bundle contains `manifest.json`, sanitized `trace.ndjson`, a short README,
and `screen-recording.mp4` only when the user chose one. It never includes OCR text, URLs,
foreground package names, screenshots collected by SubHub, installed-app inventories, credentials,
or automatic network transport.

Inspect a received bundle with:

`python scripts/inspect_censor_lab_bundle.py <bundle.zip> --extract-dir <new-directory>`

The generated `trace.log` is accepted by `scripts/analyze_censor_trace.ps1`; `sync-markers.json`
maps the visible start/stop cards onto monotonic trace time. Never extract unknown ZIP paths or
merge an untrusted bundle into an existing directory.

Android compatibility notes learned from this path:

- Compile availability is governed by the Android SDK stubs, not the configured Java language
  level. Avoid Java SE convenience APIs such as `Files.readString` when the Android API surface
  does not expose them; use bounded UTF-8 streams.
- An application `Context` is intentionally not a visual context and `getDisplay()` may throw.
  Background diagnostics should resolve display metadata through `WindowManager`, or receive an
  explicit display-scoped context when the exact window matters.
