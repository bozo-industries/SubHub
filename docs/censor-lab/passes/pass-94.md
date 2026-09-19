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

## Required next steps — do not call this feature complete

1. Build independent event/geometry endpoint alignment with uncertainty propagation;
   never create training truth from the renderer's own trajectory.
2. Supply stable Android surface/configuration identity without retaining page content.
   Current cache identity deliberately contains per-process salt and window identity;
   it must not be reused as a durable calibration key.
3. Integrate a bounded production observer that activates only during active censoring,
   cancels on scope changes/disarming and backs off after confidence or poor evidence.
   Existing DEBUG anchor sampling is not this feature and must not simply be enabled.
4. Persist only compact validated parameters, with compatibility/revalidation rules.
5. Apply learned timing and displacement safely to live presentation and capture/camera
   accounting without double-applying motion or reinterpreting historical cache data.
6. Validate overhead and alignment on multiple real apps/devices, including stop/reversal
   behavior, and obtain the user's perceived verdict. No new capture/inference/drop/
   publication timing population exists for this unwired core.
