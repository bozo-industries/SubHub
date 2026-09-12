# Pass 76: verify cache admission and pivot to live-track geometry

Status: cache bookkeeping corrected; the measured visual errors are not explained by admitted
cache regions. No visual-performance acceptance or default experiment change.

## Changes and tests

Cache hold eligibility now requires a positive count of requested, anchored cache identities
retained by the view, not merely candidate regions passed to it. Independent current-quality
regions are not credited to the requested cache. The counter observes renderer snapshots, not
compositor-visible pixels; it does not prove a region is on-screen or correctly attached.

New write diagnostics distinguish unknown correspondence, crop rejection and unconfirmed
observations, including face-class crop rejection. The existing confidence/crop rules are unchanged.
Strict parser fixtures cover both new records and reject impossible counts.

Android admission testing exposed a prototype-worktree lifecycle bug: `clearContent()` tried to
mutate an immutable consolidation result. It now replaces snapshot lists with empty lists rather
than clearing borrowed/immutable collections. Final Android tests pass on both the exact staged
APK and the prototype-worktree APK, including overlap filtering and explicit clear/release.

- Exact staged snapshot: 501 JVM tests, zero failures, lint and paired APK builds passed.
- Prototype worktree: 591 JVM tests, zero failures, lint and paired APK builds passed.
- Two new JVM tests cover empty-view-cache hold rejection and crop/confirmation diagnostics.
- Android view-admission/clear test passed 1/1 on both final builds. The earlier prototype run
  failed with `UnsupportedOperationException`; that failure was not accepted as success merely
  because ADB returned shell status zero.
- Four strict spatial parser fixtures pass. Unrelated prototype changes remain outside the commit.

## Controlled-gap evidence

The recorded diagnostic candidate used staged APK SHA256
`388D9F672FC74BD028EC8CE5CBFDA77A43C268DCC937A1C4823D2059FAB8B9F2`.
`pass76-on-gap` completed the ten-gesture, 24-second native recording fixture: 5015 ms pause,
zero screenshot dispatches during it, 55 fresh callbacks afterward in the full recording trace.
Both gap records parsed; capture parsing has no malformed records. The final counter was then
hardened to inspect retained cache identities after optional consolidation, and the lifecycle
fix above was tested on both builds. The recording is not a new final-build performance A/B.

Full trace: 243/243 spatial records, 58 source frames, 49 known, 53 queries, 46 queries with regions.
However, all 79 recorded view applications admitted zero requested cache regions; 46 had candidates
but no admission. Corrected HOLD count is zero. The previous pass's nonzero HOLD count therefore
must not be interpreted as proof that useful cached masks were retained.

Within gesture markers: 82/82 records; 17 source frames, eight known; 13 queries/writes and six
queries with regions; all 39 view applications admitted zero cache regions. Known writes include
eight face-class observations, one rejected by the crop. Four visual observations were unconfirmed.
Full-trace face observations are 48, still with only one face crop rejection. Crop filtering exists
but is not sufficient evidence to explain all visible displacement or to justify widening the ROI.

Contact-sheet inspection still shows displaced and duplicate visual masks despite zero admitted
cache regions. For this fixture, the next investigation must target the non-cache tracking and
rendering path. A larger cache/reference bank cannot directly correct the primary masks shown here.
Reference retention may still matter for other re-entry cases; do not generalize this single fixture.

## Bounded gesture metrics

Nine raw/parsed publications; request-to-publication median 217 / p95 599.2 ms. Detector
preprocess/runtime/postprocess medians 3/68/1 ms; maximum cumulative inference drops zero.
Publication queue median 6 / p95 95.8 ms; input-to-draw p95 74.05 ms. Four text publications,
all empty. Capture preparation median 105 / p95 243.4 ms; completed/failed/partial requests 17/1/1.
Spatial CPU median 3061 / max 5134 us inside the gesture interval. This includes an intentional
capture pause and heavy native recording load, so it is not a throughput or first-detection gain.

Full optical analysis completed: 398 frames, 163 motion frames, 388 matched boxes, corrected
relative residual median/p95 5.357/36 video pixels, within-five rate 45.4%. Missing/merged masks
and constant offsets remain outside the metric; no absolute alignment or detection-recall verdict.

All probe preferences read back false, arm marker absent, worktree target/test APKs restored after
terminal instrumentation. Pixel untouched, no release or deployment. Next: compare raw detector,
post-projection, tracker raw/filtered, and view/draw geometry on matched frames before choosing a
correction. Preserve unknown pixel-time provenance; do not label a receipt as an exact capture time.
