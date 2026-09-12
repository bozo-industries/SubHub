# Pass 81: complete motion-surviving scene integration

Status: scene survival is restored in committed service code. Visual acceptance still fails.
This reconciles previously tested prototype behavior with the branch; it is not a new Pixel win.

## Historical and current evidence

Jolli identified coordinator checkpoint `2c4e5576`. Pass 32 already tested this policy on Pixel,
where 96/97 scenes published versus 77/97 in its reference, but alignment remained inadequate.
Pass 33 explicitly says its APK included uncommitted Pass 31/32 service work. Therefore the earlier
Pixel artifact must not be equated with committed HEAD, nor this integration described as a newly
proved improvement over that artifact. Both historical documents matched their GitHub copies.

Before this pass, HEAD's `applyScrollMotion` called `invalidateCurrentScene("motion")` for every
nonzero delta, despite Ultra enabling continuous inference and the coordinator already having
`invalidateForMotion`. The working tree contained the service integration, but it was absent from
the tested committed snapshots used in recent emulator passes.

## Implementation and verification

The service now carries the captured continuous-inference flag through `beginScene` into
`SceneContext`, and uses the existing coordinator's motion policy. Only reprojectable fast-only
scenes survive ordinary translation. Non-continuous and settled atomic scenes still close;
supersession and structural/document invalidations remain unchanged. No quality join, model,
preset, confidence, timestamp or camera policy was changed.

The companion event-cache fence is included: source-generation-mismatched fast observations may
render, but cannot insert/update event-coordinate cache evidence. Existing cache queries remain
available. This is distinct from the opt-in image-coordinate cache's own source-image validity.
`WORLD_CACHE_QUERY` now reports write admission and source generation; a dedicated strict parser
supports legacy unknown status and rejects malformed fields or claimed rejected writes with mutations.

Three Android tests instantiate an unconnected service and exercise scene flag propagation,
motion handling before/after commit, conservative cancellation, supersession/document invalidation,
and actual event-cache admission. The cache test verifies rejected old-generation insertion,
accepted current-generation insertion, and no geometry update from a later stale observation.
All three passed on both the exact staged APK pair and the prototype worktree APK pair. The three
actual cache-test trace rows parsed as two rejected writes and one admitted write. Existing JVM
coordinator tests cover atomic-scene rejection and repeated reversals as well.

Exact staged snapshot: 527 JVM tests, zero failures, lint and paired target/test APK builds passed.
Prototype worktree: 617 JVM tests, zero failures, same build checks. Three new parser fixtures pass.
Selective staging preserved unrelated prototype work, and staged source hashes matched the tested
export. The gesture runs below exercise the real motion call site in addition to the service tests.

## Emulator scene delivery

All runs used the controlled Chrome article, Ultra and ten gestures. Baseline is the frozen final
Pass 80 build; the candidate adds scene retention. Both comparison modes enabled the same tracking
experiment/corrections and suppressed cache output. Videos and the first candidate trace predate
the companion event-cache fence, a path disabled in those comparisons. The final default trace
uses the fully fenced build with experimental tracking/cache output disabled and face tracing only;
its capture-gap probe was never armed. These sequential trials have different capture outcomes,
not identical-input timing or randomized causal runtime evidence.

| Run | Scenes begun | Publications raw/parsed | Motion invalidations | Multiple-face-track publications |
| --- | ---: | ---: | ---: | ---: |
| pass81-baseline-video | 9 | 4/4 | 6 | 0 |
| pass81-candidate-video | 7 | 5/5 | 0 | 0 |
| pass81-baseline-trace | 29 | 17/17 | 16 | 2 |
| pass81-candidate-trace | 28 | 27/27 | 0 | 3 |
| pass81-final-default-trace | 28 | 25/25 | 0 | 4 |

All face records also parsed without truncation. The trace-only comparison supports restoration
of scene survival: 27/28 versus 17/29 published, with seven active-fast publications versus two.
It does NOT show fewer duplicates or lower latency. Candidate video had only seven captures;
its input shortfall cannot be hidden by the improved publication fraction.

## Pipeline and visual outcome

Times below are ms; age is request-to-publication, not independently measured pixel age.

| Run | Age median/p95 | Preprocess/runtime/postprocess medians | Publication queue median/p95 | Event-to-draw p95 | Completed/failed/partial captures |
| --- | ---: | --- | ---: | ---: | --- |
| Baseline video | 334/541.85 | 6.5/114.5/1.5 | 4.5/122.75 | 37.5 | 9/2/2 |
| Candidate video | 233/581.2 | 4/73/2 | 3/126.75 | 47 | 7/1/1 |
| Baseline trace | 175/307.4 | 4/39/1 | 3.5/64.2 | 75.75 | 29/3/0 |
| Candidate trace | 263/530.7 | 4/63/1 | 19/142.6 | 44.7 | 28/3/1 |
| Final default trace | 273/468.8 | 5/54/1 | 6.5/210.75 | 30.6 | 28/4/0 |

All capture parsers reported zero malformed records. Reported cumulative inference drops were zero
in every run, not proof that all requests reached presentation. Text publication counts were
4/4/4/5/4 in table order, all empty; text quality/stability and false-positive flashes are unvalidated.
Final preparation median/p95 was 82.5/150.85 ms. Startup/first-detection latency was not measured.

Native video frame counts matched embedded timestamps: candidate 317/317, baseline 285/285.
Both contact sheets were inspected. Misplacement and uncovered faces remain; in the candidate,
the face is visibly uncovered in multiple sampled frames after returning from the fling. Fewer
scene cancellations are not a substitute for visible coverage. No visual acceptance or claimed
new physical-device result.

## Disposition

Tracking, spatial-cache and capture-gap flags were independently read back false. Final worktree
target/test APKs were restored after successful three-test Android verification. Pixel untouched;
no signed candidate, tag, release, deployment or new user acceptance in this pass.

Continue alignment/capture-gap work against a clearly identified runtime baseline. In particular,
do not repeatedly rediscover uncommitted behavior already present in the frozen Pixel trials, or
compare a clean HEAD build to those APKs as if they contained the same source. The motion-aware
integration is now a tested committed foundation; capture availability, current-display pose,
remaining duplicate/coverage problems and final real-device confirmation are still open.
