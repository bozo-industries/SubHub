# Pass 62: main-thread sampling after emulator restart

Status: diagnostic only; prior multi-second queue stall did not reproduce. No algorithm win.

The prior emulator was absent on resume. Started the existing betasafe_play_api35 AVD with
host GPU, six cores and 8GB RAM. Installed paired target/test APKs. All GPU, anchor, and row
observer experiment flags remained false. Pixel untouched. Existing unrelated prototypes remain
in the worktree; measurements describe that working snapshot, not a pristine release build.

An initial instrumentation-owned sampler disconnected Accessibility: dumpsys showed no bound
service and a crashed-service entry. Its idle samples are invalid pipeline evidence, despite a
passing test. Readiness prevented gestures. The replacement is a debug/emulator-only one-shot
service sampler, armed by setup instrumentation then activated by reinstalling the target APK.
It consumes its flag, samples code locations every 100ms for 45s, and has no render authority.
Do not treat stack sample counts as exact wall-time attribution; sampling itself adds overhead.

Successful ten-gesture trace: app/build/reports/device/pass62-main-live. All 441 stack records
parsed (including Android synthetic method names); maximum sampling call cost 8ms. End marker
confirmed 441 samples. 388 samples were in nativePollOnce, 15 in render sync, 12 in Binder, five
in Object.wait. First application frame was the surface-identity ancestor query in 15 samples
and source resolution in two. The full sampling interval includes startup and idle tail, not
only gestures. These are leads for event-path investigation, not proof of the old stall's cause.

| Metric | Ten-gesture run |
| --- | --- |
| Raw / parsed overlay publications | 30 / 30 |
| Capture age median / p95 | 175 / 346ms |
| Published preprocess / inference runtime / postprocess median | 2 / 52 / 1ms |
| Maximum cumulative inference drops | 0 |
| Capture callbacks complete / failed / partial | 30 / 3 / 1 |
| Capture preparation median / p95 | 51 / 105.1ms |
| Explicit readback median / p95 | 44.183 / 89.471ms |
| Publication queue median / p95 / max | 5 / 60.2 / 72ms (29 pairs) |
| Main-to-tick median / p95 | 9 / 39.4ms |
| Motion input-to-draw median / p95 | 5.5 / 17ms (32 draws) |
| Visible scene deadline misses | 5 / 30 |
| Text scans accepted / stale / content-active | 6 / 2 / 2 |
| Text publications | 5, all zero-region |

Capture parser malformed records: zero. No optical alignment/flash recording; zero-region text
cannot establish text-group stability. This is not a device acceptance run. The change from
Pass61 includes a restarted emulator/environment; no performance improvement is attributed to
the sampler. Repeat uninstrumented control and longer scrolling before choosing whether to
address persistent ancestor-query work or reproduce an accumulating main-thread stall.

Checks: 530 JVM tests, zero failures/errors; lintDebug; paired assembleDebug and
assembleDebugAndroidTest; setup instrumentation 1/1; three strict sample-parser fixtures.
No signed evaluation release or production deployment. Goal remains active.
