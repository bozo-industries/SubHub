# Pass 86: candidate dependency and physical-validation boundary

## Current evidence

At this checkpoint only `emulator-5554` is connected. The Pixel-availability
request is unanswered. No physical-device session, installation, or trace is
running. This is not a verified wait on a live measurement process.

The remaining ScreenshotAccessibilityService working-copy delta is 1,311 added
and 221 removed lines. It combines multiple runtime decisions rather than a
small collection of missing helper declarations. Review of the relevant paths
identifies these coupled groups:

| Group | Working-copy implementation | Required evidence before acceptance |
| --- | --- | --- |
| Execution providers | DetectionEngine puts NNAPI ahead of the cached provider; service forces CPU fast initialization for atomic scenes | Pixel startup, actual execution placement, fast/quality overlap cost, recall parity |
| Quality scheduling | QualityBackfillRunner, QualityConcurrencyGovernor, source mailbox, reservations and deferred retries | Sustained capture age, dropped frames, fast runtime tails, quality cadence and source release/cancellation |
| Detail recovery | QualityTilePlanner, preparation path, source-coordinate remapping | Whole-frame plus crop recall and timing without starving fast captures |
| Late presentation | QualityPresentationAligner, late-result mailbox, later fast publication | Same-object correspondence, alignment, no one-frame false positives or second-wave jumps |
| Scroll-coordinate basis | RenderSourceTimeline integration, async viewport anchors, provisional surfaces, overlay/cache changes | Correct origin/phase during scrolling, reversals, window changes and source gaps |

This is a dependency map, not a complete approval of every hunk. The provider
gate currently recognizes CPU-fast plus NNAPI-quality by provider names. That
predicate alone does not prove all model work executes on separate hardware.
The new governor monitors fast-runtime tails, but passing its unit tests does
not establish contention behavior on the Pixel.

Passes 83--85 recovered shared-face recall and rejected two unsafe blanket
category matches in the working-copy prototype. Those are independently useful
safety results, not measured improvements to the physical scrolling experience.
The quality IoU default remains an uncommitted 0.18 experiment versus committed
0.35; it has not been adopted or calibrated here.

## Next measured experiment

Resume with a connected Pixel and the user's availability confirmation. Preserve
the exact artifact/source fingerprint and settings for each baseline/candidate;
do not equate the frozen Pass 33 artifact, clean HEAD, and current working copy.
Package the agreed candidate with the existing compatible signing path only
when device evaluation can proceed. Do not silently import the entire service
delta as a supposedly tested baseline or claim that helper-level tests prove it.

Collect matched capture-age and drop accounting, preparation, preprocessing,
model runtime, postprocessing, and overlay-publication timing together with
visible first detection, sustained scrolling/reversals, stable text groups, and
false-positive flashes. Verify raw/parsed publication counts and recording
timing before interpreting any comparison. Obtain the user's speed/lag/alignment
verdict; use it to choose the next change instead of selecting provider or
correspondence heuristics from emulator behavior alone.

## Verification and status

Read-only source/diff and device inventory checks completed. No application code,
device preferences, signing configuration, or installed APK changed in this
checkpoint. No new APK or benchmark was produced; all requested runtime and
stability metrics are unmeasured for this checkpoint, not zero. Documentation
is checked with `git diff --check`; previous build/test results are not presented
as fresh runtime evidence.

Accessibility acceptance remains unproven and MediaProjection optimization has
not started. The next measured pass is blocked on physical-device availability;
the full objective is not complete.
