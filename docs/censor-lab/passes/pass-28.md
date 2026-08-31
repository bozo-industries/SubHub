# Pass 28 — MediaProjection teacher calibration

- Status: `implementation in progress`
- Date: 2026-08-31
- Runtime baseline: Pass27 Accessibility/App Mode
- Teacher capture: explicit, temporary full-screen MediaProjection session
- First device/app gate: Pixel 8 Pro + Chromium Emiru Google Images

## Primary hypothesis

Accessibility events reveal scroll intent and sparse/coalesced displacement but not the exact pixel
trajectory shown by every app. A temporary full-screen MediaProjection recording can act as a
teacher: its video provides the actual content translation while the Censor Lab trace records the
event, renderer, detector, and display timelines. A learner can fit a small device baseline and a
bounded correction for each stable scroll surface, then normal App Mode can predict presentation
phase without requiring MediaProjection again.

Teacher observations never become detector, tracker, cache, stats, penance, dwell, or tap authority.
The first implementation is shadow-only and cannot change visible censors.

## Calibration suite

Run the same gesture vocabulary on:

1. Chromium/WebView image results;
2. X or another custom/coalesced feed;
3. a RecyclerView/native list;
4. a Compose list;
5. a video/animated-feed rejection control;
6. a static-page false-motion control.

Each app run contains slow drags, medium drags, flings, short reversals, rapid jitter, return-scroll,
and idle settling. Repeat the suite on the Pixel and emulator profiles spanning 60/90/120 Hz,
different display sizes/densities, and constrained CPU/GPU configurations.

## Two-level profile

- Device baseline: event-delivery latency distribution, refresh/display timing, generic fling
  response, and confidence priors.
- Surface correction: gain and latency residuals, evidence reliability, reversal braking, and
  prediction horizon for a privacy-safe stable scroll-owner token.

Profiles contain numeric coefficients and confidence only. They are byte-bounded, versioned by
calibration schema/app/model/display configuration, expire conservatively, and fall back to the
event-only camera whenever confidence is insufficient.

## Synchronized evidence

The Censor Lab bundle already provides one `elapsedRealtimeNanos` timebase for trace events and
records MediaProjection video start/stop time, dimensions, frame rate, bitrate, display size,
density, and refresh rate. Pass28 adds:

- exact Accessibility event source and receipt uptime;
- salted surface token, confidence, cacheability, and document epoch;
- raw/resolved/applied scroll displacement and evidence kind;
- source/capture/current camera positions and motion generation;
- numeric-only live/cached censor geometry for video masking/scoring;
- renderer input-to-draw, displayed camera, prediction lead, and actual render tick;
- gesture start/end markers from the replay harness;
- decoded video-frame PTS plus robust global translation/confidence.

No trace field stores page text, URLs, OCR output, raw package names, raw Accessibility node IDs, or
uncropped frames. The user-approved video remains local and is deleted or exported under the
existing Censor Lab lifecycle.

## Learning and validation contract

1. Convert video frames to tiny luminance/edge samples and estimate global translation with
   feature/band agreement. Reject local video, animation, reflow, and ambiguous motion.
2. Align visual displacement with Accessibility source/receipt timelines and search bounded
   latency/gain parameters using robust loss.
3. Train on alternating gesture groups and evaluate on held-out groups from every app/device.
4. Promote a profile only when it improves held-out viewport residual by at least 30%, has under
   1% false visual acceptance on static/video/reflow controls, and never double-applies motion.
5. Normal mode may use the profile only to predict one presentation camera. It cannot mutate
   cumulative scroll, tracker offsets, motion generation, cache, detector scenes, or user stats.

## Initial success thresholds

- Pixel and emulator trace/video alignment error p95 at or below one recorded video frame.
- At least 90% accepted visual samples agree across three or more horizontal bands.
- Held-out active-scroll residual p95 improves by at least 30% in Chromium and the custom-feed app.
- Static/video/reflow false-camera acceptance remains below 1%.
- No refresh-rate-specific constants and no more than 10% residual spread across 60/90/120 Hz.
- Stored device + surface profile data stays below 64 KiB total and introduces no per-frame heap
  growth in normal App Mode.

## Loop guards

- Do not train on the same gestures used for final scoring.
- Do not infer unseen visual content or detector boxes from the calibration model.
- Do not branch on app package names in the camera equations.
- Do not apply visual displacement through `applyScrollMotion()`; it is presentation evidence only.
- Do not call a trace improvement a pass until the user confirms the resulting normal-mode motion.

## First Pixel baseline-only run — session `76affda105`

The first explicit full-screen session completed successfully and remained shadow-only. It recorded
80.08 seconds of local video and 1,265 telemetry records with zero dropped trace events. The pulled
artifact contained a valid 1072x2400 MP4 (111,892,927 bytes), 188 scroll events, 57 numeric scene
records, 128 renderer draw records, and no malformed trace lines. The start sync-card onset differed
from its trace marker by about 3 ms. The original analyzer paired the stop marker with the end of the
one-second stop-card hold, however, creating a false 0.987 clock slope. Marker alignment now groups
saturated runs and uses the onset edge at both ends.

Masked four-band optical flow accepted 1,676 of 1,777 sampled intervals. The first cached analysis
sampled every second frame and included multi-second encoder gaps. Its exploratory symmetric
Gaussian fit appeared to improve p95 from 149.5 px to 111.2 px, but an audit proved that kernel could
consume future Accessibility events and that its pseudo-held-out IDs were timeout-created scroll
bursts, not human gestures. Those numbers are retained only as a rejected-prototype record. The
learner now rejects timing gaps, uses decoded presentation timestamps, requires real touch-boundary
groups, and fits a one-sided causal response against an immediate event-only baseline. No profile
from this session was or can be promoted.

The run also exposed two measurement defects before a second gate:

- Chromium alternated one strong scroll owner with transient low-confidence companion sources,
  producing four session tokens and 94 low-confidence cache invalidations. Pass28 now keeps the
  proven same-window owner through at most eight companion callbacks/one second while still
  invalidating on expiry or a real window/owner change.
- Gap-derived scroll bursts are not precise gesture boundaries. The service now observes the
  system-only touch-interaction start/end events without intercepting input, records their source
  and receipt time, and tags every scroll callback with the active touch ID. This lets the next run
  train and hold out complete human gestures, including the post-lift fling tail.

Generic `AccessibilityService.onMotionEvent()` was researched but rejected for touchscreen input:
Android documents that requested motion-event sources are withheld from the rest of the system.
Touch exploration was also rejected because it changes normal gesture handling. Neither mechanism
is acceptable for a transparent censor, so the next student must use passive touch boundaries plus
Accessibility corrections or another non-intercepting observation path.

Before another run, both MediaProjection services also gained one synchronized, owner-checked
lease. Censor Lab and Screen Capture can no longer race two projection sessions or consume a grant
behind each other; every failure and teardown path releases only its own lease.
