# Pass 48: distinguish platform failure and transient GPU cost

Status: diagnosis plus stronger ownership test; sustained GPU acceptance still missing.

Pass47 publication loss was a screenshot request id11803650 dispatched at 11803661 uptime,
then callback-failure-1 at 11808666: 5016 ms request age. Code 1 is Android's internal screenshot
error, not detector queue loss. Official constants:
https://developer.android.com/reference/android/accessibilityservice/AccessibilityService.html
No equivalent error1 appeared in the inspected Pass41/42/46 controls; they contain only
interval-too-short code3. Temporal association with the GPU experiment is not proof of cause.

The 56 ms GPU interval ended before the next quality prepare began; preceding quality readback
also ended earlier. Therefore direct overlapping quality readback is not supported for that
specific spike. Shared render/CPU pressure remains possible, not established.

## Verification and repeat

Strengthened ownership test: 24 fresh source bitmaps, color/alpha changes each call, six calls
per identical size before resize. This exercises retained resources rather than recreating
them every call. Paired build/lint passed, emulator ownership instrumentation 3/3 passed.

Same production APK as Pass47; app/build/reports/device/pass48-gpu-repeat, ten gestures:
30 fast publications, capture-age p50/p95 217.5/348.85 ms, actual draw p95 40.4 ms.
No five-second error; one code3 at 6 ms. GPU ran six times (7,13,27,13,25,53 ms), then the
single-overrun circuit disabled it. The 53 ms call comprised ~34.3 ms render/sync and 18.4 ms
small readback; subsequent software readback was ~45.7 ms. Still a mixed-path trace, not a
sustained A/B. Preference restored OFF, target reinstalled; collector completed.

## Next action

The 48 ms threshold currently treats one modest overrun as permanent session failure, preventing
measurement of the remaining gesture. Test an explicit bounded health policy: retain 48 ms
target, disable after repeated consecutive overruns, and keep immediate disable for null/failure
or a hard stall. Unit-test resets and terminal behavior. This must NOT weaken the final latency
gate: compare full-run capture/preparation/draw p95 against the same software workload and
reject a slower/less stable result. Do not claim success from remaining enabled. Goal active,
anchors and GPU currently OFF; no Pixel or optical acceptance.
