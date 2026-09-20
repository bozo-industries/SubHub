# Pass 103 — reproduce and repair refinement handoff gaps

## Reproduction and changes

A deterministic test using the observed approximate cadence reproduces a continuity
failure: a source at 100 ms publishes raw masks at 221 ms and refinement at 307 ms;
the next source at 434 ms publishes raw masks at 555 ms and refinement at 641 ms.
The former 500 ms reuse lifetime expires at 600 ms, introducing a 41 ms fallback
interval on an unchanged target. The regression failed before the repair and passes
afterward. This proves a mechanism capable of the observed pulsing, not attribution
of every recorded rectangle to that mechanism.

Refined reuse now obeys the existing 750 ms maximum evidence age, the same limit as
freshly displayed coverage. It does not renew the source timestamp or remove the
scope, basis, geometry, or token checks. Expiry remains scheduled on static screens.

Busy quality admission now defers the one latest person request instead of discarding
it. Quality resource release wakes that request; there is no polling or waiting on
the fast executor. Fast arrival still preempts it, expired scope rejects it, and newer
sources replace it. Resource release during admission cannot lose the wakeup. All
quality unlock paths notify, including initialization and rejected/failed admission.
Pending person work gets one opportunity before another quality pass claims the lock.

The Lab allowlist now mirrors person-model/provisional/publication records for both
capture modes. Coverage mode is included in the manifest. The strict person parser
accepts native NDJSON envelopes as well as logcat lines. Future schema records reach
the parser and are reported incomplete instead of disappearing from the export.

## Verification

Exact staged source tree `6b419d7104469b75a3d9a5f2bf626bd7ed106105` was exported to
`C:/Users/user/Code/SubHub-pass103-continuity-804a`, excluding unrelated prototypes.
`testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` passed in one
invocation with `-PimmediateQualityExperiment=true`: **808 tests, zero failures**.
Seven strict person-parser fixtures pass. Updated native Lab-export assertions compile
but have not been executed in this pass. No phone update or new recording occurred.

No new live capture, queue, inference, publication, or stability measurements are
claimed. The Pass 102 recording remains the real-device failure evidence. These
changes need another dense Pixel pass before being considered visually successful.

## Incomplete work

This is not a complete repair of the rejected build. Unsupported provisional growth,
overlapping person labels/regions, and scroll displacement remain to be addressed.
Do not sign/install this checkpoint as though those failures are fixed. Continue the
recorded-case reproduction and presentation work, then validate the combined candidate
and obtain user acceptance.
