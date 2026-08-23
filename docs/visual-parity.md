# Visual parity audit

The licensed Beta Blocker 1.67 APK and maintained SubHub build were exercised side by side on the same API 35 emulator. Licensed screenshots, APKTool output, and JADX references remain outside Git in `C:\Users\user\Code\BetaSafe-private`.

## Matched shell

- Near-black burgundy root treatment with restrained star/gradient texture
- Compact dark header cards, small section labels, dense utility cards, and violet primary/outline actions
- Three fixed primary destinations: Censor, Limits, and Money
- Centered bottom pill with purpose-built icons, a filled violet active capsule, and immediate state feedback
- Centered icon-over-label Censor tool tiles and a text-only controller EDIT control
- Adaptive page and navigation margins at phone, 600 dp tablet, and 840 dp large-tablet breakpoints
- A four-column Censor tool grid on tablets and multi-column settings choices where space permits
- Full-screen Browser chrome with compact tab strip, address controls, shields, progress, and WebView frame

Large illustrative hero art was removed from utility screens because it changed the licensed app's hierarchy and pushed controls below the fold. Project-owned demoness assets remain available for feature-specific illustration and Popup Storm content.

## Functional visual states

The audit includes inactive and active Censor states, a ticking current-session clock, App Mode armed/waiting and recognition-active states, Settings selection feedback, Help accordions, Export controls, browser loading/content, compact detail headers, Money Rules, and daily limits. Limits and the watched-app selector remain available in both recognition modes because app budgets are independent of censor scheduling.

An instrumentation smoke test launches 15 user-facing activities and writes screenshot evidence to the Android Gradle additional-output directory. A 19-page map additionally captures scrolled Money and Settings states. The complete pass has been exercised at 1080×2400/420 dpi, 1600×2560/240 dpi portrait, and 2560×1600/240 dpi landscape, with screenshot evidence kept outside Git. Device screenshots used for comparison are intentionally not committed.
