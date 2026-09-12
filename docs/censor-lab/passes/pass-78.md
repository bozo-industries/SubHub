# Pass 78: compare registered source movement with face movement

Status: image registration explains substantially more source-to-source movement than scroll events
in this controlled sample. No tracking/rendering correction or visual acceptance is claimed.

The opt-in face trace now emits v2 with the source registration's capture/document/window scope,
map generation and source Y pose. Unknown registration emits `-`, not zero. The v1 parser remains
supported. All records must parse without truncation before motion comparisons are emitted; unknown,
nonmonotonic, changed scope/map/geometry and horizontal movement break adjacent comparisons.
Comparisons require a single visual face of the same class in each adjacent publication, but this
is NOT identity proof. Analyze one process/session at a time; these map numbers are not global IDs.
The registered pose is not a current-display camera and does not establish exact pixel time.

## Measured source correspondence

`pass78-live-geometry` used the exact staged target/test APKs on emulator-5554, the existing controlled
Chrome article, and ten gestures. Spatial cache and geometry tracing were enabled; the capture-gap
marker was never armed. There was no native video recording, so no absolute visual alignment verdict.
All 19 bounded face records parsed, with zero truncation. Ten adjacent single-face comparisons were
eligible; six were stationary and four moved (source pixels):

| Previous/current request ID | Face center delta | Registered image delta | Event delta | Face minus image | Face minus event |
| --- | ---: | ---: | ---: | ---: | ---: |
| 18348397 / 18349119 | -168 | -177.65 | -111 | 9.65 | -57 |
| 18354329 / 18356060 | -67 | -72.4625 | -25 | 5.4625 | -42 |
| 18356060 / 18356414 | -125 | -136.74375 | -17 | 11.74375 | -108 |
| 18356414 / 18356766 | 187 | 187 | 18 | 0 | 169 |

This supports testing image-based tracker continuity without weakening association thresholds.
It does not establish why events missed the motion or prove that the body crop moves every object.
Two publications still contained multiple primary face tracks. Eighteen fresh face tracks had
raw-to-filtered center differences of median zero / maximum 4.5 px; smoothing is still not the
dominant measured source of the large displacement.

Spatial telemetry: 137 raw/parsed records, 30 frames / 21 known; CPU median 2391 / max 6892 us.
Unlike pass 76, cached masks DID reach the final view in this run: 18 of 53 applications retained
requested cache identities. There were 18 positive queries, 20 candidate-positive applications with
no admission, and zero hold events. Source/primary geometry remains separately logged; do not use
this run as a cache-free visual comparison or pool its cache behavior with pass 76.

## Pipeline and verification

19 raw/parsed overlay publications. Request-to-publication age median 197 / p95 267.2 ms;
preprocessing/runtime/postprocessing medians 4/40/1 ms; maximum reported cumulative inference drops
zero. Publication queue median 6 / p95 61.5 ms, main-to-tick median 14 / p95 55.25 ms, event-to-draw
p95 42.5 ms. Five text publications were all empty, so text stability was not validated.
Capture records had zero malformed entries, 29 completed callbacks, two failed requests and one
boundary-partial request. Preparation median 63 / p95 94.8 ms. Publication counts differ from
callback and queued-publication counts; these are not proof of complete coverage or higher throughput.

Exact staged snapshot: 504 JVM tests with zero failures, lint, assembleDebug and assembleDebugAndroidTest
passed together. Prototype worktree: 594 tests with zero failures and the same build checks passed.
Six parser fixtures pass. The initial new JVM fixture incorrectly used an image shorter than the
registrar's minimum; it failed, was corrected, and both final builds passed. Enable/disable emulator
setup instrumentation each had the expected terminal `OK (1 test)` result. Both experiment preferences
were independently read back false, the arm marker was absent, and worktree target/test APKs restored.
Pixel untouched; no signed release, deployment, or user acceptance.

Next: test source-to-source track correction using a registration-only experiment (no cache output).
For previous/current source event cameras Cp/Cn and source-image poses Sp/Sn, extra vertical track
shift is `(Cn-Cp)*sourceScale - (Sn-Sp)`. Combine it with pending event motion BEFORE clipping; do not
also reproject incoming detections by this shift. Keep the prior basis at the last successful tracker
update, not merely the last published view. Validate map owner identity, scope, geometry, horizontal
motion and unknown gaps. Text/fixed-region safety needs explicit eligibility tests; a global body
registration alone does not prove every track moved. Follow with repeated visual A/B and Pixel testing.
