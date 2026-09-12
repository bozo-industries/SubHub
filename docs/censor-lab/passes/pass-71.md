# Pass 71: fresh sustained GPU preparation comparison

Status: existing opt-in latency benefit reproduced; no default change or display-alignment claim.

Same paired APKs verified in Pass 70, no source changes. Four ten-gesture trace-only runs used
the same Chrome page with page-top reset: software, GPU, GPU repeat, software repeat. Each mode
transition used the explicit instrumentation preference followed by target reinstall. GPU repeat
shared the already-warmed service. This is a sequential short comparison, not randomized device
acceptance; model/runtime variation remains a confound for end-to-end effect size.

## Bounded live results

All times in milliseconds. S1/G1/G2/S2 correspond to that run order.

| Metric | S1 | G1 | G2 | S2 |
| --- | ---: | ---: | ---: | ---: |
| Raw/parsed publications | 32/32 | 29/29 | 32/32 | 29/29 |
| Request-to-publication median / p95 | 166 / 262.35 | 138 / 193.2 | 112.5 / 232.95 | 152 / 267.4 |
| Capture preparation median / p95 | 43 / 88 | 14 / 51 | 11.5 / 27.25 | 47 / 74 |
| Explicit readback median | 40.595 | 4.408 | 2.954 | 40.031 |
| Detector preprocess / runtime / postprocess medians | 2.5 / 48 / 1 | 2 / 31 / 1 | 2 / 35.5 / 1 | 3 / 39 / 1 |
| Publication queue median / p95 | 2 / 34.5 | 4 / 34.6 | 2 / 29.25 | 1 / 24 |
| Motion input-to-draw p95 | 17.8 | 15.15 | 17.9 | 16.5 |
| Maximum cumulative inference drops | 0 | 0 | 0 | 0 |
| Completed callbacks / failed / boundary-partial requests | 31 / 1 / 1 | 29 / 1 / 0 | 32 / 1 / 0 | 29 / 2 / 1 |
| Text publications / empty publications | 5 / 5 | 6 / 5 | 5 / 5 | 6 / 5 |

Capture-span parsing has zero malformed records in every run. Boundary-crossing publications
and callbacks explain S1's 32 versus 31 counts; they are not assumed one-to-one. GPU records
inside the gesture markers are 29/29 and 32/32 raw/parsed, all successful, maximum 69 and 58 ms.
The path remains active through both runs; software runs have zero GPU records. This is not
mixed fallback evidence. No consistent publication-count gain or optical/text-stability evidence.
Artifacts are the four ignored `app/build/reports/device/pass71-*` trace directories.

## Startup and compatibility

GPU-session first publication used software: request age 372 ms. Warm-up then took 261 ms;
the second publication had age 418 ms, followed by 118 ms for the third. The worker-owned
warm-up does not delay the first publication but can delay a subsequent pending capture.
Do not advertise faster first detection from these warmed measurements.

Five Android tests passed in 3.68 seconds: source ownership, alternating dimensions/alpha,
terminal close/thread confinement, synthetic pixel fidelity and private real-image detector
comparison. Seven baseline detections retained with seven matches and no unmatched additions;
negative image remained negative. Worst mean channel error across fixtures was below 0.361.
The corpus is small and does not establish general detection-quality equivalence.
Warmed real-image GPU medians 5.789 / 5.790 / 6.106 ms versus software 20.087 / 19.824 / 30.432 ms.
These isolated warmed costs exclude native inference and live contention.

The 574-test JVM suite, lint and paired build from Pass 70 apply to these unchanged source/APKs;
they were not rerun for this documentation-only checkpoint. GPU preference restored false and
read back independently; target reinstalled after terminal instrumentation. No Pixel access,
signed evaluation release, default change or deployment. Jolli search confirmed the original
post-first-publication warm-up and bounded-overrun decisions (6f05946, 471c028).

Next: use the source-frame map for actual cache correspondence, keeping unknown frames and
current-display prediction separate. The repeated GPU latency evidence is sufficient to retain
the existing opt-in candidate; another identical trace-only trial is not the next bottleneck.
