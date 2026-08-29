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

### Mandatory Emiru Google Images gate

No censoring build is release-ready until it passes this gate in Accessibility/App Mode on the
physical Pixel. Assign Chrome to protection, open Google Images for `Emiru`, enable face detection
and the complete available detection-filter set, and keep the result grid visible. Record the
device model, Android and Chrome versions, query time, app build, battery temperature, and thermal
status because Google results and device conditions are not fixed fixtures.

Exercise the same result grid in this order while collecting one uninterrupted diagnostics trace:

1. Open or refresh the grid and observe first coverage from rest.
2. Scroll slowly and continuously in one direction.
3. Perform ordinary fast flings, then let the page settle completely.
4. Reverse direction after a pause.
5. Perform several rapid, short up/down jitter movements.
6. Continue mixed browsing for at least 60 seconds to expose warm-device and sustained-load
   behavior.

Repeat the visual pass for every censor appearance. This includes compositor-only appearances and
source-frame appearances such as blur, pixelation, TV static, and glitch; a smooth solid overlay
does not prove that a source-frame effect is smooth.

The gate requires both an objective trace pass and an explicit human visual pass. It fails if any
of the following is visible:

- a face repeatedly escapes coverage while remaining clearly visible;
- an overlay freezes and then catches up in a single jump during an ordinary scroll;
- an overlay overshoots, reverses late, or keeps moving after the page has settled;
- a false-positive face or text box flashes for a single observation;
- a departed box, empty text bar, or other ghost survives the post-scroll settle;
- rapid short direction changes cause track identity swaps or large unrelated jumps;
- any appearance materially stalls scrolling compared with the uncensored page.

The accompanying trace must report capture-age, preprocess, inference-runtime, postprocess, and
publish-interval p50/p95/max values; dropped/stale frames; scroll raw/applied displacement and
amplification; track creation, identity changes, and geometry jumps; Accessibility text scans,
acceptance/discard reasons, and expiry; CPU/thermal state; and separate active-scroll and settled
segments. A run with a stale-frame queue, unexplained 100-pixel-or-larger geometry jumps, or a
post-scroll overlay that has not converged by the next authoritative observation fails even if its
aggregate averages look acceptable.

Metrics are compared with the last accepted Pixel baseline, but numbers alone cannot waive a
visible regression. The tester's smoothness verdict is a required release artifact alongside the
trace.

The iOS feature source is useful for browser, Safari-extension, and offline export comparison, but
its owned rendering surfaces are not a system-wide Android tracking benchmark.
