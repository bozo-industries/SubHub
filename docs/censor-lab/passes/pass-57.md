# Pass 57: clean-source shadow telemetry

Status: observer integration verified; performance/accuracy acceptance NOT established.

Commit abbe676 adds capture epoch and source-size fences, extracts descriptors from already-
prepared software pixels behind row_motion_experiment, and emits ROW_MOTION numeric records.
No render/tracker/cache authority. Capture-phase uncertainty clears the observer baseline.
Strict standalone parser and two fixtures expose malformed/unknown records as incomplete.
Full unit/lint/paired APK builds passed. Pixel not accessed.

An initial replay attempt started before the install wrapper completed and failed readiness
without gestures. Waited for that exact wrapper to terminate, confirmed the Chrome image page,
then ran fresh pass57-row-shadow-ready. Do not start dependent commands while an install command
still has a live session, even if earlier output says the setup instrumentation finished.

## Result

- 24/24 shadow records parsed, seven accepted (including stationary samples).
- Accepted examples: -147.26,-14.03,0,+98.18,0,0,-112.2 source pixels.
- Wall cost average9.79ms, maximum61ms; not an isolated CPU benchmark.
- Whole run had only6 fast publications, capture-age median868.5/p951018.75ms, native runtime
  median205.5ms, actual draw median503ms. This is globally abnormal relative to earlier controls;
  cannot attribute it to a few milliseconds of observer arithmetic without a current OFF run.
- dumpsys cpuinfo returned an older reporting interval/process, so it does not establish current
  contention causality. Do not use cumulative host process CPU as instantaneous utilization.

Shadow flag restored false and reinstall completed successfully. Collector finished. GPU/anchors
remain OFF. Next: current software-only baseline, inspect host/emulator load and separate observer
thread CPU time from wall wait before optimizing or enabling anything. Then validate shadow
measurements against synchronized optical evidence, including static/video/reflow controls.
No live box correction or Pixel acceptance; goal active. Weekly remaining13% at start; pause5%.
