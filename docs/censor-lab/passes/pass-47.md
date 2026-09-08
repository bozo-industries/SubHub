# Pass 47: post-first-publication GPU warm-up

Status: startup ordering improved; live sustained GPU path NOT accepted.

Commit 6f05946 queues one GPU warm-up after a successful first overlay publication. First
capture stays software; worker owns warm-up and subsequent GPU resources. Steady-state 48 ms
stall guard unchanged. Warm-up uses private model-sized black pixels, never detector input.
Full unit/lint/paired builds passed; staged changes isolated from unrelated service work.

Initial replay attempt ran before the install wrapper completed; readiness correctly refused
gestures. It produced no valid run. Waited for the same wrapper to complete, then used a fresh
run directory and confirmed live GPU logs before replay.

## Evidence

- APK SHA256 B7FEAEF35DBE4A5DB28ED04EDDBB145215CBBF72B36841CD86C95E34901778B2.
- pid17612: GPU_WARMUP afterFirstPublish=true elapsedMs=190 ready=true at 07:11:46 local.
- Before replay GPU_PREPARE was 5-14 ms, success=true: warm-up is effective for steady idle use.
- app/build/reports/device/pass47-gpu-warmed-ready: ten gestures, 11.224 seconds. GPU then
  logged 13, 19, 31, 56 ms and disabled itself on the 56 ms result. This is mixed GPU/software
  evidence, NOT a sustained GPU performance comparison.
- Only 17 fast publications, capture-age p50/p95 193/351 ms, runtime median 54 ms;
  actual draw p50/p95 6/22.5 ms, per-publish dropped counter zero. Reduced publication count
  needs supersession/quality-contention analysis; zero counter is not a clean throughput gate.
- Preference restored false, target reinstall successful; collector completed; anchors OFF.

Next incomplete action: correlate GPU preparation with quality readback and scene supersession
in this trace. HardwareRenderer shares RenderThread, so measure fast-only vs concurrent quality
before changing stall policy. A single 56 ms call is not proof persistent GPU failure, but do not
simply raise the threshold to manufacture a pass. Preserve cold/steady distinction and first
publication guarantee. Need a full sustained same-workload GPU/OFF A/B plus video before
accepting the path. No Pixel claim, goal active.
