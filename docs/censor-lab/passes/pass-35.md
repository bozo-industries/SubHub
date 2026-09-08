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

## Current-only quality candidate

Added explicit `CURRENT_ONLY` versus `CACHE_BACKFILL` admission. Current-only requires known request-time application window, document/capture epoch, transform, phase, motion generation and camera coordinates; its source stays in the existing one-slot mailbox. It cannot enter the coordinator confirmation table. Service prototype routes its result only to the next suitable fast publication, with repeated phase/window checks and a 2.5-second age limit, not a fixed one/two-tick cap. Cache-mode motion-tolerant behavior remains separate. Request-time window/document propagation prevents relabeling an older capture as a newer window.

The full dirty-worktree service integration passed469 JVM tests, lint and paired APK build. Independent review found no functional blocker. The narrow policy checkpoint also passed28 isolated JVM tests against the staged source snapshot, excluding older uncommitted cross-category/IoU changes. Service/runner integration remains in the existing dirty candidate pending its own coherent review; policy commits alone are not a complete app build of this behavior.

Emulator-only candidate SHA256 `8553404DC984B4A34CF8042F5FC9DAA932F1131D7176C360C53E1CDC3A4CBEC9`. `pass35-current-only/trace.log`, PASS35_CURRENT_BEGIN/END,335.644 seconds including idle, keyboard scrolls and user browsing (not deterministic A/B):

- 982 fast publications; capture105/162.95ms median/p95, native25/45ms, model pre2/4ms and post1/2ms; publication337/383ms; reported fast queue drops0. There were38 active fast publications only.
- 920 quality completions, including213 `CURRENT_ONLY`; their backfill matched/inserted/promoted/refined counters remain0. Initial provisional/cache-mode quality is included in the other707 completions.
- 878 later-fast quality presentations overall, ready-to-present225/260.15ms median/p95. Source1118 was consumed by fast1120, proving it is not locked to exactly one subsequent tick.
- Quality native87/143.05ms median/p95; preparation59/83ms. Native interval analysis finds18 fast/quality overlap pairs. After correcting the parser for optional `oldFrame`,923 preparation spans and817 preparation/fast-native overlap pairs are visible; the prior zero was a parser miss, not evidence of hardware isolation.

This directly reproduces and clears the total quality-admission loss seen earlier. It does not prove perfect alignment, eliminate visible clutter, establish calibrated Pixel performance or validate physical-device quality. The signed Pass34 APK does not include this newer service prototype.

## Autonomous touch replay and recording controls (8 September)

The already-open emulator Chrome Emiru page now accepts the existing ten-gesture touch replay without user setup. `pass35-touch-trace-20260908` contains all ten paired replay markers and nonzero scroll in each gesture interval; its 11.011-second selection contains32 fast publications, capture age143/249ms median/p95, native34/84ms, and zero reported fast drops. Raw/applied travel both3,191px; net displacement-11px, no amplification. `touchId=0` throughout: use the explicit replay markers, not inferred touch IDs or the analyzer's unrecognized swipe-marker count. Quality ready-to-present wait232/558ms median/p95 (maximum609ms) still warrants investigation. This short run is diagnostic, not a statistical performance gate.

The same replay with Android `screenrecord` (`pass35-touch-video-20260908`) severely perturbs the workload:18 fast publications and capture-age p95571ms. Video has413 frames/24seconds,307 content-changing pairs, and decoded PTS gaps p95317ms/max442ms. Reject it as a performance or smoothness gate; nominal encoder settings are not actual temporal coverage.

Host `gdigrab` of the exact emulator window plus existing NVENC provides a better observation path without changing Windows security settings or repairing the proprietary screenshot helper. `pass35-host-20260908.mp4` contains719 frames/24seconds at388x864,245 content-changing pairs, PTS-gap p5033.3ms/p9533.3ms/max66.7ms. Associated `pass35-host-trace-20260908` has27 fast publications and capture-age136/203ms median/p95. These sequential short runs have different page/thermal state; this establishes feasibility, not zero recorder overhead or Pixel calibration. All three collectors and both recorders exited; no background recording remains.

### Invalidated alignment oracle

The original analyzer's near100% alignment result for the Android video is invalid. Its red-channel predicate misses the actual muted-purple censor borders and instead matches colored page imagery. A separate synthetic fixture proves that unmasked white censor labels can make moving censors over stationary content appear perfectly aligned. Do not use earlier color-only motion scores to accept a pass. The correction must identify dark-filled supported censor rectangles, exclude their entire interiors from page-flow evidence, reject flow endpoints inside overlays, and retain the explicit limitation that relative motion cannot prove absolute target alignment.

Implemented the conservative correction and original-pixel threshold comparisons. Ten focused analyzer tests pass, including moving-label contamination, muted-purple/magenta recognition, colorful-art rejection, masked flow endpoints, and explicit null results when no supported censors are present. This supports opaque black-filled bordered boxes only; overlapping components may merge, unsupported effects are not measured, and absolute alignment remains unproven. Eight capture-span tests also pass. No application binary changes accompany this diagnostic checkpoint.

Same first60 Android-video frames, OpenCV single-threaded: old/new matched motion frames28/28 and components204/192. The old reported within2px rate100% becomes10.71% with corrected identification and original-pixel units; residual median0.280→3.246px, p950.734→13.638px, maximum0.894→16.500px at720px video width. This is a diagnostic-oracle correction, not an app regression or an uncontaminated performance result. Manual frame4 inspection changes from seven artwork components/no actual censors to five actual censor interiors. The first60 host-video frames are stationary and correctly produce null alignment rather than a passing score.

Full719-frame host-video analysis then completed single-threaded: supported censors in719 frames,163 matched motion frames/791 matched boxes,22.09% within2 video pixels and57.67% within5. Global relative-motion residual median4.339px/p9528.420px; local-background correction p9528.0px. These are388px-wide video coordinates, not native screen pixels, and neither absolute offset nor unmatched/merged-box penalties are measured. Next incomplete action: isolate slow-start/reversal versus steady-scroll errors against the host video and trace, using real image anchors before changing the presentation model. The application candidate is unchanged; physical-Pixel acceptance and the existing dirty service-integration review remain pending.
