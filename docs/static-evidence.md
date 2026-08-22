# Static extraction evidence

## Scope and authorization

- Target/client version: Beta Blocker Android 1.67, package `com.betablocker.lite`, version code 16
- User-owned artifact inspected: licensed APK supplied locally by the user
- Authorized operations: read-only static analysis, private decompilation, and local rebuild/signing
- Explicit deep-analysis authorization: repackaging was requested; no runtime hooks, certificate bypass, traffic interception, or authentication bypass was used
- Private working location: `C:\Users\user\Code\BetaSafe-private`
- Repository handling: APKs, decompiled output, ONNX models, keys, and rebuilds are excluded from Git

Original artifact SHA-256:

```text
70d88ac1e6f95c247c2e889da5be11987633f2a4e923e51ca128754f3d74c642
```

## Discovery ladder

| Rung | Attempt | Evidence gained | Limitation / next decision |
| --- | --- | --- | --- |
| Official/public | Reviewed the current itch.io product page | Current download is 1.67/71 MB; claimed local inference, browser, capture, and Android requirements | Marketing claims do not prove implementation |
| Existing artifacts | Inspected APK metadata, manifest, ZIP contents, signatures, DEX, native libraries, and resources | Package/version, permissions, single DEX, ONNX assets/runtime, debug signer, supported ABIs | Static metadata does not prove runtime behavior |
| Minimal raw request | Not attempted | Not needed for the requested rebuild workspace | No authenticated product API was identified |
| Proxy capture | Not attempted | Avoided because static analysis answered the current questions | Browser traffic remains unobserved |
| Static analysis | APKTool and JADX private output | Capture/inference/render flow, settings/statistics storage, browser URLs, local diagnostics server | Some methods require smali because JADX failed to reconstruct them |

## Endpoint evidence ledger

All entries are statically derived and unverified at runtime.

### `GET https://suggestqueries.google.com/complete/search`

- Auth/header names: none found
- Query: `client=firefox`; `q` is the URL-encoded user query
- Response: parsed as a JSON array containing suggestion strings
- Evidence: `MainActivity$setupSearchSuggestions$1$afterTextChanged$1`
- Confidence: statically derived, high

### `GET http://<device-address>:8765/`

- Auth/header names: none found
- Input: none
- Response: HTML diagnostics dashboard
- Evidence: `DiagnosticsServer.start`, `buildHtml`
- Confidence: statically derived, high

### `GET http://<device-address>:8765/data`

- Auth/header names: none found
- Input: none
- Response: JSON diagnostics snapshot containing preset/capture/inference/timing/class-stat fields
- Evidence: `DiagnosticsServer.start`, `buildJson`, `DiagnosticsCollector`
- Confidence: statically derived, high

### `GET http://<device-address>:8765/reset`

- Auth/header names: none found
- Input: none
- Effect: resets in-memory diagnostic counters
- Response: small success response
- Evidence: `DiagnosticsServer.start`, `DiagnosticsCollector.reset`
- Confidence: statically derived, high; not executed

## Rebuild evidence

- APKTool 3.0.2 decoded 8,336 files and rebuilt the unchanged tree successfully.
- `zipalign` verification passed at 4-byte alignment.
- `apksigner` produced and verified a locally signed APK using v2 and v3 schemes.
- Rebuilt badging preserved package `com.betablocker.lite`, version code 16, version name 1.67, minimum SDK 26, and target SDK 36.
- JADX 1.5.5 generated 4,183 files and reported method-level errors; smali remains the fallback.
- Runtime install/smoke test is pending because no ADB device was connected.

## Security and uncertainty notes

- The diagnostics server appears unauthenticated and not loopback-bound. Exposure depends on whether and when application code starts it; no call site was established in this static pass.
- The APK requests Internet permission because the safe browser and suggestion/download features access arbitrary remote content. No dedicated remote product API was identified.
- No claims from third-party screenshots about paywalls, device administration, or other controls were accepted without artifact evidence.

## Artifact handling

Only hashes, paths, schema names, and architecture summaries are recorded here. The original APK, full decompiled trees, models, signing material, and rebuilt APKs remain private and untracked.
