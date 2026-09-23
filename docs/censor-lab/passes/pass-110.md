# Pass 110: bounded current-viewport quality handoff

Status: local candidate verified; not installed or accepted on Pixel.

## Recorded cause

Lab109 (Pass108 installed) has three stationary upper-card disappearances. Each follows
the same sequence: `QUALITY_IMMEDIATE_PRESENT`, one `QUALITY_LATE_PRESENT`, then a
fast publication with an empty cached presentation layer. Motion generation stays 896,
camera stays `(0,0)`, and the fast lane continues detecting only the lower face.

| Quality source | Capture uptime | First fast publication | Next fast publication loses quality |
| --- | ---: | ---: | ---: |
| 1169 | 348610244 | 348611037 | 348611354 |
| 1177 | 348613633 | 348614438 | 348614822 |
| 1180 | 348614998 | 348615806 | 348616157 |

All times are milliseconds in the trace's uptime domain, not video PTS. Numeric-only
regressions preserve these timelines. Video mask-off frames are approximately 20.200,
23.668 and 25.002 seconds; compositor timing is not assumed to be a constant offset.

The pending quality slot was consumed by the first compatible fast reader, even for
`CURRENT_ONLY` results that never enter the world cache. Therefore the next fast
publication necessarily replaced those masks with an empty presentation layer.

## Change and safety boundary

- Current-only snapshots can be read by subsequent compatible fast publications.
- Keep the existing 2500 ms maximum **source capture age**, without renewal on reads.
  A main-thread expiry callback also removes the layer if fast publications stop.
- Preserve capture/document/surface/token/window/dimension/motion/camera/phase fences.
  Recheck current-only eligibility at actual UI publication and reject superseded sources.
- A newer quality result replaces the entire current-only snapshot, including empty results.
  This conservative policy does not accumulate partial tiles or treat their misses as proof
  about regions outside the tile. It can still lose coverage when tile/full-frame recall changes.
- Durable cache confirmation, world-cache single-use handoff, inference models, thresholds,
  text, and scroll prediction are unchanged. Existing dirty cache prototypes are excluded.
- Keep the displayed base free of temporary quality additions so replacement/expiry cannot
  resurrect them through the immediate-refresh path.
- `QUALITY_RETAINED_PRESENT` counts repeat handoffs separately from initial ready-to-present
  latency. Its parser rejects incomplete/unknown fields and exposes raw and parsed counts.

## Verification and performance

The one-shot reproduction fails three of the initial six slot tests, including the recorded
second-fast-tick regression. The candidate now has nine slot/expiry/fence tests. The exact-index
snapshot passes all 826 unit tests, `lintDebug`, `assembleDebug`, and `assembleDebugAndroidTest`
in guarded Gradle invocations with immediate quality both enabled and disabled. Trace-parser fixtures pass,
including raw/parsed retained counts, incomplete records, and unsupported fields. The build
uses two workers and an 8 GiB process-tree limit. No native instrumentation or new Pixel run
has been performed for this pass.

No new device performance result yet. Lab109 baseline remains: fast capture-to-publication
median/p95/max 158/212/240 ms; preprocessing 2/5/8 ms, runtime 49/90/113 ms,
postprocessing 1/2/7 ms, 81/81 parsed fast records, reported fast queue drops 0.
Quality capture-to-ready 368.5/576.85/754 ms; bitmap preparation 46/62.8/67 ms,
preprocessing 9/18/28 ms, runtime 223/324.5/428 ms, postprocessing 3/4/7 ms;
42/42 quality records. Immediate ready-to-present 9.5/15.35/16 ms (14/14),
initial late ready-to-present 333.5/455.1/465 ms (12/12). Quality dropped counter
49→54, stale 20→20, preemptions 13→14, cancellations 31→32; these are not additive.

This targets one demonstrated dropout mechanism, not the entire observed 2.65/0.55/2.9-second
gaps. Tile/full-frame recall changes, overlapping duplicate borders, scroll alignment and
foreground clearing remain separate issues. Require a fresh Pixel Lab and the user's perceived
speed/lag/scroll/text/false-positive verdict before claiming improvement or acceptance.
