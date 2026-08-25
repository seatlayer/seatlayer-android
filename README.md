# SeatLayer Android Seat Map SDK for Reserved Seating

[![CI](https://github.com/seatlayer/seatlayer-android/actions/workflows/ci.yml/badge.svg)](https://github.com/seatlayer/seatlayer-android/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.seatlayer/seatlayer-android)](https://central.sonatype.com/artifact/io.seatlayer/seatlayer-android)
[![Kotlin](https://img.shields.io/badge/Kotlin-coroutines-7F52FF.svg)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-API%2024%2B-3ddc84.svg)](https://developer.android.com/)
[![License: MIT](https://img.shields.io/badge/license-MIT-111827.svg)](LICENSE)

The official SeatLayer Android SDK for adding an interactive seating chart and
seat picker to ticketing apps. It renders live seat availability, creates
temporary holds, finds best-available seats, and exposes every buyer action
through a typed Kotlin coroutine API while your trusted server does the booking.

[SeatLayer Android SDK on Maven Central](https://central.sonatype.com/artifact/io.seatlayer/seatlayer-android) ·
[Android seat-map documentation](https://docs.seatlayer.io/buyer-sdk/mobile/) ·
[SeatLayer reserved-seating platform](https://seatlayer.io/) ·
[Buyer seat-map demo (web)](https://app.seatlayer.io/demo/play/grand-theatre) ·
[SeatLayer iOS seat map SDK](https://github.com/seatlayer/seatlayer-ios) ·
[SeatLayer Flutter seat map SDK](https://github.com/seatlayer/seatlayer-flutter) ·
[SeatLayer React Native SDK](https://github.com/seatlayer/seatlayer-react-native) ·
[SeatLayer AI Toolkit](https://github.com/seatlayer/seatlayer-ai-toolkit)

> **Production SDK:** `0.2.0` is the current release, published to Maven Central
> as `io.seatlayer:seatlayer-android`. Pin the documented release and validate
> your event, checkout handoff, lifecycle, and target devices before rollout.

## What is included

- `SeatLayerView`, an Android `FrameLayout` subclass that owns the embedded
  seat map and can be created in Kotlin or inflated from XML.
- A coroutine-based controller for holds, best available, general admission,
  tiers, floors, view modes, and zoom.
- Typed Kotlin models, `StateFlow` readiness, and a `SharedFlow` event stream.
- An origin-restricted AndroidX WebKit message bridge with no unrestricted
  `addJavascriptInterface`.
- A pinned, immutable `seatlayer-js@0.66.0/mobile.html` production document.
- A runnable sample app plus protocol, envelope, model, and bridge unit tests.

## Requirements

- Android API 24 or newer (`minSdk = 24`, compiled against API 36)
- JDK 17 to build the SDK
- An AndroidX application
- An Android System WebView that supports the `WEB_MESSAGE_LISTENER` feature —
  `load` fails with a clear transport error when it does not

## Install

The SDK is on Maven Central, so the default Android repository set already
resolves it:

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("io.seatlayer:seatlayer-android:0.2.0")
}
```

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
```

Releases before `0.2.0` were distributed as JitPack tags
(`com.github.seatlayer:seatlayer-android:v0.1.3`). That channel still resolves
the tagged sources, but Maven Central is the permanent coordinate. Always pin an
exact version — never JitPack's `main-SNAPSHOT`.

## Quick start

Create one `SeatLayerView`, collect its events, then `load` a configuration from
a lifecycle-appropriate coroutine.

```kotlin
class CheckoutActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var seatMap: SeatLayerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        seatMap = SeatLayerView(this)
        setContentView(seatMap)

        scope.launch {
            seatMap.controller.events.collect { event ->
                when (event) {
                    is SeatLayerEvent.SelectionChanged ->
                        renderSelection(event.seats)
                    is SeatLayerEvent.HoldExpired ->
                        showExpiredMessage()
                    is SeatLayerEvent.Error ->
                        reportSeatLayerError(event.error)
                    else -> Unit
                }
            }
        }

        scope.launch {
            seatMap.load(
                SeatLayerConfiguration(
                    event = "ev_your_event_key",
                    currency = "USD",
                    maxSelection = 8,
                ),
            )
        }
    }

    override fun onDestroy() {
        seatMap.destroy()
        scope.cancel()
        super.onDestroy()
    }
}
```

`load` is a suspending function that returns `ReadyInfo` — the negotiated
protocol revision, the event mode (`live` or `test`), the transport, and the
event key. Await it before sending commands. The SDK moves WebView work to the
main thread itself.

## Hold seats

Seat selection happens inside the map. Your app then creates a short-lived hold
and hands the opaque id to your backend:

```kotlin
scope.launch {
    val hold = seatMap.controller.hold(ttlMillis = 10 * 60 * 1_000)
    if (hold != null) {
        checkoutWith(
            holdId = hold.holdId,
            expiresAt = hold.expiry,
            items = hold.items,
        )
    }
}
```

`HoldResult` also exposes `timeRemainingMillis`, which is convenient for driving
a countdown in the buyer UI.

## Security boundary

The Android app **selects and holds** inventory. Your trusted backend
**inspects and books** the hold after payment or order validation.

- Never ship a SeatLayer secret key in the APK, the WebView, or app config.
- Send only the `holdId` and your normal checkout context to your backend.
- Calculate the charge from server-inspected hold items, not app input.
- Reuse your stable order id as `bookingRef` for safe booking retries.

Continue with
[seat holds and secure server-side checkout](https://docs.seatlayer.io/buyer-sdk/holds-and-checkout/)
before connecting payment and booking.

## Android runtime and WebView architecture

`SeatLayerView` loads exactly one page: the immutable
`https://cdn.seatlayer.io/seatlayer-js@0.66.0/mobile.html` document. That
canonical HTTPS origin is what origin-bound private buyer sessions are minted
for, and no event key or bearer is ever placed in the page URL or in events.

Native and web talk over AndroidX WebKit's `addWebMessageListener`, restricted to
`https://cdn.seatlayer.io` and to main-frame messages. File access, content
access, mixed content, automatic popups, multiple windows, and third-party
cookies are all disabled, and any navigation away from the pinned page URL is
refused. If the renderer process exits, the SDK surfaces a transport failure
rather than leaving a blank view.

The public contract matches the Web, iOS, and Flutter SDKs:

- commands are `suspend` functions that throw typed `SeatLayerException` values
  (`Bridge`, `Timeout`, `Transport`, `Incompatible`, `Destroyed`);
- events arrive as a typed `SeatLayerEvent` `SharedFlow`;
- protocol negotiation (revision 1) fails closed and reports `sl_incompatible`
  when the app and bundle share no revision or a required capability is missing;
- unknown events survive as `SeatLayerEvent.Unknown`, and value classes such as
  `EventMode` and `SeatLayerViewMode` accept unrecognised raw strings, so a newer
  bundle never crashes an older app.

Every command carries a correlation id and a timeout (15 seconds by default); a
late reply for a timed-out or replaced chart is discarded.

## Layout requirement

Give the map a definite height or make it full-screen. Do not place it inside a
`ScrollView`, `NestedScrollView`, or a scrolling list item — the canvas owns pan
and pinch for map navigation, so an enclosing scrolling parent and the map fight
over every gesture. The SDK already disables the WebView affordances that would
compete with the canvas: scrollbars, over-scroll, and the built-in zoom
controls. The hosted page sets `touch-action: none` so the canvas receives raw
pan and pinch gestures instead of browser scrolling.

## Commands

```kotlin
val controller = seatMap.controller

val seats = controller.getSelection()
val hold = controller.getCurrentHold()
controller.extendHold()
controller.release()

val best = controller.bestAvailable(quantity = 4)
val ga = controller.holdGeneralAdmission(areaId = "floor", quantity = 2)

controller.setSeatTier(seatId = "A-12", tierId = "adult")
controller.selectObjects(listOf("A-12", "A-13"))
controller.setSelectableObjects(listOf("A-12", "A-13", "A-14"))
val validity = controller.getSelectionValidity()
controller.setFloor("balcony")
controller.setViewMode(SeatLayerViewMode.Isometric)
controller.setColorblindSafe(true)
controller.zoomToFit()
```

The full command surface: `hold` · `resumeHold` · `extendHold` · `release` ·
`releaseLabels` · `bestAvailable` · `holdGeneralAdmission` · `setSeatTier` ·
`getSelection` · `getCurrentHold` · `selectObjects` · `deselectObjects` ·
`clearSelection` · `selectCategories` · `deselectCategories` ·
`setSelectableObjects` · `setMaxSelection` · `getSelectionValidity` ·
`refreshAccess` · `getGeneralAdmissionAreas` · `getFloors` · `setFloor` ·
`setColorblindSafe` · `setViewMode` · `getViewMode` · `zoomIn` · `zoomOut` ·
`zoomToFit` · `destroy`

Before calling a newly introduced command against an older web runtime, inspect
the negotiated capability:

```kotlin
if (controller.bundle.value?.supportsCommand("bestAvailable") == true) {
    controller.bestAvailable(quantity = 2)
}
```

## Events

`controller.events` emits: `SelectionChanged` · `SelectionValidityChanged` ·
`SelectionValid` · `SelectionInvalid` · `SelectionLimitReached` ·
`BuyerAccessExpired` · `BuyerAccessUnavailable` · `SelectedObjectsUnavailable` ·
`HoldChanged` · `HoldRestored` · `HoldExpired` · `GeneralAdmissionClicked` ·
`Hint` · `Error` · `SeatHovered` · `DeckTapped` · `Checkout` · `Unknown`

See [the bridge reference](docs/bridge.md) for lifecycle, events, errors, and
the full command surface.

## Configuration

`SeatLayerConfiguration` accepts:

| Option | Purpose |
| --- | --- |
| `event` | Required public event key. |
| `apiBase` | Optional SeatLayer API endpoint override. |
| `publicKey` | Optional public SDK key. Never provide a secret key. |
| `maxSelection` | Maximum buyer selection. |
| `selectedObjects`, `selectableObjects` | Initial selection and selectable allow-list. |
| `numberOfPlacesToSelect`, `selectionValidators` | Exact-count and adjacency/orphan rules. |
| `buyerAccessToken`, `buyerAccessTokenProvider` | One-shot or renewable private buyer access. |
| `locale`, `messages` | Locale and UI message overrides. |
| `currency` | Buyer-facing currency. |
| `colorblindSafe` | Accessible palette preference. |
| `initialView` | Flat, isometric, or perspective view. |
| `showsWebSeatTooltip` | Enables the web-rendered tooltip; off by default. |
| `commandTimeoutMillis` | Per-command timeout; defaults to 15 seconds. |
| `handshakeTimeoutMillis` | Initial ready timeout; defaults to 30 seconds. |
| `hostInfo` | Extra host identification sent with the handshake. |

For private channel inventory, mint short-lived sessions on your backend for the
exact allowed origin `https://cdn.seatlayer.io`:

```kotlin
SeatLayerConfiguration(
    event = "ev_private",
    buyerAccessTokenProvider = { context ->
        buyerBackend.mintSeatLayerAccess(context.reason)
    },
)
```

Bearer values stay in memory. The provider is re-invoked with a typed reason
(`initial`, `expiring`, `expired`, `unauthorized`, `reconnect`, `manual`), and a
provider failure is reported to the bundle without leaking its message.

## Build locally

```bash
./gradlew validate
./gradlew :sample:installDebug
```

`validate` runs unit tests, Android lint, the release and sample builds, Maven
POM generation, and the retained fixture checksum check.

## Frequently asked questions

### How do I add a seat map to an Android app?

Add `io.seatlayer:seatlayer-android:0.2.0` from Maven Central, put a
`SeatLayerView` on screen, and call `load(SeatLayerConfiguration(event = "…"))`
from a coroutine. The quick start above is a complete interactive seating chart
with live availability; the
[Android seat-map documentation](https://docs.seatlayer.io/buyer-sdk/mobile/)
covers lifecycle, commands, events, and runtime requirements in depth.

### Is this a native Android seat map or a WebView?

Rendering runs in an Android `WebView` on SeatLayer's immutable, version-pinned
buyer runtime, and application code never touches the web layer: commands,
payloads, errors, and events are all typed Kotlin. `SeatLayerView` is a real
Android `View`, the controller is coroutine- and `Flow`-based, and the API is
named to match the web `SeatingChart` so Android and web read as one product.

### Does it work with Jetpack Compose?

Yes. `SeatLayerView` is an ordinary Android `View` (a `FrameLayout` subclass),
so a Compose screen hosts it with `AndroidView { SeatLayerView(it) }` and a
definite size — do not place it in a scrollable column. Collect
`controller.events` inside `repeatOnLifecycle` so the stream stops with the
screen. The SDK itself has no Compose dependency and ships no Compose wrapper;
the bundled sample uses a plain `Activity`.

### Can I write the integration in Java instead of Kotlin?

The public API is Kotlin-first: commands are `suspend` functions and state is
exposed as `StateFlow`/`SharedFlow`, which Java cannot call idiomatically
without the Kotlin coroutines interop helpers. Add Kotlin to the module that
hosts the seat map — the rest of a Java app can stay as it is.

### How do temporary seat holds work?

When a buyer selects seats, the SDK creates a temporary hold that reserves the
inventory against concurrent buyers for a limited window. The hold expires
automatically if checkout does not complete — `SeatLayerEvent.HoldExpired` tells
the app to return the buyer to the map — and `extendHold` and `resumeHold` cover
longer checkouts and app restarts. This prevents double-selling without locking
seats forever.

### Can I use my own payment provider?

Yes. SeatLayer never processes payment inside the seat map. The app hands the
`holdId` to your backend, and your backend charges through any payment provider
you already use — Stripe, Adyen, Razorpay, or your own — before booking the hold
through the
[server-side checkout flow](https://docs.seatlayer.io/buyer-sdk/holds-and-checkout/).

### Can I evaluate the SDK without a SeatLayer account?

The unit test suite runs with no account and no WebView, because the bridge
channel is an interface that the tests substitute. Running the sample app on a
device does need an event key: it loads the hosted runtime and renders real
inventory. Create a free SeatLayer test event for that — `ReadyInfo.mode`
reports `test`, and a test event books no real inventory.

## Continue your Android integration

- [Follow the mobile seat-map integration guide](https://docs.seatlayer.io/buyer-sdk/mobile/)
  for setup, lifecycle, commands, events, and runtime requirements.
- [Connect seat holds to secure server-side checkout](https://docs.seatlayer.io/buyer-sdk/holds-and-checkout/)
  without exposing booking credentials in the app.
- [Run the complete checkout example](https://docs.seatlayer.io/examples/complete-checkout/)
  to connect the buyer hold id to payment and idempotent booking.
- [Compare SeatLayer's mobile seat map SDKs](https://docs.seatlayer.io/buyer-sdk/mobile/)
  when choosing between native Android, iOS, Flutter, and React Native.
- [Read the Android bridge reference](docs/bridge.md) for the protocol
  lifecycle, correlation, timeouts, and the full typed event surface.
- [Explore the 3D seating chart for web buyers](https://seatlayer.io/3d-seat-map/)
  as a separate browser capability when comparing the wider buyer experience.
- [Point AI coding agents at the SeatLayer docs index](https://docs.seatlayer.io/llms.txt)
  (`llms.txt`) for an agent-readable map of the documentation.

## SeatLayer SDK ecosystem

| Surface | Package or source |
| --- | --- |
| JavaScript | [`@seatlayer/js`](https://www.npmjs.com/package/@seatlayer/js) |
| React | [`@seatlayer/react`](https://www.npmjs.com/package/@seatlayer/react) |
| React Native | [`@seatlayer/react-native`](https://www.npmjs.com/package/@seatlayer/react-native) |
| iOS | [`seatlayer-ios`](https://github.com/seatlayer/seatlayer-ios) |
| Flutter | [`seatlayer`](https://pub.dev/packages/seatlayer) |
| Android | [`seatlayer-android`](https://github.com/seatlayer/seatlayer-android) (this package) |
| Server SDKs | [Node.js, Python, PHP, Ruby, .NET, Java, and Go](https://docs.seatlayer.io/server-sdk/install/) |

Releases follow semantic versioning. See [CHANGELOG.md](CHANGELOG.md).

## License

[MIT](LICENSE)
