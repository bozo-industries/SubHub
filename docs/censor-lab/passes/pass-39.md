# Pass 39: classify anchor measurement loss

Status: diagnostic only; not an accepted alignment improvement.

## Plan and result

Instrument rejection causes before changing geometry tolerances or fitting sequential node
reads as though they were simultaneous. Preserve all acceptance thresholds and privacy:
only fixed enum names and counts, never provider exception messages or node content.

Core diagnostics committed in bce291d. Android node classification and service log suffix
remain part of the pre-existing uncommitted integration; do not mistake the core commit
alone for the installed application. Full working-tree unit suite: 515 tests, zero failures;
lintDebug and paired assembleDebug/assembleDebugAndroidTest passed. The independently
materialized staged sampler suite passed 19 tests.

## Emulator evidence

- APK SHA256: 6E12E17B3EAAEA199A10DE4FC781C80AAD30F6DD610A6D97F161E6F6F8C43DBA.
- Emulator-5554, already-open Chrome Emiru image page, restored to page top and visually
  checked with the ADB PNG helper. No Windows screenshot-helper repair is claimed.
- Local artifact: app/build/reports/device/pass39-rejection-audit/trace.log and analysis.json.
- Ten controlled gestures, 11.663 seconds; trace-only, no motion-video acceptance claim.
- Parser accounted for all 25 fast publications; only one had a known render-source reference.
- First/last periodic stats: reads 298 to 404, accepted 270 to 340, slow drops 27 to 59,
  invalid drops 0 to 2, resets 0 to 5. These are periodic snapshot deltas, not exact gesture
  boundaries; pre-run cumulative counts must not be attributed to this run.
- Invalid reasons: INVISIBLE:1 and INCONSISTENT_TRANSLATION:1. No clipping rejection.
- Maximum cumulative read span was 64 ms. The existing 16 ms rejection policy was unchanged.

The data does not justify simply widening the geometry tolerance. Read-budget losses are
frequent, while disappearing anchors and sequential geometry disagreement both exist.
More accepted observations alone are not sufficient: capture-time reference coverage remains
poor. Next incomplete action: distinguish per-node timing/latency from group incoherence
with bounded timestamps, then evaluate whether a coherent replacement/read strategy improves
coverage without accepting stale measurements. Keep scope and capture-phase guards intact.

The experiment was restored OFF through the setup instrumentation and the same target APK
was reinstalled successfully. Collector exited. No Pixel validation; the larger goal remains
active. Weekly quota at start: 53 percent remaining (pause threshold: 5 percent).
