# Pass 17 — preserve authoritative scroll timing

- Status: `accepted`
- Baseline: Pass16
- Gate: Pixel 8 Pro, Emiru Google Images, Accessibility mode

## Hypothesis and vector

Removing lumped alternating-debt reconciliation would preserve the timing of authoritative
Accessibility deltas and eliminate visible bounce without losing net displacement.

## Objective evidence

- 113.947 s; 325 fast publishes; 146 active-scroll publishes.
- Capture age p50/p95/max: 137/179.8/218 ms.
- Runtime p50/p95/max: 43/58/143 ms.
- Scroll raw/applied ratio: 1.0; amplified events: 0; adjusted pixels: 0.
- Geometry jumps at least 100 px fell from 89 to 26; maximum fell from 2,867 to 216 px.
- A separate display-phase problem remained: viewport-lead maximum 5,351 px during a fling.

## Human verdict and disposition

The pass was visibly faster and less behind. Keep the no-amplification rule. It did not solve
mid-transit alignment or smoothness, which became separate hypotheses.

Re-entry rule: do not restore suppressed-event debt or lump it into a later event.
