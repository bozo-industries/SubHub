# Android observation when Windows capture is unavailable

The bundled Windows Computer Use helper fails on this Windows 10 build (19045) with `SetIsBorderRequired ... 0x80004002`. Microsoft's [IsBorderRequired API documentation](https://learn.microsoft.com/en-us/uwp/api/windows.graphics.capture.graphicscapturesession.isborderrequired) lists introduction in build 20348. The installed proprietary plugin exposes no source or supported option to disable that call. This repository does not patch its binary or change Windows permissions.

For a user-authorized Android device/emulator, run:

```powershell
./scripts/capture_android_frame.ps1 -Serial emulator-5554
```

The script captures the device screen through ADB directly to a unique local PNG using a binary stream, then reports its path, dimensions and elapsed time. It needs an already-authorized online device. No Windows desktop pixels, credentials, uploads, app launch or input actions are involved. It is an observation-only fallback, not a replacement for the Windows helper or permission to route around denied input operations. Do not pass its coordinates or fabricate screenshot IDs for `sky` input calls.

Validation on 2026-09-08: real emulator capture decoded and visually inspected at 1344 x 2992 (1,887,464 bytes, approximately 1.09 seconds). Script parsing passed; a nonexistent serial failed without emitting a successful PNG path. Incomplete captures retain a `.partial` extension. This latency is unsuitable for per-frame alignment measurement; use it for setup/state inspection only.
