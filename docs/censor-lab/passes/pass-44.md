# Pass 44: real-image GPU preparation compatibility

Status: small corpus compatibility passed; production unchanged.

Extended GpuReadbackProbeAndroidTest with an explicitly supplied private PNG corpus,
production rectangular shape, same CPU engine/config, one-to-one category/IoU matching,
no unmatched extras, and at least three positive baseline detections across the corpus.
Negative controls are valid only alongside that positive evidence. Synthetic-only runs
cannot satisfy the corpus gate; an absent corpus explicitly skips this test.

First attempted corpus contained existing censors and produced an empty detector baseline;
it was rejected, not called success. Replaced it with visually inspected uncensored historical
screenshots, preserving the initial files separately. Current test corpus lives only in the
emulator app-private files/gpu-readback-corpus-v2, not in Git.

## Final run

2026-09-08 06:59:15-16 local, emulator pid16360; 2/2 instrumentation tests passed.
Paired APK build and lintDebug passed. No production changes.

| Source in local tmp | Baseline / GPU / matched detections | CPU median ms | GPU median ms | GPU max ms | RGB MAE |
| --- | --- | ---: | ---: | ---: | ---: |
| gpu-emu-emiru-all.png | 1 / 1 / 1 | 22.8364 | 7.4487 | 12.1012 | 0.2824 |
| gpu-emu-emiru-images-ready.png | 6 / 6 / 6 | 20.8809 | 7.1492 | 11.8636 | 0.3604 |
| ig-main-feed.png | 0 / 0 / 0 | 25.0812 | 6.0860 | 22.6575 | 0.2922 |

Two warmups and nine preparation measurements per image. Detector comparison uses the final
prepared pair, requires IoU >=0.75 and exact category, and forbids reusing a detection for
multiple matches. This is relative to the existing detector, not independently labelled recall
or precision. Only three source images: no generalization claim. Synthetic rerun also passed,
but its GPU max reached 39.67 ms, reinforcing the need for live contention/tail measurements.

## Next incomplete action

Implement an opt-in, worker-owned small-surface preparer with explicit image/buffer/source
ownership, resize teardown, unsupported-color/API fallback, and no stale-frame return.
Keep software preparation as default until lifecycle tests and a controlled live trace show
lower preparation/capture age without increasing draw latency or reducing coverage. No policy,
tracker, quality authority, or camera changes belong in that integration. Pixel validation
and alpha/wide-gamut coverage remain outstanding; goal active, anchors OFF.
