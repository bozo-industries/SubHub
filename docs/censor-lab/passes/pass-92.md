# Pass 92: comparable head distances for body grouping

## Demonstrated defect

The first active Pass 91 Pixel dump left identical live/cache torso boxes separate,
alongside another overlapping torso group. A reduced numeric regression reproduced
the failure: the upper-card head at `(842,1179,272,238)` narrowly beat the nearby
lower-card head at `(966,2125,176,173)` when assigning the torso at
`(867,2347,217,224)`. Distances divided by each candidate head's own dimensions
were not comparable. The larger distant head gained an artificial ranking advantage.
The distinct-head/envelope guard then rejected the torso merges.

Keep the existing head-relative eligibility bounds, but rank eligible heads using
shared source-pixel distances and the existing horizontal/vertical weighting.
No extra model, cache policy, capture scheduling, immediate-delivery setting,
padding, or stabilization duration changes. This remains a geometric heuristic,
not person identification. Existing distinct-person and image-column guards remain.

## Verification

The new captured-geometry regression failed before the change and passed afterward.
It checks one body output containing all four torso inputs, including the exact
live/cache duplicate, with full raw coverage. Additional cases check half/double
scale and reversed input order. All overlay tests pass.

Exact staged source tree `84ca2099af22b4ea8d78c359b4ddb5187ed77b3a` was exported into
an isolated directory, excluding unrelated dirty experiments. With immediate quality
enabled, `testDebugUnitTest lintDebug assembleDebug` passed all 656 tests and built
the debug APK. No instrumentation or Pixel replacement installation was performed
for this change yet. The user is still evaluating Pass 91.

## Installed Pass 91 observational timing sample

The installed APK remains the fingerprinted Pass 91 candidate, not this fix. A
PID-scoped, tag-filtered logcat history read contained 74.871 seconds of uncontrolled
usage. Raw/parsed counts agree: 156/156 fast publications, 79/79 quality-ready
records and 29/29 immediate presentations. No overlay/quality record failed parsing.
This is not a matched workload, a controlled scroll run, or a Pass 92 benchmark.

| Installed Pass 91 metric | p50 ms | p95 ms |
| --- | ---: | ---: |
| Fast capture to publication | 233 | 328.25 |
| Fast total inference | 88 | 182 |
| Fast preprocessing / runtime / postprocessing | 3 / 83.5 / 1 | 6 / 178 / 4.25 |
| Quality capture to ready | 419 | 844.2 |
| Quality bitmap preparation | 72 | 116.5 |
| Quality total inference | 241 | 495.2 |
| Quality preprocessing / runtime / postprocessing | 10 / 228 / 5 | 17.1 / 476.9 / 6.1 |
| Immediate quality ready to publication | 13 | 18.8 |
| Immediate quality capture to publication | 238 | 277.8 |

Fast publication drop counters were zero. The quality dropped counter rose from
1 to 44 within the observed ready records; 48 replacement/coalescing offers, one
explicit stale drop and one cancellation were logged. These are different counters
and must not be summed into a synthetic queue-drop total. The bounded log can omit
events outside its interval. Quality pre/post timings were extracted from all 79
ready records because the existing summary exposes runtime but not those fields.

The tag filter does not provide complete renderer motion, text, or false-positive
evidence. Zero entries in those summary sections are missing evidence, not proof of
stability. The earlier layout dump demonstrated residual fragmentation; perceived
jitter, merge/unmerge oscillation, trailing, text stability and false positives still
require the user's verdict. No improvement in detection speed is claimed for this
render-only fix. MediaProjection work remains pending Accessibility acceptance.

## Private signed candidate ready, not installed

Signing run `35446109355` succeeded for exact source
`efd37a118010a1742688e89428d4067e86a6f178`, immediate quality enabled,
`publishedRelease=false`. Compact artifact `10585064342` was downloaded and its
provenance, checksum and expected compatible signer independently verified.
ARM64 APK SHA-256:
`12ad32a4fc46a76c483d74f7328e6368666d223ae1c3d43fde1ae9b62298cee4`.
No public release/tag was created. The Pixel remains on Pass 91; do not conflate
the signed candidate with an installed or visually accepted build.

The user subsequently instructed "just proceed". The verified ARM64 candidate was
replacement-installed successfully without clearing app data. An independent
`base.apk` readback matched the SHA-256 above, and the Accessibility service
rebound (PID 28721 at verification). The phone was Dozing and the layout diagnostic
reported `active:false`, so no live grouping or performance verdict is available
from this post-install interval. Device/user acceptance remains outstanding.
