# Pass 107 — retain NudeNet and remove the independent person network

## User-directed scope

Keep the original NudeNet detector and all category controls. Remove the second
person model and its runtime overhead, but retain model-neutral whole-person geometry
for a future body-capable NudeNet export. Provide a standalone training guide that
requires only PERSON annotations, not NudeNet's unavailable original dataset.

## Changes

- Remove the NanoDet asset, provenance/license files, preparation script, decoder,
  model session, shared image copy, inference backend/worker and both services' scheduling
  and late-publication callbacks. Remove tests specific to that retired implementation.
- Retain `PersonBox`, `WholePersonGeometry`, coverage presentation and renderer contracts.
  Current settings explicitly say the original model covers detected areas only; the
  whole-person preference remains stored for a future body-capable model. Settings locks,
  covered/exposed controls, text handling and original-class statistics are unchanged.
- Reject outputs other than the current 22-feature contract. A future 23-feature export
  needs an explicit decoder/publication integration pass; replacing an asset is not sufficient.
- Keep historical person-trace parsing so old Lab recordings remain analyzable.
- Add the independent [PERSON-only training kit](../../../training/nudenet_person/README.md),
  committed separately as `4a6a0694`. Frozen backbone/neck/original head and normalization
  state preserve original outputs; only the new box/class head trains. One ONNX graph shares
  feature extraction. No real dataset was downloaded or trained, and accuracy is unproven.

## Verification

The exact removal index `958bc7ff13974893d2654b8b40365380795cd4f6` was exported to
`C:/Users/user/Code/SubHub-pass107-single-model`, excluding unrelated cache prototypes.
It passes **802 unit tests**, `lintDebug`, `assembleDebug` and `assembleDebugAndroidTest`
with the immediate-quality experiment enabled. Both APKs were built together.
The existing settings instrumentation expectation was updated for the explicit future-model label.

The independent training kit passes 12 contract/metric fixtures, a synthetic gradient step,
unchanged frozen-state checks, exact PyTorch output parity at three shapes, checkpoint
round-trip, and ONNX Runtime parity at five square/rectangular shapes. The export test caught
and fixed cached anchors being baked into the graph. Synthetic weights/exports were discarded.

All **four native renderer/settings tests pass** on the task-owned read-only emulator,
using the declared `SubHubTestRunner` and matching target/test APKs. The run verifies
rendered pixels, shared-body decorations, frame replacement, preference round-trip and
locked settings UI. One earlier attempt stopped at the task's memory cap and was not
counted; the complete passing run used a 720x1280 software-rendered framebuffer. The
emulator was stopped afterward. Neither run establishes Pixel performance.

## Device and performance status

No Pixel installation or new recording occurred. The Pixel remains on rejected Pass 104.
Pass 105 remains the latest measured capture/queue/preprocessing/inference/postprocessing/
publication and stability evidence; removing the second network is not itself a measured
speedup. The separate same-direction scroll-slowdown correction remains next.
