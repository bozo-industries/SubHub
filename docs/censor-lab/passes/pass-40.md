# Pass 40: bound baseline reads; identify budget losses

Status: bounded-work fix verified; anchor alignment experiment not accepted.

## Change and checks

Discovery previously called readBounds with an unlimited deadline. It now stops subsequent
node queries after the 16 ms budget and rejects incomplete/late baselines. An individual
Binder call cannot be interrupted by this deadline. Normal sampling thresholds are unchanged.
New counters distinguish single-node stalls from accumulated group cost and discovery drops.
All diagnostics remain fixed numeric/enum metadata, with no provider text.

Core commit d29580a is pushed. Independently staged sampler suite: 21 tests. Full dirty-tree
suite: 517 tests, zero failures; lintDebug and paired target/test APK builds passed. The
existing Android adapter and service telemetry are still uncommitted prototype integration.

## Trace-only emulator run

- APK SHA256: 1205F75653020363D41CA0C4074A86CEB6642C732EF9DBC4E52EA09CDF1D2480.
- Local artifacts: app/build/reports/device/pass40-read-budget/trace.log and analysis.json.
- Same ten-gesture Chrome Emiru replay, 11.273 seconds. No motion-video acceptance claim.
- All 31 raw fast publications parsed; zero known render-source references.
- Periodic first/last snapshots: reads 90 to 170; accepted 83 to 130;
  slowDrops 6 to 31; singleNodeBudgetDrops 1 to 10; aggregateBudgetDrops 5 to 21.
  These snapshots bracket more than the exact marker interval, not exact gesture totals.
- Discovery budget drops zero; max individual read 33 ms, max group read 34 ms (cumulative).
- Geometry rejection counters ended at INCONSISTENT_TRANSLATION:3, RESIZED_OR_CLIPPED:1.
- Capture age p50/p95 243/369.5 ms; inference 67/121; preprocessing 3/11.5;
  native runtime 58/105; postprocessing 1/6.5; publish interval 349.5/589.7.
- Per-publish dropped counter zero (not a claim about all cancellation types).
- Input-to-draw p50/p95 42/50.6 ms. This is internal draw timing, not optical alignment.

Conclusion: most observed budget overruns accumulated across nodes, but single-call stalls
also matter. Bounded discovery did not trigger in this run; only its deterministic test proves
the work-saving case. The run does not support promoting anchors or loosening stale guards.
Next: examine why capture-time reference coverage is zero alongside the expensive sequential
refresh path; compare sampling cost versus its actual usable presentation contribution before
investing in a more elaborate geometry fit. Also isolate the 42 ms input-to-draw delay from
provider sampling overhead with an OFF baseline and video. Do not interpret renderTickMs=50
as a hardcoded 50 ms scheduling interval: it is measured Choreographer callback spacing.

Experiment restored OFF using instrumentation, same APK reinstalled successfully; collector
finished. Physical Pixel acceptance remains outstanding. Goal stays active.
