# Android native picker visual-parity contract

Status: implementation and credentialed hosted-event evidence recorded on
2026-08-31; physical-device, newer-runtime, and publication acceptance remain
open.

Android does not define a separate SeatLayer visual language. The ready-made
Compose picker uses Android-native primitives while retaining the established
Web, Flutter, and React Native information hierarchy, density, product tokens,
immersive ownership, and buyer journey. The headless and component APIs remain
composable, but they receive the same semantic state and styling primitives as
the ready widget.

## Reference lock

The following source and rendered evidence was inspected before Android UI
changes were made:

- SeatLayer Web mobile picker `0.71.5`: `SeatPicker.ts`, `pickerSkeleton.ts`,
  `pickerBootReveal.ts`, `pickerStyles.ts`,
  `pickerStylesMap.ts`, `pickerStylesTray.ts`, `pickerLegend.ts`,
  `pickerFloors.ts`, `pickerSeatStateCard.ts`, `pickerTrayDense.ts`,
  `pickerMotion.ts`, `pickerSelectionFlight.ts`, `pickerSeatViewChrome.ts`,
  `pickerView3dNav.ts`, and `seatingChartImmersive.ts`.
- Flutter SDK commit `848be0c3dfadaba5efcda04d951a436cbd983e6f`:
  `doc/media/picker-flow.gif`, `doc/media/picker-section.png`,
  `lib/src/picker/picker_adaptive_layout.dart`, `picker_header.dart`,
  `picker_legend.dart`, `picker_floor_strip.dart`, `picker_dock_bar.dart`,
  `picker_confirm_card.dart`, `picker_cart_sheet.dart`,
  `picker_seat_view_chrome.dart`, `picker_venue_3d.dart`,
  `picker_accessibility.dart`, `picker_styles.dart`, and
  `picker_tokens.g.dart`.
- Flutter component goldens: `test/goldens/header_*`, `price_legend_*`,
  `floor_strip_*`, `dock_bar_*`, `confirm_card_*`, `cart_sheet_*`,
  `seat_view_chrome_*`, and `venue_3d_*`.
- Cross-platform reference captures reviewed by filename:
  `01-overview-ios-light.png`, `02-section-dock-ios-light.png`,
  `05-confirm-card-ios-light.png`, `06-peek-ios-light.png`,
  `08-dense-list-ios-light.png`, `12-3d-seatview-ios-light.png`, and their
  Web light/dark counterparts.
- React Native SDK commit `9046330090d86b8c7e88f8967a763c9af05a8261`:
  `src/picker/SeatLayerPickerAdaptiveLayout.tsx`, `header.tsx`,
  `SeatLayerPriceLegend.tsx`, `SeatLayerFloorStrip.tsx`,
  `SeatLayerDockBar.tsx`, `SeatLayerConfirmCard.tsx`,
  `SeatLayerCartSheet.tsx`, `SeatLayerVenue3DChrome.tsx`,
  `SeatLayerSeatPanoramaChrome.tsx`, `accessibility.tsx`,
  `adaptiveSafeLayout.ts`, `immersiveChrome.ts`, `motion.ts`, and
  `tokens.g.ts`.
- React Native rendered fixtures:
  `react-native-picker-fixture-light-390x844-ios26.5.png` and
  `react-native-picker-fixture-dark-390x844-ios26.5.png`.
- The owner-provided cross-SDK closure matrix for tiers, immersive state,
  accessibility, telemetry, checkout ownership, and compatibility.

Repository-local Android tokens are source-locked in
`seatlayer-compose/src/main/resources/io/seatlayer/android/compose/picker_tokens.json`.

## Decision-to-reference record

| Major decision | Established reference used | Android result and evidence |
| --- | --- | --- |
| Header and top-rail density | Web `pickerStyles.ts`/`pickerStylesMap.ts`; Flutter `picker_header.dart`, `picker_legend.dart`, and goldens; RN adaptive layout/header/legend | A 56dp one-line header, compact scrolling price rail, compact Map/3D control, and Android 48dp hit regions. See `overview-test-light-compact-api35.png`. |
| Floors, section depth, and one-level step-out | Web `pickerFloors.ts`; Flutter floor/dock sources and `picker-section.png`; RN floor strip, dock, and back-state sources | Duplicate runtime `all` sentinels are removed, one native All floors action remains, and focused-section chrome has one contextual venue step-out. See `section-focus-floors-light-compact-api35.png`. |
| Typography, colour, radii, elevation, and icon rhythm | Web picker theme/style files; Flutter `picker_styles.dart`/`picker_tokens.g.dart`; RN `styles.ts`/`tokens.g.ts` and light/dark fixtures | Neutral enterprise surfaces, restrained elevation, branded accent, consistent rounded geometry, and native vector icons in light/dark themes. See overview, cart, confirmation, 3D, and panorama captures below. |
| Seat/tier, GA, and table decisions | Web `pickerSeatStateCard.ts`/`pickerGaPrompt.ts`; Flutter confirm/tier/prompt sources and goldens; RN confirmation/decision-prompt sources | A centered, modal decision surface with visible Adult/Child/guided tiers, plus dedicated GA and variable-table quantity flows. See the three decision captures below. |
| Cart, hold, remove/undo, and checkout priority | Web dense tray/hold sources; Flutter `picker_cart_sheet.dart` and cart goldens; RN `SeatLayerCartSheet.tsx`, cart list, and hold-lapse sources | A square edge-to-edge compact ticket dock (matching the Flutter/RN defaults), dense expanded list, visible hold lifecycle, undo notice, and a checkout action that stays reachable in compact and short wide layouts. The square dock prevents dark renderer pixels showing through rounded corner cutouts. See cart/recovery/wide and hosted cart captures. |
| Selection-to-cart motion | Web `pickerSelectionFlight.ts`/`pickerMotion.ts`; Flutter picker motion; RN selection-flight and reduced-motion sources | A brief non-interactive confirmation cue starts only after authoritative cart retention and respects disabled system animation. Contract/controller tests cover the state boundary; static captures show its start/end surfaces. |
| 3D and panorama ownership | Web `pickerView3dNav.ts`, `pickerSeatViewChrome.ts`, and immersive surface source; Flutter/RN 3D and seat-view chrome | Ordinary 2D chrome stands down. Cart/Continue remain where required; the runtime owns map/panorama pixels and gestures, while Android owns only negotiated surrounding chrome. See the 3D/panorama captures. |
| Responsive and safe layout | Flutter adaptive layout/system overlay; RN adaptive/safe-layout sources | Compact portrait, compact landscape, 320dp split width, RTL, 1.5x font, gesture navigation, and a short forced-wide composition were inspected. Wide checkout remains pinned. See adaptive captures. |
| Accessibility controls | Web accessibility menu; Flutter/RN accessibility sources | Only runtime-authored needs render; counts, selection, disabled zero inventory, independent filters, TalkBack labels/state, and 48dp actions are present. See `accessibility-sheet-light-api35.png`. |
| Loading, retry, empty, and sales-closed hierarchy | Web `pickerSkeleton.ts`, its loading styles, and `pickerBootReveal.ts`; Flutter status views; RN status/empty sources | A faint venue-shell silhouette and restrained 2dp indeterminate line replace the generic spinner/empty rectangle; cart chrome stays absent before ready. Retry/empty states remain branded and never expose a raw event key. See the four deterministic states and hosted loading capture. |

## Ready-widget layout contract

On compact surfaces the picker uses a 56dp header, one compact top rail, an
optional 30dp floor strip, contextual map-edge actions, a 52dp focused-section
dock, a compact cart peek, and a centered confirmation decision. Android touch
regions are at least 48dp even where the established visual footprint is
smaller. Test disclosure and required attribution cannot be replaced by a
builder.

At 840dp or wider, navigation, best available, cart, and checkout move into a
360dp side rail instead of stretching phone stacks. On a short wide window,
nonessential rail chrome yields to the cart and pinned checkout action.

In venue 3D and panorama, regular legend/floor/filter/map-control/dock/prompt
chrome stands down. The runtime remains authoritative for chart geometry,
panorama pixels and drag gestures, while Android shows only capability-backed
native controls. Panorama hardware/predictive Back sends
`picker.closeSeatView` only when that additive command is advertised; the
hosted `0.71.5` runtime does not advertise it, so its own close affordance
remains authoritative.

## Deterministic Android evidence

The evidence tests render the production ready-made Compose hierarchy from
typed protocol-2 state. Only renderer-owned chart/3D pixels are replaced by a
clearly labelled deterministic canvas. This makes the captures suitable for
native hierarchy, density, colour, semantics, clipping, and safe-area review;
it does not turn them into hosted-runtime or real-inventory proof.

Environment: AVD `SeatLayer_RN_Pixel5_API35`, Android 15 / API 35,
`sdk_gphone64_arm64`, Android System WebView `124.0.6367.219`, 1080×2340
portrait and 2340×1080 landscape captures.

All files are under `docs/android-sdk-internal/evidence/`:

| Buyer state | Screenshot |
| --- | --- |
| Handshake/loading | `loading-light-compact-api35.png` |
| Failure and retry | `error-retry-light-compact-api35.png` |
| Empty inventory | `empty-inventory-light-compact-api35.png` |
| Sales closed | `sales-closed-light-compact-api35.png` |
| Test-mode venue overview | `overview-test-light-compact-api35.png` |
| Floors, section focus, and step-out | `section-focus-floors-light-compact-api35.png` |
| Adult/Child/guided tier confirmation | `confirmation-tiers-light-compact-api35.png` |
| GA quantity | `ga-quantity-light-compact-api35.png` |
| Table quantity, dark | `table-quantity-dark-compact-api35.png` |
| Expanded cart and active hold | `cart-hold-light-compact-api35.png` |
| Hold lapse and remove undo | `hold-lapse-undo-light-compact-api35.png` |
| 3D target, neighbours, and recenter | `venue-3d-target-dark-compact-api35.png` |
| Panorama target restoration chrome | `panorama-target-dark-compact-api35.png` |
| Panorama unavailable | `panorama-unavailable-light-compact-api35.png` |
| Expanded accessibility sheet | `accessibility-sheet-light-api35.png` |
| 1.5x font scale | `confirmation-large-font-light-api35.png` |
| RTL | `overview-rtl-light-compact-api35.png` |
| 320dp split width | `overview-split-320dp-light-api35.png` |
| Compact landscape | `overview-compact-landscape-light-api35.png` |
| Short forced-wide cart/checkout | `cart-wide-landscape-light-api35.png` |

The portrait batch rendered all 17 portrait scenarios: 16 passed immediately,
two landscape assumptions skipped, and one test-only localized-label assertion
was corrected and passed on its exact rerun. Compact-landscape and
wide-landscape tests each passed separately in landscape. The accessibility
evidence test also passed separately.

## Credentialed hosted-event evidence

The same API 35 AVD completed the real DesiPass list → details → **BOOK NOW**
journey independently through ready Compose, custom Compose, and direct ready
View hosting. The authorized development client key came from an ignored local
environment file and was never rendered, logged, copied into this directory,
or passed to SeatLayer. The event had one live price tier, so the visible
Adult/Child/guided-tier decision remains deterministic contract evidence rather
than a live multi-tier claim.

All buyer-safe hosted captures are stored beside the deterministic evidence:

| Hosted state | Screenshot |
| --- | --- |
| DesiPass event list | `hosted-events-light-api35.png` |
| Event details and BOOK NOW | `hosted-event-details-light-api35.png` |
| Web-derived native loading treatment | `hosted-picker-loading-light-api35.png` |
| Ready Compose overview | `hosted-ready-compose-overview-api35.png` |
| Section focus | `hosted-ready-compose-section-api35.png` |
| Seat confirmation | `hosted-ready-compose-confirmation-api35.png` |
| One-ticket square dock | `hosted-ready-compose-cart-api35.png` |
| Checkout handoff | `hosted-ready-compose-checkout-api35.png` |
| Venue 3D overview and explicit target | `hosted-ready-compose-3d-overview-api35.png`, `hosted-ready-compose-3d-target-api35.png` |
| Panorama open, drag, and same-target renderer-close restoration | `hosted-ready-compose-panorama-api35.png`, `hosted-ready-compose-panorama-drag-api35.png`, `hosted-ready-compose-panorama-restore-api35.png` |
| Custom Compose loading and overview | `hosted-custom-compose-loading-api35.png`, `hosted-custom-compose-overview-api35.png` |
| Ready View loading, overview, cart, and checkout | `hosted-ready-view-loading-api35.png`, `hosted-ready-view-overview-api35.png`, `hosted-ready-view-cart-api35.png`, `hosted-ready-view-checkout-api35.png` |
| Live selected cart through landscape and back to portrait | `hosted-ready-compose-cart-landscape-api35.png`, `hosted-ready-compose-cart-restored-api35.png` |

The live styling was compared with the reference lock above: native chrome kept
the same compact hierarchy and buyer journey, while the runtime retained chart,
3D, panorama pixels, and drag ownership. Portrait → landscape → portrait kept
the same selected one-ticket cart and emitted no second ready/chart-load event.
The runtime-owned panorama close restored the same seat target. Hosted `0.71.5`
did not advertise `picker.closeSeatView`; hardware Back therefore sent no
unknown command and intentionally did not close panorama.

## Acceptance boundary

The captured matrix covers native ready-widget state, accessibility basics,
safe-drawing/gesture insets, responsive composition, light/dark, RTL, large
font, a real hosted buyer journey in all three demo modes, live selection/cart
rotation retention, and runtime-owned 3D/panorama interaction. Source and
focused tests cover custom builders, headless state, Back reducers, command
ordering, capability gates, multi-tier ordering, empty/error/expiry states, and
older-runtime compatibility.

Still not claimed:

- native panorama Back against a runtime that advertises
  `picker.closeSeatView`;
- same-row 3D previous/next boundaries and capability-gated rotate/move against
  a hosted runtime that advertises the additive position commands;
- real multi-tier, live hold expiry/remove-undo/rejection, or Activity/process
  recreation with `initialHoldId` (host-handled rotation was exercised);
- physical-device TalkBack, cutout, gesture, IME, or timing acceptance; or
- owner approval, publication, tagging, or release acceptance.

Those are explicit owner/hosted-runtime/device gates, not dependencies on iOS.
