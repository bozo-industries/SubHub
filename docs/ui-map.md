# SubHub UI screenshot map

These captures come from the real debug app on an Android 15 phone-sized emulator. They are the
visual QA baseline for layout, hierarchy, color, clipping, and Dom/Sub visibility—not mockups.

## Sub Space

| Home | Sub-safe settings | Arrangement library |
|---|---|---|
| ![Sub Space home](screenshots/ui-map/page-map/00-sub-home.png) | ![Sub Space settings](screenshots/ui-map/page-map/00b-sub-settings.png) | ![Sub Space arrangement library](screenshots/ui-map/page-map/00c-sub-arrangements.png) |

Sub Space keeps only Home and Settings in the bottom navigation. Atmosphere appears as a compact,
read-only Home card; its full editor and navigation destination are reserved for Dom Space.

## Dom Space: core navigation

| Home | Limits |
|---|---|
| ![Dom Space home](screenshots/ui-map/page-map/01-dom-home.png) | ![Limits](screenshots/ui-map/page-map/02-limits.png) |

| Wallet | Wallet rules and caps | Checkout and history |
|---|---|---|
| ![Wallet](screenshots/ui-map/page-map/03-wallet.png) | ![Wallet rules](screenshots/ui-map/page-map/03b-wallet-rules-and-safety.png) | ![Wallet checkout](screenshots/ui-map/page-map/03c-wallet-checkout-and-history.png) |

| Settings | Censor appearance | Detection categories |
|---|---|---|
| ![Global settings](screenshots/ui-map/page-map/04-global-settings.png) | ![Censor appearance](screenshots/ui-map/page-map/05-censor-settings.png) | ![Detection categories](screenshots/ui-map/page-map/05b-settings-detection-categories.png) |

| Phrases and tools | Photo censoring |
|---|---|
| ![Phrases and tools](screenshots/ui-map/page-map/05c-settings-phrases-and-tools.png) | ![Photo censoring](screenshots/ui-map/page-map/07-censor-photos.png) |

## Progress, help, and configuration

| Help and safety | Statistics | Milestones |
|---|---|---|
| ![Help and safety](screenshots/ui-map/page-map/08-help-safety.png) | ![Statistics](screenshots/ui-map/page-map/09-statistics.png) | ![Milestones](screenshots/ui-map/page-map/10-achievements.png) |

| Custom images | Profiles | Imported packs |
|---|---|---|
| ![Custom images](screenshots/ui-map/page-map/11-custom-images.png) | ![Profiles](screenshots/ui-map/page-map/12-profiles.png) | ![Imported packs](screenshots/ui-map/page-map/13-configuration-packs.png) |

## Atmosphere and arrangements

| Atmosphere hub | Whispers | Popup Storm |
|---|---|---|
| ![Atmosphere](screenshots/ui-map/page-map/14-atmosphere.png) | ![Whispers](screenshots/ui-map/page-map/15-whispers.png) | ![Popup Storm](screenshots/ui-map/page-map/16-popup-storm.png) |

| Arrangement library | Drafts | Creator |
|---|---|---|
| ![Studio library](screenshots/ui-map/page-map/17-studio-library.png) | ![Studio drafts](screenshots/ui-map/page-map/17b-studio-drafts.png) | ![Studio creator](screenshots/ui-map/page-map/17c-studio-create.png) |

## Maintenance

| Updates | Diagnostics | Service lock details |
|---|---|---|
| ![Updates](screenshots/ui-map/page-map/18-updates.png) | ![Diagnostics](screenshots/ui-map/page-map/19-diagnostics.png) | ![Service lock](screenshots/ui-map/page-map/20-service-lock.png) |

## Pack-update rule

Studio packs carry a private random creator-device identity. Sub Space may update the active
arrangement only when the incoming pack has the same pack ID, creator-device ID, name, and author.
New, renamed, or differently authored arrangements still require Dom Space. This identifies a
continuation of the same arrangement; it is not a cryptographic author signature.

## Refreshing the map

Run `PageMapScreenshotTest` on a phone-sized Android emulator, then copy its `page-map` output from
the app's external files directory into `docs/screenshots/ui-map/page-map`. Review every image at
original size before accepting UI changes; a passing render test alone cannot judge visual polish.
