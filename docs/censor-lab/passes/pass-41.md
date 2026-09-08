# Pass 41: separate draw time from presentation time

Status: diagnostic correction verified; no claimed visual speed improvement.

## Finding and change

The OFF control (pass41-anchor-off-control, same ten gestures) still reported 44.5 ms
median input-to-draw. Inspection showed traceRenderedMotion used renderTimeMillis(), which
can return a future Choreographer expected-presentation timestamp. That was not execution
latency. The renderer now measures DRAW and SETTLED on SystemClock.uptimeMillis, separately
reports inputToPresentationMs, and marks drawClock=uptime. Animation evaluation is unchanged.

The analyzer keeps historical data but labels legacyUnknownClockDraws; do not compare old
input-to-draw directly with corrected values. The fixture checks both clocks, old records,
viewport residuals and render tick preservation. Commit 378a8bc is pushed and remotely verified.
Full unit/lint/paired APK builds passed; both working and independently staged parser fixtures
passed. Existing unrelated prototype integration remains dirty.

## Corrected emulator control

- APK SHA256: 2460A15E04EB2279577BECAD704F88805158359BC39FA69E1DDB5CB35E9CCE76.
- Local trace/analysis: app/build/reports/device/pass41-clock-separated/.
- Experiment OFF, ten Chrome Emiru gestures, 11.422 seconds, trace-only; collector completed.
- 29/29 fast records parsed; 34/34 draw records explicitly use uptime.
- Actual input-to-draw p50/p95/max: 6.5/35.35/64 ms.
- Expected input-to-presentation p50/p95/max: 42/73.45/101 ms. This is not measured display
  latency; it is the animation/presentation clock target.
- Fast capture-age p50/p95: 236/297 ms; inference 51/104.8; runtime 48/89;
  preprocessing 3/5.6; postprocessing 1/7.6; publish interval 351/830.15 ms.
- Per-publish dropped counter zero; no claim about all supersession/cancellation types.
- Request-to-capture p50/p95 45/62.55 ms; callback delay 1/4 ms.

Next incomplete action: reconstruct per-capture accepted/dispatch/callback/prepare/scene/fast
stages from CAPTURE_SPAN and FAST_READY. The slow total is not explained by the median draw
execution time. Separate capture-worker readback, detector queue wait and main publication
delay before changing scheduling. Preserve timing units: CAPTURE_PREPARE uses microseconds;
CAPTURE_SPAN uses uptime milliseconds. Do not add medians from unmatched populations or
claim optical alignment based on these internal timestamps.

Current emulator retains the corrected build with anchors OFF. No Pixel validation; goal
active. Weekly remaining quota checked at start: 51 percent, above the 5 percent stop limit.
