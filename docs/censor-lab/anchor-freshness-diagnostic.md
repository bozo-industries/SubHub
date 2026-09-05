# Retained-node freshness diagnostic

Status: instrumentation-only feasibility probe; **not approved for production polling**.
This does not change censor placement, caches, tracker authority, app settings, or gesture input.

## Question and method

Can retained accessibility leaf nodes expose common viewport movement before the next scroll
event is produced, at a cost suitable for frequent background sampling?

`ViewportAnchorFreshnessAndroidTest` is disabled by default. The harness must prepare the target
and wait for `ANCHOR_SHADOW_READY` before externally injecting gestures. Enable with
`-e enableAnchorFreshnessProbe true`; the target argument is `anchorFreshnessTargetPackage`
(default `com.android.chrome`). Use `com.subhub.app.test/com.subhub.app.SubHubTestRunner` and
build the target and test APKs together before installation.
`anchorFreshnessAnchorCount` accepts 3 through 5 (default 5).

The test preserves other accessibility services and restores its temporary automation flags
and thread priority. Setup has separate bounded root readiness and traversal deadlines; the
traversal caps depth at 32 and node fetches/visits at 192, prunes outside-viewport branches,
retains empty-bounds virtual ancestors, and selects up to five distinct leaf bounds.

After the first event arms sampling, reads run independently with a 16ms fixed delay, one
in flight and no backlog. At least three anchors must agree on translation. A later event
confirms a candidate pair: receipt-order confirmations are weak evidence; only pairs whose
read ended **before the immediately next event's source timestamp** count as source-time-confirmed.
Skipped callback sequences cannot prove this and remain unconfirmed. Numeric
logs distinguish both cases. No fresh-root lookup occurs during measurement.

The session is capped at 20s and stops after an observed refresh batch exceeds 8ms. Android's
synchronous node refresh has no caller-provided timeout: the guard prevents subsequent work,
not a long first Binder call. Enabled inconclusive outcomes fail explicitly; a default-disabled
skip or a zero transport exit code is not successful evidence. Even a positive diagnostic is
not a production or visual-alignment gate.

An explicit `anchorFreshnessSurveyOnly=true` mode separates signal discovery from feasibility.
It performs eight static warm reads, records the first/warmed costs and every 8ms exceedance,
and allows collection until an observed 50ms hard cutoff. Survey completion uses reason 12,
not strict success; absent freshness or an early stop still fails. `productionEligible=0` is
unconditional in both modes. Changing the diagnostic collection cutoff is not changing the
production budget. The harness must acknowledge its collector marker before launching the
test, then require the matching session's READY before gestures; starting a logcat process
does not itself prove subscription readiness.

## GPU-emulator observations, 2026-09-05

Device: `emulator-5554`, restored virtual Pixel profile 1344x2992, 480dpi, normal font.
Application SHA256: `8ABB890BC520D48CA2E34642F8A2A04FF016A84421767AB43D775A6350C8EC46`.
Final paired instrumentation SHA256:
`295BAB4FFDF5F5041A94F386C4D50DBFAB538513BC84B170A5557B68F9818E3C`.

1. Early setup returned no root; no motion evidence. Root readiness handling was corrected.
2. A depth-8 traversal found only one anchor (22 fetches/visits). The saved hierarchy placed
   Chrome's WebView at depth 8 and its content down to depth 25, so this was a diagnostic
   discovery limitation, not evidence that Chrome offers no usable nodes.
3. The bounded deeper traversal found five anchors at 160 fetches/87 visits in 123ms. The first
   five-node refresh took 15ms, exceeded the 8ms guard, and stopped with `reason=6`. There were
   **zero scroll events, zero measurement samples, and no injected gestures**. Instrumentation
   reported a failure, correctly; the ADB transport still exited zero.

Final raw evidence is local under
`app/build/reports/device/anchor-freshness-emulator/retry-20260905-112325/`.
The prior discovery result is under `retry-20260905-111512/`, including recovered scoped logcat.
Raw hierarchies and captures are private local artifacts, not repository files.

## Interpretation and next decision

The unchanged five-node strategy has not met the intended read-cost budget on this emulator.
A single first read does not establish warmed cost, Pixel cost, freshness, or lack of freshness.
No first-detection, queue-drop, model-stage, overlay-publication, or visual-stability improvement
is claimed: this was not a live pipeline performance pass and collected none of those metrics.

Before choosing a production polling design, separate cold/read-cost measurement from signal
freshness and quantify per-node/warmed costs with a bounded diagnostic. Consider fewer
spatially independent anchors or lower asynchronous sampling cadence only if the measurements
support it. Do not relax the success criteria merely to obtain a green result, reconnect the
old main-thread poller, or infer that faster sampling is safe from this setup-only run.

## Follow-up surveys: signal exists, cost is not yet safe

The application APK remained unchanged. These are exploratory emulator runs with different
anchor sets/scroll positions, not a matched performance A/B or a Pixel calibration.

| Run suffix | Actual anchors | Warm median/p95 | Moving reads | Moving median/p95/max |
| --- | ---: | --- | ---: | --- |
| 113215 | 5 | 7/9ms | 26 | 13/31/91ms |
| 113509 | 3 | 5/7ms | 18 | 8/14/58ms |

Both hit the 50ms survey guard and correctly failed. The table separates moving reads from
warm-up; the original summary's overall read percentiles combined both. The first run used
five anchors despite a requested three because count configuration was not implemented yet;
the later source implements and validates that argument. Test APK hashes were respectively
`05BC45EF8DE8DD8AEB96287F3197968094708B81C710A9881CF2042563585D3C` and
`CAD426743C35EF65B0740F6292890981B5A7FF9E0BDE31B1E25756D1E818ED19`.

The three-anchor trace originally reported three source-time confirmations. Independent
review rejected one because it skipped an intermediate event. Two remain valid under the
stricter adjacent-event rule: common -15px movement read in 5ms completed 37ms before event 2's
source time; common -12px movement read in 9ms completed 63ms before event 5's source time.
The immediate-next-event requirement is now implemented and regression-tested. These are
evidence of updated node positions ahead of event production, not proof of rigid page motion
or correct rendered overlays; resize/clipping/animation controls and video validation remain.

An intervening 113352 run collected static warm reads only: its harness missed the start marker
before logcat subscribed, so it injected no gestures. It contributes no moving evidence.
The harness now confirms the marker is visible before launching instrumentation.

Next implementation investigation: a latest-only **off-main** three-anchor sampler that never
makes rendering wait; reject stale/slow samples and structural changes, measure rigid geometry,
and keep measured presentation separate from authoritative tracker/cache coordinates. First
prove its timestamp/rebase/fallback behavior with automated tests and a recorded emulator run.
Do not accept the current occasional 58-91ms reads on a Choreographer/UI callback.
