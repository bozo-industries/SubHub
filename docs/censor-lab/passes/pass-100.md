# Pass 100 — frame-scoped whole-person presentation

## Scope and status

Integrates the Pass 99 geometry into the renderer without altering detector outputs,
tracker state, cache authority, statistics, or penalties. This is an intermediate
implementation checkpoint: services and settings do not call the new API yet, so
whole-person coverage is still unavailable to users. No phone update or real-device
performance/quality claim is made.

## Implementation

- `OverlayController.beginPersonCoverage` accepts the selected detections and support
  cues immediately after their raw frame publication. It returns a process-unique
  generation token for asynchronous `refinePersonCoverage` calls.
- Provisional and refined rectangles expand displayed geometry only, after existing
  grouping. Group member IDs retain their original coverage and identity. Expansion
  never feeds back into overlap grouping, raw geometry, or frame-to-frame matching.
- New raw publications, explicit clears, measured-origin changes, and release reject
  earlier tokens. Controller replacement cannot reuse a prior instance's token.
- Coverage expires 750 ms after capture, not model completion. A scheduled invalidation
  removes the expansion even on a static screen; original censor regions remain.
- Accessibility mapping uses the capture camera, source/display sizes, exact origin,
  bias and source timestamp. Unknown/mismatched world provenance fails closed to parts.
  Projection prediction translates the person rectangle with its associated part.
- Text and cache-only entries do not acquire expansions. Ambiguous trigger matches
  retain parts. Lists are bounded to avoid unbounded matching work on the UI thread.
- Effect sampling uses original predicted displacement, not the expanded top-left:
  coverage size changes must not shift blur/pixelation source pixels.

## Verification

Exact staged source tree `04b084cbecc184f58a8ec68b94ab80f1a980778e` exported to
`C:/Users/user/Code/SubHub-pass100-render-85b1`, excluding unrelated cache prototypes.
`testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` passed together
with `-PimmediateQualityExperiment=true`: 790 tests, zero failures. Eleven new unit
tests cover lifecycle tokens, expiry, coordinates, grouped coverage, prediction,
text/cache exclusion, and ambiguity. An Android pixel-rendering test is compiled
but has **not been executed**. No instrumentation or ADB operations ran this pass.

Capture age, queue drops, preprocessing/inference/postprocessing, overlay publication
latency, and real-device stability are **not measured in this pass**: the service
scheduler is not connected and there is no valid live workload. Existing timing
records are unchanged; no new trace schema or fabricated zero-work metrics.

## Remaining work

Connect both services to shared-image, bounded low-priority person inference; cancel
person work for fast demand even when concurrent quality bypasses the existing gate.
Wire default-off settings and PIN/pack locks. Validate rendered pixels on Android,
then sign an exact-source candidate and obtain Pixel timing and user acceptance.
The service integration must publish provisional coverage in the same main-thread
transaction as its raw masks, and pass only matching-source triggers/cues to this API.
