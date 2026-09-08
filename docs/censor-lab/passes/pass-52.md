# Pass 52: one physical Pixel GPU compatibility probe

User connected the Pixel and explicitly requested ONE test, then release the phone. Superseded
the planned clock/viewport recording. No additional physical-device testing after completion.

Device38121FDJG00GCN, Pixel8Pro/husky, API37. Built target/test APKs together, signed both with
the existing local release key (credentials only in temporary environment, cleared), installed
with -r preserving app data, then ran exactly:

GpuReadbackProbeAndroidTest#compareSmallGpuReadbackWithSoftwareReference

Adjusted the synthetic probe to use the actual GpuBitmapPreparer adapter rather than a separate
inline renderer. No live GPU flag enablement. Artifacts (not committed):
app/build/reports/device/pass52-pixel-gpu/{target.apk,test.apk,result.txt}.

## Result

One instrumentation test, passed, reported duration0.997s. Pixel log at07:43:11 local, pid24092:

- CPU preparation median48.317993ms.
- GPU preparation median4.691650ms, maximum9.218221ms.
- Worst RGB channel mean absolute error0.334411/255 against software reference.

This validates warmed preparation compatibility on the physical Pixel, not whole-app latency,
detector recall, scrolling alignment, cold-start or long-run contention. Synthetic source is
1344x2992; two warmups/nine measurements. Do not turn this into a claim of10x app speed.

User informed immediately that phone is free; no subsequent ADB queries, gestures, recordings
or tests. Current signed candidate target was installed; GPU remains opt-in/default OFF.
Goal remains active. Continue emulator/local work; do not reuse phone without a new user grant.
The uncommitted SCROLL_SOURCE_BOUNDS observer from planned Pass52 remains local and untested
in its intended synchronized recording; do not claim the browser-controls hypothesis verified.
