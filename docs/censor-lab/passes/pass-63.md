# Pass 63: uninstrumented controls and visible scroll failure

Status: publication backlog not reproduced; visible motion still fails. No application changes.

Same running emulator/process as Pass62, with the one-shot sampler finished and its flag consumed.
All row/anchor/GPU experiments remain off. Three successive ten-gesture controls used the existing
Chrome tab without restart. These are short repeated controls, not a long-duration soak. Artifacts
are app/build/reports/device/pass63-control-{a,b,c}. Current source is 6849b44 plus the pre-existing
dirty prototypes. Pixel untouched; no release or deployment.

| Metric | Control A | Control B | Control C |
| --- | --- | --- | --- |
| Publications | 30 | 29 | 32 |
| Capture age median / p95 ms | 134 / 240.45 | 148 / 183 | 127 / 217.4 |
| Published preprocess / runtime / postprocess median ms | 1 / 37 / 1 | 2 / 41 / 1 | 2 / 35 / 1 |
| Maximum cumulative inference drops | 0 | 0 | 0 |
| Publication queue median / p95 ms | 1 / 17.4 | 1 / 37 | 1 / 20.5 |
| Motion input-to-draw p95 ms | 14.8 | 12.05 | 14 |

All control capture parsers reported zero malformed records. None reproduces the earlier
multi-second queue delay. Do not infer a code improvement from the restarted environment.

## Separate visual run

app/build/reports/device/pass63-visual contains a 24-second 720x1600 Android recording (479 frames,
reported average 19.764fps), ten-gesture trace, frame extracts, and alignment.json. Visual inspection
confirms the current tab is a Red Bull Emiru article, not the earlier image-results page. Keep this
workload separate from previous page comparisons; this also limits interpretation of Pass62.

The six-second frame shows a mask above page content, overlapping Chrome's toolbar/status area.
Other inspected frames show changing/duplicated masks. This does not yet distinguish geometry drift,
retention, or missing clipping, and category correctness cannot be inferred with every category enabled.

Relative-motion analysis: 455 frames with supported censors, 207 motion frames with matches, 869
matched boxes. Corrected median absolute residual 5.355px, p95 36px, max 162px at recorded resolution;
46.38% within five pixels, 14.49% within two. Many samples have stationary boxes over moving page
content followed by jumps. Local fallback supported 48 frames; five remained uncertain. The analyzer
uses nominal FPS, excludes unmatched/merged boxes, and cannot observe constant spatial offsets.
It is not an absolute-coverage or release gate. No one-frame-flash acceptance is established.

The recorded run is slower and must not be pooled with unrecorded controls: 26 raw/parsed
publications; capture median238.5/p95459.25ms; preprocess/runtime/postprocess medians2/92.5/1ms;
maximum inference drops0; preparation median56/p95127.8ms; publication queue median4/p9552.75ms;
main-to-tick median13/p9563.75ms; input-to-draw p9527ms. Capture spans:27 complete,3 failed,1 partial,
zero malformed. Four text publications do not establish text stability.

## Next implementation target

Prioritize missing/intermittent camera displacement and stale/off-viewport masks, not speculative
main-thread optimization. Scroll events include zero-motion records and explicit deltas while the
video shows continuous motion. Ancestor identity queries remain a cost lead, not established cause.
Do not simply drop identity ancestry or clip to an assumed Chrome toolbar height: nested surfaces
and collapsing browser chrome need verified bounds and consistent capture/render coordinates.

Checks: ten alignment analyzer fixtures pass with bundled Python3.12 and the existing
tmp/calibration-python packages. Default PATH Python3.13 cannot load those cp312 native modules;
use the matching bundled interpreter, not a global package replacement. Prior530 JVM/lint/paired
APK verification still applies because this pass changes no app source. All replay/recording and
analysis processes reached terminal completion. No user verdict or goal completion.
