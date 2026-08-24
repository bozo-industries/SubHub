# SubHub

**A private Android control space for consensual D/s: live censoring, app limits, and an optional tribute wallet.**

SubHub keeps the everyday experience simple. The Dom configures the rules, locks the controls, and hands the device back. The Sub sees one clean Home page with the active boundaries, current session, pact timer, and any tribute due.

> **18+ project.** The real-device examples below show social-media filtering in an adult-content context. The images demonstrate the censor overlay; detected content remains covered.

<p align="center">
  <img src="docs/screenshots/live-image-and-text-filter.jpg" alt="SubHub covering an image and nearby text on a real Android device" width="280" />
  &nbsp;&nbsp;
  <img src="docs/screenshots/live-text-filter.jpg" alt="SubHub covering multiple text regions on a real Android device" width="280" />
</p>

## Interface

SubHub uses a dark plum control-room surface, quieter violet structure, and hot-pink emphasis only where attention or selection matters. Dom mode exposes the full system; Sub mode collapses the same configuration into a focused hand-off screen.

<p align="center">
  <img src="docs/screenshots/ui-map/page-map/01-censor-home.png" alt="SubHub Home in Dom mode" width="250" />
  &nbsp;
  <img src="docs/screenshots/ui-map/page-map/05-censor-settings.png" alt="SubHub visual censor-style previews" width="250" />
  &nbsp;
  <img src="docs/screenshots/ui-map/page-map/04-global-settings.png" alt="SubHub global settings" width="250" />
</p>

The repository keeps a full, device-rendered [UI page map](docs/ui-map.md) as a design and regression reference. It covers every primary page plus scrolled states for long forms and app assignment.

## What it does

| Area | Purpose |
|---|---|
| **Home** | Enter or leave service, choose an optional 1-hour to 30-day pact, and see only the active rules and session state. |
| **Censor** | Cover selected visual categories and explicit text with blackouts, bars, blur, pixelation, custom images, and other local effects. |
| **Limits** | Give assigned apps an individual daily allowance, a shared allowance, or both. A spent app returns to Android Home. |
| **Wallet** | Keep a bounded local tribute ledger and settle it through an explicitly configured PayPal flow. |
| **Settings** | Assign apps, choose the capture path, enable only the modules a couple wants, and control the Dom/Sub boundary. |

### Dom mode and Sub mode

- **Dom mode** reveals every setting, assignment, safety limit, and payment control.
- **Sub mode** hides configuration and leaves one focused Home surface. Starting service remains available without unlocking settings.
- The Dom PIN protects configuration. A pact can keep service active for a chosen duration.
- Optional Hardcore Mode adds Android-supported device-admin friction and reboot re-arming. It is a speed bump, not an unbreakable security boundary.

### A session in four steps

1. **Shape the rules in Dom mode.** Enable only the modules the relationship uses, assign apps, choose censor behavior, set allowances, and bound any tribute rules.
2. **Lock the controls.** Sub mode removes configuration from view and turns Home into the hand-off surface.
3. **Enter service.** Start an ordinary session or select a pact first. Session time and blocks persist until service actually ends; merely reopening SubHub does not create a new session.
4. **Review together.** Statistics, allowance usage, and the local tribute ledger remain available without mixing configuration into the Sub experience.

### App-aware protection

SubHub can run against every external app or only assigned apps. Each assigned app may independently receive censoring, a time limit, or both. App Mode uses Android Accessibility for foreground awareness and visible-text geometry; Screen Capture uses Android's MediaProjection approval path. Detection, tracking, and rendering are suspended outside the selected scope.

### Censor engine

Blackout, Censor Bar, Blur, Pixelate, Custom Image, TV Static, Glitch, Privacy Tape, and Error Popup share one tracked-overlay pipeline. Stable regions survive ordinary frame changes and scrolling; repeated frames do not become fresh blocks or fresh tribute events. Border choices include classic, gradient, glow, and rainbow treatments, with effect-specific color controls.

Four presets trade battery for coverage without exposing raw confidence tuning:

| Preset | Intended use |
|---|---|
| **Low** | Battery-first sessions and slower devices. |
| **Medium** | Balanced everyday behavior. |
| **High** | More frequent analysis and better small-region coverage. |
| **Ultra** | Maximum local compute, concurrent visual/text work, and the fastest refresh path on flagship hardware. |

### Tribute protocol

The Wallet can count enabled events such as a new temptation, lingering on one still screen, tapping a visible censor, opening an assigned app, or testing a Hardcore lock. Every event is gated behind active service. Price, batching, cooldowns, daily and weekly caps, and the Dom's correction window are configurable.

PayPal credentials belong to the installation and are entered only in Dom mode. Merchant secrets are encrypted with Android Keystore. Saved-wallet and automatic Hardcore checkout remain separate opt-ins and still depend on PayPal account eligibility.

## Privacy at a glance

- Visual inference and text classification run on the device.
- Captured frames are processed in memory; SubHub does not provide a remote content-analysis service.
- Censoring, Limits, and Wallet are independent modules and can be hidden entirely.
- Sub mode can always settle an enabled Wallet, but it cannot edit its rules.
- Device Admin declares no wipe, camera, password, lock-screen, or monitoring policy.
- Android may still require system approval for capture, Accessibility, overlays, notifications, or Device Admin.

## Safety and control boundaries

SubHub is designed for informed adult use, but playful framing never replaces an exit path:

- The Dom PIN governs configuration; it is not presented as device-grade authentication.
- Pacts lock the ordinary stop action until their timer ends. Dom mode remains the configuration boundary.
- Hardcore Mode uses only platform-supported friction. Android, the device owner, safe mode, ADB, or uninstall after Device Admin removal can still defeat it.
- Tamper tribute accepts only explicit, rate-limited signals such as a wrong Dom PIN, a blocked uninstall/data-clear action, a sealed stop attempt, or out-of-band Device Admin removal. No charge is created when service is off.
- Tribute totals are capped locally. PayPal settlement and any saved-wallet automation require their own setup and eligibility.

## Build the editable source

Requirements:

- Android Studio or a JDK 17 command line
- Android SDK 35
- Android 8.0+ device or emulator; a modern Android release is recommended for the full app-aware workflow

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

The debug build uses package `com.subhub.app` and produces ABI-specific APKs:

```text
app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
app/build/outputs/apk/debug/app-x86_64-debug.apk
```

For a focused device pass:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

See [Architecture](docs/architecture.md), [Device smoke test](docs/device-smoke-test.md), [visual audit](docs/visual-parity.md), and [PayPal integration](docs/paypal-penance.md) for deeper implementation notes.

## Project layout

```text
app/src/main/java/com/subhub/app/   Human-editable Android source
app/src/main/res/                   Purple SubHub interface and resources
app/src/androidTest/                Device and UI contracts
app/src/test/                       Local logic contracts
docs/                               Architecture, safety, and reconstruction notes
scripts/                            Repeatable private-workspace and build helpers
```

## Licensed reconstruction boundary

SubHub is a clean, human-editable reconstruction informed by a lawfully purchased copy of Beta Blocker Mobile. The maintained app has its own `com.subhub.app` identity and a distinct interface, navigation model, Dom/Sub boundary, Limits module, and Wallet workflow. The public [Beta Blocker Mobile product page](https://isla2d.itch.io/beta-blocker-mobile) remains useful product inspiration for local filtering and customizable censor styles.

The purchased APK, decompiled vendor code, vendor artwork, signing material, account credentials, and private captures must not be committed or redistributed. Keep reverse-engineering evidence in the adjacent private workspace and confirm that any distribution remains within the purchased license and applicable law. See the [reconstruction roadmap](docs/reconstruction-roadmap.md) and [static evidence notes](docs/static-evidence.md).

The README presentation follows the concise screenshot, privacy, compatibility, and build patterns used by mature Android projects such as [Bitwarden Android](https://github.com/bitwarden/android) and [Mushotoku](https://github.com/tomfrischmuth/mushotoku), adapted to SubHub's own visual language.
