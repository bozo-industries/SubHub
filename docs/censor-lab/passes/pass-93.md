# Pass 93: continuous sparse-event scroll reconstruction

## Request and active-path evidence

After Pass 92 the user reported that scroll movement remains much too jumpy and
asked to prioritize smoothness. The signed release configuration uses sparse
Accessibility scroll events: `startExperimentalAnchorSampler` is DEBUG-gated.
That sampler's expiry path is therefore not the active candidate's motion source.

The old event estimator applied up to 56 pixels immediately, consumed remaining
measurement error in 16 ms, and continued only a fraction of the inter-event travel.
This produced recurring correction bursts followed by slow movement. Existing tests
explicitly required the 56-pixel jump; those assertions were not smoothness evidence.
Jolli's earlier `520c7f7`, `17e9721` and `6fc75aa` motion work informed this review;
the current code and numeric replay, not the historical summary, establish the defect.

## New trajectory

- Event camera authority remains exact and separate from display-only reconstruction.
- Continuing measurements preserve displayed position and velocity. A critically
  damped correction reconciles phase error while an observed-velocity forecast
  carries motion between callbacks. Source timestamps estimate velocity; rendering
  does not retroactively begin a newly delivered animation in the past.
- First observations, reacquisition after a long gap and corrections exceeding half
  the viewport remain immediate. There is no preceding reliable motion to reconstruct
  in those cases. This is not a promise that every possible scroll is jump-free.
  A lone event gets only a quarter-delta forecast; the second observation establishes
  a stream. Acquisition correction is faster than steady-state correction so this
  conservative first forecast does not prolong known measurement lag.
- Sharp deceleration and reversal stop forecasting and use a monotone 32 ms brake.
  The previous one-frame snap requirement is replaced by continuous two-frame braking.
- Forecast displacement remains capped at 18% of the viewport and 1.25 times the
  observed delta (a quarter delta on acquisition). Reaching that cap does not
  initiate a backwards return before the
  expected next observation plus 24 ms delivery tolerance. Missing input eventually
  returns to authoritative position rather than accumulating drift.
- Capture, inference, immediate quality delivery, cache policy and body grouping
  are unchanged. Measured-anchor, non-authoritative correction and coordinate reset
  paths retain their separate policies.

## Synthetic comparison, not device acceptance

Baseline is the committed `ViewportMotion` at `216a227`; candidate is the new event
trajectory. Replay uses 80/120/160 ms event intervals, 0.5/2/4 pixels per millisecond,
8/16 ms render samples, and either no callback delay or repeating 0/20/4/16/8 ms
delays. The first 600 ms are excluded from steady-state metrics. Both implementations
receive identical inputs. The candidate unit fixture enforces no recurring mutation
jumps, no backwards frames, bounded frame bursts and mean error below 20 ms of travel
across all 36 combinations. Startup, stopping and reversal have separate fixtures.

Representative 120 ms events, 2 px/ms movement, delivery jitter:

| Numeric replay metric | Previous | Candidate |
| --- | ---: | ---: |
| Maximum immediate measurement jump | 56 px | 0 px |
| Maximum 8 ms sampled movement | 119.513 px | 28.531 px |
| Mean tracking error, 8 ms samples | 112.378 px | 17.360 px |
| Maximum 16 ms sampled movement | 194.524 px | 53.120 px |
| Mean tracking error, 16 ms samples | 112.220 px | 17.375 px |

With steady delivery the candidate follows that constant-velocity case at 16 px per
8 ms sample after warmup. At 4 px/ms with 160 ms observations it still hits the lead
cap: maximum 8 ms movement is 72.957 px with jitter, and mean error is 57.759 px.
That limitation is explicit; the change does not create new high-rate measurements.
Prediction after an unreported stop also remains a real-device validation concern.

The old implementation failed the new smoothness fixture before the change. Existing
reset, source freshness, polling handoff, coverage response, bounded forecast, reversal
and drift checks pass after updating assertions tied specifically to the removed snap.

## Passive frame evidence

The existing `render-layout` dump now contains a versioned `motion` object with up to
256 render evaluations: time, displayed X/Y, event-authoritative X/Y and input sequence.
Sampling uses fixed primitive arrays with no per-frame allocation or log writes.
JSON is generated only for an explicit dump. Total/retained counts, truncation and
rejected-frame counts are explicit; fixtures round-trip and check ring chronology.
Clearing content clears this history. No page text, images or new control endpoint.

These timestamps are render-evaluation uptime, not measured photon presentation.
They can reveal frame gaps and trajectory discontinuities, not independently prove
alignment with the underlying app. A zero/old/empty ring is missing evidence.

## Validation status

Exact staged source tree `453014a27f61e2a1d900399cd6637ed7f61686b5`, exported without
unrelated dirty experiments, passed 664 tests, lintDebug, assembleDebug and
assembleDebugAndroidTest in one Gradle invocation with immediate quality enabled.
No instrumentation was run on the Pixel. Signed candidate verification follows
before installation. Pixel was Dozing during implementation;
there is no new controlled on-device capture-age, queue-drop, preprocessing,
inference, postprocessing or publication-latency population yet. Those metrics remain
instrumented but must be remeasured after activation. User perception of scroll
smoothness, stopping/reversal behavior, text stability and flashes remains required.

## Signed Pixel update

Private signing run `35451337405` succeeded for exact source
`a5cf1d4979d3dc8eb6f28e79d68686cd0dd31eb2`, immediate quality enabled, no public
release. Compact artifact `10586836479` was downloaded; provenance, checksum and
the expected compatible signing certificate were verified. ARM64 APK SHA-256:
`0f7687e5572342690b0be59ff9947eb89d7011b2173644e785e1b3bdb1d716b4`.

Replacement installation succeeded without clearing app data. Independent installed
`base.apk` readback matched that hash, and the Accessibility service rebound (PID
18753 at verification). The phone subsequently reported Awake, but recognition
reported `armed=false`, and the layout dump remained `active:false`. No app-mode
settings were changed by the verification. Enable App Mode and use a protected app
before collecting live scroll samples; this inactive interval proves no visual or
performance outcome. Source/installation evidence is not real-device acceptance.
