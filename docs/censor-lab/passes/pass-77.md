# Pass 77: locate live-face geometry discontinuity

Status: smoothing is not the dominant error in the measured run; track continuity is the next target.
No rendering-policy change or visual acceptance.

Added a bounded, numeric-only face-geometry trace behind the existing explicitly enabled emulator
capture-gap probe. The pause was NOT armed for this run. Each published record contains pre-projection
scene face observations, selected tracks' raw/filtered boxes, missing-frame counts, observation source
and the event-camera coordinates used by the pipeline. Encoding is capped at eight faces per list;
the parser exposes truncation. It records model class codes, not arbitrary text or person identities.
This is not an exact pixel-time or absolute display-alignment measurement.

Two encoder JVM tests and three strict parser fixtures pass. Exact staged snapshot: 503 JVM tests,
zero failures, lint and paired APK builds passed; prototype worktree: 593 tests and the same build
checks passed. The service hunk was staged separately and the index snapshot verified independently.

## Normal scroll trace, spatial cache OFF

`pass77-live-geometry` completed ten gestures without native video recording or an injected pause.
Inside its gesture markers, all 17 geometry records parsed with no truncation. Seventeen fresh
visual face tracks had raw-to-filtered vertical center differences of median 0.5 / maximum 5 source
pixels. All face tracks, including missing ones, also had maximum 5 pixels. Accessibility tracker
configuration disables object velocity prediction. Do not change smoothing to explain a much larger
visible error without contrary evidence from another workload.

Three publications contained multiple face tracks. A concrete pair illustrates the discontinuity:

- Earlier source observation: face box `(642,792,236,191)`, source scroll Y 29.
- Source request 17108015: face box `(635,609,248,202)`, source scroll Y 36; tracker/current camera Y 48.
- New track 6: raw box `(635,597,248,202)`, missing count zero.
- Old track 5: raw box `(642,773,236,191)`, filtered box `(637,776,240,195)`, missing count one.

The source face center moved 177.5 pixels while source scroll changed by only 7. Reprojection of the
new observation by -12 pixels is internally consistent with the recorded cameras. The old track was
only shifted by the event-camera movement and remained while a new identity appeared. This supports
an unaccounted source-frame-motion/association problem, not a large smoothing delay. Browser controls
are a plausible contributor, but this trace alone does not prove the origin of the image movement.

## Pipeline control metrics

17 raw/parsed overlay publications; request-to-publication median 152 / p95 332.4 ms. Detector
preprocess/runtime/postprocess medians 4/34/1 ms; maximum cumulative inference drops zero.
Publication queue median 3 / p95 55.4 ms; input-to-draw p95 18.4 ms; five text publications.
Capture parsing: zero malformed records, 29 completed callbacks and four failed requests. These
counts differ from publication counts; zero inference drops is not a complete throughput claim.

Probe preference restored false and read back independently; worktree target/test APKs restored
after terminal instrumentation. Other experiments remain OFF. Pixel untouched; no release or deployment.

Next test source-image correspondence as a tracker-continuity correction between registered frames,
separate from current-display prediction. Account for the event reprojection already applied, combine
offsets before clipping, exclude text/fixed regions, and reset continuity after unknown/scope changes.
Do not promote receipt times to exact pixel time or weaken identity/confidence gates merely to merge
these tracks. Verify the correction on known image pairs and then actual repeated scroll behavior.
