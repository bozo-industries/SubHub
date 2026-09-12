# Pass 72: source-image cache placement, wired into the service

Status: functioning debug opt-in integration, not an accepted smoothness improvement.

`SpatialRegionCache` gives confirmed visual regions a separate source-image coordinate space.
The screenshot worker registers the prepared crop and carries an immutable frame through the
scene shared by fast and quality work. Fast cache observations undo the existing event-tail
reprojection before storage; confirmed quality observations retain the existing two-capture
admission rule. Queries place cached regions on a registered source frame, then apply the existing
event-tail approximation for presentation. Scroll updates use that same source frame rather than
querying the legacy event-coordinate cache. This does not change the detector or text path.

Unknown/new-invalid images cannot write or return spatial cache regions. The last good image and
regions may survive for later re-identification, but old fast results cannot restore output while
the latest image is unknown. Scope, map and horizontal-motion changes fence reuse. Only boxes
fully inside the registered crop are admitted/returned; text is excluded. Existing track/quality
confirmation rules remain; source-image correspondence does not grant exact-time render provenance.
Queries expire after 750 ms without a newer registered source. Registration holds a separate lock
from UI cache queries. The cache is allocated only when explicitly enabled; default remains OFF.

An Accessibility surface name is not required for this separate image-coordinate map. Its own
window/document/map scope and image matches provide correspondence; this does not promote
untrusted Accessibility nodes to legacy cache identity or establish a current display camera.

## Exact staged-snapshot verification

The worktree contains older uncommitted prototypes. The service integration was staged separately
and the exact index exported to an ignored fresh directory for independent builds. This exposed
two previously concealed dependencies: missing screenshot request document/window parameters,
and a GPU test relying on an uncommitted public provider initializer. The callback now carries and
checks the requested scope; a test-only same-package bridge preserves CPU selection without
widening the production API. Unrelated prototypes remain uncommitted.

- Exact staged source: 491 JVM tests, zero failures/errors; lint and paired target/test APK builds.
- Prototype worktree: 581 JVM tests, zero failures/errors; lint and paired APK builds.
- Seven new cache tests: image motion without event motion, unknown/recovery, expiry/map reset,
  event-tail inversion, unchanged confirmation/crop limits, horizontal reset, source scaling and
  invalid input retaining reference data without returning old cache output.
- Two strict parser fixtures cover both new records, unknown states, extra fields and malformed
  values. Raw/parsed counts are verified; missing records are not silently interpreted as zero work.
- Staged-APK real-image GPU detector test passed 1/1 in 2.414 seconds with the test-only bridge.
  Emulator-only explicit spatial flag setup/disable tests passed. All instrumentation followed
  paired builds and target restoration happened after terminal instrumentation.

## Live integration evidence

The initial prototype-worktree pair had 32 versus 28 publications (OFF/ON), request-age medians
199.5/172 ms and p95 291/319 ms. Detector preprocess/runtime/postprocess medians were 3/50/1 and
2.5/41.5/1 ms; drops zero; draw p95 16.4/21 ms. The ON interval had 28 registration records,
24 known frames and 24 queries with regions. This is not a tail-latency win or visual acceptance.

The first staged-APK probe found zero queries because of the legacy surface-name gate; fixed
before the final build. Intermediate staged probes are not pooled into final evidence.
Final exact staged APK, `pass72-staged-final`, ten gestures:

- 58/58 spatial records: 32 images, 24 known; 26 queries, 18 with regions, one inserted region,
  maximum three entries. Some entries were established before the gesture interval.
- Registration plus prepared-pixel extraction CPU median 2401 / max 6063 us.
- 23/23 overlay publications; request-to-publication median 143 / p95 236.7 ms.
- Detector preprocess/runtime/postprocess medians 3/33/1 ms; maximum inference drops zero.
- Publication queue median 3 / p95 17.8 ms; motion input-to-draw p95 47.8 ms.
- Five text publications, four empty; no optical, detection-recall or text-stability verdict.
- Capture parser: zero malformed records, 31 completed callbacks, one failed and one boundary
  partial request. Capture and publication populations differ because some scenes do not publish.

The final staged trial proves the integration is exercised without the old prototypes, not that
it improves visible alignment. There is no matched staged-baseline video comparison yet. The
worktree and staged APKs are distinct populations and their timings must not be compared as A/B.
Spatial and GPU flags are OFF; worktree target/test APKs restored; Pixel untouched, no release or
deployment. Next: same-APK optical ON/OFF comparison, including unknown-frame gaps, duplicate
coverage and actual attachment of cached regions. Do not treat merely returning regions as success.
