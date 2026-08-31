# Migrate from Android SDK 0.2.x to 0.3.4

SeatLayer Android `0.3.4` is an additive release. Existing raw
`SeatLayerView` integrations can update the core coordinate without adopting
Compose or rewriting their protocol-1 integration. The new protocol-2 native
picker is a separate surface that applications opt into.

Follow this guide when upgrading from `0.2.x`; do not
assume the new coordinates resolve before owner-approved publication.

## Choose the integration you want

| Goal | Dependencies | UI owner |
| --- | --- | --- |
| Keep the existing raw map | `seatlayer-android` | Host app plus the renderer |
| Use the ready-made native picker | Both artifacts | SeatLayer native components |
| Replace selected Compose parts | Both artifacts | Shared through builders |
| Build a custom Compose picker | Both artifacts | Host app |
| Build a custom View/XML picker | `seatlayer-android`; add Compose only for `SeatLayerPickerView` | Host app |

Both artifacts use exactly the same version. Do not mix core and Compose
versions.

## Existing raw integrations

Update only the version:

```kotlin
dependencies {
    implementation("io.seatlayer:seatlayer-android:0.3.4")
}
```

No Compose artifact is required. `SeatLayerView`, `SeatLayerController`, the
existing configuration and event models, and protocol-1 negotiation remain
available. The supported `0.2.0` public JVM surface is checked against a frozen
baseline during release validation.

Your existing lifecycle remains valid:

```kotlin
private lateinit var seatMap: SeatLayerView

override fun onDestroy() {
    seatMap.destroy()
    super.onDestroy()
}
```

The renderer host is now created lazily when `load` starts. Optional runtime
prewarm uses a temporary credential-free WebView for the pinned mobile page,
then destroys it without creating a picker session, so it does not alter raw
map behavior or carry event state into a later load.

`SEATLAYER_BUNDLED_WEB_VERSION` is retained for compatibility but deprecated.
Use `SEATLAYER_HOSTED_WEB_VERSION` for diagnostics.

## Adopt the ready-made native picker

Add the aligned Compose artifact:

```kotlin
dependencies {
    val seatlayerVersion = "0.3.4"
    implementation("io.seatlayer:seatlayer-android:$seatlayerVersion")
    implementation("io.seatlayer:seatlayer-android-compose:$seatlayerVersion")
}
```

Then replace the host-owned raw chrome with `SeatLayerPicker`:

```kotlin
SeatLayerPicker(
    configuration = SeatLayerConfiguration(event = "ev_your_event_key"),
    onCheckout = { handoff ->
        checkoutBackend.start(holdId = handoff.holdId)
    },
    onError = ::reportPickerError,
    onClose = ::finish,
)
```

This path supplies the adaptive native header, legend, confirmation, GA/table
quantities, cart, checkout, floors, accessibility filters, 3D and seat-view
controls, hold recovery, lifecycle reconciliation, and Android Back behavior.
Panorama Back uses `picker.closeSeatView` only when the loaded runtime advertises
it; older runtimes continue to use their own close control without a failed or
unknown bridge command.

## Adopt incrementally or keep full UI ownership

Do not reuse protocol-1 raw controller commands inside a protocol-2 picker.
Choose one of the supported protocol-2 ownership models instead:

- configure the ready picker with `SeatLayerPickerOptions`, theme, strings,
  and styles;
- decorate or replace any stock part with `SeatLayerPickerBuilders`;
- compose public components inside `SeatLayerPickerScope`;
- host the ready tree in View/XML with `SeatLayerPickerView`; or
- bind `SeatLayerPickerStateHolder` to `SeatLayerPickerMapView` and render a
  completely custom View hierarchy around the map.

For custom Compose layouts, install `SeatLayerPickerLifecycle` and
`SeatLayerPickerBackHandler` or provide equivalent host behavior. Preserve the
test-event indicator and required attribution. For custom Views, observe the
holder's immutable `state` flow and invoke semantic actions only through its
typed `controller`.

## Lifecycle and shutdown

The ready Compose picker owns its scoped session lifecycle. View/XML and fully
custom integrations should perform orderly, hold-aware shutdown:

```kotlin
lifecycleScope.launch {
    pickerView.close()
}
```

For a custom headless integration, call `SeatLayerPickerStateHolder.close()`
before destroying or abandoning its map View. Keep the map in a container with
a definite size and outside scrolling parents because it owns pan and pinch
gestures.

The ready widget keeps its holder across recomposition. Its map renderer moves
between compact and wide layout branches without being disposed, so hosts that
handle orientation/window-size configuration changes can retain the same live
session and cart across rotation or resizing. This does not turn Activity or
process destruction into saved Compose state. If the host supports recreation
during an active hold, retain the opaque hold id in host state and recreate with
`SeatLayerPickerOptions(initialHoldId = restoredHoldId)` plus the renewable
buyer-access provider. The runtime then owns hold resumption into the new
picker session. Validate that path with real inventory; never put the hold id
in logs, screenshots, or diagnostic command lines.

## Checkout and private events

Protocol 2 transfers a typed `SeatLayerPickerCheckoutHandoff` containing the
opaque hold id, expiry, currency, line items, and app-facing total. Send the
hold id to your trusted backend, inspect it there, calculate the payable amount
server-side, and book only after payment or order validation. Never place a
SeatLayer secret in the APK or trust app-provided totals for settlement.

Private events should prefer the renewable in-memory
`buyerAccessTokenProvider`; provider values and errors are not included in SDK
callbacks or diagnostics.

## Upgrade checklist

1. Set every SeatLayer Android artifact to `0.3.4`.
2. Keep only `seatlayer-android` if the app stays on the raw map.
3. Run the existing raw flow and confirm it still negotiates protocol 1.
4. For a native picker, select one ownership model and use its protocol-2
   state/controller consistently.
5. Verify test-event disclosure, attribution, accessibility labels and 48dp
   targets in custom UI.
6. Exercise selection, confirmation, cart, hold, checkout handoff, Back, app
   background/foreground, and hold expiry with real hosted inventory.
7. Validate release/minified builds and a current Android System WebView on a
   physical device.

See the [native picker reference](native-picker.md) for all components and
customization points, and the [bridge reference](bridge.md) for the protocol
and security boundary.
