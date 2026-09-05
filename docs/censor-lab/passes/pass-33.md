# Pass33 — bounded same-direction phase correction

Status: candidate, not accepted. Date:2026-09-05.

## Hypothesis and scope

Pass32 synchronized Pixel video shows three independent censors lagging their local tiles by
38–55px during clean slow downward scrolling. The source emits roughly46–63px deltas every95–116ms.
The current motion estimator applies only18% of its existing measurement error before a Hermite
trajectory that lasts longer than the next event interval. Repeated updates retain a phase deficit.

Trial: apply100% of same-direction measurement error, retaining the existing56px correction cap.
Do not change prediction horizon, reversal correction/braking, return leg, detection, cache, or
quality policy. This is a deliberately bounded experiment, not a claim that sparse observations
can guarantee perfect alignment under unobserved stops. Larger correction jumps remain a risk.

Reference APK: Pass32 SHA256273D906FA829E73DD19E8CA9FDCE5EBE37049EE1986912B568C2A09174F03684.
Reference video: `app/build/reports/device/pass32-alignment-ready/alignment.mp4`, recorded frame
timestamps and gesture phases are documented in Pass32. No accepted performance baseline is implied.

## Plan and gates

1. Test the isolated motion estimator at60/100/120ms input intervals and8/16ms rendering steps,
   including both directions, reversals, sharp deceleration, and silent-stop settling.
2. Keep same-direction corrections <=56px and reversal corrections <=96px. Do not enlarge the
   viewport-based prediction cap. Target at least40% lower steady slow-scroll mean lag in the
   analytic fixture; report faster-scroll limitations rather than hide them.
3. Remove/shelve the unrelated cache candidate before building: its quality skip changes runtime
   even without new service wiring. Freeze/sign the isolated APK and run full tests/lint.
4. Repeat the Pixel slow/medium/jitter video. Target >=40% lower cumulative slow-scroll lag;
   reject if larger correction jumps visibly worsen smoothness, reversals, or settling. Inspect
   independent local tile anchors, not the legacy relative-motion-only aggregate.
5. Ask the user for a verdict. Alignment goal remains incomplete even if this trial improves it.

Initial temporary experiment: existing ViewportMotion tests pass with fraction1.0. Correctly loaded
production classes give slow0.5px/ms mean lag9.4/15.9/20.5px at60/100/120ms intervals versus old
20.4/34.2/44.2px. At2px/ms, candidate mean54.1/115.6/163.7px versus81.6/136.9/179.6px; the unchanged
cap still limits faster movement. These are synthetic measurements only.

Frozen APK: `app/build/reports/device/pass33-phase-candidate/SubHub-pass33-phase-correction-arm64-v8a-release-signed.apk`,
SHA256 `45BCE7246D018E3AD2566ACEB42E44B3F22D457071974B16F211B68B095AF018`.
Established release certificate verified. `source-fingerprint.json` beside the APK records main
source/resources before the separate UI agent began edits. Full421 unit tests, debug/release lint,
app+test APK build and signed release build pass. Cache candidate was shelved; javap disassembly
of restored cache implementation exactly matches retained Pass32 compiled cache (zero diff lines).
An initial test failure caught a missing co-observation guard during shelving and was fixed before
this artifact was built. Never weaken that distinct-target regression to make the trial pass.

Installed base APK independently matches the frozen hash. Motion implementation/tests checkpoint
`17e9721` was pushed to `origin/codex/censor-pipeline-v2`; other integrated Pass31/32 work remains
uncommitted, so the APK is still identified by artifact/source fingerprint rather than clean HEAD.

Device video: `app/build/reports/device/pass33-alignment/alignment.mp4`, SHA256
`710312E8F09617016B9D2173F570D95025FB5D435EDF1722453D5A673A6B04BF`.
Trace: `calibration-20260905-075117-logcat.txt` in the same folder, SHA256
`D11B533DDF79C401A52F25123958681F2D5ED54F7E92CE307F5828214F60D950`.
Monotonic log and run manifest are alongside. Bounded16.274s:44 begun/42 committed, queue
replacements0,36 quality completions/13 later fast publications/3 late-quality drops.
Fast p50/p95: capture-to-publication107/130.9ms, model preprocessing2/6.9ms, total inference
33.5/53.8ms, native31/46.95ms, postprocessing1/2.95ms. Publication interval334/673ms,max718ms.
This short encoder-active run is not a throughput/release gate. Before installation Pixel
battery82%,37.3C; do not infer clean speed attribution from differently heated runs.

Human verdict: "slightly better?"; user specifically identifies fast-scroll onset failing to catch
up and remaining drifted. User now requests objective video evaluation instead of a questionnaire
after every pass. Continue recording per-pass objective outcomes; request subjective input only
when it is necessary or for final acceptance. Bounded cumulative comparison is complete; keep
candidate status, not accepted/solved.

Bounded video comparison (three independent local texture anchors, confidence .997–1.000):
at PTS7.784/7.948/8.100/8.265/8.434s, page movement is0/90/169/256/343px.
Three isolated box movements are0/48/156/230/334,0/58/166/244/350,
and0/50/158/234/338px. Unlike Pass32's persistent38–55px lag, Pass33 catches up
to within9px at the last sample, but still starts32–42px behind. The bridge from burned monotonic
timestamp to PTS has about.017ms spread across nine samples; one sampled display drop occurs
around31.85s, after the gestures. Merged/touching boxes and offscreen entrants were excluded.
This supports improved steady slow attachment, not perfect alignment or fast-start/jitter safety.
Two clean medium-down tracks oscillate approximately-58..+11px and-55..+48px. Two reliable jitter
anchors show transient drift up to+134/-92px, returning within about7–20px over160–320ms at stops.
Comparable Pass32 peaks were+166/-150px, but sample cadence differs, so this is directional evidence
rather than a release-grade A/B. Remaining merged/touching contour identities are ambiguous;
isolated late box movements in nominally static frames still need investigation.

Rollback: frozen Pass32. If this improves attachment but worsens smoothness, reject the trial and
measure a fresh-position observer off the UI thread instead of increasing jump caps.
