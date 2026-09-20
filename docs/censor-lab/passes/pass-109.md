# Pass 109 — user recording after Pass 108

Read-only review, 2026-09-20. No censor implementation change or phone reinstall.
The phone still runs the signed Pass 108 development candidate (`45179ccd`,
version 0.6.3/code 19). This recording uses an X search feed, not the earlier
comparison scene; it does not establish a controlled before/after improvement.

## Evidence and completeness

- Latest user-exported Lab bundle: 43.491 seconds, CPU Accessibility,
  detected-area coverage, Ultra preset, capture preference 512, actual fast frame
  320, scale 0.75, zero configured inference interval, all 17 categories.
- Bundle validator: 1,031/1,031 events, zero recorder drops. Video: 1,072 × 2,400,
  2,299/2,299 decoded frames. All 36 dense 100-ms contact-sheet pages reviewed.
- Exact critical frame PTS verified with FFmpeg. OpenCV's negative initial-frame
  timestamp was rejected; no invented global timestamp correction was applied.
- Border measurements use this bundle's configured color. The first attempt's
  obsolete color threshold found no masks and was discarded, not reported as
  absence of censor work. The corrected extraction found 1,360 candidate frames.
- Private recordings and derived images remain ignored local artifacts under
  `app/build/reports/device/pass109-user-lab`; no page content is committed here.

## Visible failures

The build has **not** reached real-device acceptance.

1. **Stationary masks disappear repeatedly.** On the upper card, masks appear at
   19.534 s, disappear at 20.200 s, reappear at 22.853 s, disappear at 23.668 s,
   reappear at 24.219 s, disappear at 25.002 s, and reappear at 27.903 s.
   Adjacent source-feature flow has median (0, 0) at all seven transitions,
   with 336–433 matched pairs. Missing-mask intervals are approximately
   2.654 s, 0.551 s, and 2.902 s. This is not explained by source scrolling.
2. **Duplicate borders and labels remain.** Overlapping duplicate mask outlines
   are visible from approximately 29.3 s despite the auxiliary person model
   having been removed. Body-mask footprints also resize or replace one another.
3. **Scroll attachment remains imperfect.** Visible lag/misalignment persists
   around 31.7–32.6 s. This review does not quantify direction reversal or establish
   that the previously fixed trajectory defect is the cause.
4. **Foreground clearing is late.** Masks cross browser chrome/app transitions
   and remain over the Diagnostics screen before clearing at frame 2,240,
   PTS 40.976 s. No exact transition-to-clear duration is claimed.
5. **Text masks arrive late in the visible sequence.** They appear around 20.2 s
   and 37.6 s; this is observational, not a calibrated text-pipeline latency.

The opening 0–16.3 s includes Diagnostics, app switching, browser startup and
page loading. Those intervals must not be represented as inference latency.
No semantic false-positive/false-negative rate was annotated.

## Trace timing

Raw/parsed counts match: 81/81 fast publications, 42/42 quality-ready records,
14/14 immediate presentations and 12/12 late presentations. No person-model
records are expected in this single-model runtime.

| Measurement (ms) | Median | p95 | Maximum |
|---|---:|---:|---:|
| Fast request → publication | 158 | 212 | 240 |
| Fast inference | 52 | 101 | 121 |
| Fast preprocessing | 2 | 5 | 8 |
| Fast runtime | 49 | 90 | 113 |
| Fast postprocessing | 1 | 2 | 7 |
| Quality capture → ready | 368.5 | 576.85 | 754 |
| Quality bitmap preparation | 46 | 62.8 | 67 |
| Quality inference | 238.5 | 338.25 | 436 |
| Quality preprocessing | 9 | 18 | 28 |
| Quality runtime | 223 | 324.5 | 428 |
| Quality postprocessing | 3 | 4 | 7 |
| Immediate ready → presentation | 9.5 | 15.35 | 16 |
| Immediate capture → presentation | 373 | 483.35 | 510 |
| Late ready → presentation | 333.5 | 455.1 | 465 |

Fast publication interval median is 350.558 ms, maximum 934.258 ms. Fast queue
counter is zero in the recorded publications. Quality cumulative counters change
as follows: dropped 49→54, stale 20→20, preemptions 13→14, cancelled runs 31→32.
These counters overlap and must not be added. Late-presentation records do not
contain capture age; that metric is unavailable rather than zero.

The immediate path presents ready results promptly, but that does not remove the
upstream capture/inference age or the observed renderer/cache persistence failures.
Further implementation needs causal evidence for those failures; this review
does not assert a cache-expiry, merge, or foreground-clear root cause.
