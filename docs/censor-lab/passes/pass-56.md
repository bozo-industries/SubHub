# Pass 56: scoped software-frame shadow observer

Status: tested observer foundation; not integrated or a visible improvement.

Commit d791d99 adds RowMotionObserver. It extracts160x4 luminance summaries from prepared
ARGB arrays up to512x512, retains descriptors only, and returns displacement in input pixels
with exact previous/current capture timestamps. Caller supplies a fixed crop, document/window
scope and recent-scroll signal. No extra hardware readback or source retention.

Rejects invalid geometry/pixels, timestamp reversal/duplication, gaps>750ms, scope/crop changes,
and motion without a recent scroll signal. Invalid-order frames clear the baseline rather than
becoming a stale replacement. Still no tracker/cache/camera/render mutations.

Eight targeted estimator+observer JVM tests passed. Observer checks scale/time mapping,
caller buffer mutation, idle-motion gating, invalid-order recovery, gap/document/window/crop
fences and invalid input reset. No Android device touched. Full integration/performance not
claimed; API assumes one owning worker and does not provide its own synchronization.

Next: opt-in shadow telemetry on prepared capture frames, including exact capture scope,
interval and observer cost. Compare clean-source measurements against synchronized video,
including static/video/reflow controls. Add parser fixtures before relying on a new trace schema.
Only later consider presentation residual with source-phase references and subtract event motion
over the same interval. Do not directly add observer dy to document/tracker coordinates.
Pixel remains released; GPU/anchors OFF. Weekly remaining14% at start; pause threshold5%.
