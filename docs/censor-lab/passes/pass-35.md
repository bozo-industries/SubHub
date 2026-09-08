# Pass35 — live preparation validation and missing quality

Status: diagnostic, not accepted. No physical-Pixel validation. Emulator debug APK SHA256 `7298899DF6E2BEAEBA0501B666A6C4DA3FAEF9501489CC55A52A9E193577AC45`, anchor experiment disabled. Existing GPU Pixel AVD: 1344x2992,480dpi,60Hz,6 cores,8GB,RTX3060 host renderer,High process priority. Hardware keyboard enabled to permit supported keyboard-only control; no permission/security changes. Windows screenshots remain unavailable; Android-only PNG observation plus supported keyboard input opened Chrome and the Emiru image page autonomously.

## Trace scope

`app/build/reports/device/pass35-keyboard-live/trace.log`, unique PASS35_KEYBOARD_BEGIN/END markers and acknowledged collector readiness,485.230 seconds. Mostly idle browsing with only three keyboard scroll sessions (12 events,7 nonzero authoritative inputs); **not a canonical touch/fling/jitter test**. Occasional setup PNG capture also occurred during collection. No video/smoothness acceptance claim. Collector stopped cleanly.

## Results

1,430 fast publications, zero reported queue drops; publication interval median334ms/p95367.6ms/max599ms. Capture age median98ms/p95141ms/max323ms; inference21/32ms median/p95, model preprocess1/3ms, native19/29ms, postprocess1/1ms. Only five active-scroll publications: capture134/295ms median/p95, native31/140.2ms. Small active sample is inadequate for a scroll-performance verdict.

Request-ID span analysis:1,431 requests,1,430 completed callbacks/scenes,one failed request aged10ms,zero malformed records. Preflight2/3ms median/p95; dispatch-to-callback29/49ms (includes platform and callback scheduling); callback-to-prepare0/1ms; preparation32/43ms; explicit readback27.947/38.238ms; scale API0.775/4.287ms; prepare-to-scene0/1ms. Thus the isolated preparation improvement is present in this live run, but no matched prior-build live A/B was performed.

Seven motion draws had input-to-draw34/49.7ms median/p95. Raw/applied displacement totals both6,905px with zero amplification. Too little motion and no suitable video to establish alignment or smoothness. The emulator's idle native timing is substantially faster than historical Pixel traces; it is **not speed-calibrated** and must not substitute for same-build Pixel validation.

## Critical remaining bug

Quality inference/presentation records:zero. Instead,1,429 quality sources were dropped as `invalid-stamp`, all with `surfaceEmpty=true` and token0. `enqueueQualityInference()` requires a valid long-lived backfill stamp before any quality execution. The page's low-confidence scroll producer disables world caching, which also disables live quality. This conflates safe cache reuse with permission to refine the current viewport. Visible missing coverage and clutter remain; fast-only latency cannot be reported as a successful full-dual-pipeline result.

Next: allow fenced current-frame quality without granting long-lived cache identity. Preserve independent document/window/transform/motion/sequence checks and latest-only ownership; late results may join an appropriate fast tick, never publish independently or seed an untrusted world cache. An independent read-only audit is checking the integration seams before changes.

## Tooling

`scripts/analyze_capture_spans.py` reports numeric request-stage distributions and counts partial/malformed/duplicate/failure records. Missing stages are never zero latency; every report is explicitly ineligible as an acceptance gate. Eight stdlib tests cover field order, failures, missing spans, invalid timestamps, duplicate records and marker scoping. Run alongside the existing censor trace analyzer, not instead of it.
