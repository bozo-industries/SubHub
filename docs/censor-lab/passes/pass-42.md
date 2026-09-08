# Pass 42: locate publication costs

Status: diagnostic evidence; no claimed performance improvement.

Added five numeric CAPTURE_SPAN stages: fast-ready, geometry-ready, publication-post,
publication-main, publication-tick. This reuses the existing request identity/uptime schema;
the renderer, detector and scheduling remain unchanged. Core commit eb3bb9c pushed and
verified. Full unit/lint/paired APK builds passed. Other integration remains dirty.

## Controlled emulator trace

- APK SHA256: 81CB8B18003F20BD2BF50B4B9D285C83663852F47E2CFCCEA3FFACAB87190858.
- Local artifacts: app/build/reports/device/pass42-publication-spans/trace.log and analysis.json.
- Anchors OFF; same Chrome ten-gesture replay; 13.001 seconds, trace-only.
- Within exact BEGIN/END markers: 36 request IDs, 32 complete stage chains. Complete chains
  include presentation attempts that may subsequently fail freshness checks; not all are
  successful overlay publications. No inferred nearest-timestamp joins are needed.

| Stage interval | Median ms | Maximum ms |
| --- | ---: | ---: |
| Accepted to dispatch | 7 | 17 |
| Dispatch to callback | 42 | 64 |
| Callback to prepare | 1 | 7 |
| Prepare | 63.5 | 101 |
| Prepared to fast-ready | 61.5 | 186 |
| Fast-ready to geometry-ready | 4.5 | 13 |
| Geometry-ready to publication-post | 0 | 3 |
| Posted to main entry | 8.5 | 123 |
| Main entry to presentation tick | 14 | 193 |

Do not sum medians or compare these complete-attempt populations directly to successful
publication-only statistics. Successful fast publications: 30; capture age p50/p95
201.5/294 ms, runtime 43.5/91.15 ms, preprocessing median 3 ms, postprocessing median 1 ms.
Actual input-to-draw median 5.5 ms. Per-publish dropped counter zero, not proof of no rejected
presentation attempts. The collector completed; no video or Pixel acceptance is claimed.

## Decision / next action

Do not move policy/stat bookkeeping to another queue: measured work is negligible. The
readback/preparation path is a major cost, and main/frame-tick tails remain. Next inspect
InferenceBitmapPreparer against its earlier hardware-path experiments and measure a bounded
same-frame alternative (small GPU render/readback versus full software readback), including
quality-lane contention. Check existing tests/history first; do not repeat rejected synthetic
claims or change production solely on a one-image benchmark. Also classify the long tick
attempts as completed, replaced, or stale before calling them visible freezes.

Current emulator build stays anchors OFF. Goal remains active; physical acceptance outstanding.
