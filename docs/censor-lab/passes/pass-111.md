# Pass 111: remove scroll learning and source-clock native X reconstruction

Status: exact-index unit/lint/build and native framework checks passed; Pixel/user acceptance pending.

## Scope and evidence

The user requested removing the complete scroll-learning system and following static app
curves, specifically for the native X app, rather than X inside a browser. The previously
installed Pass 110 reported no applied learning events, so removing learning alone cannot
explain or fix the observed alignment problem.

Read-only analysis of X 12.31.0-prod.01 found RecyclerView's ViewFlinger using framework
`OverScroller.fling`, default friction, and per-frame `computeScrollOffset`/current-position
increments. The separate smooth-scroll method uses `startScroll` and an interpolator;
drag and smooth-scroll must not simply be treated as flings. The complete base-APK scan
covered all 12 DEX files after fixing rejection of legal empty index tables. Its complete
class hierarchy has no app-specific RecyclerView subclass. This is static evidence, not
a live observation of which timeline produced a particular event.

The fixed spline and physical coefficients come from
[AOSP OverScroller](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/widget/OverScroller.java),
blob `abb147108cac6eb42840772fedf1e2ccf7c8db59`. The implementation preserves Apache attribution.
This reference is not claimed to be the exact Pixel Android 17 build.

## Changes and limits

- Delete the learning observer, acquisition surface, calibration scale, episodes/budget/key,
  profile codec/repository and reference aligner, together with their runtime hooks and
  learning-only tests. No stored phone data is cleared; old profile files are never read.
- Native reconstruction is restricted to `com.twitter.android` events declaring the exact
  `androidx.recyclerview.widget.RecyclerView` class. Only authoritative pixel deltas use it.
  Indexed guesses retain the existing conservative fallback. Browser/other-app event
  reconstruction, experimental anchors, detector models, thresholds and cache matching
  remain unchanged.
- No saved calibration, training, app-specific gain or background learning work. Two contiguous
  intervals can identify physical spline parameters by bounded deterministic inversion; a
  third must independently agree before spline continuation is used. Drag/acceleration,
  reversal, gaps and poor fits use a linear/endpoint-velocity fallback. Accessibility does
  not expose touch/fling state: admission is a conservative shape check, not certainty about
  the app's internal state, and a smooth-scroll curve can still be locally ambiguous.
- Evaluate displacement at event-source time plus actual elapsed time, including callback
  delivery age. Preserve visible position on continuing callbacks and bound missing-input
  lifetime. An already-expired callback cannot obtain a fresh prediction interval.
- Native lead is bounded by 1.25 event displacements plus this callback's physical delivery-age
  continuation, and at most 45% of the viewport (matching the existing polled-presentation
  safety envelope). This avoids the previous 18% cap forcing large catch-up steps in fast
  native motion. First observations invent no cadence or fling. Reversal/stop, stale ordering,
  reset and discontinuity have explicit correction/reacquisition behavior.
- Prediction remains display-only: no camera rescaling, tracker/cache distance changes or
  inference changes. Trace record schemas are unchanged. The Downloads button from the
  previous independent checkpoint is included in the combined candidate.

## Verification

Focused tests cover the fixed table, known physical fling inversion, drag/acceleration/reversal
rejection, independent third-interval validation, source-vs-delivery clocks, stale/ordered
events, acquisition, reset, bounded lead, callback continuity, native routing and the recorded
Pass 105 slowdown. The exact-index isolated snapshot passes all 769 tests with no failures,
errors or skips, plus `lintDebug`, `assembleDebug` and `assembleDebugAndroidTest` in the same
Gradle invocation, immediate quality enabled. Two workers and an 8 GiB process-tree limit
were used. The test total excludes the deleted learning-only suite; existing unrelated
cache/harness prototypes are not included.

Six native API 35 emulator tests pass with the declared custom runner: framework fling
distance at six positive/negative velocities; 24 intermediate positions bounded by measured
framework call times against the fixed spline; and the four Downloads/UI integration tests.
Both APKs were rebuilt together after adding the intermediate-position check. These tests ran
in a separate disposable emulator, leaving the user's visible UI-review emulator untouched.

An 8 ms steady-drag replay spans 80/120/160 ms callbacks, 0.5/2/4 px/ms motion and 0/20/40 ms
delivery delay. At 160 ms, 4 px/ms, 40 ms delay, mean position error changes from 214.313 px
to 1.139 px, and maximum 8 ms step from 107.760 px to 35.636 px. Callback-time position jumps
are zero. These are idealized synthetic results, not Pixel/X performance measurements.

Require a fresh native X Lab and the user's verdict on alignment, lag, reversal/stop, text
stability and false-positive flashes. Existing capture/inference performance evidence belongs
to Pass 110; there is no new real-device latency claim for this candidate yet.

## October 7 private signing and installation

Private signing run [37594444886](https://github.com/confiteor48/SubHub/actions/runs/37594444886)
passed release tests/lint/build and signature verification for exact source
`e2b0b7a2614384804bbda22ad2bed154e101a610`, immediate quality enabled and public release
disabled. Compact artifact `11470107231` was checked against its provenance, checksum
and the existing signing lineage. ARM64 APK SHA-256:
`14086169736e537b563c87d51a60f3140dcdfeb800a0bdc352017aecf2086385`.

Replace-install on Pixel succeeded without clearing app data. The installed `base.apk`
was independently pulled back and matched that exact hash. Version is 0.6.3/code19;
device-local update time is October 7, 10:37:06. The previous Pass 110 APK was backed up
locally before replacement. Accessibility remains enabled and the service rebound.
The initial render diagnostic was inactive because the foreground app was outside the
selected protection list; this is installation/binding evidence, not performance evidence.

No tag or public release was created. The requested native X trial and fresh Lab remain
required before declaring alignment or the broader goal successful.
