# Pass 85: prevent cross-family backfill confirmation

## Reproduced failure

Reviewing the uncommitted quality-lane dependencies found a blanket visual-family
match in QualityBackfillCoordinator. This predicate affects batch deduplication,
two-capture promotion and refinement of already-promoted candidates. It is not
merely renderer smoothing: ready regions can enter long-lived cache coverage.

Three added tests failed against that working-copy prototype (15 coordinator
tests ran, 3 failed):

1. A face observation supplied the first hit for a breast observation at identical
   geometry, promoting the latter without a second breast observation.
2. Face and breast regions in one capture deduplicated each other, losing one
   independent confirmation candidate.
3. A confirmed breast candidate lent its ready state to a first buttocks result
   at the same geometry, reporting refinement instead of requiring confirmation.

The tests use identical boxes to isolate category matching from the independent
world-IoU threshold experiment. A further existing prototype fixture now asserts
that face-to-breast category change with drift must not promote a region.

## Correction and scope

Restored the working-copy category predicate to committed HEAD's narrower rule:
equal categories, sex-specific face family, or text family. Distinct body-part
categories no longer confirm, refine or deduplicate one another. A genuine
repeat body-part observation still promotes after its own second capture.

Because the unsafe broadening was never committed, this checkpoint commits only
regression coverage and this report; no production source delta is necessary.
The working-copy IoU default remains 0.18 and is **not staged**. The committed
default remains 0.35. Neither threshold is newly validated by these tests.
Provider selection, quality runner/governor, cropping, timing, and service
integration changes remain uncommitted and were not imported as dependencies.

## Verification and acceptance

- Working tree: `testDebugUnitTest lintDebug assembleDebug` passed with 629 tests,
  zero failures/errors/skips, after removing the blanket category match.
- Isolated staged tree `d4eba4f6673a108eff4a0ad47b6b45f7352026be`: the same Gradle
  command passed with 543 tests, zero failures/errors/skips. Its source retains
  committed HEAD's category predicate and 0.35 threshold. Only this report was
  added afterward; tested application/test source is unchanged.
- No install, Android instrumentation, signed release candidate, preference
  change or public deployment was performed.
- Capture age, queue drops, preprocessing/inference/postprocessing time, overlay
  publication latency, first-detection speed, scroll alignment, text stability
  and false-positive flashes were not remeasured. This is a confirmation-safety
  regression test, not a runtime-performance pass or a physical-device result.

Physical-device validation remains required. Restricting category reuse is not
proof of correct identity within a category, calibrated thresholds, or removal
of every one-frame flash. Accessibility acceptance and MediaProjection work
remain outstanding.
