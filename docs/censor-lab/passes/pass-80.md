# Pass 80: object-local source correspondence

Status: local pixel correspondence is implemented and exercised; visible censoring is still not
acceptable. Keep the emulator-only experiment OFF. No Pixel release or acceptance.

## Change

The registration-only experiment can now retain a bounded grayscale prepared image (at most
512x512 bytes per image) in its existing frame objects. Ordinary cache mode does not retain this
additional image data. Images are immutable copies, in memory only, never written by the matcher.

When global correspondence cannot provide a correction, `SourcePatchMatcher` checks four disjoint
7x7 patches across the previous object's box. It requires at least three agreeing textured patches
covering both halves in each axis, rejects repetitive/aperture-only patterns, refines a vertical
translation near the detector's proposal, and requires refined mean-adjusted error <= 8 and
correlation >= 0.95. Detector x jitter is not promoted to horizontal image motion. Unsupported box
sizes, scale changes, large lateral changes and out-of-image samples fail closed.

Local correspondence does not require a known global pose, but still requires the same map owner,
capture/document/window scope, source/viewport geometry, ordering and a receipt-age gap <= 1500 ms.
Unknown global poses remain unknown. Only pixels from the last successful tracker update are used;
missing tracks, text and anchored observations remain ineligible. The current detection must still
support IoU >= 0.5 and improve over event-only overlap by >= 0.2. Ambiguous candidates, shared
detections and occupied global/event positions are rejected. More than 16 eligible local pairs
rejects the entire local search, not merely its unexamined tail. A known stationary header can
cancel an incorrect body-event shift without moving in the source image.

The correspondence is an association aid, not person identification, an exact pixel timestamp or
a current-display camera. Jolli/source review informed keeping the existing scope boundaries and
unknown-pose semantics instead of weakening the global registrar's motion-hint policy.

`SOURCE_TRACK v=2` separates global/local counts, bounded pair counts and per-thread CPU time for
the motion-consumption stage. Its parser retains v1 support without inventing legacy local/CPU data.
The final implementation skips local image searches when event-only IoU already exceeds 0.8:
an improvement of 0.2 would be mathematically impossible. This does not change the admission rule.

## Verification

Thirteen new JVM tests cover noisy/scaled detector proposals, changed surroundings, unrelated or
uninformative imagery, immutable image ownership, unknown global poses, body-crop boundaries,
stationary headers, text, ambiguity, stale/different documents, budget rejection and ordinary cache
memory behavior. Exact staged snapshot: 527 JVM tests, zero failures; lint and paired target/test
APK builds passed. Prototype worktree: 617 tests, zero failures, same build checks. Six source-trace
parser fixtures and six face-trace fixtures passed. Application/source hashes matched the tested
index export before commit.

The initial trace predates the impossible-search optimization; both videos and the final trace
use the final application code. All valid workloads used ten gestures, no injected capture pause,
and no cache output. Both video runs reset the article toward its top and verified recognition
readiness, but remain sequential, non-randomized trials with different capture outcomes, not
equal-pixel/identical-timing A/B. No analysis job ran alongside the recorded workloads.

## Observed behavior

| Run | Tracker records raw/parsed | Global/local corrected tracks | Correction CPU median/max us | Face publications raw/parsed | Multiple-face-track publications |
| --- | ---: | ---: | ---: | ---: | ---: |
| pass80-on-trace (initial) | 24/24 | 3/6 | 3424/10797 | 18/18 | 2 |
| pass80-on-video | 6/6 | 0/2 | 7127.5/26540 | 3/3 | 1 |
| pass80-off-video | 8/8 | 0/0 | 23.5/1072 | 5/5 | 3 |
| pass80-final-on-trace | 19/19 | 4/1 | 91/20890 | 17/17 | 2 |

All listed geometry/source records parsed, with no face truncation or local budget overrun.
Initial local correction executed on five updates; the final trace executed one local update.
The differing workloads/counts do not support treating the CPU medians as a controlled speedup.
Worst-case measured correction-stage cost remains significant and is not hidden by the lower median.

Both videos decoded fully and matched embedded timestamps: ON 296 frames, OFF 286. Contact sheets
were inspected. Both still show uncovered or displaced face masks, including slow movement and
return from the fling. The ON video also shows that correspondence work can be discarded later:
of local-corrected requests 22391382 and 22391881, only 22391382 appears in the publication trace.

Relative-motion analyzer ON/OFF: 116/99 supported motion frames, 281/261 matched boxes;
median/p95 residual 8.483/36.75 versus 7.344/30.862 px, within-5-px fractions 0.3103/0.3636.
These are not absolute alignment measurements, do not penalize missing/merged boxes, and do not
establish improvement. The visible trial is rejected regardless of aggregate counters.

## Pipeline measurements

Milliseconds; age is request-to-publication, not independently measured pixel age.

| Run | Age median/p95 | Preprocess/runtime/postprocess medians | Publication queue median/p95 | Event-to-draw p95 | Completed/failed/partial captures |
| --- | ---: | --- | ---: | ---: | --- |
| Initial trace | 196.5/294.85 | 3/37.5/1 | 4.5/37.7 | 30.25 | 30/2/0 |
| ON video | 442/476.2 | 11/118/1 | 22.5/293.5 | 90.15 | 14/3/0 |
| OFF video | 275/565.6 | 8/96/1 | 9.5/254.65 | 40.75 | 14/2/1 |
| Final ON trace | 215/429.2 | 4/47/1 | 2/47.7 | 59.1 | 28/3/0 |

Overlay raw/parsed counts equal the face-publication counts above. All capture parsers reported
zero malformed records. Maximum reported cumulative inference drops was zero in every run, but
this is not throughput success: ON video began 14 scenes, invalidated 10 for motion, and published
only three. Text publications were 4/3/3/4, all empty; text stability and flashes remain unvalidated.

Registrar records were respectively 30/14/14/28 raw and parsed, with 15/5/4/21 known poses.
Cache queries, writes and applications were all zero. Final preparation median/p95 was
63.5/124.35 ms. Capture gaps, publication rejection and current-display displacement remain distinct
from the object-local source association this pass added.

An initial optical-analysis wrapper prematurely consumed native stdout with `Select-Object -First`.
The producer failure was reproduced independently, the analysis rerun with complete output consumption,
and both analyses reached terminal success. Cookbook transport guidance was updated separately; the
failed wrapper was not treated as successful verification.

## Next action: scene publication integration

The emulator's actual stored preset is Ultra. Both HEAD and the prototype enable continuous fast
inference for its four-thread configuration. However, the committed service still calls
`invalidateCurrentScene("motion")` unconditionally for nonzero viewport movement. The coordinator
already implements `invalidateForMotion(reprojectableFast)`, retaining eligible fast-only transactions
while rejecting settled atomic scenes and preserving structural/supersession invalidation.

The dirty worktree contains a service integration of that contract via
`invalidateNonReprojectableSceneForMotion()` and a scene-level continuous-inference flag. Review and
validate that narrow integration next; do not import unrelated prototype files wholesale or alter
the preset to obscure the inconsistency. This is a concrete next bottleneck, not proof that it is
the sole cause of visible errors. Better correspondence cannot improve a discarded publication.

Tracking, spatial-cache and capture-gap flags were restored false and independently read back; the normal worktree target/test
APKs were restored. No physical-device mutation, signed release, deployment or user acceptance.
