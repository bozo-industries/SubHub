# Pass 67: typed capture-time provenance blocks invalid camera anchors

Status: timing contract and experimental guard corrected; normal render geometry remains unchanged.

CaptureTimeReference distinguishes window client receipt, display server completion, and explicitly
verified pixel-capture time. Accessibility factories never claim exact pixel timing. Invalid clock
ordering also cannot become exact. CaptureScrollTimeline now requires the typed reference, carries
it through InferenceFrame and phase refresh, and distinguishes event-order confidence from pixel-time
knowledge. Existing event-coordinate estimates and quality/cache policies remain unchanged: this is
not a claim that their approximate alignment has been solved.

VisualCameraShadow accepts a visual anchor only when both frame times are explicitly verified.
Receipt-time row pairs remain available as relative pixel observations but cannot drive time-based
camera correction. A clock-kind transition resets the shadow estimate rather than mixing origins.
No application Accessibility source currently provides the verified-pixel-time kind.

Added CAPTURE_TIME records with request/reported/callback timestamps and provenance. ROW_CAMERA now
reports pixelTimeKnown. The camera parser recognizes older records but marks their provenance
incomplete; a new record cannot report an accepted correction with pixelTimeKnown=false.

Corrected Pass66's metric description after auditing the InferenceFrame constructor: the existing
captureAgeMs field is request-to-publication latency, not reported-stamp age or exact pixel age.
No raw measurement changed. Android15's display screenshot path also stamps server completion,
not a buffer-provided presentation time; its source was checked at the same android-15.0.0_r1 tag.

## Verification and live compatibility

552 JVM tests, zero failures; lintDebug and paired assembleDebug/assembleDebugAndroidTest passed.
Tests explicitly separate ordered events from known pixel timing and reject receipt-based anchors.
Four camera-parser and two capture-time fixtures pass.

Emulator-only ten-gesture shadow run: app/build/reports/device/pass67-time-provenance. All32 raw
CAPTURE_TIME and32 ROW_CAMERA records parsed; all32 receipt timestamps have unknown pixel time,
all32 camera records are uncertain, zero accepted corrections. This is the intended guard, not
evidence that motion is absent. No source-frame recording was enabled.

Restored row/save flags false through setup instrumentation and completed target reinstall before
the separate pass67-control replay. Enable/disable setup each passed1/1. Both replays completed.

| Metric | Shadow diagnostic | OFF control |
| --- | --- | --- |
| Raw / parsed publications | 29 / 29 | 30 / 30 |
| Request-to-publication median / p95 ms | 157 / 244.6 | 142 / 220.5 |
| Preprocess / runtime / postprocess median ms | 2 / 41 / 1 | 2 / 34 / 1 |
| Maximum cumulative inference drops | 0 | 0 |
| Publication queue median / p95 ms | 2 / 32.4 | 1.5 / 23 |
| Motion input-to-draw p95 ms | 13.8 | 17 |
| Text publications | 6 | 7 |

Zero malformed capture records in both runs. No optical recording or stability/coverage/flash
acceptance in this pass. These short compatibility runs do not establish a speedup. Pixel untouched;
no signed device-evaluation release or production deployment. Normal overlay alignment still needs
a spatially grounded correction path, not a fixed subtraction from receipt time.
