# Pass 83: preserve shared-category face recall in committed source

## Scope and decision

Pass 82 identified DetectionPostProcessor as one of nine files whose working
copy, but not HEAD, matched the frozen Pass 33 source fingerprint. This pass
selectively commits that existing decoder change with expanded policy tests.
It does not import the broader cross-category VisualTrackArbitrator prototype.

The old committed ambiguity gate rejected a candidate when the two leading
class scores were close, even if both labels mapped to the same enabled censor
category. With the shared `face` category enabled, FACE_FEMALE/FACE_MALE
competition now preserves the winning face detection. The winning score is not
summed or increased. Per-class confidence floors, geometry and NMS are unchanged.
Different resolved categories, including separately enabled `face_female` and
`face_male`, retain the ambiguity rejection. A disabled runner-up also retains
that rejection. This is restoration relative to HEAD, not a newly demonstrated
recall improvement relative to the frozen Pixel candidate.

## Verification

- Working-copy focused decoder suite: 10 tests, zero failures/errors/skips.
- Added coverage for either leading face label and ties, sex-specific settings,
  disabled face categories, unchanged confidence floors and unambiguous faces.
- Policy checks exercise all four decoder overloads, including contiguous
  buffers with a nonzero position and unchanged buffer position after decoding.
- Exact staged source tree `e036683464166b74f8590eb30fec891d6b4e86f6`, exported
  independently of the dirty worktree: `testDebugUnitTest lintDebug assembleDebug`
  passed. XML totals: 534 tests, zero failures/errors/skips. This report is the
  only addition after that tested tree; application and test source are unchanged.
- No Android instrumentation, device installation, preference change, signing
  change, release tag or public deployment was performed.

## Runtime evidence and remaining acceptance

This is a decoder-policy/source-reproducibility checkpoint, not a performance
experiment. Capture age, queue drops, preprocessing/inference/postprocessing
time, overlay publication latency, first-detection latency, scroll alignment,
text-group stability and false-positive flashes were **not remeasured**. No old
trace is relabeled as a measurement of this committed snapshot, and zero work
or zero false positives must not be inferred from the absence of a run.

Only emulator-5554 was connected when checked; Pixel availability remains
unconfirmed. No new signed candidate is presented for acceptance. Physical
device profiling and the user's significant-improvement verdict remain required;
Accessibility is not accepted, and MediaProjection work is not advanced by this
checkpoint. The remaining working-copy-only baseline dependencies still need
individual review before a reproducible device candidate can be offered.
