# Pass 79: guarded source-image tracker continuity experiment

Status: implemented and exercised, but not a reliable visual fix. Keep OFF by default.
Unknown image poses and capture gaps still leave duplicated/displaced or uncovered masks.

## Implementation and safety

An explicit debug/emulator-only `spatial_tracking_experiment` preference enables registration-only
testing. Its separate `correctTracks` switch provides the control/treatment. Both modes suppress
spatial and legacy cache output; ordinary fast/quality tracking remains in place. This is not a
production setting, current-display camera estimate, or claim of exact source pixel time.

`SourceTrackContinuity` keeps the frame and refreshed source-event coordinates of the last successful
tracker update. Same-map checks include owner identity, capture/document/window scope, source and
viewport geometry, crop, ordering and horizontal movement. Unknown updates clear continuity. A pure
proposal is computed before geometry mutation; its old basis is invalidated before applying motion,
so a later aborted update cannot silently reuse it. Runtime viewport/document/window checks also
reject a source that stopped belonging to the current surface.

Extra vertical shift is `(currentSourceEventY - previousSourceEventY) * sourceScale - imagePoseDelta`.
It is combined with pending event movement BEFORE clipping. Incoming detections keep their ordinary
event-tail reprojection; they do not receive the correction a second time. Raw, filtered and prediction
origin boxes are shifted together by the existing tracker operation.

Only active visual tracks seen in the preceding update are eligible, with centers inside both
registered body crops. An incoming same-class, non-text/non-anchored visual detection must support
the translated box at IoU >= 0.5 and improve over the best event-only overlap by at least 0.2. A
stationary object with better event-only support, ambiguous candidates, a detection already occupied
by another track, or two tracks claiming the same detection cannot trigger this correction. These
gates are stricter than ordinary association; no detector confidence or registrar threshold changed.
They are conservative spatial evidence, not proof of person identity or arbitrary fixed-region motion.

`SOURCE_TRACK v=1` counts successful tracker updates and applied corrections, not displayed frames.
Three strict parser fixtures reject inconsistent/unknown records. Existing face v2 traces remain
available automatically in this explicit experiment. Ten JVM tests cover movement/reversal,
double-counting, source scaling/refreshed event cameras, unknown chains, map ownership, fixed/text
objects, ambiguity/occupied detections, missing tracks and combined-before-clipping behavior.

## Live results and limits

All valid runs used the isolated staged build and ten gestures on the controlled emulator Chrome
article. These are sequential trials with different capture outcomes and slightly different initial
page positions, NOT randomized equal-pixel A/B. Native video adds substantial emulator workload.

| Run | Video | Tracker updates raw/parsed | Corrected updates / tracks | Max extra shift | Face publications raw/parsed | Multiple-face-track publications |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| pass79-off | yes | 15/15 | 0/0 | 0 | 13/13 | 6 |
| pass79-on | yes | 9/9 | 0/0 | 0 | 4/4 | 1 |
| pass79-on-repeat | yes | 8/8 | 1/1 | 130 | 7/7 | 3 |
| pass79-on-trace | no | 23/23 | 4/6 | 167 | 17/17 | 0 |
| pass79-off-trace | no | 27/27 | 0/0 | 0 | 21/21 | 6 |
| pass79-final-on-verified | no | 25/25 | 1/1 | 168 | 20/20 | 4 |

All listed face records parsed without truncation. The last run includes the final runtime-scope
guard; earlier runs predate that guard. The first recorded ON run made zero corrections, so its
differences cannot be attributed to the treatment. Inspection of its trace showed intervening
`NO_MOTION_HINT`/`UNMATCHED` tracker updates breaking the source chain. Do not remove these guards
merely to improve a trial count.

One concrete exercised pair in `pass79-on-trace`: face track 13 remained track 13 from request
19680580 to 19680936 as the source face center moved +192.5 px, registration estimated +187 px,
and event movement accounted for +20 px. The additional +167 px preceded association. This proves
execution and a useful instance of continuity, not broad elimination of duplicates. The final run's
four duplicate-face publications show that the earlier zero-duplicate result was not repeatable.

Native video decode counts matched embedded timestamps exactly: OFF 264, ON 254, repeated ON 282.
All three contact sheets were inspected. They still show uncovered faces around the first slow
movement and return from the fling, and misplaced/duplicate masks. No visual acceptance. The first
OFF wrapper reported a terminal failure despite complete encoder output; that artifact was recovered
only after independent decode/timestamp validation, not by assuming the wrapper succeeded. Later
recordings retained the native process handle and completed with a known zero exit code.

Relative-motion analysis (not absolute alignment; missing/merged boxes are not penalized): OFF
median/p95 6.982/37.95 px, first ON 8.789/52.219, repeated ON 6.536/35.7. Within-5-px fractions
were 0.3548/0.3438/0.3419. These do not override the visible coverage failures or establish improvement.

## Pipeline measurements

Times are milliseconds; age is request-to-publication, not independently measured pixel age.

| Run | Age median/p95 | Preprocess/runtime/postprocess medians | Publication queue median/p95 | Event-to-draw p95 | Completed/failed/partial captures |
| --- | ---: | --- | ---: | ---: | --- |
| OFF video | 452/606.4 | 4/128/1 | 5.5/87.9 | 33.55 | 29/0/1 |
| ON video | 506/658.2 | 9.5/96.5/1 | 14/156.8 | 75 | 15/1/2 |
| Repeated ON video | 322/466.5 | 3/124/1 | 2/43.05 | 34.6 | 22/3/1 |
| ON trace | 189/285.2 | 3/36/1 | 5/52.9 | 27.55 | 29/2/0 |
| OFF trace | 195/429 | 4/43/1 | 5/97.9 | 49.25 | 30/2/0 |
| Final ON trace | 223.5/355.35 | 4.5/52.5/1 | 7/62.45 | 28 | 33/2/1 |

Overlay raw/parsed publication counts equal the face-publication counts in the first table.
Every listed capture parse had zero malformed records and every run's maximum reported cumulative
inference drops was zero. Different callback/queued-update/publication counts and natural capture
failures mean this is not proof of complete throughput. Text publication counts were respectively
4/3/4/4/3/6, all empty; text stability and false-positive flashes remain unvalidated.

Registration-only ON/OFF/final trace records: 29/30/34 raw and parsed, 21/18/22 known frames;
median registrar CPU 2203/3235.5/2508.5 us. Cache queries, writes and cache applications were all zero.
The final trace's preparation median/p95 was 73/136.8 ms; main-to-tick median/p95 16.5/75.45 ms.

`pass79-final-on-trace` is excluded: its collector stopped before the final marker was flushed.
The strict analyzers rejected it. The helper now acknowledges the final marker before stopping,
and `pass79-final-on-verified` is the replacement, not an invented boundary for the rejected trace.

## Verification and next work

Exact staged snapshot: 514 JVM tests, zero failures; lint and paired target/test APK builds passed.
Prototype worktree: 604 tests, zero failures, same build checks. Strengthened unknown-chain tests
were rerun after the final build without changing application sources. Initial synthetic shifts
exceeded the registrar's existing search range and were rejected; valid-range fixtures replaced
them without changing registration thresholds. Nine related Python fixtures pass.

All explicit emulator setup/cleanup instrumentation had the expected `OK (1 test)` terminal result.
Both tracking flags, spatial cache and capture-gap flags were independently read back false; worktree
target/test APKs restored. Pixel untouched. No signed release or deployment. Cookbook guidance was
updated separately for matching Python native ABI and acknowledging final collector markers.

Next investigate correspondence availability across unknown updates and body-crop boundaries,
with per-object source-image evidence rather than blanket motion-hint bypass or relaxed IoU. Retain
the safety of fixed/text objects while making the correction apply reliably during real scrolling.
Capture gaps and current-display lag remain independent problems. Repeated visible acceptance and
the user's Pixel verdict are still required before promoting this experiment.
