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

## Required next steps — do not call this feature complete

1. Supply stable Android surface/configuration identity without retaining page content.
   Current cache identity deliberately contains per-process salt and window identity;
   it must not be reused as a durable calibration key.
2. Integrate a bounded production observer that activates only during active censoring,
   cancels on scope changes/disarming and backs off after confidence or poor evidence.
   Existing DEBUG anchor sampling is not this feature and must not simply be enabled.
3. Connect local persistence and candidate revalidation to that observer.
4. Apply learned timing and displacement safely to live presentation and capture/camera
   accounting without double-applying motion or reinterpreting historical cache data.
5. Validate overhead and alignment on multiple real apps/devices, including stop/reversal
   behavior, and obtain the user's perceived verdict. No new capture/inference/drop/
   publication timing population exists for this unwired core.
