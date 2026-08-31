# Pass 27 — content-space memory

- Status: `implementation-complete; device gate pending`
- Date: 2026-08-31
- Baseline: Pass26 exactly-once scene transaction and display-tick presenter
- Device gate: Pixel 8 Pro, Emiru Google Images, Accessibility/App Mode
- Signed Pixel candidate SHA-256:
  `7D3B6F59BBC230CC45C7BBBF0DE11522CD78407CF008B2EF5243DFD4817A03F6`

## Primary hypothesis

The renderer already moves world-space geometry every display frame, but the tracker clips and
deactivates regions when they leave the viewport and the next scene replaces the renderer list.
Therefore reverse scrolling unnecessarily waits for a new screenshot and detector pass. A bounded,
document-scoped cache of previously confirmed world regions can include nearby offscreen coverage
in each immutable scene snapshot, allowing it to cross back into the viewport on the first vsync.

## One primary vector

Add a metadata-only `ContentSpaceRegionCache`:

1. Cache only regions confirmed by at least two committed scenes, or corroborated by the atomic
   fast+quality result. Never cache a one-frame fast-only candidate.
2. Store canonical display-pixel world geometry, stable render identity, category/class,
   confidence/evidence, timestamps, document epoch, scroll-surface identity, and layout band.
   Store no screenshots or reconstructable content.
3. Use fixed primitive arrays, an allocation-bounded LRU, and vertical spatial buckets. Hard
   bounds are 2,048 entries, 2 MiB metadata, and at most 24 render candidates per near-query.
4. At one atomic scene commit, update the cache once and publish live regions union nearby cached
   regions. Motion re-entry is applied atomically with the camera mutation in one UI callback;
   cache-only work is forbidden from entering Pass26's detector-scene broker, where it could evict
   a newer authoritative scene. Cache never updates tracker, stats, penance, dwell, or tap state.
5. Offscreen omission is not a miss. On re-entry, render cached geometry immediately, then let the
   next exact scene validate, correct, or end it asynchronously.
6. Live geometry wins by stable identity/world overlap, so one subject cannot render as both live
   and cached coverage.

## Safety and invalidation

- Hard clear on package/window/navigation epoch, recognition reset, rotation/density/viewport or
  model/category configuration change, scroll-surface identity change, and discontinuous camera.
- Remove an in-view cached region after positive contradiction from two unified scene commits.
  Grace is asynchronous and immediately cancellable; visual same-document TTL is 30 minutes and
  anchored text is 60 minutes. Foreground/document/surface/configuration fences remain immediate.
- Cached geometry uses a solid compositor censor until fresh source pixels exist. Blur, mosaic,
  static, and glitch must not sample stale image pixels.

Dirty-band invalidation and bounded local reflow remain a later, separately instrumented vector.
This initial gate uses conservative document/surface/configuration/geometry fences plus two-scene
in-view contradiction; it does not claim partial reflow support.

## Implemented evidence before the Pixel gate

- Cache storage has a hard 2,048-entry and 2 MiB metadata ceiling, query output is capped at 24,
  and visible entries outrank offscreen prefetch when that cap is reached.
- Re-entry spatial queries are throttled to roughly one-third of a viewport; already-published
  world regions continue moving every display frame without any query or allocation.
- Inference frames snapshot document epoch and surface identity before work. Cache commits use a
  lock-protected compare-before-write fence, so a worker from the old document cannot repopulate a
  cache after invalidation.
- A new surface is never seeded from the previous track list. One-frame fast tracks and one-frame
  quality-only tracks cannot enter memory; initial promotion requires two tracked frames.
- Cached render IDs occupy a negative namespace and live geometry suppresses overlapping cached
  geometry before draw.
- 15 focused JVM cache tests pass, including bounds, TTL, contradiction, visible-query priority,
  geometry changes, and non-1:1 coordinate round-trip.
- 14 Android overlay tests pass on emulator, including immediate leave/re-entry, live/cache dedup,
  and cached geometry at differing capture/display scales.
- The required 79-task unit/lint/debug/debug-test gate passes. The production-signed candidate is
  installed wirelessly on the Pixel with all pre-existing Accessibility services preserved.
- A live package-transition smoke exposed and removed same-window invalidation churn: cache history
  now survives transient window-state events and clears only when the selected application window
  ID actually changes.

## Look-ahead boundary

Generic Accessibility and MediaProjection only expose currently rendered display/window pixels;
they cannot reveal never-seen below-fold images. Accessibility descendant prefetch can only batch
nodes the target app already exposes (maximum 50 per request), so offscreen text/semantic discovery
is a separate instrument-first experiment and provisional hints cannot render until their node
identity and bounds remain valid. App-owned adapters or an owned WebView may later provide real
cooperative prefetch, but stock Android Chrome cannot be treated as if it supplied a DOM extension.

## Objective success thresholds

- A confirmed region that leaves and returns during the same document appears on the first display
  tick, without waiting for `CAPTURE_PHASE`, `FAST_READY`, or `QUALITY_READY`.
- Zero live+cache duplicate boxes and zero cache-originated stats/penance events.
- Zero cached regions survive navigation, refresh, foreground switch, rotation, or surface change.
- Zero one-frame fast false positives enter the cache.
- At least 90% cache-hit rate on the canonical down/up Emiru return segments, with re-entry
  first-draw p95 at or below one device frame plus Accessibility event delivery.
- No regression to Pass26 active motion, exactly-once scene commits, or 280 ms visible deadline.

## Required evidence

Unit tests cover retention outside the viewport, immediate return, contradiction, epoch/surface and
rotation invalidation, band reflow, LRU/distance bounds, false-positive exclusion, and anchored text.
An Android renderer test must prove a publisher can replace its live list while the cache restores
the offscreen world region. The physical Pixel gate must include deliberate down/up returns, fast
jitter, page refresh/navigation, and lazy-loaded Google Images reflow.
