# Pass37 — measure early anchor motion

Status: experimental, not accepted. Continue from the measured catch-up fix in Pass36; no physical-Pixel validation.

## Evidence

Existing opt-in `ViewportAnchorFreshnessAndroidTest` ran against the open emulator Chrome page with three anchors, survey mode and external touch gestures. Setup visited70 nodes/fetched140 in49ms. Eight stable warmups took6ms median/7ms p95. Before an anchor refresh failed during scrolling,161 samples and27 events contained29 source-time-confirmed between-event moving pairs (37 receipt-time pairs;8 still unconfirmed). Read cost median6ms/p9511ms/max13ms, with33 reads exceeding the stricter8ms budget. The instrumentation test correctly failed as inconclusive (`reason=5`, refresh failed), not as a production pass. This is positive signal evidence but does not establish continuous availability or a safe60Hz production budget.

## Next controlled experiment

Use the existing debug-only, single-worker/latest-only anchor sampler without changing its estimator or thresholds. An explicit instrumentation toggle sets only its private experiment preference; it grants no permission and does not arm protection. Reinstall the same app candidate to restart the service, verify `ANCHOR_ASYNC_START`, then repeat host-recorded gestures. Compare independent image attachment, read/drop/reset counts, and fast/quality latency. Return the flag to false if the experiment is inconclusive or regresses. Never promote on positive refresh counts alone.
