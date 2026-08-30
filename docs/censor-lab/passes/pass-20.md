# Pass 20 — continuously streaming quality lane

- Status: `rejected`
- Baseline: Pass18/19 lineage
- Gate: 30.225 s smoke trace; no canonical device gate

## Hypothesis and vector

A second worker/session would let high-resolution quality inference run continuously without
blocking the 320 px real-time path.

## Objective evidence

- 87 fast publishes; 40 active; 86 quality-cache commits.
- Fast capture age p50/p95/max: 171/207.7/252 ms; runtime: 56/78.1/114 ms.
- Quality runtime p50/p95/max: 154/189/239 ms.
- 32/86 quality caches crossed source/cache motion generations; maximum reprojection 826 px.
- Three duplicate suppressions and four geometry jumps at least 100 px.

## Human verdict and disposition

The lane felt faster but worse and required major smoothing polish. The architecture allowed stale
cross-scroll quality and shared-resource contention.

Re-entry rule: a separate quality lane is eligible only when final commits are epoch,
motion-generation, and fast-submission safe, and an overlap/preemption benchmark shows the fast
lane does not pay for quality work.
