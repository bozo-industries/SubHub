# Pass 49: sustained GPU trial versus software

Status: promising first same-APK pair; repetition/video/Pixel acceptance outstanding.

Commit 471c028 adds worker-confined GpuPreparationHealth. Target remains 48 ms; three
consecutive overruns disable the experiment, any failure/invalid duration/>96 ms disables
immediately, successful in-budget calls reset the streak, disabled is terminal. Two unit
tests cover boundaries/recovery/terminal behavior. Full unit/lint/paired builds passed.
This admission policy does not redefine final performance acceptance.

## Controlled pair

Same APK SHA256 FAE9984ECCC9B05CE3A25E4F01B3F3419DEF5FA01501408C14DCCACA19887D44;
same ten gestures and page-top restoration. GPU first, software second; sequential order
and host variability remain confounds. Each mode restarted with its explicit preference.
Artifacts: app/build/reports/device/pass49-gpu-policy and pass49-software-control.

| Metric | GPU | Software |
| --- | ---: | ---: |
| Marker duration seconds | 11.400 | 11.502 |
| Fast publications | 31 | 27 |
| Capture age median/p95 ms | 164 / 273 | 234 / 386.8 |
| Native runtime median/p95 ms | 50 / 109.5 | 57 / 123.8 |
| Actual draw latency median/p95 ms | 6.5 / 41.5 | 8.5 / 65.55 |
| Engine preprocessing median ms | 3 | 3 |
| Postprocessing median ms | 1 | 1 |
| Publish interval median ms | 346 | 347 |
| Per-publish dropped counter | 0 | 0 |

GPU_PREPARE remained active through the workload and final settle (36 records in collected
trace, including boundary-adjacent samples); final six calls 8-10 ms. No longer a partial
GPU run followed by software fallback. About 30% lower capture-age median/p95 is evidence
from this pair only, not yet a robust performance claim. Independent real-image corpus
compatibility was established earlier, but live optical coverage/alignment were not measured
in these trace-only runs. No Pixel result.

Preference currently OFF after software control. Both collectors completed. Next: reverse-order
repeat with host video, quantify live overlay alignment/coverage and check first-publication
latency around warm-up. Preserve same APK and workload. If repeatable, retain as candidate
and prepare signed Pixel evaluation rather than claiming emulator acceptance as completion.
Goal active; anchors OFF.
