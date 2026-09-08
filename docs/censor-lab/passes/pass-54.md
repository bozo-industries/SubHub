# Pass 54: absolute offsets and cheap image-motion hypothesis

Status: measurement evidence, not a new live motion path. Pixel remains released/untouched.

Commit5478ed4 adds raw event absolute scroll coordinates to cached-source telemetry. Paired
APK/lint passed. Emulator-only run pass54-absolute-bounds recorded host video and bracketed
clock estimates. Recorders/collectors finished. GPU and anchor experiments OFF.

The initial companion events have absolute0,0 while visible images move. First WebView event
has absoluteY16 and bounds0,151,1344,2734; later absoluteY77 with bottom2722. Thus missing
browser-control movement is not merely a scroll delta omitted while absolute coordinates
already contain it. Bounds also vary with clipping; cannot add their deltas blindly.

Clock-adjusted first-thumbnail probe: at~1.01s after gesture start, image displacement -62video
px while received event total0; at~1.41s, image-77 vs event-31.2. Baseline patch moved3px near
start, so interpret exact values with that uncertainty. Still far beyond clock error.
Run24fastpubs, capture-age median243.5/p95480.55ms, native median53.5ms, actual draw median15ms.
This noisy run is not a performance comparison. User reported two brief glimpses of better
scrolling, explicitly tentative; do not label it acceptance.

## Offline row-descriptor probe

Local tmp/pass54-row-motion.py summarizes a64x160 luminance image into four column blocks
per row, searches vertical translation independently in four separated bands, and checks
three-band agreement. This is only an offline hypothesis on the existing recording.
At333ms sample intervals it estimates -8,-12,-48,-12,0,0video-px; total-80, close to the
tracked thumbnail's displacement. Some band score improvements are weak, and censors are
present in this host video. Therefore this does not yet prove robust camera motion or reject
local animation/reflow. No production use and no world/cache authority.

Next: build/test a bounded image-motion observer on already-prepared SOFTWARE pixels (no
extra hardware readback), with confidence/uniqueness/texture and spatial consensus rejection.
Use uncensored source frames and synthetic static/local-animation/reflow controls before
integration. Its eventual residual must subtract event displacement over the same capture
interval and affect presentation only, with per-capture source reference; never add flow as
another authoritative document delta. Keep the first-screenshot origin explicit and drop on
document/window/transform changes. Goal active, full alignment acceptance outstanding.
