# SubHub UI page map

This inventory is captured from the real debug app on an Android 15 phone-sized emulator. It is kept in the repository so visual changes can be compared against an actual rendered baseline instead of inferred from layout XML.

## Core experience

| Home | Limits | Assigned apps |
|---|---|---|
| ![Home](screenshots/ui-map/page-map/01-censor-home.png) | ![Limits](screenshots/ui-map/page-map/02-limits.png) | ![Assigned apps](screenshots/ui-map/page-map/02b-limits-app-picker.png) |

## Wallet

| Overview | Rules and boundaries | Checkout and history |
|---|---|---|
| ![Wallet](screenshots/ui-map/page-map/03-wallet.png) | ![Wallet rules](screenshots/ui-map/page-map/03b-wallet-rules-and-safety.png) | ![Wallet checkout](screenshots/ui-map/page-map/03c-wallet-checkout-and-history.png) |

## Configuration and censoring

| Global settings | Censor styles | Detection categories |
|---|---|---|
| ![Global settings](screenshots/ui-map/page-map/04-global-settings.png) | ![Censor style previews](screenshots/ui-map/page-map/05-censor-settings.png) | ![Detection categories](screenshots/ui-map/page-map/05b-settings-detection-categories.png) |

| Phrases and tools | Photo censoring |
|---|---|
| ![Phrases and tools](screenshots/ui-map/page-map/05c-settings-phrases-and-tools.png) | ![Photo censoring](screenshots/ui-map/page-map/07-censor-photos.png) |

## Review, safety, and supporting tools

| Help and safety | Statistics | Achievements |
|---|---|---|
| ![Help and safety](screenshots/ui-map/page-map/08-help-safety.png) | ![Statistics](screenshots/ui-map/page-map/09-statistics.png) | ![Achievements](screenshots/ui-map/page-map/10-achievements.png) |

| Custom images | Profiles | Configuration packs |
|---|---|---|
| ![Custom images](screenshots/ui-map/page-map/11-custom-images.png) | ![Profiles](screenshots/ui-map/page-map/12-profiles.png) | ![Configuration packs](screenshots/ui-map/page-map/13-configuration-packs.png) |

| Popup storm | Diagnostics | Service lock |
|---|---|---|
| ![Popup storm](screenshots/ui-map/page-map/14-popup-storm.png) | ![Diagnostics](screenshots/ui-map/page-map/15-diagnostics.png) | ![Service lock](screenshots/ui-map/page-map/16-commitment-pact.png) |

## Refreshing the map

Run `PageMapScreenshotTest` on a phone-sized Android emulator, then copy the resulting `page-map` folder from the app's external files directory into `docs/screenshots/ui-map`. Review the screenshots at original size before accepting UI changes; the test passing proves the pages render, but it cannot judge clipping, hierarchy, or polish.
