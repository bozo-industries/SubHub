# UI consistency checkpoint — September 2026

The first foundation batch improves the shared header, navigation, actions, and core input
styles. It preserves the purple rebrand, six-destination order, and Dom/Sub visibility rules.
It is **not** a claim of complete app-wide UI consistency or physical-device acceptance.

## Delivered

- Headers retain the compact normal-size baseline, wrap subtitles, and move the lock control
  onto its own row for enlarged text. Titles remain intact; decorative icons are excluded from
  screen-reader traversal and titles expose heading semantics.
- Navigation allocates natural label widths and reflows when needed instead of breaking words
  or reducing the user's font size. Targets remain at least 48dp. Measured navigation height
  determines the scroll-content clearance.
- Core actions and fields can grow vertically; eighteen persistent labels identify inputs.
  Wallet and total-app-minutes fields use the shared treatment.
- A brighter purple text role improves action/heading readability without changing censor
  effects or the decorative palette. The header highlight stays inside its rounded contour.

## Evidence

The paired `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` build passed:
421 unit tests, no failures/errors/skips; debug lint passed. Twelve final instrumentation
contracts passed, including actual navigation behavior, heading/selection semantics, labels,
48dp targets, and final-action clearance. Header and navigation geometry cover five widths
(320/360/411/600/840dp) and three font scales (1.0/1.3/2.0).

Forty screenshots were inspected on the GPU emulator: eight core first viewports at 320,
360, and 411dp with normal text, 360dp at 1.3x, and 320dp at 2x. Captures are local diagnostic
artifacts under `tmp/ui-foundation-batch1/*-verified`, not tracked release assets.

The UI QA application SHA256 was
`8ABB890BC520D48CA2E34642F8A2A04FF016A84421767AB43D775A6350C8EC46`;
the test APK SHA256 was
`37CFAB39A4DDF2FA2E750DE0B14941F40BCF541C0E6B4ADD04C161601CE0DAB2`.
Subsequent instrumentation-only diagnostic changes produce a different test APK.
The application included other ongoing pipeline changes; this UI commit does not identify
a clean standalone performance baseline.

Original emulator preferences were restored and hash-verified. Display size, density, font
scale, Accessibility settings, and overlay permission were restored; accounts were retained.
UI automation suppresses the real Accessibility service during these screenshots, so the
setup-required views do not validate the ready/active protection states.

## Remaining work

- Home's miniature arrangement cards still truncate status and can split long labels at
  enlarged text. Use a responsive layout in a separate MainActivity checkpoint.
- The launcher Start Protection action currently toggles an already armed session off;
  resolve its behavior separately from cosmetic work.
- Dynamic per-app Limits inputs, Wallet keyboard/large-value/full-scroll states, supporting
  screens, dialogs, permission/revocation/active states, long/localized content, TalkBack,
  hardware-keyboard focus, landscape, and rendered tablets remain to be checked.
- Synthetic tablet header/navigation checks are not full tablet QA. Core first-viewport
  screenshots are not full-page coverage. A physical-phone milestone is still required.
- Censor-label placement and scroll alignment are separate performance work and were not
  changed or validated by this UI batch.
