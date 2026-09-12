# Pass 68: native Chrome bounds do not reveal compositor toolbar movement

Status: native-geometry hypothesis rejected for continuous motion; no render changes.

Reviewed Pass53/54 and searched branch history before testing native controls. Earlier probes used
document/companion bounds, not native Chrome resource IDs. Added a debug/emulator-only one-shot
probe: bounded96-node discovery, at most8 watched native IDs, at most200 rounds/30-second loop,
100ms spacing on a background thread. Individual provider calls can extend a round. It reads only
resource IDs, bounds and visibility, not node text or content descriptions. No camera authority.

Reset the existing Chrome article to the top, armed setup instrumentation1/1, reinstalled the target
to reconnect the service, and ran ten gestures with recording. Artifacts:
app/build/reports/device/pass68-native-chrome. Probe END confirms200 rounds/eight IDs; its preference
was consumed (verified empty map). Replay and recorder reached terminal completion. Pixel untouched;
row/source-export/GPU/anchor flags remain off.

## Result

1600 raw/parsed geometry records;1335 visible, positive-area, unambiguous records. Native controls
were discovered successfully, but every usable rectangle stayed constant:

| Native ID | Top | Bottom | Usable samples |
| --- | ---: | ---: | ---: |
| compositor_view_holder | 151 | 2920 | 200 |
| control_container | 151 | 322 | 163 |
| toolbar_container | 151 | 322 | 162 |
| toolbar | 151 | 319 | 162 |
| toolbar_hairline | 319 | 322 | 162 |
| location_bar | 151 | 319 | 162 |
| toolbar_buttons | 151 | 319 | 162 |
| location_bar_status | 151 | 319 | 162 |

The video shows changing toolbar extent; the inspected three-second frame has a partially collapsed
toolbar. Some native nodes instead become invisible/missing. Their static layout rectangles and
binary visibility are not a continuous compositor-position measurement. Do not interpret missing
nodes as zero displacement, or apply these bounds as the missing camera offset. Query maxima reach
97ms, so the probe is not a justified production-rate observer even apart from missing geometry.

## Bounded pipeline telemetry

22 raw/parsed publications; request-to-publication median291/p95562.4ms;
preprocess/runtime/postprocess medians3/86/1.5ms; maximum cumulative inference drops0;
preparation median75.5ms; publication queue median16/p9596.6ms; motion input-to-draw p9528.1ms;
five text publications. Capture parser malformed records0. Recording plus native queries perturbs
the workload; this is not a performance comparison. Absolute alignment and flash/text stability
remain unaccepted.

Checks:554 JVM tests, lintDebug, paired APK builds; setup instrumentation1/1; three strict geometry
parser fixtures, including missing/invisible/invalid bounds and clock/schema failures. No production
deployment or signed evaluation release. Existing unrelated worktree prototypes remain separate.

Next: image-derived spatial correspondence with explicit timing uncertainty, rather than further
tuning native-bounds polling or treating receipt stamps as exact pixel time. Separate content-space
registration from presentation prediction; validate both against the saved clean-source corpus.
