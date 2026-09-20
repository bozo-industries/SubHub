# Pass 99: optional whole-person coverage and shared model input (in progress)

## User request and boundary

Add opt-in Whole person coverage alongside default Detected areas. Enabled visual
categories trigger only their associated person; text is separate. Show an immediate
provisional body rectangle from coherent head/torso cues, then refine with a lightweight
person detector. Original selected regions remain covered. The user also requested
shared image preparation and ONNX efficiency work instead of treating differing input
formats as a hard barrier.

This checkpoint is infrastructure, not a finished setting or device-ready feature.
Settings, protected pack integration, service admission/lifecycle, renderer refinement,
native Android execution, real-image quality and Pixel acceptance remain incomplete.
X scrolling remains independently unresolved; no motion behavior changes are included.

## Implemented foundation

- Default-off `CensorCoverage` in `DetectorConfig`, retained by `toBuilder`.
- `DetectionEngine` can reuse its already-read image pixels and already-computed
  network output for a detached shared image and head/body support cues. Disabled
  categories remain support only: original detections/tracker/statistics are unchanged.
  This incurs an optional compact pixel copy and support decoding, not a second
  screenshot, bitmap readback or body-part model run. It is not yet called by services.
- Shared person input adapter performs centered letterboxing, RGB normalization and
  CHW writing into a reusable direct tensor. No additional Bitmap or intermediate
  float array. Fast input semantics remain unchanged. The existing compact fast
  pixels limit person detail; that tradeoff requires real-image validation.
- Lazy, cancellable single-threaded person CPU session with idle spinning disabled,
  explicit output-name/shape checks and preprocessing/runtime/postprocessing timings.
  Caller must still integrate shared low-priority admission before enabling it.
- Pure person DFL decoder and provisional/refined association. Ambiguous people and
  neighboring head conflicts retain part coverage; no new synthetic infractions.

## Model and ONNX optimization

Source: [OpenCV Zoo NanoDet](https://github.com/opencv/opencv_zoo/tree/47534e27c9851bb1128ccc0102f1145e27f23f98/models/object_detection_nanodet),
Apache-2.0, full license and provenance packaged with the model. Original SHA-256:
`4b82da9944b88577175ee23a459dce2e26e6e4be573def65b1055dc2d9720186`.

The reference demo converts file/camera BGR to RGB before applying its means and
standard deviations; the adapter follows that actual code path. The shipped graph
has three stride levels, not four: 8/16/32 with 2704/676/169 candidate rows. Outputs
are resolved by name because ORT and OpenCV expose them in different orders.

ORT warned that constant initializers were also graph inputs, preventing some
optimizations. `scripts/prepare_person_model.py` removes those 158 input declarations
only, preserving all 225 operators, all 158 weight tensors, and output declarations
byte-for-byte. Uses ONNX 1.17.0 and ORT 1.20.0, matching the application's ORT version.
Derived model: 3,796,745 bytes, SHA-256
`d891b8c939ce062b13fc7440195d427eeecbec307c2417564d6908309b263e19`.

Local numerical checks: one zero and four seeded random normalized inputs; maximum
absolute original/static output difference `6.496906280517578e-06`, all outputs pass
`atol=1e-5, rtol=1e-5`. Original ORT vs OpenCV on a separate seeded input differed by
at most `6.198883056640625e-06`. These establish execution parity, not person accuracy.

Desktop CPU benchmark: one inference thread, idle spinning disabled, alternating
execution order, six warmup iterations and 30 timed runs per graph. Median original
19.6554 ms; static 15.67085 ms (about 20.3% lower). Not device-performance evidence.
No first-censor, throughput, thermal or whole-pipeline speedup claim follows from it.

## Verification state

Focused detection tests passed. Full dirty-worktree run: 791 tests, five failures in
`QualityCacheEvidenceTest`, overlapping the pre-existing excluded cache prototype.
Exact staged-source tree `ca4de1b2f99e85dac50c446b446741f91ccadc53`, exported to
`C:/Users/user/Code/SubHub-pass99-foundation-72c4851a`, passed all 779 unit tests,
`lintDebug`, `assembleDebug`, and `assembleDebugAndroidTest` in the same invocation
with `-PimmediateQualityExperiment=true`. This excludes the unrelated cache, harness,
extension and viewport-poller changes. Only this report text changed afterward.
The resulting universal APK contains the static model, license and provenance;
packaged model hash independently matches the derived hash above. The unmodified
source model remains outside APK assets in the ignored local temporary directory.
Native Android smoke test is authored but not yet executed. No phone commands,
installation, signed candidate, public release or tag in this pass so far.

Next: finish shared-budget scheduling, stale-source fences, UI/locks/packs and both
render paths, then validate real-image association and mask continuity. Report full
pipeline metrics and ask for the user's real-device verdict before calling this done.
