# Pass 74: distinguish capture outages from unmatched images

Status: capture-gap policy implemented and unit-tested; live outage validation still missing.
The spatial experiment remains OFF and visible placement is not accepted.

## Implementation

The cache now records a frame only after cache state has been applied to the overlay view. Queued
cache data is revalidated at that point; a newer unmatched image removes that queued cache while
independently supplied current quality regions remain. This is a view-delivery marker, not a
compositor timestamp or proof of exact pixel/display time.

When a source query expires after 750 ms with no new image, an event may move already-applied
coverage without issuing a stale query or inserting regions. This requires a known latest image,
matching applied map/scope, current document/window and motion-generation fences. The hold is
bounded to six seconds from the applied source receipt. New unmatched images, map/scope changes,
unapplied captures and over-limit holds do not qualify. The ordinary 750 ms query limit is unchanged.

Recovery attempts spatial registration before discarding an old reference. If matching or motion
eligibility fails after three seconds, a new map is still established; match thresholds are not
relaxed and intermediate images remain unknown. Motion eligibility can now include an event
between the scoped reference receipt and recovery, rather than requiring an event only in the
last 750 ms before a delayed callback. Future events and old events outside that interval do not
authorize delayed motion. This is an eligibility rule, not an exact pixel-time estimate.

## Verification

- Exact staged snapshot: 496 JVM tests, zero failures/errors, lint and paired APK builds passed.
- Prototype worktree: 586 JVM tests, zero failures, lint and paired APK builds passed.
- Five added cache tests cover bounded applied-only holding, new unknown/map rejection, queued
  cache revalidation, five-second spatial re-identification with preserved regions, and invalid
  event-history eligibility. Existing unrelated-image expiry and scope tests still pass.
- Three strict spatial parser fixtures, including the new HOLD record, and ten optical fixtures pass.
- The service patch was staged independently of the older prototypes and exported for testing.

## Recorded smoke comparison

Same staged APK in both modes, SHA256
`2540FB1D1992FEF825B1B0D9EC2ED8E9A2496236DB6FD843F27591931EAF3CBA`.
Ten gestures/page reset and 24-second native recordings, OFF then ON. Artifacts are the ignored
`pass74-off-video` and `pass74-on-video` directories. Both recorders/collectors and full optical
analyses completed. This repeats the heavy recording-load stress setup, not an unperturbed benchmark.

| Metric | OFF | ON |
| --- | ---: | ---: |
| Raw/parsed publications | 9/9 | 14/14 |
| Request-to-publication median/p95 ms | 300/440.8 | 275.5/388.65 |
| Detector preprocess/runtime/postprocess medians ms | 5/105/1 | 3/73/1 |
| Capture preparation median/p95 ms | 94/196.3 | 89.5/217.8 |
| Publication queue median/p95 ms | 12/109.8 | 6/114.8 |
| Motion input-to-draw p95 ms | 37.8 | 25.65 |
| Maximum cumulative inference drops | 0 | 0 |
| Text publications, all empty | 3 | 4 |

Capture parsing: zero malformed records; completed/failed/partial requests 23/3/1 and 28/3/1.
ON spatial parsing: 47/47 records, 29 images, 16 known, 18 queries, 12 with regions, CPU median
1845/max 3143 us. No long callback-code-1 failure occurred and HOLD count was zero. Therefore this
run does not validate the new live outage behavior; deterministic tests are not a substitute.

Full videos have 343/356 sampled frames, 123/143 motion frames and 284/400 matched boxes. Corrected
relative-motion residual median/p95 is 6.497/33.037 versus 8.018/36.000 video pixels; within-five
rates 34.96%/32.87%. Contact sheets still show displaced and duplicate masks, including partially
uncovered faces. Equal-time samples are not phase-matched, and the analyzer excludes missing or
merged masks and constant offsets. No significant alignment, coverage or false-positive improvement
is established. Timing variation under recording load does not establish a production speed gain.

Spatial flag restored false and independently verified; worktree target/test APKs restored after
terminal setup instrumentation. GPU and other experiments remain OFF; Pixel untouched, no release
or deployment. Next: a controlled, bounded capture-gap fixture to exercise view holding/recovery,
then address the separately visible live/cache duplicate and placement problems. Do not wait for
an accidental screenshot failure or call the overall candidate accepted from these unit tests.
