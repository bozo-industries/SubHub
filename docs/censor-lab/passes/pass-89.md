# Pass 89: deterministic render-only body grouping

The user requested a second merge pass for large overlapping censors that plausibly
belong to one body, explicitly accepting occasional over-merging. This implementation
uses the existing category, rectangle and render-coordinate evidence; it does not
add a model, inspect source pixels, or change detector confidence/confirmation.

## Rules and integration

- Deduplicate nearby face boxes before assigning body boxes to a nearby head.
  Assignment uses normalized horizontal distance, vertical order and scale.
- Reject a proposed component containing assignments to distinct heads. Compare
  against original head representatives, not expanded unions.
- Keep duplicate cleanup, then run a more permissive body-overlap pass. A shared
  head permits 18 percent overlap of the smaller box; without head evidence require
  40 percent overlap and stronger axis alignment. Minimum side is 48 viewport/world
  pixels. Non-overlapping boxes and text do not enter this additional merge pass.
- Limit components to eight original members and body union area to three times
  the largest member. Above 128 input regions skip cosmetic grouping and retain
  every input, bounding work in crowded scenes.
- Preserve original live snapshots across cache refreshes; never treat an earlier
  merged render rectangle as fresh body/head evidence. Keep coordinate bases separate.
- Select a deterministic live-first, lowest-track-id label anchor and preserve all
  constituent rectangles in the union. During steering, the group immediately covers
  its full target union instead of growing slowly from the old single-member box.

This is a heuristic, not proof of person identity. Heads can be absent or duplicated,
and nearby people can still be associated incorrectly. That tradeoff is deliberate;
the original detections, tracking identities and cache confirmation remain unchanged.
Separate merge/split temporal hysteresis is not implemented in this checkpoint.

## Verification

24 consolidator tests cover torso grouping with/without a head, duplicate head
observations, two/three-person rejection, detector-order independence, coverage,
steering growth, text, source-basis fences, transitive bridging and crowded fallback.
The working tree passed 647 unit tests, lintDebug and assembleDebug. An isolated
staged app snapshot passed testDebugUnitTest, lintDebug and assembleDebug too.

The CONSOLIDATE trace reports input/output counts, not visible pixels or identity
accuracy. It is rate-limited and emitted only for reductions, so its record count
is not the total frame count. Parser fixtures require one raw record to yield one
parsed record and reject inconsistent counts or unsupported fields.

A warmed desktop-JVM microbenchmark of the actual grouping code (300 measured
iterations, regular synthetic cards) measured 0.108/0.213 ms p50/p95 for 24 regions
and 1.108/1.957 ms for 72 regions. These are host cost checks, not Pixel timings or
a full-pipeline performance comparison.

Capture age, queue drops, preprocessing/inference/postprocessing, overlay publication
latency, actual merge/split flicker and perceived scroll behavior have not been
remeasured for this change. No new APK is installed and no release is published.
The next signed candidate must preserve the cleaner Pass 87 behavior while comparing
quality-arrival latency and grouping on the Pixel. Pass 88's immediate-quality
service integration and compatible candidate packaging remain pending separately.
