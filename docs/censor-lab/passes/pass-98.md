# Pass 98: obtain the missing X producer evidence

## Rejected shortcut

Replay of the existing `ScrollDeltaStabilizer` against the user-supplied Pass97 X
trace confirms that blanket fallback routing is not a sufficient fix. Episode57
changes from +820 net pixels to -751, removing the isolated +1571 impulse, but
episodes48/49 change from zero net reported motion to -205/-299, and episode56
changes from -86 to +192. The existing unit test for isolated Twitter corrections
does not establish correct distance across the entire recorded sequence. No such
routing change is shipped, and Chromium's motion path remains unchanged.

## Diagnostic changes

`SCROLL_METADATA schema=1` records the raw offset, maximum offset, visible indices,
item count and explicit delta sent in the existing Accessibility event. A coarse
class-kind number distinguishes standard WebView/scroll/list/recycler classes from
unknown classes; no raw class, package, resource ID, node text or content description
is included. The salted actual motion producer token and source event timestamp
join this record to `SCROLL_EVENT`.

The existing identity walk exposes only primitive outcome counts: source present,
nodes visited, owner depth/kind and traversal failure. It does not perform extra node
reads, refreshes, walks or resource lookups. Event fields already in the delivered
parcel are sufficient for the new record. Logging still has overhead to measure;
no new input permission or motion authority is granted.

The `scroll-learning` DUMP response now retains the last observer/calibration/admission
snapshot after shutdown, nested under `lastSession`. Current top-level state remains
DISABLED and applied=false. This prevents leaving X to report a result from destroying
the diagnosis. The snapshot is memory-only, bounded to one session, and protected by
the existing Android DUMP permission.

The trace parser exposes raw/parsed/unparsed metadata counts and marks unsupported
schemas, missing fields and unknown appended fields incomplete. Fixtures test the
new record and malformed cases. Existing trace schemas are unchanged.

## Verification and remaining gate

The exact isolated staged tree `5d073425d7e2869b6e0956a888c9d4f710928801` passed all
763 unit tests, `lintDebug`, `assembleDebug`, and `assembleDebugAndroidTest` in one
invocation with `-PimmediateQualityExperiment=true`. Metadata parser, existing trace
parser and motion-clock fixtures also passed in that export. Only this verification
prose changed afterward. Unrelated dirty cache experiments remain excluded.

Private signing run `35482589382` succeeded for exact source
`e55feb4e10ee0bee668c22ffd35d49653084867c`. Compact artifact `10596810526` was
downloaded and verified: exact source, immediate-quality flag enabled, no published
release, matching checksum and installed-compatible signer.
APK SHA-256: `cda084365ac6a069d8d88385a9f495ba2b246d6ce342570a373902fe81bae1e6`.
Signer SHA-256: `3ad7c66a3b50ddc0d71b8907f7f91926e39287f2c4f1def67831a30d439260dd`.
No installation was performed; the phone remains on Pass 96.

This checkpoint does not fix X's motion. One short X scroll is needed with the new
metadata to determine whether the large impulse coincides with scrollbar-range or
item-index changes and whether ownership failed because the source is absent, no
scroll owner exists, or traversal fails. The existing video remains available; a new
video is not required for those numeric fields. Subsequent correction must preserve
genuine reversals and fast flings, not merely suppress inconvenient motion.
