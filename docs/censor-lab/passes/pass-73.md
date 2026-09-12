# Pass 73: spatial-cache video trial fails coverage acceptance

Status: do not enable the current candidate by default. The recorded ON run loses face coverage.

Same committed Pass 72 APK in both modes, SHA256
`16F13BC122485980A9D34487D975F47C61B382327F52C97924395D35D7FEDCB8`.
Software preparation remained enabled (GPU OFF). Each run restored page top on the same Chrome
page, used ten gestures and a 24-second native Android recording at 720x1600. OFF preceded ON;
these are not randomized or matched capture-failure trials. One initial readiness check refused
before any recording or gestures; the later ready run used a fresh artifact directory.

Private artifacts: `pass73-off-video-ready` and `pass73-on-video` under the ignored device-report
directory, each containing `alignment.mp4`, `trace.log`, `analysis.json`, `optical.json` and a
contact sheet. Both recordings/collectors and full video analyzers completed. Ten analyzer fixtures
passed. The APK/source did not change, so Pass 72 staged build/test evidence still applies.

## Bounded gesture trace

| Metric | OFF | ON |
| --- | ---: | ---: |
| Raw/parsed overlay publications | 15/15 | 7/7 |
| Request-to-publication median / p95 ms | 211 / 333.4 | 266 / 485.4 |
| Detector preprocess/runtime/postprocess medians ms | 4/70/1 | 7/84/1 |
| Capture preparation median ms | 65 | 104 |
| Explicit readback median ms | 55.037 | 97.465 |
| Publication queue median / p95 ms | 5 / 72 | 1.5 / 29.75 |
| Motion input-to-draw p95 ms | 20.2 | 24.5 |
| Maximum cumulative inference drops | 0 | 0 |
| Text publications, all empty | 4 | 5 |
| Completed callbacks / failed / boundary-partial | 28/4/1 | 15/4/1 |

Zero malformed capture records in either run. ON has 21/21 spatial records: 15 source images,
eight known, six cache queries and only one query with regions. CPU median 4443 / max 8133 us.
Native recording coincides with substantially higher preparation/inference cost than trace-only
runs. Do not attribute the whole timing difference to registration or treat this as an unperturbed
steady-state performance comparison. Zero inference drops hides lost capture/publication work.

## Visible failure and measurement limits

Contact-sheet inspection shows the ON face mask absent at recorded wall times 13:10:33.927 and
13:10:35.783 while the face remains visible. The fling also has visibly displaced masks. The OFF
contact sheet retains face coverage in its later reversal samples. These samples are not exact
gesture-phase pairs, and the runs did not experience equivalent screenshot failures; they prove
that the ON run fails coverage, not that the cache alone caused every difference.

Full relative-motion analysis decodes 387/401 frames OFF/ON (not sample-limited), with 365/385
supported-censor frames, 164/169 motion frames and 428/415 matched boxes. Corrected absolute
residual median/p95 is 5.910/58.087 versus 4.989/36.000 video pixels; within-five-pixel rates are
42.07%/50.30%. This apparent aggregate improvement cannot override observed coverage loss:
unmatched/merged/missing boxes are not penalized, constant offsets are invisible, and time uses
nominal FPS rather than decoded PTS. Neither detection recall nor absolute attachment is validated.

## Capture-gap diagnosis and next implementation

ON request 10496873 began at 13:10:31.873 and failed at 13:10:36.912, age 5039 ms (callback code 1).
The previous source registered at 13:10:31.815, with receipt 10496690. Scroll events continued during
the outage. The current event path refreshes cache output even when its 750 ms source-query TTL
returns an empty list, providing a mechanism for dropping previously displayed cache regions.
At 13:10:37.006, the next source starts a new map because the reference age exceeds three seconds;
only one region is inserted afterward. This supports a capture-gap failure hypothesis; the trace
does not establish why the screenshot failed or identify every mask's originating render lane.

Next distinguish no new image from a newly unmatched image. Consider retaining only already
presented, same-map coverage through a capture outage while existing event motion continues;
do not re-query stale frames or publish an old map after a scope change. Track actual presented
frame/map identity, not merely the newest captured frame, because some scenes never publish.
For recovery, evaluate spatial re-identification before discarding an expired reference, without
predicting through the gap or weakening match confidence. Motion eligibility must cover events
between reference and recovery, not only the final 750 ms before a delayed callback. Validate
these cases explicitly before another visual trial; do not simply lengthen TTLs to obtain a pass.

Spatial preference restored false and independently read back; worktree target/test APKs restored
after terminal instrumentation. All experiments remain OFF. Pixel untouched; no release or deployment.
