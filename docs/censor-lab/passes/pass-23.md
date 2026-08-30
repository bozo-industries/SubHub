# Pass 23 — cancel stale quality and fence handoff

- Status: `candidate`
- Baseline: Pass22a/Pass22d
- Device gate: not yet run
- APK: `SubHub-pass23-pixel-arm64-signed.apk` (generated, not tracked)
- SHA-256: `F974FDF148C3C36D48E6490F8562B31B3E69CB76D47CAC37A1F38EECCB842344`
- Temporary upload: `https://limewire.com/d/3nM6G#71IVVCqcvg`

## Hypothesis and vector

Per-run ONNX cancellation and fast-submission fencing should stop optional quality inference from
competing with or committing after newer fast work.

## Offline evidence

- Fifteen randomized paired emulator trials.
- Unpreempted fast median/p95: 52.3465/159.02031 ms.
- Preempted fast median/p95: 40.9856/100.5504 ms.
- Cancellation median: 41.0529 ms; paired wins: 10/15.
- Both emulator providers were CPU, so this proves API/lifecycle behavior and a directional
  contention improvement, not Pixel NNAPI behavior.

## Freeze boundary

The uploaded APK is an exact frozen candidate. Subsequent worktree changes added stronger
fast-sequence commit fences and removed omission-based early retirement; those changes belong to
Pass24 and must not be attributed to this APK.

## Eligibility

Compare on the physical Pixel against Pass22d. Require no late quality commit after newer fast
submission, no detector-omission flash, no fast-lane latency regression, and a better human visual
verdict before accepting.
