# Pass 75: controlled capture-gap fixture

Status: live hold/recovery control flow exercised; visual coverage still fails acceptance.

## Reproducible fault injection

`CaptureGapProbe` is a one-shot admission pause, not a sleeping thread or blocked callback.
It exists only with an explicit private preference, DEBUG build and ranchu/goldfish emulator
hardware. After normal coverage is established, a private empty `cache/capture-gap-arm` marker
starts a five-second pause at an idle capture boundary. The marker is consumed; later arm attempts
cannot extend or restart that probe instance. Scroll, render and other workers continue. Deadline
polling resumes ordinary requests. No exported component or physical-device control was added.

Paired target/test APKs are required before `CaptureGapSetupAndroidTest` sets `enableCaptureGap`
to explicit true/false. That setup also removes stale arm markers. Reinstall the target after the
instrumentation process ends. `scripts/run_capture_gap_probe.ps1 -RunName <fresh-name> -RecordVideo`
checks emulator geometry, explicit setup, established publications and collector/video readiness,
then creates the marker and waits for pause acknowledgement before ten gestures. Disable through
instrumentation afterward. Without `-RecordVideo`, the same fixture runs trace-only.

`analyze_capture_gap.py` requires exactly one valid start/end pair, no screenshot dispatch inside
the pause, fresh matching dispatch/callback evidence afterward, all ten gesture pairs and complete
capture parsing. This simulates missing screenshots, not every side effect of an actual Android
internal capture failure. It is a fault fixture, never a throughput benchmark.

## Verification

Exact staged snapshot: 499 JVM tests, zero failures, lint and paired APK builds passed. Prototype
worktree: 589 JVM tests, zero failures, lint and paired builds passed. Three new probe-state tests
cover unarmed behavior, deadline/one-shot behavior and late polling. Three strict parser fixtures
cover valid pause/recovery, dispatch during pause, missing recovery and schema changes. PowerShell
syntax parsing passed. Explicit setup/disable instrumentation passed 2/2 in each mode transition.

Both runs used the same staged APK, SHA256
`9BFBF4391C1732D74B3A3E18FB1BE785F121D3382770C0954F46E4F204339D94`.
OFF then ON, same page-top reset and ten gestures, 24-second native recordings. Artifacts are the
ignored `pass75-off-gap` and `pass75-on-gap` directories; all collectors/recorders/analyzers completed.

## Observed gap and response

- OFF: 5086 ms observed pause, zero dispatches during it, 57 fresh callbacks afterward in the
  full recording trace; two raw/parsed gap records and no malformed capture records.
- ON: 5041 ms pause, zero dispatches during it, 40 fresh callbacks afterward; two raw/parsed gap
  records and no malformed capture records. Sixteen HOLD records occurred between gap markers,
  referring to the previously applied source id 13164754.
- ON's first returning source at 13:55:05.282 was BASELINE, not re-identified into the old map.
  One following frame registered; subsequent return frames were unmatched. A separate natural
  callback-code-1 failure lasting 5046 ms occurred after the injected gap. Do not pool that failure
  into the controlled pause or call the two full recordings equivalent performance loads.

The HOLD records prove the branch executed, but do not identify how many visible regions were
actually cache-derived after view overlap filtering. Contact sheets still show displaced masks
during the gap. ON later loses face coverage while the target is visible; OFF recovers face coverage
in later samples. This is not a successful visual fix or proof of improved detection quality.

## Bounded gesture metrics (stress fixture only)

| Metric | OFF | ON |
| --- | ---: | ---: |
| Raw/parsed overlay publications | 8/8 | 2/2 |
| Request-to-publication median/p95 ms | 241/467.6 | 254.5/286.45 |
| Detector preprocess/runtime/postprocess medians ms | 3.5/57.5/1 | 2.5/59.5/0.5 |
| Capture preparation median/p95 ms | 67/223.8 | 78/142.6 |
| Publication queue median/p95 ms | 7/213.75 | 55/79.65 |
| Motion input-to-draw p95 ms | 42.2 | 31.9 |
| Maximum cumulative inference drops | 0 | 0 |
| Text publications, all empty | 3 | 3 |

ON spatial parsing is 27/27 records: six images, three known, five queries, one with regions and
16 holds. CPU median 5235.5/max 5941 us. Zero malformed capture records; completed/failed/partial
requests within gesture markers are 17/1/1 and 5/2/2. Additional callbacks/failures outside those
markers explain differences from the full recording totals above.

Relative optical residual median/p95 is 5.924/38.1 versus 5.361/33.0 video pixels, with 313/310 matched
boxes and within-five rates 36.22%/46.78%. Missing/merged masks and constant offsets are not penalized;
these aggregate scores do not override the visible coverage failure. Native recording adds heavy
load, and the injected pause intentionally removes capture work. No first-detection or throughput gain.

Both private preferences independently read back false, arm marker absence verified, worktree
target/test APKs restored after terminal instrumentation. GPU/other experiments remain OFF. Pixel
untouched; no release or deployment. Next: retain useful reference spaces across an unmatched view
and measure actual cache-vs-live view output and rejection reasons before claiming loop recovery.
The current single-reference reset and crop/output gates need to be distinguished, not relaxed blindly.
