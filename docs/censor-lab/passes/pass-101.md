# Pass 101 — optional whole-person coverage integration

## User-visible behavior

Censor settings now offers **Detected areas (default)** and **Whole person**. The new
option retains controller PIN and both pack-lock checks, and is portable through the
censor pack allowlist. Unknown or incorrectly typed values fall back to detected areas.
Whole-person processing remains entirely on-device; text, detector selection, tracking,
statistics, penalties, and the default setting are unchanged.

Both Accessibility and MediaProjection publish provisional geometry in the same main
thread transaction as the original masks. A lazy NanoDet session consumes the fast
detector's detached compact pixels, without another screenshot/readback or body-part
inference. Quality-only passes explicitly disable unused person pixel preparation.

## Scheduling and source safety

- Person inference shares the existing fast executor and is queued only after raw
  publication. New fast arrivals cancel its pending/native work. It does not acquire
  the existing zero-wait fast gate, which would otherwise drop incoming fast frames.
- Person execution temporarily uses background thread priority and restores the prior
  priority afterward. A separate nonblocking admission lock excludes quality inference;
  quality model initialization also holds that lock. Existing governed fast/NNAPI
  concurrency remains intact.
- The mailbox retains at most one pending image. No indefinite admission retries.
  Model failure disables optional inference until settings reload; original coverage
  remains. Settings reset and shutdown cancel/release the lazy session.
- Accessibility results require the same capture/document/fast sequence/motion generation
  and capture-time age. Projection has its own source sequence. Main-thread publication
  additionally checks controller identity and the renderer's process-unique token.
- Unanchored Accessibility frames may expand only when the exact current tracker-assigned
  ID matches. Known anchor references additionally require matching origin, bias, and
  source timestamp. Uncertain capture phase does not start person coverage.
- A refined shape can be reused for at most 500 ms across matching raw publications,
  avoiding repeated provisional growth. Same category, coordinate basis, dimensions,
  high trigger overlap, and small size change are required. Reuse does not renew evidence;
  scheduled redraws enforce expiry even without a new frame. Clear/off/document changes
  remove retained shape state. The original masks are always included.

## Verification

Exact staged source tree `acb0266b1147791949bfe925f6fbdcc3fe7e22d8` was exported to
`C:/Users/user/Code/SubHub-pass101-integration-921d`, excluding unrelated prototypes.
`testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` passed in the same
invocation with `-PimmediateQualityExperiment=true`: **801 unit tests, zero failures**.

The matching x86_64 application and test APKs were installed on a task-owned, headless,
read-only `betasafe_play_api35` emulator at `emulator-5580`. The declared custom runner
was verified before invocation. Terminal result was **OK (4 tests)**:

1. Repeated bundled-model execution and cancellation.
2. Actual provisional/refined pixels, original coverage, stale-token rejection, and
   short refined-shape reuse.
3. Settings default/round-trip/invalid-type behavior and portable key.
4. Locked settings UI rejects a programmatic coverage toggle.

These are native component/UI tests, not evidence of a live, bound Accessibility pipeline.
The emulator was stopped after testing; its read-only disk changes are not persisted.
No Pixel connection or phone installation occurred.

## Instrumentation and remaining acceptance

Added versioned `PERSON_MODEL`, `PERSON_PROVISIONAL`, and `PERSON_PUBLISH` records for
model total/preprocess/runtime/postprocess time, capture-to-publication age, applied
refinement, submissions, queue drops, preemptions, and admission denials. Model total
includes cold initialization. `scripts/analyze_person_coverage.py` compares raw and
parsed record counts, checks run links, rejects unknown versions/fields, and leaves
missing timing distributions null. Its five parser fixture tests pass.

Live capture age, queue-drop rates, inference timings, publication latency, scroll
stability, real-image person accuracy, and perceived improvement remain **unmeasured**
for this candidate. Cold session initialization and cancellation latency on Pixel are
specific performance risks to measure. The existing pipeline trace remains necessary
alongside the person records. Prepare a private signed artifact, then obtain Pixel
evaluation and user feedback before calling the feature or overall goal accepted.
