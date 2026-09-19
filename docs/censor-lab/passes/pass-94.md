# Pass 94: automatic scroll learning — evidence-gate checkpoint

## User direction

The user reports that Pass 93 looks a lot better. The next requested feature is
automatic learning during ordinary app use, per app/device/scroll surface, without
a lab recording mode or hand-authored app profiles. The collection-scope question
has not been answered; implementation proceeds with active-censoring-only scope.
Broader foreground-app monitoring is not enabled.

## Implemented core, not yet a running feature

- `ScrollLearningKey` separates package, app version, structural surface digest,
  viewport dimensions, density, rotation, refresh rate, axis and event-evidence type.
  It has no unique device identifier; storage is intended to remain device-local.
  Raw page text, URLs and runtime window labels are not accepted as surface keys.
  The Android adapter must still produce and validate genuinely stable structure;
  hashing an unstable identifier would not make it durable.
- `ScrollCalibrationLearner` accepts only fresh, independently measured, scope-matched
  displacement pairs over aligned intervals. It rejects self-predicted evidence,
  weak anchor consensus, slow reads, uncertain geometry, duplicates and overlapping
  intervals. It uses robust median displacement scale and timing statistics.
- Twelve training pairs across at least two gestures can propose a model. Six
  held-out pairs from a later gesture must validate it before a profile is exposed.
  Elapsed time alone never confers confidence. Inconsistent or insufficient data
  produces no profile. Three consecutive fresh monitoring contradictions revoke an
  existing profile, including sign changes or mappings outside the learnable range.
- `ScrollLearningBudget` bounds attempted probes, not just useful samples: a six-second
  burst, 96 attempts, 256 ms cumulative read work, a 16 ms individual read deadline and
  30-second cooldown. Lease tokens prevent a stale completion charging a newer burst.
  These are common resource/safety bounds, not app-specific scroll profiles. The
  runtime owner must obey the gate and cancel/recycle its reads; this class does not
  itself schedule or interrupt Android calls.

## Verification

Exact staged source tree `d34aa30fd3920ac968e16c4a98481da5dc5c68c1` passed 680 unit tests,
lintDebug and assembleDebug with immediate quality enabled in an isolated export.
Sixteen new tests cover held-out validation, outliers, incomplete evidence, revocation,
disarming, configuration separation, stale/future scope, duplicate evidence and probe
budgets. No new data collection, device installation or runtime behavior is enabled by
this checkpoint; the Pixel remains on Pass 93.

## Aligned evidence and local persistence checkpoint

`ScrollReferenceAligner` retains up to 96 independent anchor observations. It aligns
event source intervals, not callback arrival times, to short locally consistent
geometry segments. It never extrapolates missing endpoints or bridges different
anchor origins. Interpolation carries read-time, geometry-consensus and local
curvature uncertainty; pairs exceeding the learner's uncertainty limit are rejected.
Sparse or staircase-like provider updates do not become precise calibration truth.

Geometry must be fresh when sampled (32 ms age, 16 ms read duration). The learner can
use that retained history to match events delivered up to 500 ms later. That bound
applies to historical calibration evidence only; no live-pose freshness limit was
relaxed. The end-to-end fixture recovers a known physical scale from independent
geometry without using overlay predictions, including delayed event delivery.

`ScrollProfileCodec` and the worker-only `ScrollProfileRepository` provide a bounded,
versioned format for at most 32 validated profiles. Entries expire after 30 days;
unknown schemas, unknown fields, invalid values and clock rollback fail closed.
Atomic writes receive an independent readback. Storage is app-private and under
`getNoBackupFilesDir()`, with no frames, page text, URLs or unique device identifiers.
The runtime adapter must explicitly attest that a surface key is durable before saving.
Loaded profiles are only candidates: six fresh independent holdout pairs are required
before reuse. File corruption or failed validation cannot grant motion authority.

Exact staged source `02a6b030b043a0b529df289a3a2bcee84012e029` passed 698 unit tests,
lintDebug and assembleDebug in an isolated export with immediate quality enabled.
Tests cover the numerical evidence-to-profile path, no-extrapolation/uncertainty
rejection, origin changes, bounded history, schema round trips, expiration, corruption,
file-size bounds and fresh validation of saved candidates. Android atomic-file I/O
has compiled but has not yet been exercised through the running feature on-device.
These components are still unwired: no new collection, profile file or device update
has been activated by this checkpoint.

## Android observation checkpoint — integrated, not yet applied or deployed

The Android service now owns a separate `AutomaticScrollLearningObserver` only while
censoring is active. Actual scroll events initiate worker reads. No idle discovery or
all-foreground monitoring is enabled. Its eight-event queue recycles evicted event
attachments, breaks missing-interval lineage, and coalesces invalidation work. These
temporary platform events are not logged or persisted. Numerical geometry is retained
only in bounded in-memory history.

`AndroidScrollLearningSurface` resolves the actual, non-sticky event producer and
transfers its nearest scroll owner's nodes to bounded discovery. Native durable
signatures require verified compiled Android resources and complete ancestor structure;
runtime window/unique IDs never enter them. Browser, unverified and truncated structures
remain session-local. The digest is only a prior-candidate lookup key: identical native
resource structures can occur in multiple instances, so fresh producer-scoped validation
is mandatory. Resource lookup and node acquisition run on the worker, not the callback.

Production discovery starts within that scroll owner, skips nested scrollers, allows
at most 32 fetches and shares a 16 ms deadline with acquisition and baseline reads.
All attempted/failed work is charged to the burst budget. An in-flight Android Binder
call cannot be forcibly bounded; an overrun is rejected, counted and triggers cooldown.
Read ticks stop after 250 ms without a scroll event and recycle all retained nodes.
Scope/disarm changes fence in-flight results. Multi-record, diagonal, clamped or
unpairable events invalidate calibration lineage instead of silently bridging it.

The observer connects independent geometry, interval alignment, training, holdout
validation, local prior loading and verified native-profile persistence. It never feeds
its own predictions back as evidence. Synthetic tests show accepted measurements and
validation across separate budgeted bursts; this does not prove that any Android app
will expose usable geometry. In particular, the 96-read/256-ms-work cap can require
multiple bursts, so a few seconds of wall time does not guarantee a profile.

The DUMP-protected `scroll-learning` service diagnostic reports schema-versioned numeric
counts, collection state, read cost, queue loss, alignment rejection, sample counts,
storage failures and any validated scale. It explicitly reports `applied:false`.
No new trace-parser record was added. No raw identifiers, page strings or images are
included in this diagnostic.

Exact staged source tree `4f54008030a015c50335e926b4deb9cd95e0d05d` passed all 720 unit
tests, lintDebug, assembleDebug and assembleDebugAndroidTest in one invocation, in an
isolated export with immediate quality enabled. The 22 new tests cover structural
identity/privacy, ownership, bounded queuing, stale scope, disarming, cooldown,
invalidation coalescing, source failures and end-to-end synthetic profile validation.
Both APKs compiled; no Android instrumentation or real-device observer run is claimed.

## Live application checkpoint — implemented, awaiting device evidence

The observer now publishes an immutable, scope-fenced handoff only after holdout
validation. Different producers, invalidation, shutdown, inactive scope and more than
35 seconds without an accepted monitoring reference revoke eligibility. Stored priors
still require fresh validation. Worker and application diagnostics remain separate:
the service reports actual applied-event counts, scale transitions and last active scale.

`ScrollMotionCalibration` converts only future raw event increments, before direction
filtering, and preserves fractional pixel residuals. It never rescales accumulated camera
coordinates. An independently validated mapping supplies authoritative physical deltas
to the existing tracker, capture timeline and overlay path exactly once. Unsupported
events and unsafe extrapolation return to the original producer behavior. A rejected
profile cannot repeatedly oscillate between coordinate systems on the same surface.

Scale activation/revocation advances the rendering document epoch, clears historical
world-cache/quality work and fences in-flight captures/publications. A second document
guard under the scene lifecycle lock prevents an old result from entering tracking after
the transition. Actual surface/document changes still invalidate both learning and
rendering epochs; a calibration-only change deliberately preserves the learning scope.
Existing on-screen tracks and camera position are retained, not multiplied or cleared.

The event trajectory uses learned cadence for acquisition and forecast lifetime,
delivery lag to shorten forecasts for unusually late events, and bounded measured jitter
for return timing. Actual event intervals still determine measured velocity. Configuration
does not alter the current displayed position or velocity. This is not a newly learned
deceleration model: the existing braking/return shape and travel cap remain intact.

Exact staged source tree `114fecc839971577937dda8f9a604293d6f967b7` passed all 736 unit
tests with no failures or skips, lintDebug, assembleDebug and assembleDebugAndroidTest
together in an isolated export with immediate quality enabled. The 16 added tests cover
handoff revocation/expiry, independent geometry through physical increment application,
fractional distance, invalid mappings, timing continuity and a synthetic fast-producer
acquisition comparison. These results do not establish Android acquisition success or
an end-to-end performance improvement.

## Required next steps — do not call this feature complete

Sign/install this development candidate and validate Android resource/node acquisition,
overhead, storage and alignment on multiple real apps, including stop/reversal behavior,
then obtain the user's perceived verdict. No new capture/inference/drop/publication timing
population exists yet. At this source checkpoint, Pixel remains on Pass 93.
