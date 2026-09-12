# Pass 65: scoped camera shadow and timestamp-matched optical checks

Status: shadow integration verified; visual estimates still lack sufficient accuracy for rendering.

VisualCameraShadow now serializes same-producer event intervals and row measurements, bounds
producer history, rejects old document results and horizontal movement, resets on capture geometry
changes, and reports proposed camera/correction coordinates. It shares the existing debug-only
row-shadow opt-in. No value feeds the production camera, tracker, cache, or renderer.

Built and installed paired APKs, armed the flag through explicit emulator setup instrumentation,
then reinstalled the target to restore the bound service. Recorded ten gestures on the same Red
Bull article. Artifacts: app/build/reports/device/pass65-camera-shadow. Disabled the flag afterward,
waited for setup completion and matching target reinstall, and verified the preference false.
GPU/anchor experiments remained off; Pixel untouched.

## Accounting and motion evidence

Full recording trace:68 raw/parsed ROW_MOTION and68 raw/parsed ROW_CAMERA records. Fifteen row
estimates accepted; six also accepted by the reconciliation core; sixteen camera records exposed
interval uncertainty. Mean row CPU1.049ms, maximum5.820ms; wall mean1.868ms/max23ms. These counts
include setup and idle tail, not only the bounded gesture window. They do not prove accuracy.

Android Winscope v2 timing metadata supplies431 timestamps matching all431 decoded frames. Paired
CAPTURE_SPAN clocks supply823 wall/uptime correspondences, with14ms full spread. Twelve of fifteen
accepted row pairs had nearest video frames within30ms at each endpoint; three were explicitly
unmeasurable. This is approximate pixel correspondence, not identical captured/displayed frames.

Direct optical flow across the entire screenshot interval failed on large movements: inspecting
decoded frames10 and21 showed the page moving up about54 recorded pixels while direct flow reported
the wrong sign. Replaced that comparison with accumulated adjacent-frame flow, rejecting any pair
with missing edges. Do not use row-optical.json (the rejected direct-pair trial) as accuracy evidence;
row-optical-adjacent.json contains the corrected comparison.

Of twelve measured pairs, seven early/settled pairs differed by0.06-10.02 source pixels. Five later
rapid-direction-change pairs differed by33.27-77.11 pixels, including a wrong-direction disagreement.
Global feature flow, recording overhead and nearest-frame timing remain limitations; this is enough
to withhold rendering authority, not enough to label every discrepancy a row-estimator error.
All corrections remain shadow-only. The coarse row grid also needs precision scrutiny.

## Bounded gesture telemetry

| Metric | Recorded shadow run |
| --- | --- |
| Raw / parsed publications | 22 / 22 |
| Capture age median / p95 ms | 338.5 / 511.65 |
| Published preprocess / runtime / postprocess median ms | 2.5 / 87.5 / 1 |
| Maximum cumulative inference drops | 0 |
| Capture callbacks complete / failed / partial | 24 / 1 / 1 |
| Capture preparation median / p95 ms | 67.5 / 171.55 |
| Publication queue median / p95 ms | 5.5 / 146.5 |
| Main-to-tick median / p95 ms | 16.5 / 101.7 |
| Motion input-to-draw p95 ms | 17.45 |
| Text publications | 5 |

Capture parser malformed records:0. No text-group stability, absolute coverage, or one-frame-flash
acceptance. Do not pool this recording with unrecorded controls or call the camera shadow faster.

Checks:548 JVM tests, zero failures; lintDebug; paired APK builds; emulator enable/disable setup
1/1 each; three camera-parser, two timing-metadata, two adjacent-flow fixtures. Earlier ten optical
analyzer fixtures passed in Pass63. No production deployment or signed device-evaluation release.

Next: isolate the rapid-reversal disagreements using clean-source evidence and exact frame-pair
inspection; do not promote the row estimator based on accounting tests or first-direction success.
