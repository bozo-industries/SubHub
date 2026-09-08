# Pass 53: synchronized evidence of unreported viewport movement

Status: missing-motion evidence established; no camera fix yet. Pixel untouched after Pass52.

Committed numeric cached-source observer c61677b. No refresh, parent traversal, or motion
mutation. Paired build/unit/lint passed before the observer's original prepared build. Installed
that same prepared target only on emulator-5554. First recording readiness failed before any
gestures; recorder completed, invalid artifact retained. Waited for recognition, then a fresh
valid run: app/build/reports/device/pass53-viewport-ready, corresponding.mp4/.err and
-clock-before.json/-clock-after.json. All recording/collector processes finished.

## Clock / optical evidence

Host-minus-device before1174.5ms (uncertainty19.5ms), after1175.5ms (16.5ms). Stable within
bounds. Converted gesture0 to host video using midpoint1175ms. Visually verified initial
first-thumbnail tree patch x9:34/y277:303; template correlation .989-1.0 on cited samples.

| Seconds after gesture start | Image displacement video px | Reported event displacement video px |
| --- | ---: | ---: |
| .41 | -12 | 0 |
| .61 | -31 | 0 |
| .81 | -47 | 0 |
| 1.01 | -66 | -13.0 |
| 1.21 | -82 | -31.2 |

The ~51px discrepancy is far larger than uncertainty from the ~20ms clock bounds. This is
not evidence for increasing prediction velocity: the stream omits a component of visible
movement. Collapsing browser controls is the leading explanation, consistent with their
visible ~50px height change. This one trajectory does not establish a general camera formula.

## Source bounds

Zero-delta companion sources initially report rect0,151,1344,2920, non-WebView/non-scrollable.
WebView sources include0,151,1344,2734 and, later, tops319-334 with bottom2920; other records
have changing bottom/top bounds. Thus these are not safely interchangeable viewport origins:
clipping, document scrolling and browser control transforms must be distinguished. Observer
examples cost0-1ms. Raw node content/class strings are not logged.

Next: correlate raw event absolute scroll coordinates with WebView bounds, and acquire an
initial compatible viewport reference before movement. Avoid treating clipped node tops as
unconditional camera deltas. Keep document coordinates distinct from viewport transform and
carry the transform at screenshot capture for reprojection; reuse Pass38 source-reference
contract. Do not re-enable global screenshot motion blindly. GPU and anchors stay OFF;
goal active, full live/Pixel alignment acceptance outstanding.
