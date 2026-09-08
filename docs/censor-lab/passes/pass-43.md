# Pass 43: model-sized GPU readback probe

Status: promising isolated experiment; NOT production or detector-accuracy acceptance.

Jolli history confirmed single-readback work (86b5d65) removed redundant hardware round trips,
but the live path still transfers a full screenshot to software before resizing. This probe
uses public API29 HardwareRenderer/RenderNode to draw the source directly into a 144x320
ImageReader surface, then copies that small hardware buffer into software.

Official API contract: https://developer.android.com/reference/android/graphics/HardwareRenderer
and https://developer.android.com/reference/android/graphics/HardwareRenderer.FrameRenderRequest
All renderers share a render thread; sync/wait is acceptable for this isolated probe, not
automatic justification for a blocking production capture path.

## Evidence

- New instrumentation-only GpuReadbackProbeAndroidTest. No production/settings change.
- 1344x2992 sRGB synthetic gradient/checker source; two warmups, nine measured repetitions.
- Emulator-5554 GPU-host setup, 2026-09-08 log pid16018, 06:54:48 local.
- CPU preparation median 30.9821 ms; GPU model-sized render/readback median 7.4244 ms,
  maximum 13.2162 ms. GPU measurement includes display-list rerecord, sync and software copy.
- Worst RGB channel mean absolute error 0.32923/255 versus current software preparation;
  test requires less than 3. This is NOT an independent detector recall/precision gate.
- Instrumentation 1/1 passed; paired APK build and lintDebug passed. Production APK unchanged.

First probe exposed a test-harness issue: a retained unchanged scene may produce no new image
even with successful sync. Every trial now rerecords the display list. Use SdkSuppress for
instrumentation API restrictions, rather than RequiresApi alone. Resources are owned locally
and released; no source pixels are persisted by this probe.

## Next incomplete action

Run the exact GPU path on representative real screenshot corpus and compare detections at
the production rectangular shape, plus small text/edges and alpha/color-space behavior.
Then build a bounded, single-worker lifecycle adapter with fallback, no stale frame reuse,
no source bitmap release before render completion, and test cancellation/resize/teardown.
Measure live fast/quality contention and main/render-thread latency before enabling. This
could address the measured 50-65 ms readback bottleneck; synthetic speedup is not yet a
user-visible improvement or Pixel result.

Goal active. Anchors remain OFF. No recording or collector remains active.
