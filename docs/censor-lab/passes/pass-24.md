# Pass 24 — fast-first startup and next architecture vector

- Status: `partial-success`
- Baseline: Pass22a/Pass22d; Pass23 is an unvalidated comparison candidate
- Device gate: deferred until the Pixel returns

## Locked changes

- Initialize the 320 px fast detector before optional quality refinement.
- Start capture without waiting for quality-provider/session construction.
- Warm quality only after the first fast overlay is visibly published.
- Keep Ultra fast-frame preparation at 320 px even while quality is still warming.
- Preserve epoch, motion-generation, and fast-submission commit fences.
- Never retire coverage from detector omission; keep the asynchronous 2.5 s hard cap.

## Primary hypothesis

Separate executors did not isolate the CPU/NNAPI resource. A fast-priority execution gate should
make the stronger invariant—fast and quality detector execution never overlap—while a bounded
stable-scene window gives quality enough slack to finish without starving fast work. Pass24 does
not change identity thresholds or renderer-motion constants.

## Exact vector

- Fast demand is registered before its frame enters the latest-only queue.
- Quality never waits for the inference resource and cannot barge ahead of registered fast work.
- An admitted quality run owns one execution lease; a fast arrival cancels it and then acquires the
  same lease before running.
- Quality is admitted only when the second platform-safe capture deadline provides enough budget.
- A stationary scene may yield one capture while quality is active. The window expires at 300 ms;
  motion or a required settled fast frame prevents the yield and preempts quality.
- Native begin/end intervals, fast wait, quality-gate rejection, startup ordering, and stable-window
  events are traceable and parsed by the analyzer.

## Offline gates

- Fast detector can publish while the quality detector is absent or initializing.
- Quality initialization cannot mutate or close the active fast engine.
- No quality cache/tracker commit after a newer fast submission or motion generation.
- Cancellation and executor-shutdown races recycle every retained bitmap/hardware buffer.
- Full unit, lint, debug APK, Android-test APK, and focused emulator instrumentation pass.

Current offline evidence: 264 unit tests, lint, APK assembly, 13 existing focused Android tests,
and the real-ORT inference-gate Android test pass on x86_64. The gate test observed a cancelled
quality native run ending before the next fast native run began. This is lifecycle evidence only;
the Pixel driver and visual gate remain outstanding.

Frozen Pixel arm64 candidate: `SubHub-pass24-pixel-arm64-signed.apk`, SHA-256
`8AC4E6BBA5BD472439CA32734137117E2CA87D30EBDC421B03C1C0B96F701925`.

## Pixel eligibility

Only a frozen, hashed APK that clears the offline gates may enter the canonical Emiru replay. Record
capture age, runtime, publish interval, quality overlap/preemption, geometry churn, text stability,
and the user's explicit speed/smoothness/coverage verdict against the rollback anchor.

## Pixel verdict

The user compared the frozen Pass24 arm64 APK with the Pass22a rollback anchor and confirmed less
disruption, less freezing, and fewer one-frame disappearances. Those scheduling and cache-safety
changes are retained.

Pass24 is not a release-quality motion solution. Censors born during a scroll were consistently
misaligned, like the existing in-transit tracks. This rejects further smoothing-constant tuning as
the primary vector: asynchronous detections must be placed using an explicitly owned capture and
viewport phase, or rendered conservatively while that phase is uncertain.
