# Pass 25 — one content-space camera

- Status: `candidate`
- Date: 2026-08-30
- Baseline: Pass24 partial-success
- Device gate: Pixel Emiru Google Images, Accessibility mode
- Artifact: `SubHub-pass25-pixel-arm64-signed.apk`
- Artifact SHA-256: `3DF45B547A192A317384729ED47A844009A04A7F88F78CE45C59BB9831A5C0DA`

## Locked evidence

- Preserve Pass24's fast-first startup, fast-priority inference gate, generation fences, cache
  preservation, and interruptible omission grace.
- Do not tune smoothing constants or identity thresholds until birth-phase placement is objective.
- A censor born during motion must not enter the precise render layer at a phase that cannot be
  justified by its source-frame timestamp and the current viewport transform.

## Primary problem

The detector observes an asynchronous screenshot while the renderer advances a separately inferred
viewport. Reprojection can make totals correct while still placing a new track at the wrong visible
phase. Interpolation then makes the wrong placement smooth rather than accurate.

## Implemented vector

- Visual and text tracks are published in stable content/world coordinates.
- One camera projects established tracks, newly born tracks, retained coverage, text, and retained
  source-frame effects into the current display frame.
- Screenshot captures resolve the camera phase from the screenshot timestamp and the event-time
  motion timeline, including events delivered after screenshot callback but before inference
  publication.
- Event time owns capture/history reconstruction. Receipt/expected-vsync time owns presentation;
  a delayed callback cannot begin an animation in the past.
- Track geometry and its camera/generation metadata cross the render boundary as one immutable
  snapshot. Quality remains generation-fenced and cannot become a second camera authority.
- Text fallback identity is derived after conversion into world coordinates.

## Resolved architecture decisions

1. Every precise-layer track uses stable content coordinates and the same display-frame camera.
2. Stock Accessibility has no reliable display-rate screen-phase source. Pass22 node polling found
   nodes in roughly 54 ms but their refreshed bounds did not move between scroll events.
3. `AccessibilityService.onMotionEvent()` is not an acceptable fallback because the earlier trial
   intercepted normal touch. MediaProjection remains optional and cannot be the default because of
   recurring consent friction.
4. Unknown phase is an input-evidence problem. It must not be hidden with track-specific smoothing,
   quality geometry, debt amplification, or per-app constants.

## Cross-app parity gate

Chrome and X must run equivalent slow-scroll, fling, reversal, and short-jitter sequences. For each
app, record separately:

- physical/trace gesture start to first non-zero Accessibility motion;
- Accessibility event age at receipt;
- receipt to first camera draw;
- page optical flow versus censor-border motion;
- established-track residual, birth residual, omissions, duplicates, and settled handoffs.

Equivalent input evidence must produce equivalent camera output regardless of app. If X supplies
later or coarser evidence than Chrome, the failure belongs to the camera input boundary. The only
eligible follow-up is an app-agnostic provisional motion observation (for example conservative
whole-frame visual odometry) that is reconciled against later authoritative motion. It must never be
added as a second displacement or tuned with X-specific smoothing constants.

## Required objective gate

- Separate established-track alignment from birth-phase alignment.
- Measure center error and coverage on every display frame during slow scroll, fling, reversal, and
  short jitter gestures.
- Count precise-layer births with unproven phase, one-frame omissions, stale-track dwell, duplicates,
  overshoot, and post-settle refinement interruptions.
- Emulator/synthetic evidence may validate causality and races; only the Pixel Emiru replay and user
  verdict can accept the pass.

## Offline and emulator evidence

- `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`: passed.
- Exact Pixel-size emulator (1344×2992): 23 focused Android renderer, inference-priority,
  non-square scaling, retained-source, text, re-entry, lifecycle, and structure tests passed.
- Full Emiru trace: 233 scroll events, raw/applied displacement ratio 1.0, zero amplified events;
  all 23 captures crossing a motion generation resolved through the timestamped camera timeline.
- Recorded clean-grid subset: camera receipt-to-draw p50 45 ms. The corrected visual oracle compares
  each censor against its local unmasked background when page-wide optical flow disagrees or box
  lifecycle changes. It measured 0.43 px median absolute residual, 2.53 px p90, 3.38 px p95,
  95.08% within 5 px, and a 24 px maximum confined to a small refinement case.
- The emulator is not a speed gate. Screen recording pushed active-scroll inference to roughly one
  second and Accessibility event age to 448 ms p50 / 1,444.5 ms p95. Large residual-tail samples
  from the original global-flow oracle were frame-classified as lazy-loaded masonry reflow rather
  than censor detachment. The independent visual audit found no high-confidence sustained
  box-versus-page detachment in those outlier samples.

## Disposition

Pass25 remains a candidate until the Pixel Emiru and Chrome-vs-X gates classify first-evidence
latency separately from camera response. Do not tune smoothing or identity thresholds from the
overloaded x86 emulator trace.
