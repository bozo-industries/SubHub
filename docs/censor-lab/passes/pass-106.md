# Pass 106 — duplicate scroll-companion handoff

## Reproduced defect

The rejected Pass 104 recording contains an absolute-position movement of 281 pixels
at source uptime 336559725, followed by a virtual companion's explicit 281-pixel movement
at 336559737. Both are attached to the same trusted surface, but different observed motion
producers. Both previously mutated the camera. The 12 ms interval also inflated the
event predictor's inferred speed. This exact, tightly adjacent duplicate occurs once in
the new recording; it does not explain every scroll or presentation defect.

Separate producer baselines remain essential: Chromium's virtual callbacks can contain
zero or unrelated absolute offsets. This fix does not restore the earlier mixed-baseline
bug or blindly deduplicate all equal scroll deltas.

## Change

A bounded one-sample handoff recognizes only a trusted absolute event immediately followed
by an explicit event reusing that same trusted surface. It requires identical signed x/y
displacement, different nonzero producer tokens, matching document/surface/dimensions,
and ordered source and delivery times within 32 ms. Unknown/intervening events, scope
changes, different displacement, and repeated same-producer motion break the match.
One absolute sample supplies only one duplicate credit.

The duplicate is reported through the existing `SCROLL_EVENT` format with source
`companion-duplicate`, original raw movement, zero applied movement, and the adjustment
count. It does not mutate the camera, restart prediction, teach a duplicate interval to
scroll calibration, or trigger screenshot-motion fallback. Reset clears the credit.

## Verification

The pass-through baseline failed three new behavioral tests, including the recorded
281-pixel case. All ten dedicated tests then passed with the fix. The exact staged source
tree `15a3941d3614a4637492c1a82cc6bf6818b45dd8` was exported to
`C:/Users/user/Code/SubHub-pass106-motion-e3e8`, excluding unrelated dirty prototypes.
It passed **823 unit tests**, `lintDebug`, and `assembleDebug assembleDebugAndroidTest`
in the same Gradle invocation with `-PimmediateQualityExperiment=true`.

The matching target and test APKs were installed on a task-owned read-only emulator.
The declared custom runner was verified; all **six native scroll-resolver tests passed**,
including actual Android absolute and explicit event construction for the recorded pair.
The companion trace fixture verifies raw 562 versus applied 281 pixels; the existing
numeric metadata/parser-rejection fixture also passes. The emulator was stopped afterward.

No new Pixel install, recording, or runtime performance measurement occurred. The current
device evidence remains Pass 105's complete fast/quality/person timing and negative user
verdict. This is a verified mechanism fix, not proof of a smooth real-device experience.

## Remaining work

The separate 22.865 s same-direction slowdown can make the forecast retreat toward an
authoritative position behind its prediction. Reproduce and fix that separately rather
than attributing it to this one duplicate. Stationary whole-person replacement and partial
overlap decorations also remain unresolved. Combine verified improvements before the next
signed candidate; the phone remains on Pass 104.

## Single-model feasibility finding

The user raised the cost of the second person network. The recorded median model totals
are fast inference 31 ms and person-model work 78 ms; person refinement publication is
292 ms from its screenshot timestamp. These are different clocks/populations and must
not be naively added or treated as the sole cause of geometry errors.

The downloaded [Nano candidate](https://huggingface.co/Felldude/Yolo11_NSFW_Nano) at revision
`ebe21ab7d9c1b28120793437cc045963a9f43efd` has SHA-256
`b096b404a66a2ffc69b4a9c232d07b24196e6531b205dd9794d29e9a50e54d38`.
Static ZIP/pickle-opcode inspection, without unpickling or executing model code, confirms
ten labels: person, breast, vulva, butt, male, penis, anal, vaginal, blowjob, handjob.
Its card omits person/male, but the actual checkpoint includes them. Despite its repository
name, checkpoint metadata names `yolov8n.pt`, contains C2f blocks and depth/width multipliers
0.33/0.25, and records training image size 1280. Do not assume YOLO11 architecture from the
repository title or infer a Pixel timing from checkpoint size.

The [Small candidate](https://huggingface.co/Felldude/Yolo11_NSFW_Small) at revision
`2c81e0eb7b33ded0f63d279bd703d3a13afdbc1f` has SHA-256
`d2ead036175e6dee1f7e0b33da0d95d70aa0f11756ea407274ee2dbbd6059509`.
Its static metadata confirms the same ten labels, `yolo11s.yaml`, and image size 1024.
Both artifact hashes match their upstream LFS identifiers. Neither was executed, converted,
benchmarked, committed, or installed. The author publishes AGPL-3.0 metadata.

Neither candidate has NudeNet's covered/exposed distinctions or its full face/feet/belly/
armpit category set. They are not feature-equivalent replacements. Preserve current controls;
one compact joint person-plus-existing-parts detector would require suitable training and
validation. No feature removal or model replacement has been authorized by the feasibility
discussion.
