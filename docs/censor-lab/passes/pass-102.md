# Pass 102 — dense-frame review of rejected Pixel behavior

## Acceptance result

The user judged the installed Pass 101 experience "very bad" and correctly rejected
the initial two-second contact sheet as insufficient for visual analysis. Pass 101 is
**not accepted**. The earlier small timing sample did not demonstrate a good experience.

## Evidence and analysis scope

The newest user-provided lab export was pulled from the Pixel's Downloads. Its private
video and derived images remain under `app/build/reports/device/pass102-user-lab`, not
in Git. No new phone recording, settings change, or installation was performed.

- Manifest: 1,065 events, zero reported recorder drops; all 1,065 NDJSON records parsed.
- Video: all 1,535 frames decoded, matching the container frame count. Variable frame
  rate; the actual timestamps, not frame index divided by average FPS, were used.
- Measured mask-candidate footprint changes across 1,225 frames in the active-screen
  interval. Pink-bordered, mostly black rectangles selected transitions for inspection;
  these are not a semantic ground-truth segmentation or a false-positive-rate metric.
- Visually inspected 18 contact sheets at approximately 100 ms spacing, plus consecutive
  frame crops around the two highlighted failures. This is denser visual sampling, not
  a claim that every decoded frame was individually inspected by a human/model.
- Checked eight transitions with forward/backward-validated optical flow outside masks.
  151–363 source-feature pairs survived per transition; median source displacement was
  effectively zero. The large mask changes are therefore not explained by page motion.
- Independently verified the six cited frame timestamps using FFmpeg `-copyts`, a
  frame-index `select` filter, and `showinfo`. OpenCV reported an invalid negative time
  for the first frame only; FFmpeg confirmed first PTS zero and the selected later PTS.
  No global timestamp offset was applied to conceal that anomaly.

## Confirmed visible failures

1. **Stationary-page pulsing.** At frame 553 / 13.804356 s the large masks collapse
   to part-sized boxes. Frames 553–555 stay small; frame 556 / 13.854144 s expands
   them again: approximately 49.8 ms of reduced coverage, with stationary source pixels.
   Similar expansion/contraction occurs at frames 317/328 and 537/574.
2. **Large transient overcoverage.** Frame 1387 / 27.722056 s expands masks across
   neighboring image-tile regions; frame 1402 / 27.970344 s contracts them again.
   The excursion lasts about 248.3 ms. The source image remains effectively stationary.
3. **Scroll misalignment.** Dense sequences around 21.6–23.3 s and 25.5–27.5 s show
   boxes visibly displaced from their targets during movement and browser-header changes.
   A calibrated pixel-error distribution has not yet been established.
4. **Overlapping labels/regions.** Several expanded masks retain smaller labeled boxes
   on top of them, producing overlapping captions and inconsistent one-person coverage.

## Timing evidence and its limits

All 53 raw fast-publication records parsed. Reported `captureAgeMs` is the existing
request-to-publication metric, **not exact pixel age**. Median 121 ms, p95 167.8 ms,
maximum 189 ms. Inference median 34 ms / p95 47.2 / max 76; preprocessing 2 / 4.4 / 7;
runtime 31 / 44.4 / 70; postprocessing 1 / 2 / 5. The sampled queue-drop counter stayed
zero. These numbers do not excuse or quantify the visible continuity failures.

The archive contains no `PERSON_*` records because the new producer tags/prefixes were
not added to the Censor Lab recorder allowlist. Its manifest also omits coverage mode.
Absence is therefore **incomplete instrumentation**, not zero person-model work. A later
logcat read could no longer recover that recording interval. No exact claim is made
about whether each large rectangle came from the model or provisional geometry.

Code inspection identifies relevant mechanisms to reproduce: short proof-expiry followed
by fallback growth, per-source replacement of coverage, duplicate trigger associations,
one-shot denial of person work while quality owns the exclusion lock, and provisional
eight-head-height extrapolation bounded only by the viewport. These are candidates,
not yet a verified attribution for each video transition.

## Next implementation gates

1. Repair lab coverage-mode and person/geometry telemetry; add parser/recorder fixtures.
2. Reproduce recorded-cadence publication, expiry, and quality-admission transitions in
   deterministic tests before changing timing constants or enlarging retention windows.
3. Fix continuity and bounded person admission, constrain unsupported provisional growth,
   and consolidate same-person presentation without changing detector/tracker authority.
4. Address measured scroll displacement separately; do not hide it with larger masks.
5. Verify the exact source, sign a candidate, and repeat dense real-device evaluation.
   User acceptance remains mandatory. Do not call a green build or faster mean inference
   a completed visual fix.
