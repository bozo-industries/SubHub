# Pass 22 — polling, motion, smoothing, and rollback series

- Status: `rollback-anchor`
- Baseline: Pass20/21 lineage
- Gate: Pixel 8 Pro, Emiru Google Images, Accessibility mode

## Pass22a — safe rollback anchor

The exact prior stable APK was restored after later flashing regressions. It remains the safe
installed lineage. Its canonical trace was not retained, so it is a behavioral rollback anchor,
not an objective performance winner.

## Pass22b — Accessibility polling experiment

Node-anchor discovery improved to roughly 54 ms, but refreshed Accessibility nodes did not expose
useful between-event motion. Polling produced no reliable screen-phase signal and was abandoned.

Re-entry rule: do not restore node polling unless a target app demonstrably exposes changing bounds
between scroll events.

## Pass22c — exact rebase and one-shot quality cache

- 68 fast publishes; capture age p50/p95/max 135/171.65/195 ms.
- Runtime p50/p95/max 57/77.3/106 ms.
- 153 scroll events; raw/applied ratio 1.0; no amplification.
- Visual alignment regressed: within 2 px 0.6454, within 5 px 0.7201, residual p95 156.08 px,
  versus Pass16's 0.6902/0.7357/67.609 px.
- The user saw severe flashing. Pass22c was rejected and rolled back.
- Retained video SHA-256:
  `001EBEACF41EB24FEA737772A3D25D861B0CECABF22DFF893968E9F0B35FEACE`.
- Retained alignment JSON SHA-256:
  `6A65F131050DB554AA741975F4D17D76DF7037124858125C158EDB747F3B94B6`.

Re-entry rule: exact rebase cannot return unchanged; it needs a display-phase model that improves
mid-transit residual without reviving overshoot or omission flashes.

## Pass22d — frozen comparison candidate

- APK: `SubHub-pass22d-signed.apk` (generated, not tracked).
- SHA-256: `B680B4A35A2409DB6993FA189934885F0C9F408CF01846661282DBAAFC5A6442`.

Pass22d remains frozen for comparison. Never overwrite this file with a newer worktree build.
