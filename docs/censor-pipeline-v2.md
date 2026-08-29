# Censor pipeline V2

The live censor is a display-rate scene renderer fed by slower, asynchronous observations. It no
longer treats detector completion as a render frame.

## Runtime shape

1. Accessibility remains the frictionless default capture mode. MediaProjection is an optional
   high-throughput session because stock Android requires user consent for every new projection
   session; Hardcore mode cannot silently grant or automate that platform dialog.
2. Capture and inference run independently. Each inference worker consumes the newest available
   frame, and stale queued frames are released immediately instead of adding latency.
3. Detector, Accessibility, OCR, and future exact-geometry observations enter one source-aware
   contract. Text observations include geometry quality and stable semantic anchors.
4. The tracker applies asymmetric evidence rules: high-confidence regions cover immediately;
   borderline regions need a second spatially or semantically consistent observation.
5. Immutable track snapshots are published to the overlay. Choreographer advances motion every
   vsync with time-normalized velocity and a bounded extrapolation horizon.
6. Sparse scroll events drive a viewport transform between observations and settle back to the
   authoritative event position. The transform cannot accumulate permanent drift.
7. On Android 10+, ordinary solid censors are cached RenderNode display lists, leaving position
   changes to the hardware compositor. The Canvas renderer remains the API 26 fallback and handles
   source-frame effects.

## Text stability

Text lines are independent anchored regions. Fusion may select a more exact rectangle (for
example OCR over an estimated Accessibility node), but it never grows an overlap-connected
component. A temporary bridge rectangle therefore cannot merge neighboring lines into a large
one-frame block.

## Hardware and load policy

- ONNX Runtime benchmarks NNAPI, XNNPACK, and CPU on each device/model/configuration identity and
  caches the fastest compatible provider.
- Model input and output use reusable contiguous buffers; ordinary MediaProjection bitmaps come
  from a bounded pool.
- Android 12+ receives Dynamic Performance Framework target and actual-duration hints for the
  inference thread.
- Normal capture remains full speed. Cadence backs off only when Android reports power-save or
  moderate-or-worse thermal pressure.

## Diagnostics and acceptance

Diagnostics expose preprocess, runtime, and postprocess time; capture-to-publish age; processed
frames; and stale frames dropped by the latest-only broker. Automated coverage includes
display-rate motion replay, scroll convergence, false-hit hysteresis, anchored text identity,
non-coalescing text lines, tensor-buffer decoding, and Android renderer/text instrumentation.

Real-device release acceptance should record a representative fast-scroll trace and verify:

- no borderline one-frame censor flashes;
- no text bridge changes the shape of adjacent bars;
- display motion remains smooth between detector observations;
- capture-to-publish age stays bounded under sustained load;
- thermal backoff recovers without a stale-frame queue.

The iOS feature source is useful for browser, Safari-extension, and offline export comparison, but
its owned rendering surfaces are not a system-wide Android tracking benchmark.
