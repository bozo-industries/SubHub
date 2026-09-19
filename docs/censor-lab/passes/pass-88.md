# Pass 88: same-capture quality refresh prototype

Pass 87 identified a 218.5 ms median post-inference quality wait in the installed
Pixel baseline. The user reported cleaner coverage but trailing and late quality.
This pass implements an opt-in local prototype, not an accepted device fix.

## Policy and local integration

ImmediateQualityPresentationGate requires the quality source to be the exact
already-displayed fast capture, with unchanged motion generation, camera,
document, capture epoch, surface/token, application window and geometry. It
rejects uncertain phase, unknown window, invalid geometry, stale/future times,
and regressed sequences. A newer capture may be in flight, but a different
already-displayed capture must use the normal alignment path.

The local service prototype schedules a coalesced display callback after quality
is ready. It uses the existing cache-only overlay setter, preserving live tracks,
text, source bitmap and all currently displayed cache coverage. It does not
update detector/tracker statistics or durable confirmation. Source-reference
bases must match, and the separate spatial-cache experiment is excluded.

Immediate display does not consume the pending normal handoff. Otherwise an
unconfirmed addition could disappear on the next fast publication, reintroducing
flashes. The retained source has a one-shot immediate marker; a newer source or
motion still replaces/clears the normal one-slot mailbox.

The build flag is off by default. Only `-PimmediateQualityExperiment=true` enables
the prototype in a candidate build. Confidence/category policy is unchanged.
This can still change visible arrival behavior, so the user's cleaner result
must be revalidated before adopting it.

## Checkpoint boundary

The committed checkpoint contains the pure gate, eight tests, and default-off
build flag. Isolated staged source tree:
`c482c0d5af822ee2aa34d2440d8ba17f9b92cd46`.
Its testDebugUnitTest, lintDebug and assembleDebug checks passed with the flag on:
551 tests, zero failures/errors. The working tree passed the same checks with the
flag both on and off: 637 tests, zero failures/errors. Trace parser fixtures pass.
The final generated default BuildConfig was verified to have the flag false.

Service wiring and QUALITY_IMMEDIATE_PRESENT parsing/fixtures remain local work
in progress, interleaved with pre-existing uncommitted quality-lane integration.
They are not silently included in this commit. The local prototype is compiled
and tested separately; those results do not imply a clean checkout has the
service integration. The parser rejects malformed immediate records rather than
reporting them as zero delivery work.

## Packaging and next work

The installed APK certificate SHA-256 is
`3ad7c66a3b50ddc0d71b8907f7f91926e39287f2c4f1def67831a30d439260dd`,
different from the local debug certificate
`255e020e3893431f1568298b93274bd78e292a88beba7c620b913eeeee62f36b`.
Do not uninstall the user's app or invent a signing key. The existing release
workflow supports build_ref with publish_release=false and retains compatible
signing in Actions. Resolve the integrated source checkpoint and candidate
flag propagation before using that artifact-only path.

No new APK was installed, no instrumentation was run, and no public release/tag
was created. Capture age, queue drops, preprocessing/inference/postprocessing,
overlay latency and visual stability were not remeasured for this prototype.
Pass 87 remains baseline evidence, not a measurement of this change. Next work:
finish integration/ownership validation, produce fingerprinted control/candidate
builds with compatible signing, and repeat the manual Pixel comparison.
