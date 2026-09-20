# Pass 96: source-aware quality-cache continuity

## Reproduced defect

The Pass 94 X avatar captures show cached avatar presence, then absence in four
stationary samples, then presence again, without a live avatar track. The cache-backed
input ID in the first sample maps to `world-cache:1519`; another input is transient
render coverage. Captures alone do not prove every cause of the flicker.

A separate deterministic reproduction against the committed cache proves that two
`SETTLED_FAST_ONLY` publications erase a quality-confirmed region: the service labels
every non-active scene as unified even if no quality observation was made. The test
fails with expected cache size 1 versus actual 0. This is independent of the old,
uncommitted matching/contradiction-grace experiments, which are not part of this fix.

## Change

- Cache evidence names its source lane, capture time, and actual quality crop.
  Fast-only/deadline scenes cannot contradict quality-confirmed regions. A genuinely
  fused complete scene retains the legacy complete-observation semantics.
- Two newer quality misses may retire a quality-confirmed region only when its entire
  footprint lies within the inspected crop. Other tiles, clipped edges, repeated or
  older captures, future timestamps, and invalid crops cannot accumulate misses.
- Quality completion now forwards empty scans too. Unpromoted hits can prevent a
  false contradiction of an existing region but cannot insert durable regions, update
  their geometry, or renew their positive lifetime. Only the existing independently
  confirmed ready set may do so. Older/replayed evidence cannot overwrite newer data.
- Existing document/surface/capture/motion fences, dormant reentry behavior, bounded
  storage, TTLs, and matching threshold remain. Fixed metadata accounting increases
  by 16 bytes per entry for provenance/time state. No detector, text, or trajectory
  tuning and no TTL extension is included.

The transient single-use late-quality presentation path is unchanged. Current-only
sources still cannot enter the durable cache. These may require additional work if
device evidence shows residual flicker; do not claim that every avatar issue is fixed.

## Validation

The isolated exact staged source tree `3a1634c725a3424677f5b1a148fde93f9e899354`
passed all 759 unit tests, `lintDebug`, `assembleDebug`, and
`assembleDebugAndroidTest` together with `-PimmediateQualityExperiment=true`.
Ten new tests cover lane classification, continuity, retirement, cropped/old/replayed
evidence, one-hit rejection, lifetime and document changes. Only this verification
prose changed afterward. The first local attempt suffered a JVM native allocation failure, not a
test assertion; after confirming the daemon had exited, a single-worker 1536 MiB
single-use daemon completed the focused run successfully.

Private signing run `35470039225` succeeded for exact source
`fdf5a72bd7c172c50cecd78ab913a8bcb84e6e87`. Compact artifact `10592777104` was
downloaded and verified: source provenance, immediate-quality enabled, no published
release, APK checksum, and compatible signer all match.
APK SHA-256: `de095c236661277e96bdb3bc4259ef9ea8a696c7aadac43b1787c520fe571896`.
Signer SHA-256: `3ad7c66a3b50ddc0d71b8907f7f91926e39287f2c4f1def67831a30d439260dd`.
On September 20 the user explicitly requested installation. Wireless mDNS identified
the same Pixel 8 Pro at its new endpoint; `adb install -r --no-streaming` succeeded.
An independent installed `base.apk` SHA-256 readback exactly matched the verified
artifact above. The Accessibility service rebound in PID 17607. No data was cleared.

Initial learning diagnostics show two offers, one failed acquisition (36 ms), zero
anchor reads/samples and no applied calibration; unknown-owner rejection is still
present. This proves admission progressed beyond the previous zero-attempt state,
not successful learning or scroll improvement. User acceptance and controlled timing/
stability measurements remain pending. No screen recording or collector was started.

Existing diagnostics
report fast/quality timing and drops, `QUALITY_BACKFILL_COMMIT.cacheEvicted`, cache
query counts, render-layout duration and membership. Capture age, preprocessing,
inference, postprocessing, publication latency, avatar continuity, group changes,
and false-positive duration must be measured on-device after signing. No performance
or user-acceptance conclusion follows from the unit tests.

## First post-install historical diagnostics

A bounded read of existing PID 17607 `ScreenshotA11y` logs (not a recording, replay,
or live collector) retained 25.792 seconds of ordinary usage. Raw counts exactly match
the parser: 42 fast publications, seven quality completions, and one immediate-quality
publication; no unparsed overlay/quality records. This unscoped population is not a
controlled before/after benchmark and mixes activity/scroll/initialization conditions.

| Metric | Median | p95 |
| --- | ---: | ---: |
| Fast capture-to-overlay | 191.5 ms | 395.35 ms |
| Fast preprocessing | 3 ms | 7 ms |
| Fast inference runtime | 60.5 ms | 137.45 ms |
| Fast postprocessing | 1 ms | 4.85 ms |
| Fast publication interval | 424 ms | 846 ms |
| Quality capture-to-ready | 455 ms | 1425.2 ms |
| Quality bitmap preparation | 68 ms | 91.8 ms |
| Quality preprocessing | 10 ms | 22.8 ms |
| Quality inference runtime | 261 ms | 461 ms |
| Quality postprocessing | 4 ms | 4 ms |
| Accessibility event age | 23 ms | 100.8 ms |

Fast publications reported a dropped counter of zero. Quality's cumulative dropped
counter ranged from 17 to 24; the history also contains 12 coalesced offers and four
cancelled runs. These counters have different meanings and must not be added together.
The single immediate-quality update was 14 ms ready-to-present and 441 ms capture-to-
present; its ordinary later-fast handoff was 364 ms after readiness. There is no
distribution claim from that single update.

Renderer/text layout stability, avatar continuity, perceived smoothness, false-positive
flashes and sustained throughput remain unverified. The tag-scoped read has no renderer
draw/consolidation records or known render-source references: missing evidence is not
zero instability. Twenty-one fast records report changed detector geometry, but that
does not measure the settled overlay geometry introduced by Pass 95. The latest service
check was bound with learning disabled and the device awake; it does not prove an active
protected-app test. No new build or installation is warranted from this history alone.
