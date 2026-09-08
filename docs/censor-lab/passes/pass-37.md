# Pass37 — measure early anchor motion

Status: experimental, not accepted. Continue from the measured catch-up fix in Pass36; no physical-Pixel validation.

## Evidence

Existing opt-in `ViewportAnchorFreshnessAndroidTest` ran against the open emulator Chrome page with three anchors, survey mode and external touch gestures. Setup visited70 nodes/fetched140 in49ms. Eight stable warmups took6ms median/7ms p95. Before an anchor refresh failed during scrolling,161 samples and27 events contained29 source-time-confirmed between-event moving pairs (37 receipt-time pairs;8 still unconfirmed). Read cost median6ms/p9511ms/max13ms, with33 reads exceeding the stricter8ms budget. The instrumentation test correctly failed as inconclusive (`reason=5`, refresh failed), not as a production pass. This is positive signal evidence but does not establish continuous availability or a safe60Hz production budget.

## Next controlled experiment

Use the existing debug-only, single-worker/latest-only anchor sampler without changing its estimator or thresholds. An explicit instrumentation toggle sets only its private experiment preference; it grants no permission and does not arm protection. Reinstall the same app candidate to restart the service, verify `ANCHOR_ASYNC_START`, then repeat host-recorded gestures. Compare independent image attachment, read/drop/reset counts, and fast/quality latency. Return the flag to false if the experiment is inconclusive or regresses. Never promote on positive refresh counts alone.

## Live result and bounded recovery fix

`pass37-anchor-on` demonstrated missing measurements rather than presenter congestion: over10.115s,41 reads yielded26 accepted/presented samples,7 slow drops,4 invalid drops and7 baseline resets, with no additional delivery/mailbox drops. Several gesture intervals had no accepted samples for over2seconds. One read measured41px beyond the event camera and was presented, proving the signal is useful when available. A single read above16ms triggered250ms of silence; three timing misses discarded the baseline, which could only be reacquired after200ms idle.

Changed only timing-miss recovery: reject the over-budget sample, preserve its valid baseline, and back off exponentially from the display interval/minimum16ms to250ms. Structural and geometry failures still clear it; no read-age/consensus limits or worker priority changed. The narrow staged source passed28 sampler/geometry tests; the integrated app passed477 JVM tests, lint and paired APK build. Candidate SHA256 `5C2003079E27850A9DDFDB0C6F442F809F2FC8ED34A387B952567DE2A2B03822`, frozen with working patch and17 untracked source copies in `pass37-candidate`.

`pass37-anchor-retry` completed29 fast publications over11.184s, capture-age median141/p95254ms and10 quality completions. Baseline losses and long sampling gaps remained. Its starting page position differs from the earlier fixed-ROI video fixture; the low19-sample fixed-template output must not be interpreted as improvement. Visible static displacement was observed, so the experimental flag was explicitly restored to false (instrumentation passed), the app restarted, and restored process10674 produced no `ANCHOR_ASYNC_START`. Recorders/collectors are stopped. Neither ON run is accepted.

## Capture-coordinate defect: next required work

Let `D` denote document-camera displacement, `B` the anchor-measured displacement not represented by that camera, and `S` a captured screen-space box. Current world conversion stores `S + D_capture`; the renderer adds `-D_now + B_now`. The source frame already contains `B_capture`, so correct source-relative rendering additionally needs `-B_capture`. A fresh still-frame box can therefore receive the same bias twice. This is code-level algebra verified in `RenderTrackSnapshot.fromWorld`, `ContentSpaceCoordinates.toWorld` and `CensorOverlayView` drawing; it does not prove the cause of every observed static offset. The trace includes residuals+20/-225px, but its final baseline reports zero residual.

Next: establish an explicit capture-time presentation reference for render inputs, with structural/time fences and tests for fresh, carried, cached and quality-only geometry. Do not simply reset the camera at publication, feed visual guesses into tracker/cache authority, or apply one current bias indiscriminately to mixed-age tracks. Keep the sampler off until this bridge and independent video checks pass.

## Platform research guard

Sequential retained-node `refresh()` calls are separate source requests, not a coherent multi-node frame. A common-ancestor prefetch is not an active-scroll shortcut: the Android15 release client explicitly removes prefetch flags for bypass-cache requests, disabled caches and scrolling windows. Verified against the [Android15 client source](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-15.0.0_r1/core/java/android/view/accessibility/AccessibilityInteractionClient.java), not only current AOSP main. Even `FLAG_PREFETCH_UNINTERRUPTIBLE` offers no documented compositor-frame timestamp guarantee. Future anchor consensus must account for individual read times; do not assume three sequential6–11ms reads are simultaneous within a4px spatial threshold.
