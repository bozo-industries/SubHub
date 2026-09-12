# Pass 69: subpixel spatial registration on clean source images

Status: spatial correspondence core validated on bounded tests/corpus; not integrated into rendering.

SpatialFrameRegistration refines a coarse vertical displacement using textured7x7 patches selected
across an8x4 grid. Zero-mean patch comparisons, distinct local minima, one-eighth-pixel refinement,
minimum inlier fraction, three-band/two-column coverage and displacement consensus reject weak or
local-only evidence. It reads already-prepared pixels and has no clock, tracker, cache or camera
authority. Image registration does not make receipt timestamps exact.

Eight JVM tests cover integer/fractional shifts, brightness variation, stationary texture, local
animation, split-direction reflow, narrow texture support, wrong proposals, unrelated frames,
invalid inputs and input immutability. An initial coarse-score cutoff rejected a valid quarter-grid
proposal before refinement; moved the strict final-error decision after subpixel refinement while
retaining a bounded preliminary cutoff. Final-error and distributed-support requirements remain.

## Private clean-source replay

Host-only scripts/java/SpatialSourceReplay.java reads the existing64 private Pass66 prepared PNGs
and strict row records. All111 source-trace row records parsed;19 accepted coarse pairs had both
images available. Refinement accepted13, rejected6. No private images or derived image content are
committed. Reports remain in the ignored Pass66 artifact directory as spatial-replay.json and
spatial-phase-comparison.json.

An independent [OpenCV phase-correlation estimate](https://docs.opencv.org/doc/doxygen/html/d7/df3/group__imgproc__motion.html)
on the same clean image pairs agreed with all13 accepted refinements: median absolute difference
0.328 source pixels, maximum1.173. This is agreement between estimators, not ground truth, absolute
target alignment, or a proof against dominant full-screen animation. Two comparison fixtures pass.

## Sparse-cadence Android cost

Explicit emulator-only corpus instrumentation processes the64 images at334ms spacing, without a
tight warmup loop. It treats every image as eligible for a coarse observation, so its59 proposals
and53 accepted refinements include additional stationary observations and are not the host run's
19-pair population. The final run separately reports18 moving proposals: CPU median1171us/max1722us.
Across all59 calls CPU median1499us; first/max23437us. Wall median1579us/max23687us. This excludes
live capture, inference, queue contention and rendering. Two strict CPU-parser fixtures pass.
The earlier run without a moving-only breakdown is superseded, not pooled into these numbers.

562 JVM tests, zero failures; lintDebug; paired APK builds passed. Corpus instrumentation1/1 passed
(final21.736s). Target reinstall completed afterward to restore the Accessibility service.

## Unchanged-pipeline control

Ten-gesture pass69-control replay completed with no experimental registration caller.28 raw/parsed
publications; request-to-publication median158.5/p95234.65ms; preprocess/runtime/postprocess3/39/1ms;
maximum cumulative inference drops0; publication queue median2/p9520.85ms; motion input-to-draw
p9517.9ms; five text publications; zero malformed capture records. These remain baseline numbers,
not gains from the new core. No optical/flash/text stability acceptance this pass.

All probes/recording flags remain off. Pixel untouched; no signed evaluation release or deployment.
Next: scoped reference-frame state, rejection/bridging policy and source-coordinate integration.
Preserve the distinction between spatial image pose and uncertain current display pose; never
bypass the typed-time guard by relabeling an Accessibility receipt as pixel time.
