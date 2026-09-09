# Pass 60: isolate and reduce row-observer CPU cost

Status: arithmetic optimization verified on emulator only; shadow observer remains OFF.

Important follow-up: the 20-iteration timings below were not steady-state measurements.
With 400 warmup iterations the current observer measured 0.190ms median thread CPU. Preserve
the older results as startup/warmup evidence, not as a steady-state cost or proven speedup.

The previous clean-source shadow/control runs showed a generally slow emulator. An isolated
instrumentation benchmark now distinguishes observer thread CPU from elapsed wall time. It
uses 144x320 prepared pixels, alternating known +/-10px translations, 20 warmup iterations
and 60 measured iterations, asserting acceptance and exact displacement every time. It does
not measure screenshot capture, inference, rendering, or live scrolling.

| Implementation | CPU median / p95 (ms) | Wall median / p95 (ms) |
| --- | --- | --- |
| Initial benchmark | 37.905 / 65.599 | 43.008 / 98.242 |
| Cache row references, unroll four columns (`9e73d17`) | 24.525 / 38.419 | 32.538 / 51.786 |
| Abandon noncompetitive candidates (`5803b32`) | 19.222 / 30.990 | 22.906 / 33.345 |

Latest maxima: CPU 39.802ms, wall 60.900ms. These are sequential single-run emulator results,
not randomized paired measurements or physical-device performance claims. The roughly 49%
lower median CPU cost is promising, but the remaining cost is still too high to grant this
observer presentation authority or enable it by default.

The estimator scores stationary fully first. Other shifts stop once their accumulated error,
divided by the complete sample count, exceeds both the current best and its ambiguity margin.
That partial score is a lower bound: it cannot conceal a better candidate or a competitor
inside the rejection margin. Stationary takes exact ties. No acceptance threshold is relaxed.

Checks: 528 JVM tests, zero failures; lintDebug; application and instrumentation APKs built
together; emulator-5554 instrumentation 1/1 passed with the declared SubHubTestRunner.
Before the final build, 1000 deterministic old/new comparisons retained acceptance and dy
across translated, locally animated, and narrow-column pairs. Saved descriptor transitions
also retained their prior results. These checks are not a proof across every possible input.

Follow-up persistent regression coverage now tests all 47 interior shifts (-23 through +23)
against 20 independent textures with bounded pixel-descriptor noise (940 pairs), asserting
exact accepted displacement, plus 100 unrelated textured pairs that must be rejected.
The expanded suite passes 530 JVM tests and lintDebug. This is deterministic correctness
coverage, not additional capture-age, queue-drop, inference, publication, or stability telemetry;
those pipeline metrics were not remeasured in this arithmetic-only pass.

Stage-probe follow-up (same production source, emulator only): descriptor CPU median/p95
2.874/8.396ms, estimator 14.080/25.661ms. Descriptor timing includes reflective invocation
overhead to avoid widening production visibility; estimator timing calls the real matcher
directly. Both assert the known alternating displacement. The two instrumentation tests pass.
The unchanged full-observer rerun measured CPU median/p95 13.878/20.834ms and wall
14.751/25.087ms. This substantial run-to-run variation limits precise speedup claims. Stage
medians are from a separate loop and must not be summed to reconstruct the full-observer
median. Matching, not descriptor extraction, is the dominant cost in this probe; avoid trading
away descriptor coverage merely to optimize the smaller stage. Full JVM/lint and paired builds
remain green. No new end-to-end performance claim follows from these measurements.

Only emulator-5554 was installed or tested. Pixel access was not resumed. Existing experimental
GPU/anchor/row-motion switches stay OFF, and no release or production deployment occurred.

Next: require broader stationary/animation/scroll controls, a repeatable CPU budget, and explicit
capture-coordinate integration before any render authority. Real-device product acceptance
remains outstanding; this pass does not resolve early-scroll alignment or visible flashing.

## Candidate ordering and warmup correction

`fff4a6c` visits shifts in increasing absolute magnitude, still evaluating every candidate and
preserving stationary-first then lowest-shift tie order. Full JVM/lint/paired builds passed;
1000 deterministic baseline comparisons retained acceptance and dy; instrumentation 2/2 passed.
Its initial short-warmup full-observer CPU median/p95 was 13.383/21.420ms, while the later stage
probe reported descriptor 0.048/1.361ms and estimator 1.061/5.241ms. The unchanged descriptor's
large reduction invalidates attribution of all timing differences to algorithm changes.

Increasing warmup from 20 to 400 iterations, with 60 measured iterations and correctness
assertions on every call, produced:

- Full observer CPU median/p95/max: 0.190/0.294/0.401ms.
- Full observer wall median/p95/max: 0.232/4.777/9.938ms.
- Descriptor CPU median/p95 (includes reflection): 0.037/0.049ms.
- Estimator CPU median/p95: 0.145/0.800ms.

Both tests passed again. This is consistent with runtime warmup/compilation effects; compilation
events were not captured, so the mechanism is an inference. Four hundred iterations are not a
general guarantee of convergence. Future comparisons must report startup separately, verify
stability across later measurement windows and fresh processes, and compare identical warmup
protocols. Do not prescribe 400 hot calls on the capture path or ignore startup cost: real
screenshot cadence is far lower than this benchmark and first-detection latency still matters.
No old-version long-warmup baseline was measured, so no steady-state optimization percentage
is established. The next useful gate is clean-source shadow behavior with complete pipeline
telemetry, not further optimization justified by the superseded multi-millisecond median.
