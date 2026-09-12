# Pass 64: timestamped visual/event camera reconciliation core

Status: implemented and unit-verified core only. Not connected to capture or rendering.

Current Ultra policy disables ScrollFrameMotionEstimator entirely to prevent double application
of screenshot motion plus Accessibility deltas. This leaves no visual correction for missed
displacement. Pass63 showed short publication queues alongside stationary/jumping masks. Simply
enabling that old estimator would reintroduce its double-translation risk.

VisualScrollReconciler supplies a bounded event ledger and vertical visual-anchor coordinate:

- A validated visual pair fixes the camera at its screenshot timestamp.
- Event intervals fully covered by that anchor do not move the camera again, including late events.
- Events entirely after the anchor remain a camera tail, including already-received events.
- An interval crossing the screenshot cannot be split exactly from timestamps. Withhold its delta
  and report unresolved uncertainty until a later visual anchor covers it. Never assume uniform
  movement across the interval or present a timestamp-end delta as an instantaneous measurement.
- The first image establishes a fixed relative origin; a rejected later pair uses an event bridge
  only when that bridge is not crossed by an ambiguous interval. Truncated history rejects evidence.

The caller must supply source intervals from the same event producer, reset on document/window/
capture scope changes, serialize access, validate visual evidence, and honor uncertainty. It must
also define cache/tracker invalidation when a correction changes previously assumed coordinates.
The core has no renderer, tracker, bitmap, settings, or service ownership. No caller currently uses
it. It therefore cannot establish better coverage, smoother scrolling, or reduced false positives.

Thirteen deterministic tests cover missing movement, delayed/double-applied events, post-capture
tails, repeated visual pairs, reversal, rejected pairs, duplicate/unlinked samples, nonzero-origin
reset, truncated history, out-of-order delivery, crossing intervals, unknown starts, and first-image
origin stability. Full verification:543 JVM tests, zero failures/errors; lintDebug; assembleDebug
and assembleDebugAndroidTest together. These are accounting tests, not detector or motion accuracy.

No device run this pass; no capture age, queue drops, preprocessing/inference/postprocessing,
publication latency, or stability metrics remeasured. Pass63 remains the latest live evidence and
must not be attributed to this unintegrated component. No Pixel access, signed evaluation release,
production deployment, or user acceptance.

Next: bounded shadow integration using scoped row measurements plus same-producer event intervals;
compare proposed corrections against clean-source/optical motion before any rendering authority.
Keep interval uncertainty, resets, retention invalidation and horizontal-motion rejection explicit.
