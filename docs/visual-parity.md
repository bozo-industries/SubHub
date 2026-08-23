# Visual parity audit

The licensed Beta Blocker 1.67 APK and maintained BetaSafe build were exercised side by side on the same 1080×2400 API 35 emulator. Licensed screenshots, APKTool output, and JADX references remain outside Git in `C:\Users\user\Code\BetaSafe-private`.

## Matched shell

- Near-black burgundy root treatment with restrained star/gradient texture
- Compact dark header card with title, edition line, divider, and five equal navigation tabs
- Pink active icon/label/underline; muted inactive navigation
- Dense 20 dp page margins, small section labels, dark utility cards, and pink primary/outline actions
- Shared Home, Settings, Help, and Export navigation behavior with immediate active-tab feedback
- Full-screen Browser chrome with compact tab strip, address controls, shields, progress, and WebView frame

Large illustrative hero art was removed from utility screens because it changed the licensed app's hierarchy and pushed controls below the fold. Project-owned demoness assets remain available for feature-specific illustration and Popup Storm content.

## Functional visual states

The audit includes inactive and active Home states, a ticking current-session clock, App Mode armed/waiting and recognition-active states, Settings selection feedback, Help accordions, Export controls, browser loading/content, compact detail headers, and the new daily-limits card. The daily-limits addition uses the same dark card, pink check/radio state, compact copy, and spacing system as the reconstructed shell.

An instrumentation smoke test launches 15 user-facing activities and writes screenshot evidence to the Android Gradle additional-output directory. Device screenshots used for comparison are intentionally not committed.
