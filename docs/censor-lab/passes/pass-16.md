# Pass 16 — baseline and scroll-debt audit

- Status: `inconclusive`
- Baseline: earlier instrumented Accessibility build
- Gate: Pixel 8 Pro, Emiru Google Images, Accessibility mode
- Artifact: old trace intentionally removed; metrics survive in the objective audit

## Hypothesis and vector

Instrument the existing pipeline deeply enough to distinguish inference latency, viewport motion,
geometry churn, and quality/text refinement delay.

## Objective evidence

- 115.463 s; 331 fast publishes; 122 active-scroll publishes.
- Capture age p50/p95/max: 138/178/259 ms.
- Active runtime p50/p95/max: 45/63/92 ms.
- Scroll raw/applied: 133,373/133,366 px overall, but 14 events were suppressed and 13 were
  amplified. Individual corrections nearly doubled several movements.
- Geometry jumps at least 100 px: 89; maximum 2,867 px. The original metric included expected
  viewport translation, so it was not itself a detector-jitter verdict.
- Visual alignment: within 2 px 0.6902; within 5 px 0.7357; residual p95 67.609 px.

## Verdict and disposition

The trace exposed a misleading aggregate: total displacement parity hid temporal bounce caused by
lumped `alternatingDebt`. Pass16 remains a diagnostic baseline, not an accepted user experience.

Re-entry rule: never judge scroll correctness from total raw/applied displacement alone.
