# Pass 61: normal-cadence shadow and observer-off control

Status: both emulator runs fail throughput/latency expectations. No motion promotion.

Built source `78582bc`, same application APK for both runs, Chrome foreground, ten-gesture
replay each. Initial shadow attempt failed publication readiness before gestures; waited for
current-process publication evidence and used a fresh run. Successful artifacts are local
`app/build/reports/device/pass61-row-shadow-ready` and `pass61-off-control`.
Observer flag restored false and target reinstall completed before the OFF run. Pixel untouched.

| Metric | Shadow ON | OFF control |
| --- | --- | --- |
| Parsed / raw overlay publications | 1 / 1 | 2 / 2 |
| Publication capture age median (ms) | 736 (one sample) | 1508.5 (two samples) |
| Published preprocessing / runtime / postprocessing median (ms) | 58 / 129 / 27 | 15.5 / 255.5 / 2 |
| Superseded scene queue drops | 8 | 10 |
| Input-to-draw median / p95 (ms) | 77 / 364.8 (5 draws) | 95 / 679.8 (10 draws) |
| Capture preparation median / p95 (ms) | 301 / 815.4 | 239.5 / 649.45 |
| Explicit readback median / p95 (ms) | 256.626 / 750.096 | 202.065 / 565.483 |
| Publication post-to-main median / p95 (ms) | 3699 / 5067.5 (11 pairs) | 1263 / 2214.4 (17 pairs) |

These tiny publication samples do not establish a relative speedup or stable distribution.
Both runs are badly delayed with the observer OFF as well as ON. Post-to-main queue latency
is a newly quantified bottleneck, much larger than the isolated matcher. Investigate main-thread
work/backlog before attributing all loss to capture or reducing detector quality.

Shadow parser matched 17/17 records, six accepted; mean observer CPU 11.003ms, max39.244ms;
wall mean23.647ms, max118ms. Actual sparse capture cadence does not reproduce the 400-hot-call
benchmark's sub-millisecond cost. Do not warm up on the capture path or claim startup is solved.
No optical ground truth was recorded, so accepted displacements are not validated camera motion.
ON had eight stale text scans and no text publication; stability/flash acceptance is unavailable.

The strict capture parser exposed 64/76 malformed records because it did not know fast-ready,
geometry-ready, publication-post, publication-main, and publication-tick. Added those exact
stages with a separate asynchronous order and paired timing fields, retaining unknown-stage
rejection and failure consistency checks. Ten parser tests pass, including reordered callback
versus worker timing and malformed/unknown publication fixtures. Reanalysis: zero malformed
records; ON17 completed callbacks/1 partial, OFF18 completed/2 partial/1 failure. Partials are
explicitly excluded, never treated as zero. Overlay parser raw/parsed parity was checked above.

No application source changed in this pass. Prior paired APK/JVM/lint verification remains
applicable; setup instrumentation passed on enable and disable. No signed device-evaluation
release or production deployment; no user verdict or goal completion is claimed.

Next: identify the main-thread work responsible for multi-second publication dispatch, with
observer OFF. Keep source-coordinate integration gated until normal-cadence cost and optical
motion controls pass. Capture, publication and visible stability all remain below acceptance.
