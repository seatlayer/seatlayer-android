# Android bridge reference

The Android SDK and the immutable hosted SeatLayer mobile runtime communicate
through protocol revision 1. Both sides negotiate their supported range and
required capabilities before the chart becomes ready.

## Lifecycle

1. Create one `SeatLayerView`.
2. Start collecting `controller.events`.
3. Call `load(configuration)` from a coroutine.
4. Await the returned `ReadyInfo` before sending commands.
5. Call `destroy()` when the owning screen is destroyed.

`destroy()` is terminal for that view. Create a new view for a later chart.
Calling `load` again before destruction replaces the active chart and rejects
any commands still awaiting replies.

## Observable state

- `controller.ready` is a `StateFlow<ReadyInfo?>`.
- `controller.bundle` describes negotiated capabilities, commands, and events.
- `controller.events` is a `SharedFlow<SeatLayerEvent>`.
- `controller.isReady` is a synchronous readiness snapshot.

Collect flows with `repeatOnLifecycle` when a Fragment or Compose screen can
move between started and stopped states.

## Commands

All commands are suspending and throw `SeatLayerException` on failure.

| Command | Kotlin API |
| --- | --- |
| Create/restore/extend hold | `hold`, `resumeHold`, `extendHold` |
| Release inventory | `release`, `releaseLabels` |
| Best available | `bestAvailable` |
| General admission | `holdGeneralAdmission`, `getGeneralAdmissionAreas` |
| Seat pricing tier | `setSeatTier` |
| Selection controls | `selectObjects`, `deselectObjects`, `clearSelection`, `selectCategories`, `deselectCategories` |
| Selection policy | `setSelectableObjects`, `setMaxSelection`, `getSelectionValidity` |
| Private buyer access | `refreshAccess` |
| Current state | `getSelection`, `getCurrentHold` |
| Floors | `getFloors`, `setFloor` |
| Accessibility | `setColorblindSafe` |
| View | `setViewMode`, `getViewMode` |
| Camera | `zoomIn`, `zoomOut`, `zoomToFit` |

Each command has a unique correlation ID and timeout. A late response from a
timed-out or replaced chart is discarded.

## Events

The typed event surface includes:

- `SelectionChanged`
- `SelectionValidityChanged`, `SelectionValid`, `SelectionInvalid`, and `SelectionLimitReached`
- `BuyerAccessExpired`, `BuyerAccessUnavailable`, and `SelectedObjectsUnavailable`
- `HoldChanged`, `HoldRestored`, and `HoldExpired`
- `GeneralAdmissionClicked`
- `SeatHovered` and `DeckTapped`
- `Hint`, `Checkout`, and `Error`
- `Unknown`, preserving forward compatibility for new event names

## Errors

Handle the sealed `SeatLayerException` family:

- `Transport`: WebView load, bridge, renderer, or timeout failure.
- `Incompatible`: no common bridge protocol revision.
- `Bridge`: a structured SeatLayer command or runtime failure.

`BridgeErrorDetails` includes a stable code, human-readable message,
retryability, inventory conflicts when supplied, and optional metadata.

Do not retry non-retryable failures automatically. For inventory conflicts,
refresh selection state and let the buyer choose again.

## Bridge security

The exact `seatlayer-js@0.66.0/mobile.html` page is served from
`https://cdn.seatlayer.io`. The native listener accepts only main-frame messages
from that exact origin, and top-level navigation is locked to the exact page.
This canonical HTTPS origin is what integrator backends must place in private
buyer-session `allowedOrigins`.

The implementation deliberately avoids the legacy unrestricted
`addJavascriptInterface` bridge. File/content access, external navigation, and
mixed content are blocked. Bearers remain in memory and never enter page URLs,
events, or error diagnostics.

## Version pairing

| Component | Version |
| --- | --- |
| Android SDK | `0.2.0` |
| Hosted SeatLayer mobile runtime | `0.66.0` |
| Explicit legacy fixture | `0.59.0` |
| Bridge protocol | `1` |

The runtime URL is immutable and version-pinned. Its final artifact checksum is
recorded by the web-runtime release pipeline before the app SDK is published.
