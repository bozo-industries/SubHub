# Pass 105 — installed Pass 104 dense Pixel review

## Provenance and scope

The user supplied a fresh Lab recording after the verified Pass 104 installation.
The first newly saved ZIP was byte-identical to an older recording; the user corrected
the export. Only the corrected session was used for this evaluation. Its manifest reports
Accessibility screenshot mode, CPU fast model, whole-person coverage, and 2,119 events
with zero recorder drops. All 2,119 NDJSON records decoded.

The private bundle and derived evidence remain in
`app/build/reports/device/pass105-user-lab`, outside Git. No settings were changed,
no replacement APK was installed, and no new recording was started by the agent.

All **2,218 video frames** decoded, matching the container count. All **34 dense sheets**
were visually inspected, including approximately 100 ms sampling across the active scene.
Sparse VFR sections around app transitions can produce larger sample gaps; this is not
a claim of constant video cadence or inspection of every individual decoded frame.
OpenCV again returned an invalid first-frame timestamp. FFmpeg independently confirmed
first PTS zero and the seven selected later timestamps used below; no global offset was
applied. Video pixels are 1072x2400, not native 1344x2992 display pixels.

## Still failing visually

- Scroll-following is not smooth or reliably aligned. At frames 1638–1639
  (29.459022–29.476378 s), source features move down about **61.4 video pixels** while
  the selected unchanged-width mask edge moves up **82 pixels**. Forward/backward optical
  flow retained 629 pairs, 627 agreeing within three pixels, with 0.033-pixel vertical MAD.
  The opposite-direction motion was independently checked in the consecutive frame pair.
- Frames 1243–1244 (22.875878–22.891511 s) show another opposite-direction mask change
  while source pixels move down about 26.3 pixels. Several masks update differently in
  the same compositor frame; this is not established as one global camera-only error.
- Full-person/part-sized coverage changes remain on stationary content. Nine intervals
  in the selected windows changed over 5% of the screen's detected mask-candidate footprint
  while median source motion was below 0.5 pixel on both axes. Examples include
  30.275378, 31.161256, and 36.159800 s. Dense review confirms prominent resizes; these
  counts are not a global flicker rate or semantic coverage score.
- Partial overlaps still carry duplicate labels/borders, including stacked captions and
  mismatched surrounding boxes. Pass 104's full-containment rule is insufficient for this
  observed presentation. Masks also remain visible over browser chrome during scrolling.
- Whole-person coverage is inconsistent across subjects and time. Some subjects stay at
  part-sized coverage, and face targets are visibly uncovered during movement. Larger
  masks alone do not satisfy accuracy or stability.
- Existing masks briefly remain over the app-switch transition back to Diagnostics.
  This needs separate lifecycle timing evaluation, not attribution to inference speed.

The motion scan covered 703 adjacent intervals in 7.1–8.4, 22.3–24.3, 27.3–31.3,
and 33–37.5 s. 702 passed the source-flow confidence gate. Candidate rectangle matching
is only an investigation aid: border fragments, shape replacement, and nested masks can
confuse whole-box identity. Therefore its aggregate residual distribution is **not**
reported as a calibrated tracking-error or before/after improvement metric. Selected
consecutive frames, rather than the aggregate alone, establish the failures above.

## Timing and parser completeness

Fast publications: **96 raw / 96 parsed**, queue-drop counter zero throughout.
Values below are median / p95 / maximum milliseconds:

| Fast metric | Median | p95 | Maximum |
| --- | ---: | ---: | ---: |
| Request-to-publication (`captureAgeMs`) | 136 | 173 | 194 |
| Inference | 31 | 48.25 | 57 |
| Preprocessing | 2 | 5 | 5 |
| Runtime | 28 | 45.25 | 51 |
| Postprocessing | 1 | 1 | 5 |

`captureAgeMs` is not a proven pixel timestamp. The differently scoped person records
start from the screenshot timestamp; do not subtract the two metrics as queue delay.

Person records: **209 raw / 209 strictly parsed**, seven parser fixtures pass. There are
58 model runs (three cancelled), 96 provisional publications, and 55 applied refinement
callbacks. Model total median/max 78/97 ms; preprocessing 9/32, runtime 67/88,
postprocessing 1/4. Provisional screenshot-to-publication median/max 113/155 ms;
refinement 292/367 ms. Applied callbacks do not prove nonempty or correctly associated
person boxes. Sampled cumulative person counters: submitted 121→215, dropped 0→0,
preemptions 141→235, admission denials 85→139. These counters are not additive and do
not directly describe unique dropped frames.

Quality-ready records: **89 raw / 89 parsed**. Median/p95/max: request-to-ready
238/410.4/486 ms; bitmap preparation 40/53/70; inference 120/158.2/196;
preprocessing 5/16/21; runtime 112/151.8/179; postprocessing 2/2/7.
The sampled dropped counter goes 0→1, stale drops stay zero, preemptions stay one,
and cancelled runs go 2→3. All **46 immediate** and **42 late** presentation records
parsed: ready-to-present median/p95/max 9.5/17/18 ms immediate, 226/296.8/323 ms late.
Immediate request-to-present is 234/321.25/369 ms. The populations differ; these are
not independent counts to sum into a total publication rate.

The Lab exporter now retains person records and coverage mode as intended. It still
does not expose each decoded person box, trigger association, retained/provisional
choice, and final per-person footprint. Timing correlation alone cannot identify the
exact producer of every resize. No speedup claim is made from this uncontrolled scene.

## Next work and acceptance

Reproduce the opposite-direction updates against actual scroll-event/source-publication
geometry, separating viewport trajectory from live/cache/person footprint replacement.
For stationary failures, capture bounded numeric ownership/coverage decisions and replay
the demonstrated model/raw/quality transitions. Preserve source validity and original
coverage while making presentation coherent; do not hide lag by enlarging masks or merely
extend stale evidence lifetimes. Include partial-overlap label ownership and app-switch
cleanup in the next regression set.

This pass is analysis, not a code fix. No Android rebuild was necessary. The installed
candidate remains Pass 104. After reviewing the findings, the user explicitly agreed
with the assessment. **Pass 104 is not accepted.** Prioritize opposite-direction scroll
jumps, then stationary coverage changes and duplicate labels. This recording is enough
to continue diagnosis and implementation; another user recording is not a prerequisite.
