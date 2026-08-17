# Android bridge reference

The Android SDK and its vendored SeatLayer Web SDK communicate through protocol
revision 1. Both sides negotiate their supported range before the chart becomes
ready.

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

The packaged HTML is served by `WebViewAssetLoader` from the app-owned
`https://appassets.androidplatform.net` origin. The native listener accepts only
main-frame messages from that exact origin.

The implementation deliberately avoids the legacy unrestricted
`addJavascriptInterface` bridge. External page navigation and mixed content are
blocked, and no remote page receives the native channel.

## Version pairing

| Component | Version |
| --- | --- |
| Android SDK | `0.1.2` |
| Vendored SeatLayer Web SDK | `0.59.0` |
| Bridge protocol | `1` |

The Web SDK asset is pinned by SHA-256 and checked by `./gradlew validate`.
