# Pass32 — preserve reprojectable fast work during scroll

- Status: candidate; device telemetry supports the mechanism, user reports improvement but alignment remains inadequate.
- Date: 2026-09-05.
- Diagnostic reference: frozen Pass31 APK `5FC30EA8D3FFF97BEEE0563D2886B26E4D03E4F377A9D26AB0BEA48FB29176A3`. Pass31 is not an accepted performance baseline.
- Worktree: `codex/censor-pipeline-v2`, committed parent `389ebc242c768a60ced08679394810b554c53e80`, with pre-existing uncommitted Pass31 plus this experiment. Do not equate the APK with committed HEAD.
- Mode/device: Accessibility App Mode, physical Pixel8Pro, Android17, 1344x2992; Chromium `org.chromium.chrome.stable`140.0.7339.248.

## Hypothesis and isolated change

Ordinary scroll invalidates a fast scene even though continuous inference retains that frame and already reprojects it before tracking/presentation. This wastes useful completed inference without a newer capture replacing it.

`SceneTransactionCoordinator.invalidateForMotion` now retains only reprojectable fast-only scenes. The captured continuous-mode flag is retained in `SceneContext`. Navigation, document/surface resets, atomic/non-reprojectable modes, and newer captures still invalidate/supersede. Motion-surviving old-generation results may render but cannot insert, update, or contradict world-cache observations. Late-quality motion fences remain unchanged.

Changed implementation: `SceneTransactionCoordinator.java`, `ScreenshotAccessibilityService.java`; regression coverage: `SceneTransactionCoordinatorTest.java`.

Success condition: zero same-sequence scene-closed drops on the repeated gesture test, materially fewer missing publications, and preserved structural fences. Falsifiers: unchanged cancellation gaps, a stale document publication, or worsening visible alignment/coverage. Cache stacks, detection recall, and hardware placement remain separate questions.

## Frozen artifacts and verification

All artifact paths below are relative to `app/build/reports/device/`; binaries/captures are not committed.

- APK: `pass32-astra-motion-candidate/SubHub-pass32-motion-retention-arm64-v8a-release-signed.apk`
  SHA256 `273D906FA829E73DD19E8CA9FDCE5EBE37049EE1986912B568C2A09174F03684`.
- Installed base APK independently hashed to the same value; release certificate verified against the established signing key.
- Trace: `pass32-astra-pixel-chromium/calibration-20260905-070503-logcat.txt`
  SHA256 `94D5DFEF1DBE034A36155A2F46632A08499C80D4C7B6E8FD2B89C0D5A6DF9F7F`.
- Run manifest: `pass32-astra-pixel-chromium/calibration-20260905-070503-run.json`
  SHA256 `C15D32A06D781205BA816CE9CB01EC7D5169BE6820C2FDCB113E7F721510A167`.
- Full420 unit tests, debug/release lint, app+test APK build, signed release build pass. Instrumentation was compiled, not executed in this pass.
- Analyzer marker-boundary regression script passes. Use explicit RUN_START/RUN_END bounds; never count buffered prefix/suffix records as this experiment.

## Pixel evidence

Same 12-gesture Chromium script, telemetry-only (no encoder). Reference duration34.694s; candidate35.710s. Candidate began at36.3C and ended37.2C versus reference near30C, so inference-runtime changes are not clean causal speed evidence. This short workload is not the full sustained/slow/fling/jitter release gate.

| Measure | Pass31 reference | Pass32 |
|---|---:|---:|
| Scenes begun / published | 97 / 77 | 97 / 96 |
| Same-sequence scene-closed drops | 13 | 0 |
| Pending-mailbox replacements | 0 | 0 |
| Fast capture-to-publication p95 | 149ms | 150.5ms |
| Publication interval p95 / max | 1037.25 / 1614ms | 656.1 / 734ms |
| Quality completions | 87 | 87 |
| Quality later fast publications | 31 | 35 |

Candidate fast p50/p95: preprocessing2/6.25ms, native30/44ms, postprocessing1/1ms, total inference34.5/47ms, capture-to-publication115/150.5ms. These preprocessing values are model preparation, not total hardware-bitmap readback. Candidate quality bitmap preparation39/66.7ms, native130/155ms, capture age231/383.7ms. Three quality cancellations, three late-quality motion drops, and one outstanding staged result remain; do not call this zero total drops. Twenty-one cache writes were rejected by the old-generation fence while fast results could still publish.

Whole-run publication gaps include pauses and are not a direct alignment measure. Text had30 scans but13 publications all with zero regions: this test does not validate text detection quality. Nine >=100px tracker-geometry changes and10 duplicate suppressions are diagnostic counts, not proof of actual page alignment or duplicate elimination.

## Human verdict, outstanding risk, disposition

Human verdict: "better, sure. biggest challenge rn I'd say would be perfect scroll-alignment". Prior verdict was acceptable speed, somewhat better alignment, but severe short-jitter stacks. This is not confirmation that the overall goal is achieved.

A subsequent35-second alignment recording is retained at `pass32-alignment-ready/alignment.mp4`,
SHA256 `102E0A12F2A3FC571571806AF815E2857B799BCDFD03FBE1A9246547408F9CA8`.
Its trace is `pass32-alignment-ready/calibration-20260905-072810-logcat.txt`,
SHA256 `CD3098EFF32F3056552EE29D084E4E928808DC3B1865620ACF9DE9F4B6877FA7`, with run manifest and separate monotonic log alongside.
It uses `pixel_alignment_probe.json` (slow/medium pairs and short jitter), bugreport frame timestamps, and an active screen encoder. Do not compare its runtime directly to telemetry-only captures.
Initial decode check:2054 frames,1344x2992,last PTS35.311311s; sampled burned monotonic timestamps minus decoded PTS agree within about10 microseconds, and sampled burned drop counters are zero. This validates clock association, not alignment. The first slow-up gesture crosses a provisional-to-stable document transition, so use later clean phases for attribution. Box-to-background measurements are still in progress. Earlier zero-byte failed captures are excluded.

Retain as a candidate only. Existing cache/live duplication remains unresolved; do not widen consolidation thresholds or claim this pass fixes it. Next vector: canonical live/cache ownership with preserved scroll-back coverage, separately tested. Requested NNAPI provider labels still do not prove execution offload. No recall parity, hardware isolation, release-readiness, or significant user-perceived improvement claim.

Rollback: reinstall the frozen Pass31 reference for this isolated comparison; older accepted rollback lineage remains unchanged. Reconsider motion invalidation only if a reproduced structural/phase violation requires it, not as a blanket response to cache duplicates.
