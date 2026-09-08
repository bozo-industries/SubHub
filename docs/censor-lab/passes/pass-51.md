# Pass 51: slow-start timing hypothesis and clock correction

Status: measurement correction / next experiment defined. No camera behavior changed.

Host gdigrab video timestamps and emulator logcat epoch timestamps were incorrectly assumed
interchangeable when correlating Pass50 first-gesture samples. A current five-sample ADB clock
probe measured device-minus-host around -1176 ms, with best RTT34 ms (midpoint uncertainty17 ms).
This was measured AFTER recording, so it cannot retroactively prove exact historical alignment.
The pure video-relative metrics remain valid within their documented limited contract; cross-
source latency/gesture matching without a clock mapping does not.

Added scripts/measure_android_clock_offset.ps1: bounded read-only samples, validates native
exit/timestamp, chooses minimum RTT and emits all bounds plus mapping/uncertainty. Capture it
before AND after future recordings; do not estimate synchronization by fitting away the very
latency being investigated.

## Exploratory evidence, not acceptance

Visually verified initial tree patch at x9:34/y277:303 in the first left thumbnail. Applying the
current +1.176s host-minus-device estimate to historical Pass50 yields high-correlation patch
motion around -35 to -52 video pixels during slow start while logged dy remains zero. GPU
example later settles at image movement -81px versus cumulative reported motion -31.8px, a
roughly49px gap. This resembles collapsing browser controls (~50video-px), but source-clock
drift and absolute patch identity must be revalidated with a newly bracketed recording.

Current Ultra path explicitly disables screenshot global-motion fallback; zero-delta companion
events therefore cannot supply missing visual movement. Do NOT simply enable that fallback:
its prior double-application risk is real. We need an explicit distinction between document
scroll and viewport/browser-control translation, not another global delta added indiscriminately.

Next incomplete action: new software/default recording with before/after clock probes and
bounded numeric event-source viewport bounds (prefer already acquired/cached node data, no
high-rate refresh loop). Verify whether bounds/top inset changes account for the missing image
translation, then design a separately fenced presentation transform. Keep stable document/cache
coordinates separate, preserve quality/capture source-phase provenance. No Pixel acceptance,
goal active; GPU and anchors remain OFF.
