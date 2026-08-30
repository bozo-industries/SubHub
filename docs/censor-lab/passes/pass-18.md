# Pass 18 — immediate motion and duplicate reacquisition audit

- Status: `inconclusive`
- Baseline: Pass17
- Gate: Pixel 8 Pro, Emiru Google Images, Accessibility mode

## Hypothesis and vector

Presenting authoritative Accessibility deltas immediately would remove interpolation lead while
preserving the fixed per-event scroll path.

## Objective evidence

- 239 fast publishes; 123 active-scroll publishes; no fast drops.
- No duplicate suppressions were reported.
- Tracks exceeded raw visual detections by at least two on 20/123 active publishes, by at least
  three on eight, and by at least four on five.
- Duplicate-like clusters were consistent with stale and newly reacquired tracks separated by
  roughly half a box height, outside the old identity and near-duplicate thresholds.

## Human verdict and disposition

First reaction: faster and less behind, but not smoother. Rare duplicate boxes remained on faster
scrolls. Preserve immediate authoritative presentation, but do not solve reacquisition by blindly
loosening identity thresholds; prove capture phase and source generation first.

Re-entry rule: any identity-threshold change needs pairwise center/IoU evidence and an adjacent-face
non-merge test.
