# Pass 50: reverse-order video comparison

Status: GPU latency benefit repeated; visible alignment remains unacceptable.

Same APK as Pass49 (FAE9984ECCC9B05CE3A25E4F01B3F3419DEF5FA01501408C14DCCACA19887D44),
software first then GPU, same ten gestures/page-top reset. Host videos 24 seconds each,
388x864,719 decoded frames/~30fps. Host recording has overhead and limited spatial precision.
Recordings and trace collectors completed; both full video analyzers completed successfully.
No new production changes in this pass. GPU and anchors restored OFF afterward.

Local artifacts under app/build/reports/device:
- pass50-software-video.mp4, pass50-gpu-video.mp4
- matching run directories with trace.log/analysis.json
- pass50-software-video-alignment.json, pass50-gpu-video-alignment.json
- pass50-software-motion.png, pass50-gpu-motion.png (decoded at3.5s; not gesture-phase matched)

## Timing

| Metric | Software | GPU |
| --- | ---: | ---: |
| Fast publications | 29 | 29 |
| Capture age median/p95 ms | 219 / 369.8 | 183 / 316.4 |
| Actual draw p95 ms | 101.6 | 44.8 |

GPU trace includes33 GPU_PREPARE records and remained active. The latency direction agrees
with Pass49, but this is still two short pairs, not a calibrated Pixel equivalence study.

## Video evidence

| Relative-motion metric | Software | GPU |
| --- | ---: | ---: |
| Supported motion frames | 162 | 157 |
| Matched boxes | 826 | 796 |
| Within2video-px | 22.22% | 21.66% |
| Within5video-px | 54.32% | 51.59% |
| Median residual video-px | 4.355 | 4.915 |
| p95 residual video-px | 42.730 | 36.909 |
| Maximum residual video-px | 74.231 | 79.349 |

This analyzer measures frame-relative motion agreement only. It cannot validate constant
spatial offset, unmatched/merged boxes, detection recall, or absolute face attachment.
Frozen frames still visibly show displaced censors; the faster preparation is not an alignment
fix. A fixed-ROI first-thumbnail probe was also run, but matching identity/gesture phases during
flings are not independently validated, so its aggregate drift values are not an acceptance gate.

## Next incomplete action

Retain GPU as an opt-in performance candidate, not a release default. Return to event/camera
timing: correlate source event timestamps, authoritative displacement and rendered geometry
against a reliably identified image patch across the slow-start/reversal portions of these videos.
Separate baseline detection placement from accumulated motion drift, and align comparisons
by gesture markers rather than equal video timestamps. Do not restart the expensive full video
analysis; outputs are complete. Then implement a demonstrated coordinate/timing correction and
validate it with the same videos/replay. Pixel acceptance remains outstanding; goal active.
