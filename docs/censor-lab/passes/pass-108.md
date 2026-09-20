# Pass 108 — same-direction slowdown without a reverse correction

## Recorded defect and reproduction

The Pass 105 recording of installed Pass 104 shows opposite-direction mask movement
at video frames 1243–1244 while source features continue down. Its main-thread scroll
inputs include 485 pixels followed by 282 pixels, with source intervals 102 and 115 ms.
The old forecast is ahead of the new measurement; its error correction pulls the display
backward even though the input direction has not reversed. The trace reports prediction
539 pixels, then 22 pixels. This is separate from Pass 106's duplicated companion event.

`ScrollSlowdownRegressionTest` retains the recorded source timestamps, main-thread input
times and 11 signed deltas. The old implementation fails the first slowdown assertion
eight milliseconds after the 282-pixel input. Both directions and 60/120 Hz sampling are
covered. This proves the event-trajectory mechanism, not that it explains every mask in
the video: person-box replacement and per-track steering can also change footprints.

## Change and tradeoff

When a continuing same-direction stream finds the display already ahead of authority,
retain that display-only lead until the bounded forecast catches it. Do not interpret
deceleration as a reversal. Fresh-event expiry still starts the finite return to exact
authority; a real zero/reversal, reset or long-gap acquisition retains its correction path.
The authoritative camera coordinate is never changed by the hold. Evaluation remains
order-independent, and timing configuration cannot move an already-created trajectory.

This avoids the demonstrated reverse correction but can briefly hold a mask that was
already too far ahead. It is not a claim of perfect alignment or extra measurement data.
The existing forecast cap is retained; no larger masks, model changes or long-lived
prediction are used to hide the error. Prediction diagnostics report the retained lead.

## Review of the last four available passes

| Pass | Actual evidence available | Finding carried forward |
| --- | --- | --- |
| 102 | Pixel video/trace of installed 101; 1,535 decoded frames, 18 dense sheets | Stationary pulsing, 49.8 ms reduced-coverage interval, 248.3 ms overcoverage excursion, scroll displacement; person telemetry incomplete. |
| 103 | Code/unit/parser checks; no new Pixel recording | Refinement handoff lifetime/admission repair, not visually accepted. Independent person scheduling is now removed by 107. |
| 104 | Code/native checks and signed installation; no separate extra recording | Bounded provisional geometry and containment-only decorations. Its actual Pixel evaluation is the recording in 105. |
| 105 | New Pixel video/trace of installed 104; 2,218 decoded frames, 34 dense sheets | Still opposite-direction movement, nine selected stationary footprint-change intervals, partial-overlap labels, and transition leakage. Rejected as a completed visual fix. |

The surviving private videos are in the Pass 102 and 105 device-report directories.
No additional Pass 103/104 videos are invented, and the deleted older Pass 101 artifact
cannot be reconstructed by relabeling these recordings.

Fast request-to-publication medians were 121 ms (102, 53 publications) and 136 ms
(105, 96 publications); inference medians were 34 and 31 ms. Both sampled fast-drop
counters stayed zero. Preprocessing/runtime/postprocessing medians were 2/31/1 and
2/28/1 ms respectively. These are different scene/population samples, **not a controlled
before/after speed comparison**. See each pass for p95/max and parser completeness.
Only 105 captured person timing: median model 78 ms and screenshot-to-refinement 292 ms.
Neither that timing nor the second network's removal proves a scroll fix.

## Verification and acceptance

The focused pure-Java suite passes all **48 tests**, including existing constant-speed,
irregular-delivery, continuity and learned-timing checks, and new recorded slowdown,
actual stop/reversal, faster catch-up, long-gap/reset and final-settlement fixtures.
Three older assertions that required same-direction deceleration to snap backward within
32 ms now instead require a bounded hold with no added lead and finite settlement. Their
actual-reversal/fallback assertions remain, alongside explicit-zero and reset checks.
Exact staged source tree `181e4556c7766b97715974e94577f493796ce20d`, exported to
`C:/Users/user/Code/SubHub-pass108-slowdown`, passes **808 unit tests**, `lintDebug`,
`assembleDebug` and `assembleDebugAndroidTest` together with the immediate-quality
experiment enabled. Unrelated cache prototypes remain excluded. The renderer/settings
native checks were completed against the immediately preceding Pass 107 cleanup;
this pass changes only the platform-independent trajectory and its tests.

No new live capture-age, queue, preprocessing/inference/postprocessing, publication or
Pixel stability metrics are claimed. The Pixel is unchanged on Pass 104. Combine the
verified single-model cleanup and scroll fixes in the next private signed candidate,
then obtain a fresh dense recording and the user's perceived verdict before acceptance.
