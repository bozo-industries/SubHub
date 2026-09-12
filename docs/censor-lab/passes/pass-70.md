# Pass 70: retained source-frame coordinates

Status: tested source-only map; no live cache or rendering integration.

`SpatialFrameMap` owns a cloned reference image, row descriptor and scoped source-image pose.
Accepted registrations advance that pose. Rejected images have a null pose and do not replace the
reference. A later match can bridge the gap without inventing intermediate coordinates. Scope,
geometry, crop changes and reference expiry create an explicit new coordinate-map generation;
stale frames and invalid same-document window transitions cannot reset the reference. References
are retained for up to three seconds for spatial re-identification, not display prediction.
Receipt timestamps establish ordering and expiry only, never exact pixel time.

The patch matcher now excludes patches with no useful vertical gradient before selecting features
by intensity variance. Vertical edges previously contributed apparent texture without vertical
position information and inflated the support denominator. Matching error, ambiguity, inlier and
distributed-coverage requirements are unchanged. A shared-displacement fallback and ranking solely
by vertical gradient were tried and rejected after worse/no-benefit corpus results; neither remains.

## Evidence

- 574 JVM tests, zero failures/errors; `lintDebug`, `assembleDebug` and
  `assembleDebugAndroidTest` passed in one invocation. Eleven new map tests cover reference
  ownership, scope/order, rejection/bridging, expiry, periodic ambiguity and twenty fractional
  round trips; one new matcher test covers vertical-edge aperture ambiguity.
- The existing private 64-image Pass 66 corpus has one baseline, 53 registered images and ten
  unknown images in one map, including two reference bridges. Unknown intervals can approach
  1.8 seconds: this is not a continuous display camera. The replay uses a recent-motion hint for
  every image and is not the population selected by live event eligibility.
- On the original 19 live-hint-selected coarse pairs, refinement now accepts 17 rather than 13.
  Independent OpenCV phase-correlation differences are median 0.347 / maximum 2.197 source pixels.
- Direct baseline-image-to-current phase correlation supports all 54 known map poses, including
  the baseline. Absolute map-coordinate differences are median 0.226 / maximum 5.725 source
  pixels (prepared images 144x320, source scale 9.35). Final map position is 16.363 pixels versus
  a direct phase estimate of 17.691 pixels. These are estimator-agreement results, not ground
  truth, absolute target alignment or proof against dominant animation. Private inputs/results
  remain outside Git under the existing Pass 66 artifact directory.
- Sparse Android corpus instrumentation passed 1/1 in 21.749 seconds, following paired APK
  installation. 59 proposals, 57 accepted. Eighteen moving calls: CPU median 1195 / max 2580 us.
  All calls: CPU median 1441 / first-max 27676 us; wall median 1543 / max 29324 us. This measures
  refinement only, excluding decode, coarse proposals and map ownership overhead. The first
  call is not cheap. Strict CPU parsing: one raw/parsed record; two parser fixtures pass.
  Target APK reinstalled after terminal instrumentation to restore Accessibility service.

## Unchanged live-path control

`pass70-control` completed ten gestures with experimental flags off and no map caller.
30 raw/parsed publications; request-to-publication median 185 / p95 240.25 ms. Detector
preprocess/runtime/postprocess medians 4 / 47.5 / 1 ms; maximum cumulative inference drops zero.
Publication queue median 4.5 / p95 27.55 ms; motion input-to-draw p95 15.35 ms. Four text
publications, all empty; no optical/text/false-positive acceptance measurement this pass.
Capture-span parsing has zero malformed records, 30 completed callbacks, two failed requests and
one boundary-partial request. Preparation median 47 ms includes explicit readback median 41.623 ms.
These baseline timings are not improvements attributable to the unused map.

Pixel untouched; no release or deployment. Next integration must carry explicit frame/map/crop
identity into source-to-source cache alignment, invalidate unknown correspondence, and keep
display prediction separate. Revisit prepared-image readback cost with controlled full-pipeline
evidence as well; spatial-map development alone does not improve first-detection latency.
