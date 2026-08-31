# Pass 26 — one visible scene transaction

- Status: `partial-success`
- Date: 2026-08-31
- Baseline: Pass25 camera core; Pass22a remains the safe rollback lineage
- Device gate: Pixel 8 Pro, Emiru Google Images, Accessibility/App Mode
- Starting commit: `e983f88581cbd2ecf08a1c02af144e4b744296b0`
- Baseline trace bundle SHA-256:
  `2A6B72572E2C87DEFE3FE043D8C60499B919A6877825394C5E02551D25C41DF2`

## Primary hypothesis

Pass25's late visual waves are caused by render-authority leakage, not insufficient Pixel compute.
Fast and quality observations currently become visible in different fast publications, and
periodic quality refreshes keep reopening a settled generation. If one immutable transaction owns
all observations for an exact capture and publishes at most once on an exact display
vsync, the fast → partial → complete → refresh sequence disappears without sacrificing coverage.

## One primary vector

Introduce a `SceneTransaction`/`SceneSnapshot` boundary:

1. A capture creates one scene epoch containing source timestamp, camera, motion generation, fast
   sequence, and an absolute commit deadline.
2. Fast and optional quality inference may run privately from that same source frame.
3. Partial quality is never committed. Quality either joins the transaction before its deadline or
   is discarded as shadow evidence.
4. The tracker is updated once with the transaction's final merged detections.
5. One immutable world-space snapshot is offered to a latest-only render queue. Each display tick
   consumes only the newest closed snapshot; a newer closed scene waits for the next tick and a
   superseded pending snapshot is disposed without becoming visible. Android's `Choreographer`
   naturally follows 60/90/120 Hz devices rather than hard-coding a Pixel refresh rate.
6. A committed scene ID is closed. A later capture may create a new scene for genuine video,
   lazy-load, or layout changes even when the motion generation is unchanged, but quality from the
   closed capture can never add, remove, or reposition visible tracks.
7. During active motion, established coverage continues through the Pass25 camera. A fast-only
   transaction may meet a strict safety deadline, but late quality from that epoch never produces a
   second wave.

This pass does not tune motion smoothing or identity thresholds.

## Hardware bake-off (completed selection evidence)

The production-signed Pixel instrumentation bake-off compared:

- one full-frame quality engine;
- sequential fast then quality on one screenshot;
- concurrent full-frame fast and quality;
- two overlapping quality tiles;
- mixed NNAPI and XNNPACK/CPU tiles when provider selection is supported.

```text
quality only                  93.70 ms  max 111.63
sequential fast + quality    154.53 ms  max 179.28
parallel full-frame pair     122.49 ms  max 137.32
two NNAPI tiles              144.49 ms  max 175.55
NNAPI + XNNPACK tiles        150.76 ms  max 156.76
```

The device remained at thermal status `0`. Parallel full-frame fast + quality wins. Pass26 must
use that topology; tiling is rejected because it is slower before paying any seam-stitching cost.
These synthetic prepared-bitmap timings select the topology but do not replace the live latency
gate.

## Baseline timing budget

The Pixel baseline provides enough nominal slack inside the approximately `342 ms` Accessibility
capture interval:

```text
capture callback       20–50 ms
fast inference         40–63 ms
quality preparation    23–57 ms
quality inference     115–165 ms
merge + vsync commit    5–15 ms
```

The hard screenshot-request-to-visible deadline is `280 ms` (stricter than buffer-capture age).
Lane joining closes at `248 ms`, reserving `32 ms`
for reconciliation, tracker update, main-thread handoff, and the next display tick. A missed join
deadline publishes/retains the safe fast scene once and closes the epoch; the late result is counted
and discarded. Tune these only from measured Pixel deadline-hit rate.

## Objective success thresholds

- Exactly one visible `SCENE_COMMIT` per exact scene ID. Motion/content generation is a stale
  fence, not the identity: quiet video and lazy-loaded pages can legitimately change between
  captures without a scroll event.
- Zero partial-quality commits.
- Zero post-commit quality-only track-count changes for the same generation.
- Zero periodic quality render commits without a new epoch.
- No visible `cachedQuality=0 → partial → complete` sequence.
- Scene commit capture age p95 at or below `280 ms`; deadline-hit rate at least 95% on the Pixel.
- Active-motion first coverage p95 no worse than Pass25 by more than 10% (`151.3 ms` baseline).
- Fast runtime p95 no worse than Pass25 by more than 10% (`60 ms` baseline).
- Zero dropped-frame queue growth, zero scroll amplification, and no stale-generation commit.
- Active duplicate-like publishes no higher than Pass25's 14 and trending downward.
- No one-frame false-positive flash, text bridge, departed ghost, overshoot, or post-settle second
  wave in the mandatory Emiru video.

## Offline falsification

- A synthetic fast result followed by partial and complete quality observations produces one
  commit, never three.
- A quality completion after the deadline cannot mutate tracker or overlay state.
- Motion, a newer capture, or a foreground/window epoch cancels the older transaction.
- Concurrent deadline and quality completion races commit exactly once.
- The latest-only render queue presents the newest immutable snapshot on a vsync and cannot replay
  an older scene.
- Overlapping-tile stitching, if selected, suppresses boundary duplicates before tracker update.

## Physical Pixel evidence still required

Run the full Emiru sequence with all filters and synchronized Censor Lab recording. Report the
standard latency/stability metrics plus scene starts, deadline hits/misses, commit count per epoch,
partial/late discard counts, time from capture to commit, and post-commit track changes. The user
must explicitly rate perceived speed, motion smoothness, lag/overshoot, text stability, false
positives/flashes, and whether any second or third quality wave remains.

## Deferred ideas

Adaptive tiling and multiple provider/engine lanes are experiments, not assumed wins. If the Pixel
driver serializes NNAPI sessions, preprocessing/memory bandwidth regresses the fast lane, or seam
deduplication adds instability, retain the simpler one-engine transaction. Reconsider tiling only
with a benchmark artifact and the same atomic scene contract.

## Disposition

Pass26 replaces independent fast/partial/complete/periodic render authority with an exactly-once
scene coordinator and a latest-only `Choreographer` presentation queue. Fifteen deterministic
state tests plus a repeated concurrent deadline/quality race cover stale, duplicate, invalidated,
deadline, and both completion orders. The full `testDebugUnitTest lintDebug assembleDebug
assembleDebugAndroidTest` gate passed. A production-signed Pixel bake-off again selected parallel
full-frame fast+quality (`111.79 ms` median) over sequential (`143.57 ms`) and both tiled variants
(`158.35/152.78 ms`).

The user confirmed the remaining limitation is deeper: despite coherent display ticks, genuinely
new visual evidence still arrives at Accessibility's roughly three-Hz screenshot cadence and the
experience still reads as passes. Pass26 remains useful infrastructure and must be retained, but it
is not the release-quality leap. Pass27 targets the missing source of continuity: a bounded
content-space memory that can restore already-seen regions on the first reverse-scroll vsync.
