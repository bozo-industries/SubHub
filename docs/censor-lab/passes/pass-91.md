# Pass 91: stable padded render layout after Pixel merge regression

## User verdict and evidence

The enabled Pass 90 candidate was reported better in trailing, but merging failed:
one screenshot blanketed a column across multiple image cards, while overlapping
torso censors remained separate. The user also reported repeated micro-adjustments
and annoying merge/unmerge oscillation. This is not Accessibility acceptance.

A subsequent device screenshot confirmed fragmented visibly overlapping censors.
The attempted bounded trace later contained no active recognition publications
(the service reported an unprotected foreground app). That interval is not valid
performance evidence; zero parsed publications is not zero work or improved latency.
Private images/UI dumps/logs remain ignored under app/build/reports/device.

## Corrected rendering model

1. Apply appearance padding to each input once, before grouping; do not pad the
   resulting union again. Head association still sees unpadded source geometry.
2. Remove the union between current group geometry and historical smoothed geometry.
   That union could form a tall corridor even when the current boxes did not touch.
3. Bound component width/height as well as area and reject an envelope swallowing a
   second distinct head. Allow up to 32 duplicate members, because eight cached copies
   previously exhausted a group's capacity before real adjacent body parts joined.
4. Hold small footprint jitter (3--12 source pixels, size-relative) only while current
   raw detector coverage remains inside the held footprint. Newly uncovered raw pixels
   still expand coverage immediately. No detector confidence or cache policy changes.
5. Keep a brief split for up to 500 ms only with a tiny gap (at most six source pixels),
   consistent coordinate basis, compatible head assignment and bounded expansion.
   Real gaps, distinct heads, basis changes and content clears invalidate that hold.
6. Allocate separate stable presentation IDs, preserving source IDs for diagnostics.
   Layout-selected geometry no longer receives another per-vsync geometry easing pass;
   viewport motion still moves censors continuously during scroll.

These are practical geometric heuristics, not perfect person identification. The user
explicitly permits occasional over-merging. The goal is stable, plausible coverage,
not a new recognition model or unbounded historical masking.

## Verification and observability

Isolated staged source tree `b0e33425f0287a06cede6536ce19bf3e60dd9cc9` passed 654 tests,
lintDebug and assembleDebug with immediate-quality enabled. New fixtures cover padded
contact, no double padding, frozen jitter, split grace/expiry, real-gap rejection,
historical-corridor rejection, source-basis reset, coverage growth, duplicate-capacity,
image-column separation and unique render IDs. Numeric diagnostic JSON has round-trip
and truncation fixtures. These checks do not establish the on-device visual outcome.

An explicit shell diagnostic is available through Android's existing service dump:

    adb shell dumpsys activity service com.subhub.app/.service.ScreenshotAccessibilityService render-layout

It reports bounded raw/output target geometry, memberships, padding, layout cost and
stabilization counts without page text or pixels. It does not navigate away, launch
UiAutomation, enable continuous collection or grant an exported control endpoint.
It reports target coordinates, not compositor/photon measurements. Truncation is explicit.

Capture age, queue drops, preprocessing/inference/postprocessing, publication latency
and sustained scroll/text stability are not remeasured for this pass yet. Preserve the
immediate-delivery setting the user found better, install a fingerprinted signed dev
candidate, inspect the provided scene through the new dump and screenshots, then ask
for another human verdict. MediaProjection remains pending.
