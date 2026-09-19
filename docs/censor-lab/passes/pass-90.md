# Pass 90: reproducible integrated quality-delivery candidate

This checkpoint closes the source gap between the local runtime prototype and a
clean build. It is an experimental branch candidate, not accepted Pixel performance.

## Integration boundary

- CPU fast plus preferred NNAPI quality sessions, adaptive contention deferral,
  one-source ownership and whole-frame/detail quality scheduling are now wired.
- Current quality can enter the normal later-fast render handoff without gaining
  durable cache confirmation. Historical quality still needs repeated observations.
- The default-off immediate-quality flag can refresh only the exact displayed fast
  capture when camera, motion, window, document, dimensions and render basis match.
  Immediate rendering retains the normal handoff rather than consuming it.
- Preserve partial quality coverage for render consolidation instead of discarding
  any candidate with modest overlap. Fully covered same-basis additions are omitted.
  Offscreen alignment and safety padding no longer relocate boxes onto screen edges.
- Coordinate-reference propagation, bounded provisional-window handling and overlay
  reattachment are included. The native anchor sampler remains debug-only/opt-in.
- ContentSpaceRegionCache's uncommitted cross-category matching, 0.18 IoU and longer
  contradiction grace are excluded. QualityBackfillCoordinator keeps committed 0.35
  IoU; its local 0.18 experiment is also excluded. Signed builds must come from Git,
  not from the wider dirty working tree.

The runtime is deliberately not equivalent to the installed Pass 87 baseline.
Matched flag-off/flag-on signed builds from one commit are required to attribute a
latency change to immediate refresh. Provider labels alone do not prove hardware
offload, and tests do not prove throughput or smoothness.

## Verification

Exact source tree `14622301277ca64edef8aef9a3b9a43f14bddf0e` passed:

- 639 unit tests with the immediate-quality flag on and off;
- `lintDebug assembleDebug assembleDebugAndroidTest` together with the flag on;
- `lintRelease assembleRelease` with the default flag off (locally unsigned);
- bounded trace/parser fixtures, including current tiled QUALITY_READY, late and
  immediate presentation, OVERLAY_PUBLISH provenance, and malformed-record detection.

No instrumentation was installed or run in this pass. The Pixel disconnected during
packaging; there is currently no live device measurement. Capture age, drops,
preparation/preprocessing/inference/postprocessing, publication wait, first detection,
text-group stability and false-positive flashes remain unmeasured for this candidate.
Pass 87 numbers must not be presented as results of this integrated source.

## Next gate

Use the existing release-signing secrets through an artifact-only build, preserve
APK SHA-256 and signing certificate, and record the exact source commit/flag in each
artifact. Do not publish or move a release tag. Once the Pixel is available, compare
matched control/candidate traces and obtain the user's verdict on first coverage,
quality arrival, lag/trailing, scroll reversals, grouping, text stability and flashes.
Accessibility acceptance and subsequent MediaProjection work are still outstanding.
