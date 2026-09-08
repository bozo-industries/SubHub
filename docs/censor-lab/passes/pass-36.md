# Pass36 — separate measured catch-up from prediction

Status: in progress, emulator candidate only; physical-Pixel acceptance remains required.

## Evidence and scope

Pass35's corrected video oracle exposed real transit error. An independent fixed-background template in the first left image (correlation0.98–0.999) follows that image while its face censor drifts by+44/-51 pixels in the388px-wide host recording during the first down/up pair. This is displacement relative to the initial settled box-to-image relationship, not a new face detector or a universal alignment gate.

The active Accessibility path uses world tracks and does not call the legacy camera `rebase()` on each publication. Do not fix that unrelated path. Replaying two actual authoritative deltas(-338 then-324px,117ms apart) into the current motion class leaves the displayed camera188px behind the known position16ms after the second callback, despite only9ms event age. The residual measurement correction currently shares a72–180ms speculative Hermite segment.

Chrome also moves page content before its first nonzero scroll event, potentially through browser-control movement. That missing-input problem is distinct; this pass does not invent unobserved displacement or re-enable the anchor experiment.

## Implementation and verification plan

1. Preserve the existing bounded immediate correction and reversal/deceleration braking. Decay the remaining measurement error independently within16ms while the bounded forecast continues on its existing horizon. Clear correction state at reset/rebase/poll/measurement transitions; preserve tracker/cache authority.
2. Add direct trace-sequence, sign/axis, refresh-time evaluation and mode-transition JVM regressions. Do not change source-time interval estimation in the same pass.
3. Build target and instrumentation APKs together, run full unit/lint checks, freeze/hash the emulator candidate, and replay the same ten gestures with host recording before/after. Compare actual image-relative motion, quality coverage, capture/runtime/drop metrics and distinguish short-run variance from acceptance.
4. Keep the wider goal active. Do not claim Pixel performance, perfect absolute alignment, or solved browser-start motion from this isolated correction.

## Candidate checkpoint

Implemented independent16ms residual reconciliation. Exact two-event replay retains the56px bounded immediate correction but changes the16ms position from187.58px behind to3.02px forecast lead; forecast amplitude95.38px and150ms horizon are unchanged. The dirty integrated app passed473 JVM tests, lint and paired APK builds. The selectively staged motion source, excluding older absolute-anchor experiments, independently passed20 JVM tests including dedicated reconciliation regressions. Earlier working-tree experiments remain unstaged.

Emulator APK SHA256 `AB374DAD138C24CE93514A46614CE771BFDED5A557B85E8CF9750EFC3A81D31C`. This is an integrated dirty-worktree candidate, not a clean-HEAD artifact. Recorder-free controls, before/after video evaluation and physical-device validation remain separate requirements.
