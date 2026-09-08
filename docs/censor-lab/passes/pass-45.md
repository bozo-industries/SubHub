# Pass 45: reject per-request GPU renderer lifetime

Status: correctness tests passed; performance experiment REJECTED for live enablement.

Implemented GpuBitmapPreparer as an experimental, synchronous, per-request renderer. It borrows
the source, handles only hardware sRGB and model edges <=512, returns owned software pixels,
releases renderer/image/buffer resources, and returns null for unsupported sources or runtime
failure. No hard wall-time bound is claimed for native synchronization.

The dirty service has a debug-only opt-in gpu_preparation_experiment flag plus fallback and
one-session circuit after failure or >48 ms; it remains OFF. That integration is NOT staged
or enabled. Do not enable it simply because correctness tests pass.

## Tests / evidence

Full unit/lint/paired builds passed. Emulator instrumentation: 4/4 passed, including alternating
portrait/landscape frames and translucent colors, no stale pixels, source ownership, software/
null/recycled fallback, and the same three-source detector corpus. Seven positive detections
still match at >=0.75 IoU with no extras; negative control remains empty.

But unlike Pass44's reused renderer, the per-request candidate is not a performance win:

| Corpus source | CPU median ms | Candidate median ms | Candidate max ms |
| --- | ---: | ---: | ---: |
| Emiru all | 20.5949 | 24.7415 | 39.0148 |
| Emiru images | 19.2387 | 21.4831 | 1166.1624 |
| Instagram control | 29.1030 | 27.3436 | 32.8643 |

Emulator log pid16611, 2026-09-08 07:02:38-40 local. Synthetic reused-renderer control in
the same instrumentation run was 5.9179 ms median. This implicates renderer lifetime/churn,
not image fidelity. No live trace or Pixel claim. Instrumentation completed.

## Next incomplete action

Refactor to one worker-owned renderer/reader per output size, explicitly discard the recorded
source after completed render, drain owned output per request, and invalidate on failures.
Provide an orderly close queued behind in-flight capture rather than concurrent main-thread
destruction. Test resize, repeated calls, alpha/fallback and source release. Rerun the exact
production adapter through corpus timing before any live flag enablement. Keep software as
default, anchors OFF, goal active. Weekly quota remaining at start: 47 percent.
