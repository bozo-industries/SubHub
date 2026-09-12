# Frozen Pixel source-lineage audit — 2026-09-12

This is a build-lineage audit, not a performance pass or new device acceptance.

## Inputs and verification

- Current compared commit: `bf8ff4469eb397ead3de578c2de01a2fa8f3336a`.
- Frozen Pass 33 APK SHA256 was independently rechecked and matches its recorded value:
  `45BCE7246D018E3AD2566ACEB42E44B3F22D457071974B16F211B68B095AF018`.
- Historical source fingerprint: `app/build/reports/device/pass33-phase-candidate/source-fingerprint.json`.
  SHA256 `D0FFC3AC320E0D931B44B5F511ACB65457FD9BDEF18B99A82D1EED005036DADB`.
- The fingerprint records parent `77532c50ef7a115ec6a354e8676b5ca3577ac945` and explicitly precedes UI edits.
  It is a historical build record, not source independently reconstructed from the APK.

The new `scripts/compare_source_fingerprint.py` compares the 389 recorded main-source/resource paths
against both the working tree and a resolved Git commit. It reports checkout-newline matches
explicitly, labels its equal-Git-blob shortcut, rejects unsafe/duplicate paths, and does not overwrite
source files. Three fixtures cover newline handling, path boundaries and classification. All passed.
The generated report is private at `app/build/reports/device/pass82-lineage/lineage.json`.

## Result

| Classification | Files |
| --- | ---: |
| Both HEAD and worktree match the frozen source | 343 |
| Only the worktree matches | 9 |
| Neither matches | 37 |
| Only HEAD matches | 0 |

There are also 23 current main-source/resource paths absent from the old fingerprint. These counts
describe content lineage, not regression severity, semantic equivalence, or APK equivalence.

All nine worktree-only matches are exact byte matches, not newline-only matches:

| File beneath `app/src/main/java/com/subhub/app/` | Current HEAD |
| --- | --- |
| `detection/DetectionEngine.java` | Different content |
| `detection/DetectionPostProcessor.java` | Different content |
| `detection/VisualTrackArbitrator.java` | Different content |
| `service/AccessibilityViewportPoller.java` | Absent |
| `service/ProvisionalScrollSurface.java` | Absent |
| `service/QualityBackfillRunner.java` | Absent |
| `service/QualityConcurrencyGovernor.java` | Absent |
| `service/QualityTilePlanner.java` | Absent |
| `service/ScrollSurfaceHysteresis.java` | Different content |

Thus a clean-HEAD build is not a faithful reconstruction of the earlier Pixel candidate. Nor is
the current worktree an exact reconstruction: 37 old files have diverged in both, including the
service, tracker, overlay, bitmap preparation and cache components. Do not label either simply
"the Pixel baseline" without specifying its artifact and source lineage.

## Implications for the next candidate

Preserve and review these differences deliberately; historical matching alone does not justify
shipping a file unchanged. Initial review found two separate post-processing policies:

- The old face-logit ambiguity handling accepts competing labels when they resolve to the same
  user-enabled category, while retaining cross-category ambiguity rejection. This is a bounded
  candidate for restoring missing detection behavior, with its existing regression tests.
- The old stale-track arbitration broadens handoff across all non-text visual categories.
  Do not import that behavior merely because its file matches the historical candidate; verify
  that it cannot suppress distinct nearby objects or weaken coverage.

No Android source, emulator preference, signing workflow or physical device was changed by this
audit. No new capture/latency/throughput measurements are claimed; the latest runtime measurements
remain Pass 81. Pixel availability was asked once, non-blockingly, because the emulator recordings
have capture gaps that limit further visual validation. Overall real-device acceptance remains open.
