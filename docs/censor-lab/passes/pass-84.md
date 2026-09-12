# Pass 84: reject unsupported cross-category motion handoffs

## Reproduction and correction

The working-copy VisualTrackArbitrator prototype classified every non-text
category as the same visual family. With one missing track, similar box sizes
and intersection-over-union of at least 0.20, it suppressed the second track;
after two observations it could move the old renderer identity to that box.
Those inputs alone do not distinguish label flicker from a separate object.

Four new regression tests failed against that prototype: a missing face with a
new body-part observation, a missing face with a confirmed body part, the reverse
body-part/face order, and separately selected sex-specific face categories.
Every failure reduced two expected tracks to one. Tests use real model class
names and categories, not just a different category on the same FACE_FEMALE
test object. Both input orderings are checked after the fix.

The working-copy broadening is removed. Motion handoff now requires matching
non-text censor categories. Geometry thresholds and existing same-category
handoff are unchanged. Relative to committed HEAD, this makes the existing
category guard explicit and adds null/text protection and regression coverage;
the substantive safety correction is relative to the uncommitted prototype.
The old prototype test now correctly rejects category change alone as identity
evidence. This does not establish that all same-category handoffs are correct,
nor change the separate near-identical-box and fast/quality reconciliation rules.

## Verification

- Before correction: 4 category-safety tests ran, all 4 failed as expected.
- Working tree: `testDebugUnitTest lintDebug assembleDebug` passed; XML totals
  626 tests, zero failures/errors/skips.
- Isolated staged tree `ce3c1609d93aee7af189724fa7672bbb7af96c29`: the same Gradle
  command passed; 539 tests, zero failures/errors/skips. The different counts
  reflect other uncommitted prototype tests excluded from the staged snapshot.
- Only this report was added after testing; source/test blobs are unchanged.
- No device install, instrumentation, preference change, release signing or
  public deployment. Existing device behavior has therefore not changed yet.

## Acceptance and measurements

This is a reproduced coverage-safety correction, not a measured scroll-speed
improvement. Capture age, queue drops, preprocessing/inference/postprocessing
time, overlay publication latency, first-detection latency, text stability and
one-frame false-positive rates were not remeasured. They remain unknown for this
snapshot; missing measurements are not zero values or evidence of acceptance.

Restricting the unsupported handoff can leave genuine cross-category duplicate
masks visible. Future correction needs evidence of object correspondence, not a
broader category gate or weaker overlap threshold. Real-device profiling and the
user's significant-improvement verdict are still outstanding. Do not promote
Accessibility or begin MediaProjection acceptance on the basis of these tests.
