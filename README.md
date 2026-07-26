# SeatLayer Android SDK

The official native Kotlin SDK for embedding an interactive SeatLayer reserved-
seating map in an Android app.

[Documentation](https://docs.seatlayer.io/) ·
[Mobile SDK guide](https://docs.seatlayer.io/buyer-sdk/mobile/) ·
[Live demo](https://seatlayer.io/demo) ·
[AI toolkit](https://github.com/seatlayer/seatlayer-ai-toolkit) ·
[Web and React SDKs](https://github.com/seatlayer/seatlayer-sdk)

> **Public preview:** `0.1.0` is available from JitPack while the permanent
> `io.seatlayer:seatlayer-android` Maven Central namespace is completed.

## What is included

- `SeatLayerView`, a native Android `View` that owns the embedded seat map.
- A coroutine-based controller for holds, best available, general admission,
  tiers, floors, view modes, and zoom.
- Typed Kotlin models, `StateFlow` readiness, and `SharedFlow` events.
- An origin-restricted AndroidX WebKit bridge with no unrestricted
  `addJavascriptInterface`.
- A pinned, checksummed SeatLayer Web SDK bundle for deterministic builds.
- A runnable sample app and protocol/unit tests.

## Requirements

- Android API 24+
- JDK 17 for building the SDK
- An AndroidX application

## Install

Add JitPack to dependency resolution:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io") {
            content {
                includeGroup("com.github.seatlayer.seatlayer-android")
            }
        }
    }
}
```

Add the SDK:

```kotlin
// app/build.gradle.kts
dependencies {
    implementation(
        "com.github.seatlayer.seatlayer-android:seatlayer:v0.1.0",
    )
}
```

For a reproducible production build, pin an exact release tag as shown above.
Do not use JitPack's `main-SNAPSHOT` coordinate.

## Quick start

The view can be created in Kotlin or placed in XML.

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

`load` is a suspending function and returns `ReadyInfo`. Run it from a lifecycle-
appropriate coroutine on Android; the SDK safely moves WebView work to the main
thread.

## Hold seats

Seat selection happens inside the map. Your app then creates a short-lived hold:

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

Create the final booking from your trusted server. Never put a SeatLayer secret
or privileged booking credential in an Android application.

## Common commands

```kotlin
val controller = seatMap.controller

val seats = controller.getSelection()
val hold = controller.getCurrentHold()
controller.extendHold()
controller.release()

val best = controller.bestAvailable(quantity = 4)
val ga = controller.holdGeneralAdmission(areaId = "floor", quantity = 2)

controller.setSeatTier(seatId = "A-12", tierId = "adult")
controller.setFloor("balcony")
controller.setViewMode(SeatLayerViewMode.Isometric)
controller.setColorblindSafe(true)
controller.zoomToFit()
```

Before using a newly introduced command with an older bundled Web SDK, inspect
the negotiated capability:

```kotlin
if (controller.bundle.value?.supportsCommand("bestAvailable") == true) {
    controller.bestAvailable(quantity = 2)
}
```

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
| `locale`, `messages` | Locale and UI message overrides. |
| `currency` | Buyer-facing currency. |
| `colorblindSafe` | Accessible palette preference. |
| `initialView` | Flat, isometric, or perspective view. |
| `showsWebSeatTooltip` | Enables the web-rendered tooltip. |
| `commandTimeoutMillis` | Per-command timeout; defaults to 15 seconds. |
| `handshakeTimeoutMillis` | Initial ready timeout; defaults to 30 seconds. |

## Security model

The SDK loads only app-packaged content from
`https://appassets.androidplatform.net`. Native communication uses AndroidX
WebKit's origin-restricted message listener. File/content access, mixed content,
popups, external navigation, and third-party cookies are disabled.

The bridge is still a client boundary. Treat every event as untrusted input,
authorize inventory changes on your server, and finalize bookings server-side.

## Build locally

```bash
./gradlew validate
./gradlew :sample:installDebug
```

`validate` runs unit tests, Android lint, release/sample builds, Maven metadata
generation, and the vendored Web SDK checksum.

## Package status

- Public preview: JitPack release tags and GitHub release artifacts.
- Permanent coordinate: `io.seatlayer:seatlayer-android` on Maven Central after
  SeatLayer's Central Portal namespace and signing credentials are activated.

Releases follow semantic versioning. See [CHANGELOG.md](CHANGELOG.md).

## Other SeatLayer SDKs

- [JavaScript and React](https://github.com/seatlayer/seatlayer-sdk)
- [React Native](https://github.com/seatlayer/seatlayer-react-native)
- [Flutter](https://github.com/seatlayer/seatlayer-flutter)
- [iOS / Swift](https://github.com/seatlayer/seatlayer-ios)

## License

[MIT](LICENSE)
