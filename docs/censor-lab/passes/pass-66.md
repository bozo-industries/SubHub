# Pass 66: clean prepared frames expose timestamp provenance

Status: diagnostic capture verified; exact-pixel-time assumption disproved for Android15 window
screenshots. No rendering change and no claimed performance/accuracy acceptance.

Added an explicit debug/emulator-only PreparedFrameRecorder. A one-shot preference arms at most
64 frames. The capture worker transfers its fresh small pixel array to a background PNG encoder
with queue capacity2; encoding never runs synchronously on the capture worker. The images live in
app-private cache and are never committed. Normal builds/runs do not record. Synthetic Android tests
verify pixel fidelity, capture limits, invalid inputs and orderly close.

Emulator run: app/build/reports/device/pass66-clean-source. Ten gestures plus a415-frame recording;
415 embedded timing records matched decoded frames. Saved64/64 private prepared PNGs, with64 saved
status records and no failed/dropped writes. Binary-safe tar transfer produced64 matching files.
The row/save preferences were disabled afterward, target reinstall completed, and both false values
were verified. Pixel untouched; GPU/anchor flags remain off. All recording/analysis processes ended.

## Why the previous optical comparison was insufficient

This run contains72 raw/parsed row records and19 accepted estimates;17 pairs passed nearest-video
timestamp gates. Late reversal pairs again disagree strongly with time-matched video. But the
prepared source images at2684801 and2685065 themselves visibly move upward by roughly15 prepared
pixels, consistent with the row estimate rather than the small motion from the time-matched video.
Inspected prepared frames contain no SubHub masks. Do not call every Pass65 discrepancy a matcher bug.

Matching clean prepared images against nearby masked video frames gives better photometric matches
34-118ms before the reported screenshot timestamp for several rapid-reversal samples. For2684801,
the best candidate is118.2ms earlier, improving the edge-weighted score from25.93 to14.58. Some other
frames have broad/positive-offset minima, so there is no justified fixed delay correction. Clock-fit
spread is12ms; content matching remains a diagnostic subject to occlusion/compression/ambiguity.

## Verified Android15 API implementation

The exact Android15 source, [AccessibilityInteractionClient.sendWindowScreenshotSuccess](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-15.0.0_r1/core/java/android/view/accessibility/AccessibilityInteractionClient.java#L1354),
constructs ScreenshotResult with SystemClock.uptimeMillis after receiving the screenshot buffer,
before scheduling the application executor callback. This is a client receipt stamp, not a buffer
presentation timestamp. The source was read through the authenticated file API for that exact tag.

Our CaptureScrollTimeline and shadow reconciliation currently interpret the reported value as the
pixel-capture instant. That contract must be corrected before granting visual anchors authority.
Correction from Pass67 call-site audit: the legacy log field captureAgeMs is request-to-publication
duration (InferenceFrame.capturedAtUptimeMillis receives requestedAtUptimeMillis), not reported-stamp
age and not exact pixel age. Keep request, reported and callback timestamps distinct.
This finding does not prove a universal compositor delay, nor authorize subtracting a fixed offset.

## Bounded telemetry (recording plus private source export)

| Metric | Run |
| --- | --- |
| Raw / parsed publications | 22 / 22 |
| Request-to-publication age median / p95 ms | 382 / 565.95 |
| Published preprocess / runtime / postprocess median ms | 2.5 / 103.5 / 1 |
| Maximum cumulative inference drops | 0 |
| Capture callbacks complete / failed / partial | 29 / 2 / 1 |
| Preparation median / p95 ms | 111 / 209 |
| Publication queue median / p95 ms | 15 / 131.6 |
| Main-to-tick median / p95 ms | 14 / 74.5 |
| Motion input-to-draw p95 ms | 23.6 |
| Text publications | 3 |

Zero malformed capture records. Added recording/export load makes this an accuracy diagnostic, not
a comparison against unrecorded controls. Absolute coverage, text stability and one-frame flashes
remain unaccepted. No signed evaluation release or deployment.

Checks:548 JVM tests; lintDebug; paired APK builds; recorder instrumentation2/2; setup enable/disable
1/1 each; two private-frame status fixtures and two prepared-image matching fixtures. Source images
remain local ignored artifacts. Next: explicit screenshot-time provenance and uncertainty handling;
do not fuse client receipt timestamps as exact pixel-time measurements.
