# Pass 55: bounded row-motion observer

Status: pure observer/test foundation, no live integration or motion authority.

RowMotionEstimator accepts four-column luminance row descriptors (96-320 rows), searches
at most +/-24 rows independently in four vertical bands, and requires three reliable bands
to agree. Reliability includes texture, peak distinction, improvement over stationary,
bounded mean error, and rejection at search boundaries. Valid stationary differs from rejection.
No Android calls, bitmap ownership, tracker/cache updates or presentation changes.

Five targeted JVM tests pass: translation/stationarity, local animation, disagreeing reflow,
flat/repeated/nonfinite/boundary inputs, and translation with bounded brightness change.
Initial15-luma mean-error ceiling rejected real descriptors despite a distinct three-band
peak;32 supports the explicit20-luma brightness test. This threshold needs broader held-out
testing and is not promoted based on the example alone.

Exact Java implementation on descriptors from the existing Pass54 host video:
2933ms reject,3266 reject,3599 accept-48video-px/3bands,3932 accept-12px/4bands,
4265 reject,4598 accept0/4bands. First execution ~2.8ms, later ~0.3-1.2ms on host JVM.
This is not a Pixel benchmark. Host frames contain censors and the descriptor crop is fixed;
those limitations prevent claiming clean-source or general-motion correctness.

Next: descriptor extraction from already-prepared software source frames and shadow telemetry,
with explicit capture/document/window/geometry/timestamp fences and no extra hardware readback.
Broaden local-animation/reflow/periodicity tests and benchmark on uncensored image pairs.
Only after those gates should an accumulated residual be considered for presentation; it must
subtract authoritative event motion over the same capture interval and preserve per-capture
source reference. Do not feed this estimator into document/tracker/cache authority.

No device accessed this pass. Pixel remains released; GPU/anchors OFF. Weekly quota checked
at start16% remaining; pause at5% as requested. Goal active.
