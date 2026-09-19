# Pass 95: X static-body grouping and moving-mask size stability

## Evidence and scope

The user accepted Chromium for now and reported X scroll differences, repeated size
adjustments on moving detections, a fragmented static lower image, and a flickering
stationary profile censor. Passive Pixel captures from installed Pass 94 were saved
privately; no captures, page content, or images are committed. The phone was released
for normal use after capture. No further phone interaction is required for local work.

A sanitized numeric layout reproduces a static connected body split into overlapping
groups: one near-identical raw region carries both face and chest labels. The face
label acts as contradictory person ownership, while the body-span cap also prevents
the full chest/abdomen/pelvis union. A separate test demonstrates that translating a
mask defeats the old four-edge position deadband and permits tiny size changes.

## Render changes

- Treat face/body labels with raw-box IoU at least 0.85 in the same source basis as an
  ambiguous head hint. Preserve both masks and all detections; only the grouping veto
  changes. Ordinary/padded overlap and foreign-source labels do not suppress heads.
- Permit an overlapping body chain to span three member heights/widths and reuse the
  existing 4.5-times-largest-member area cap. Other connectivity, head separation,
  source-basis, input-count, member-count, and fill guards remain in force. This is a
  geometric presentation heuristic, not person recognition; occasional over-merging
  remains possible, as explicitly accepted by the user.
- Separate size deadband from translation: outside stationary jitter, follow the
  proposed center with settled dimensions for small size changes. Expand immediately
  to contain all raw member boxes; meaningful resizes remain immediate. Text bypasses
  this behavior. There is no new per-frame easing or scroll-trajectory change.

## Verification and remaining work

Worktree overlay tests pass, including the captured regression, moving singleton and
body-union size stability, raw coverage, text exclusion, distinct heads, source-basis
isolation, and the existing adjacent-card regression. The isolated exact staged source
tree `f23eb924a78db8da502448d8dc5e8f930e76a995` passed 744 unit tests, `lintDebug`,
`assembleDebug`, and `assembleDebugAndroidTest` in the same Gradle invocation with
`-PimmediateQualityExperiment=true`. Only this verification prose changed afterward.
The export excludes unrelated dirty cache/quality experiments and uncommitted learning
admission changes. Local APKs are not signed with the installed release identity.

The avatar disappeared from cached renderer inputs in repeated stationary layout
dumps without any live avatar track. That is upstream of grouping and remains under
investigation; this render change does not claim to fix it. X learning admission and
touch-independent episode changes are also a separate unfinished checkpoint.

No new candidate is installed. Capture age, queue drops, preprocessing/inference/
postprocessing time, publication latency, on-device layout cost, size-change rate,
group split rate, and avatar presence continuity have NOT been measured on this
candidate. Existing layout duration/frozen-geometry/held-group diagnostics remain
available. Recollect comparable pipeline traces and layout sequences after signing
and explicitly obtain the user's perceived speed, lag, scroll behavior, text stability,
and false-positive verdict. Unit tests cannot establish real-device improvement.
