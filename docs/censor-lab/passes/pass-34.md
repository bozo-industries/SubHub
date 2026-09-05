# Pass 34: anchor experiment and capture critical path

Status: inconclusive; experimental anchor presentation remains disabled. No new physical-Pixel candidate or acceptance claim.

## Why this pass is not a performance win

Same-worktree emulator runs with and without the anchor experiment show poor throughput even without recording. These are not calibrated Pixel comparisons. In `pass34-off-trace-only/trace.log`, five fast scenes committed in 11.739 seconds; median capture age was 1,124 ms and native inference 59 ms. No scene invalidation or queue-drop evidence explains the low cadence.

Capture-worker callback-to-scene gaps were 224, 598, 680, 661 and 530 ms. This interval includes hardware scaling and software readback, which previous telemetry did not separate. Three representative frames:

| Frame | Request to capture | Callback log to scene | Scene to fast ready | Ready to commit |
| --- | ---: | ---: | ---: | ---: |
| 435 | 168 ms | 224 ms | 41 ms | 27 ms |
| 438 | 328 ms | 661 ms | 102 ms | 34 ms |
| 439 | 456 ms | 530 ms | 92 ms | 49 ms |

Quality preparation started after these pre-scene gaps, so quality overlap is not required to explain them. A separate frame had a 399 ms main-thread commit delay. Request-epoch/failure timing also suggests a roughly 5.15-second in-flight screenshot request, but absence of request-ID spans prevents definitive attribution.

## Recording validity

`pass34-nvenc-off/alignment.mp4` and `pass34-nvenc-on/alignment.mp4` each decode 599 frames in 20 seconds, at 416 x 928. Nominal 30 fps is misleading: 527/598 OFF and 514/598 ON transitions were near-identical at a 0.05 mean grayscale difference threshold. Only 62 and 78 transitions respectively exceeded 0.5. Slow motion advances in roughly 100–400+ ms steps.

These recordings demonstrate gross drift/freezes, not frame-level smoothness. ON was worse on measured slow-scroll attachment and better on jitter, with different warm/cache/content state. Neither result justifies promotion or causal attribution to the anchor experiment.

## Instrumentation checkpoint

Debug-only `CAPTURE_SPAN` records link accepted request, dispatch, callback success/failure, prepare start/end, scene creation and callback exit using request uptime as ID. `CAPTURE_PREPARE` separates scaling and hardware-to-software readback in microseconds and records dimensions, with no pixels or text. Timings preserve preparation behavior and bitmap ownership.

Validation: full 456 JVM tests, lintDebug and paired target/test APK build passed; two dedicated emulator timing/ownership instrumentation tests passed. Installed emulator debug APK SHA256: `4213BEEBE461BC95D5EC785B77EE9842F9CAA46EED376BE3D8DEB188EF721A58`. This APK contains the existing dirty worktree as well as this narrow instrumentation checkpoint; it is not equivalent to clean commit contents.

The execution guard rejected subsequent emulator navigation/rebind commands. No new live critical-path run is claimed. Next: obtain a recorder-free trace with the new spans, identify platform capture versus scaling/readback delay, then optimize that measured stage. Do not infer improved censorship from green instrumentation tests.
