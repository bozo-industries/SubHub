# Pass 87: manual Pixel baseline isolates late quality delivery

## Device and verdict

2026-09-19: manual Emiru Google Images on the USB-connected Pixel 8 Pro.
Installed 0.6.3 APK SHA-256:
`82781925205bbab63f4fd635d696de56e0d3bb13d3dc3129c621726ad0346918`.
No replacement or settings change. This is not proof of equivalence to HEAD or
the dirty working tree.

User verdict: trailing remains, coverage is much cleaner than when they last
watched, with no observed false positives or overshoots. Quality feels slow to
appear and skipped during scrolling. Positive stability feedback is not overall
acceptance. Sampled video also shows displaced coverage and masks entering
browser chrome during scrolling; distinguish geometry errors from classification
false positives.

## Collection and verification

A was trace-only; B was a separate manual video run, not a matched A/B experiment.
Collector readiness and final markers were verified. Actual marker windows were
54.538 s (A) and 57.228 s (B), not the nominal 30-second verbal cues. Cue timing
added delay; use recorded boundaries. Both collectors and the recorder stopped.

Video spans epoch 1789817452.355820703 to 1789817492.734152703 (40.378332 s), so it
does not cover all of B. Winscope v2 metadata and actual decode both contain
2,401 frames. Decode passed with preserved output timebase, no dropped/duplicated
decoder frames. Parser counts: A 152 raw / 152 parsed overlays; B 166 / 166,
zero unparsed overlays. Trace parser regression checks and 2 timestamp tests pass.

## Trace-only A measurements

| Measurement | Median | p95 |
| --- | ---: | ---: |
| Logged fast capture age | 144 ms | 184 ms |
| Fast preprocessing | 2 ms | 6 ms |
| Fast model runtime | 28 ms | 44 ms |
| Fast postprocessing | 1 ms | 1 ms |
| Fast publication interval | 335 ms | 426 ms |
| Quality bitmap preparation | 40 ms | 57 ms |
| Quality total inference | 130 ms | 168.7 ms |
| Quality model runtime | 117 ms | 150.7 ms |
| Quality capture age at ready | 249 ms | 394.5 ms |
| Quality ready-to-presentation wait | 218.5 ms | 313.35 ms |
| Paired quality capture-to-presentation total | 472 ms | 513.8 ms |
| Logged scroll input-to-draw | 4 ms | 10 ms |
| Logged scroll input-to-presentation | 35 ms | 39 ms |

The paired total joins 52 stages/presentations by sourceFastSequence and adds
each stage's captureAgeMs to its readyToPresentMs. Maximum: 828 ms. Do not add
unrelated percentiles or interpret producer timings as exact pixel/photon ages.

Fast dropped stayed at 3 throughout: zero new fast queue drops in the window.
There were 143 QUALITY_READY records, including 45 old-frame results with nonempty
coverage; 59 late stages, 52 late presentations and 7 motion-attributed late drops.
Old-frame results can feed cache confirmation; absence of a stage is not alone
proof of a discarded detection. There were 31 logged center changes >=100 px;
ordinary scrolling also causes these, so this count is not an error rate.
Text had 13 publications, 12 empty: insufficient text-group stability coverage.
Startup/first-detection was not measured because recognition was already warm.

## Next decision

Target the post-inference delivery wait and motion handling, not weaker detector
thresholds. Current source takes staged quality only on a later fast publication.
Preserve confidence/category policy, immutable coordinates, document/window
fences, and atomic overlay updates. Do not assume all missing quality is caused
by caution, or admit stale geometry to improve counts. Resolve artifact/source
lineage before a matched candidate build; repeat the manual Pixel comparison for
quality arrival, trailing, flashes and overshoot. Accessibility remains unaccepted;
MediaProjection remains outstanding. No new build, install or release this pass.

## Private artifact hashes

Ignored artifacts remain under app/build/reports/device/manual-pixel-20260919-*;
do not commit/upload raw traces or video.

- A trace: `164E416B100A52213CD337FD00473641A85B211FE8BD1EA4AC3331F0EC9E30D5`
- B trace: `8B316258A7AAC86D423CF49C99AEDB669FDF18637AC43B69FBCC9835B75A0D7E`
- B video: `9A34C94EAD5236D12AF5ED7F7BD99DFE01165E173C10B298DC5A0747DCC13D24`
