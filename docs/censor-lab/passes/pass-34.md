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

Debug-only `CAPTURE_SPAN` records link accepted request, dispatch, callback success/failure, prepare start/end, scene creation and callback exit using request uptime as ID. `CAPTURE_PREPARE` measures the scaling API call and explicit readback in microseconds and records dimensions, with no pixels or text. The scaling API may itself include hidden readback/upload; these are call-level timings, not isolated GPU-stage timings. Timings preserve preparation behavior and bitmap ownership.

Validation: full 456 JVM tests, lintDebug and paired target/test APK build passed; two dedicated emulator timing/ownership instrumentation tests passed. Installed emulator debug APK SHA256: `4213BEEBE461BC95D5EC785B77EE9842F9CAA46EED376BE3D8DEB188EF721A58`. This APK contains the existing dirty worktree as well as this narrow instrumentation checkpoint; it is not equivalent to clean commit contents.

The execution guard rejected subsequent emulator navigation/rebind commands. No new live critical-path run is claimed. Next: obtain a recorder-free trace with the new spans, identify platform capture versus scaling/readback delay, then optimize that measured stage. Do not infer improved censorship from green instrumentation tests.

## Single-readback optimization checkpoint

Inspection of [AOSP Bitmap implementation](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/graphics/java/android/graphics/Bitmap.java) corrected a false assumption in the preparer comments. `createScaledBitmap` delegates to `createBitmap`; hardware input is copied to software before drawing, and the scaled result is uploaded back to hardware. Our subsequent ARGB copy adds another readback. This is not a guaranteed GPU-downscale-before-readback path.

The production full-frame preparer now explicitly reads ordinary sRGB hardware input once, scales in software with the same filter, and immediately recycles the full-size temporary. Non-sRGB input keeps the prior conversion order. Output size, retained-source metadata and detector inputs are unchanged. This removes redundant transfers, not the unavoidable full-resolution initial readback. Tiled quality preparation is not changed in this checkpoint.

Recorder-free emulator instrumentation compared production against a frozen prior round-trip implementation on a patterned 1344 x 2992 source. Three warmups, nine measured samples per resolution, alternating execution order; every pair required exact bitmap equality. Final run:

| Long edge | Prior median / max | Single-readback median / max |
| --- | ---: | ---: |
| 320 | 136.829 / 147.472 ms | 30.007 / 31.254 ms |
| 512 | 147.712 / 190.365 ms | 32.901 / 38.042 ms |

Two earlier runs showed the same direction (roughly 139–145 ms versus 31–32 ms). These are synthetic preparation measurements, not live screenshot, detection-throughput or Pixel evidence. Five Android tests passed, including exact alpha/sRGB/Display-P3 parity and unscaled hardware ownership; 456 JVM tests, lint and paired APK build passed. Emulator artifact SHA256: `626653F70CCCEC42F42B1EB12C08DF2897473A491E5CAE22240864CD6831E7C2` (dirty-worktree build, not clean commit identity). Physical phone unchanged. Live capture age, publication latency, drops and scroll stability still need measurement before acceptance.

## Region-readback follow-up

Applied the same single-readback path to the coordinate-based region preparer. The existing working-tree quality-tile adapter delegates to it; the narrow checkpoint commits the generic preparer without bundling the older uncommitted tile-planning/service work. Source bounds are validated without integer addition overflow, crop dimensions remain the detection coordinate space, and wide-gamut conversion remains unchanged.

Recorder-free emulator test, three warmups and nine alternating-order samples per orientation, exact output equality on every pair:

| Source / crop | Prior median / max | Single-readback median / max |
| --- | ---: | ---: |
| 1344 x 2992 / 1344 x 2453 | 146.030 / 231.087 ms | 29.451 / 35.021 ms |
| 2992 x 1344 / 2453 x 1344 | 142.882 / 154.379 ms | 31.046 / 34.090 ms |

Three region Android tests passed (benchmark/parity, alpha/P3 and invalid/overflowing bounds), plus 456 JVM tests, lint and paired APK build. New emulator artifact SHA256: `7298899DF6E2BEAEBA0501B666A6C4DA3FAEF9501489CC55A52A9E193577AC45`. No navigation, live capture, phone install or end-to-end improvement claim. This demonstrates removable preparation cost, not solved scroll alignment.
