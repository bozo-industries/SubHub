# Pass38 — source-aware presentation coordinates

Status: implementation in progress. Experimental anchor presentation stays disabled until integration tests and independent video validation pass.

## Design contract

For each region, retain opaque render provenance: capture/document/window/viewport and anchor-origin identity, actual source time, and measured source bias when independently available. Unknown is not zero evidence. A known compatible region receives `B_now - B_source`; unknown or incompatible regions retain document-only rendering. Tracker matching, statistics, policy and authoritative document-camera geometry must not consume the metadata.

Fresh raw observations carry their own screenshot reference. Carried geometry keeps its earlier reference; geometry handoffs transfer it with the geometry, not the retained identity. Cache revisions retain the corresponding reference, including slot reuse cleanup. Quality alignment must explicitly transfer a common target basis or decline cross-basis alignment; never keep the old quality reference after shifting into a new basis. Text without an actual read/capture reference stays document-only. Retained effect bitmaps require their own source reference too.

Capture references come from a bounded same-origin timeline of actual anchor reads, resolved at screenshot timestamps with strict bracketing/read-age limits; no extrapolated capture truth. Ordinary predicted viewport movement is not capture evidence. Baseline replacement invalidates compatibility unless continuity was independently established.

## Implementation and verification

1. Add immutable reference metadata and bounded timeline, with missing/stale/cross-origin cases.
2. Propagate metadata through raw detection copies, track snapshots/handoffs and cache revisions without using it for tracker decisions. Separate mixed bases before renderer smoothing/consolidation.
3. Wire capture-time resolution and renderer reference-relative offsets, preserving unknown fallback and source bitmap mapping.
4. Test fresh-after-bias, carried/fresh mixtures, old-ID/new-geometry handoff, delayed quality, cache reentry/reuse, unknown text, origin changes and unequal scales. Build target/test APKs together and run full tests/lint.
5. Only then run the debug experiment and compare frozen-image attachment. Do not accept merely green algebra tests or improved internal camera residuals; physical-Pixel acceptance remains outstanding.

## Implemented candidate and verification

Added immutable reference metadata, a128-read/16ms-read/48ms-bracket source timeline, raw-observation forwarding through tracker snapshots/handoffs, cache-revision references with bounded accounting, compatible-basis consolidation/steering and bitmap transforms. Tracker matching/statistics do not use the metadata. Known render snapshots use raw geometry rather than mixing tracker-smoothed coordinates from different origins. Unknown text remains document-only. Quality alignment transfers a consistent target basis; mixed targets or partially clamped referenced shifts retain the original observations. Reprojection now also preserves reconciled track identity, which its old copy path discarded.

The service/renderer/cache integration remains in the existing experimental working tree; narrow reference/timeline/coordinate and reprojection checkpoints are committed separately. Full final JVM suite513 tests passes; lint and paired APK build passed before the final test-only additions. The measured-mode expiry edge was corrected: the48–64ms anchor fade cannot masquerade as ordinary event prediction after the reference becomes stale.

Initial ON artifact SHA256 `A43C02F357E8B945A6C91F2AA1FF92AA312402E0FE7344678C8022D16E9908D1`, frozen under `pass38-candidate` with working diff and19 untracked source copies. `pass38-source-aware` completed all ten gestures over11.203s. After updating the parser for provenance suffixes,29 raw/parsed fast records agree (zero unparsed), capture-age median174/p95287ms, native median57/p95109ms,13 quality completions. Only3 fast capture references were known (biasY0,-3,-98px); most frames correctly fell back because usable measurement brackets were absent. No acceptance or calibrated performance comparison follows from this short run. The fixed seven-second template probe again mixes gesture portions, so its aggregate is not a gate.

The added telemetry initially exposed a parser schema mismatch rather than zero inference. A targeted fixture and raw-versus-parsed diagnostics now make unsupported records visible; project instructions require this check for future trace changes.

Experiment explicitly disabled and service restarted. Final OFF-installed APK SHA256 `69808960654238430ADAC7FB9D6793202D16AEBA6851FA275ABC1D4E94C1AD8E` includes the stale-measured fallback fix; it was not the earlier ON video artifact. Recorders/collectors are stopped. Next incomplete action: improve measurement continuity/coherence (per-anchor read timing and validated baseline survival), then repeat source-aware live validation. Do not weaken source-reference guards just to increase known-frame counts.
