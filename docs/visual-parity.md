# Visual parity audit

The licensed Beta Blocker 1.67 APK and maintained SubHub build were exercised side by side on the same API 35 emulator. Licensed screenshots, APKTool output, and JADX references remain outside Git in `C:\Users\user\Code\SubHub-private`.

## Current SubHub shell

- Near-black plum surfaces with restrained star texture, quieter violet structure, and hot-pink emphasis reserved for selection and high-priority actions
- Compact dark header cards, small section labels, dense utility cards, subtle elevation, and consistent filled/outline action states
- Feature-aware destinations: optional Censor, Limits, and Wallet areas plus an always-visible Settings area
- Centered bottom pill with purpose-built icons, a filled violet active capsule, and immediate state feedback
- Centered icon-over-label controls, matched vertical rhythm, and a text-only Dom/Sub lock control
- Adaptive page and navigation margins at phone, 600 dp tablet, and 840 dp large-tablet breakpoints
- A four-column Censor tool grid on tablets and multi-column settings choices where space permits

The Censor page now provides safe, synthetic live previews for every effect instead of using copied vendor art or generic radio labels. Detection presets and border styles have their own iconography and selected states. Large illustrative hero art stays out of utility screens because it weakens the control hierarchy and pushes frequently used actions below the fold.

## Functional visual states

The audit includes inactive and active Censor states, a ticking current-session clock, App Mode armed/waiting and recognition-active states, Settings selection feedback, Help accordions, Export controls, compact detail headers, Wallet rules, and daily limits. Limits and the watched-app selector remain available in both recognition modes because app budgets are independent of censor scheduling.

An instrumentation smoke test launches user-facing activities and writes screenshot evidence to the Android Gradle additional-output directory. The page map additionally captures scrolled Wallet and Settings states. The complete pass has been exercised at 1080×2400/420 dpi, 1600×2560/240 dpi portrait, and 2560×1600/240 dpi landscape.

SubHub's own current phone baseline is versioned in [`docs/screenshots/ui-map`](screenshots/ui-map/page-map/), with a browsable [page-map index](ui-map.md). Licensed comparison captures remain outside Git and are never redistributed.
