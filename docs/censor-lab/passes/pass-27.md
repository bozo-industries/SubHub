# Pass 27 — content-space memory

- Status: `planned`
- Date: 2026-08-31
- Baseline: Pass26 exactly-once scene transaction and display-tick presenter
- Device gate: Pixel 8 Pro, Emiru Google Images, Accessibility/App Mode

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
3. Use an access-ordered bounded map plus vertical spatial buckets. Initial hard bounds: 384
   entries, roughly 256 KiB metadata, and a near-query no larger than 24 render candidates.
4. At one atomic scene commit, update the cache once and publish live regions union nearby cached
   regions through Pass26's same latest-only tick queue. Cache never updates tracker/stats and never
   publishes independently.
5. Offscreen omission is not a miss. On re-entry, render cached geometry immediately, then let the
   next exact scene validate, correct, or end it asynchronously.
6. Live geometry wins by stable identity/world overlap, so one subject cannot render as both live
   and cached coverage.

## Safety and invalidation

- Hard clear on package/window/navigation epoch, recognition reset, rotation/density/viewport or
  model/category configuration change, scroll-surface identity change, and discontinuous camera.
- Chrome lazy-load content events during active scroll dirty only the affected band; they do not
  globally erase useful history.
- Remove an in-view cached region after positive contradiction from two unified scene commits.
  Grace is asynchronous and immediately cancellable; visual same-document TTL starts at 90 s and
  anchored text at 180 s.
- If live/cache anchors agree on one local reflow translation, shift that band. If residual
  disagreement exceeds 12 px or an unsupported shift exceeds 24 px, suspend/evict the band rather
  than showing knowingly stale geometry.
- Cached geometry uses a solid compositor censor until fresh source pixels exist. Blur, mosaic,
  static, and glitch must not sample stale image pixels.

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
