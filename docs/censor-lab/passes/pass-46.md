# Pass 46: reused GPU worker; cold-start gate

Status: warmed corpus passes; live run fell back before workload, NOT GPU acceptance.

Commit d0622f9 reuses renderer/reader by output dimensions, enforces worker ownership, makes
close terminal, discards recorded source content after each readback, and resets unsuccessful
outputs. Service integration is debug opt-in, software default; cleanup queues on capture
worker before orderly shutdown. Quality is unchanged. Runtime failure or >48 ms GPU call
disables the experiment for that service lifetime. No hard native wait bound is claimed.

Verification: full unit/lint/paired builds passed; emulator instrumentation 5 tests, zero
failed/ignored (pid16802). Includes shape/alpha changes, source ownership, cross-thread rejection,
terminal close and real corpus. Seven positive detections preserved; no extras.

Warm corpus CPU/GPU medians: 19.3442/5.6941, 19.7972/5.3245, 29.0554/7.7033 ms.
GPU maxima: 7.4821, 8.0767, 14.6925 ms. Better than per-request renderer churn, but excludes
warmups by design. Do not confuse warmed cost with first-use cost.

## Live experiment and rejection

- APK SHA256 14B6144C266E84D9C727B0BF26A9BC31B0145D38903033A21FE820EF64199D3E.
- On enabling/restarting, first GPU_PREPARE was 166 ms, success=true (pid16957, 07:07:16).
  The existing safety circuit disabled further GPU work.
- pass46-gpu-live contains no GPU_PREPARE inside the gesture interval, therefore it is a
  SOFTWARE fallback trace, not GPU timing evidence. Ten gestures/11.352s; 29 fast publications.
- Fallback capture age p50/p95 218/286.6 ms, runtime median 48, actual draw p50/p95 6/24.9.
  Per-publish dropped counter zero. No optical or physical Pixel acceptance.
- Explicit preference restored false through instrumentation, target reinstalled; collector
  completed. Anchors remain OFF; default software path retained.

Next incomplete action: inspect service/model startup and prewarm the small GPU surface outside
the first-capture critical path (same owning worker), without relaxing the steady-state stall
guard or adding waits to fast inference. Record warm-up vs first actual capture separately;
verify GPU_PREPARE actually remains active during the full controlled run before comparing
latency. If warm-up cannot be hidden safely, assess first-frame software fallback while GPU
setup runs without owning capture/draw authority. Goal active, not blocked.
