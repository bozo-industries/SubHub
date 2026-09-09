# Pass 58: observer-off control and CPU/wall distinction

Status: performance environment unsuitable for old-baseline comparison; no motion promotion.

pass58-software-control ran with shadow/GPU/anchors OFF, same replay. Eleven fast publications;
capture-age median1201ms, native runtime225ms, actual draw63ms. This independently reproduces
the globally slow state without the observer. Collector completed; Pixel untouched.

Read-only state: host ~36GB free physical memory; reported CPU clock3901MHz; emulator process
61964 High priority/all12logical affinity, ~4.1GB working set. Guest ~2.5GB free of8GB; battery100%,
25C. SurfaceFlinger still reports NVIDIA RTX3060 GLES translator, not software fallback.
Current guest top showed SubHub~171%, Chrome renderer~64%, sensor service~64%, and an unrelated
shell dumpsys package request. This does not prove its owner or sole cause; no processes were
killed and emulator was not restarted blindly. Historical/cumulative CPU values are not controls.

Added cpuUs from Debug.threadCpuTimeNanos to ROW_MOTION alongside wall costMs. Parser accepts
known legacy records with CPU=null, parses the new value separately, and retains strict unknown
schema rejection. Three parser fixtures and full unit/lint/paired builds pass. New APK not deployed
in this pass; existing software/default emulator state unchanged.

Next: when emulator load is stable, collect shadow thread CPU vs wall cost, or use isolated
instrumentation for observer arithmetic. Do not optimize based solely on delayed wall timestamps
or claim the observer caused the global4x slowdown. Continue clean-source correctness work
independently. Goal active; weekly remaining11% at start, pause5%.
