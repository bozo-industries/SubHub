# Pass 97: X scroll-offset failure captured on video

## User verdict and evidence

The user considers Chromium acceptable for now, but X scrolling remains entirely off.
Pass 96 is installed; no replacement was made during this investigation. The user
recorded and exported a Lab bundle, authorizing a pull. The private local bundle
contains 51.173 seconds of telemetry and an approximately 50.6-second recording,
1,815 events, zero dropped Lab events, and 2,654 decoded video frames. No video,
screenshots, page text or raw user bundle is committed.

Raw versus parsed counts match: 93 overlay publications, 50 quality completions,
187 scroll events, 183 renderer motion inputs, 164 draws, and 60 consolidation records.
All 187 scroll events report observed producer confidence zero and reuse of the
active cache surface. All 183 moving events use ABSOLUTE evidence with raw/applied
ratio exactly 1.0; none has learned correction. A reused cache identity does not
establish ownership or reliability of the actual event producer.

## Concrete failure

Scroll episode 57 reports this vertical displacement sequence, in screen-direction
physical units: `-32, -236, -255, +1571, -119, -66, -33, -10`. The large reversal
is accepted as authoritative and applied unchanged. The video visibly contains
censors displaced onto the toolbar and away from the intended moving content.

Adjacent-frame page flow over the approximately corresponding episode has a negative
net displacement, despite the reported net being +820. The central estimate is about
-737 recording pixels, with -763 and -383 at minus/plus 200 ms shifts; none of those
three intervals has missing optical steps. This is evidence of an inconsistent motion
input, not a validated constant scale correction. Other episodes contain missing
optical steps or ambiguous media motion and must not be treated as measured totals.

Timing caveats matter: this is MediaRecorder video, not screenrecord Winscope metadata.
Decoded PTS are strictly increasing from -480.233 to 50134.722 ms; average FPS is
52.919, not a timestamp mapping. The recorder-start marker is approximate, and the
elapsed-versus-event-received clock offset spread is about 216 ms. The shifted checks
are sensitivity checks, not proof of exact compositor/event alignment. Initial random
OpenCV time seeks landed later than requested; selected visual frames and accumulated
flow were therefore obtained by sequential decode using actual PTS. The exploratory
flow script initially queried frame count after releasing the decoder (yielding zero);
the independent pre-release inspection and complete decode both count 2,654 frames.

AndroidX's [ScrollbarHelper implementation](https://android.googlesource.com/platform/frameworks/support/+/f2e05c341382db64d127118a13451dcaa554b702/recyclerview/recyclerview/src/main/java/androidx/recyclerview/widget/ScrollbarHelper.java)
estimates offsets from visible item sizes. Changing the visible average can therefore
change an offset without equivalent page displacement. This is a plausible mechanism,
not proof of X's specific implementation. SubHub currently assumes every absolute
offset difference is physical motion; that assumption is demonstrably unsafe here.

## Timing and stability observations

The hardware screen encoder was active. These figures are not a no-recorder performance
comparison. The legacy `captureAgeMs` is request-to-publication/readiness, not verified
pixel age.

| Metric | Median | p95 |
| --- | ---: | ---: |
| Fast request-to-overlay | 188 ms | 416.6 ms |
| Fast preprocessing/runtime/postprocessing | 2 / 55 / 1 ms | 7 / 95.6 / 2 ms |
| Quality request-to-ready | 346 ms | 530.35 ms |
| Quality bitmap preparation | 45 ms | 87.25 ms |
| Quality runtime | 206 ms | 314.35 ms |
| Immediate quality ready-to-present (21 updates) | 11 ms | 18 ms |
| Scroll event age | 35 ms | 180.3 ms |
| Renderer input-to-draw | 2 ms | 4.85 ms |

Fast dropped counter remains zero; quality's cumulative counter reaches 45, with
21 coalesced offers and no cancellation records in this selected session. Different
counters must not be added. Rendering quickly after receiving a wrong displacement
does not establish correct scroll following.

The existing relative-motion analyzer decoded all frames and found supported censors
in 1,218 frames, but matched only 238 moving-frame intervals. Its global median/p95
absolute residual is 6.88/53.94 recording pixels. Matching excludes lost/merged boxes
and cannot detect a constant target offset, so this is neither whole-video accuracy
nor an acceptance result. Local fallback is ambiguous on the large flat black feed
background; its improved percentage must not be promoted as successful alignment.

## Next implementation boundary

Preserve the user-accepted Chromium path. Reproduce the isolated reverse impulse and
mixed-size offset discontinuities in deterministic motion tests. Establish independent
viewport motion/producer ownership for X and distinguish discontinuous offset metadata
from physical displacement before it updates durable camera/cache coordinates. A
different global multiplier, longer cache TTL or faster draw scheduling cannot repair
the demonstrated sign reversal. Any anomaly filtering must also test genuine reversals
and accelerated flings; merely suppressing motion is not successful scroll following.

No new runtime fix, signed candidate or device success is claimed by this pass. The
recording resolves the prior export blocker and provides the next regression target.
