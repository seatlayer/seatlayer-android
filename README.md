# SeatLayer Android SDK

[![CI](https://github.com/seatlayer/seatlayer-android/actions/workflows/ci.yml/badge.svg)](https://github.com/seatlayer/seatlayer-android/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.seatlayer/seatlayer-android)](https://central.sonatype.com/artifact/io.seatlayer/seatlayer-android)
[![Android](https://img.shields.io/badge/Android-API%2024%2B-3ddc84.svg)](https://developer.android.com/)
[![License: MIT](https://img.shields.io/badge/license-MIT-111827.svg)](LICENSE)

The official native Android SDK for SeatLayer reserved seating. Version `0.3.0`
adds a complete adaptive Jetpack Compose picker, a headless protocol-2
state/controller, reusable native components, and View/XML interop while
preserving the existing raw `SeatLayerView` API.

The hosted renderer owns only venue-map pixels. Your Android UI can use the
ready-made picker, replace individual parts, compose every stock component into
its own layout, or render completely custom Views around the headless map.

[Android integration guide](https://docs.seatlayer.io/buyer-sdk/mobile/) ·
[Secure holds and checkout](https://docs.seatlayer.io/buyer-sdk/holds-and-checkout/) ·
[Web demo](https://app.seatlayer.io/demo/play/grand-theatre) ·
[Native picker reference](docs/native-picker.md) ·
[0.2.x migration guide](docs/migration-0.3.md) ·
[Bridge reference](docs/bridge.md) ·
[Release validation](docs/release-validation.md)

## Packages

| Artifact | Use it for |
| --- | --- |
| `io.seatlayer:seatlayer-android` | Raw protocol-1 API plus protocol-2 headless state, controller, and map View. No Compose dependency. |
| `io.seatlayer:seatlayer-android-compose` | Ready-made picker, all native components, themes/styles/builders, and View/XML host. Strictly version-aligned with core. |

Both artifacts ship on the same release train.

## Requirements

- Android API 24 or newer; compile SDK 36
- JDK 17 for local builds
- AndroidX
- Android System WebView support for secure WebKit message listeners
- Document-start JavaScript support when using the protocol-2 native picker
- `android:enableOnBackInvokedCallback="true"` on picker activities when
  targeting Android 15 or lower and predictive-back animation is required

## Install

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("io.seatlayer:seatlayer-android:0.3.0")
    implementation("io.seatlayer:seatlayer-android-compose:0.3.0")
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

Raw-only applications can omit `seatlayer-android-compose`.

## Ready-made native picker

```kotlin
class CheckoutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SeatLayerPicker(
                configuration = SeatLayerConfiguration(
                    event = "ev_your_event_key",
                    currency = "USD",
                    maxSelection = 6,
                ),
                modifier = Modifier.fillMaxSize(),
                onCheckout = { handoff ->
                    checkoutBackend.start(
                        holdId = handoff.holdId,
                        amount = handoff.total,
                        currency = handoff.currency,
                    )
                },
                onError = ::reportPickerError,
                onClose = ::finish,
            )
        }
    }
}
```

The default tree includes adaptive compact/wide layouts, native header and
legend, floors and sections, accessibility filters, confirmation, GA/table
quantities, dense cart, best available, checkout, 3D/seat-view controls,
hold-lapse recovery, light/dark branding, 37 locale dictionaries, lifecycle
reconciliation, and Android predictive back.

Give the picker a definite size and do not place the map in a scrolling list.
The canvas owns pan and pinch gestures.

Recomposition retains the active picker. Activity/process destruction creates
a new session; recover an existing hold by restoring its opaque id through
`SeatLayerPickerOptions(initialHoldId = ...)` and the same renewable
buyer-access provider. Verify that runtime-owned resume path with real
inventory, and never log the hold id.

## Customize or build your own UI

Three layers can be mixed without forking the SDK:

1. Pass `SeatLayerPickerOptions`, `SeatLayerPickerTheme`, localized
   `SeatLayerPickerStrings`, and `SeatLayerPickerStyles` to configure the
   ready-made picker.
2. Use `SeatLayerPickerBuilders` to decorate or replace any of 25 independent
   parts.
3. Use `SeatLayerPickerScope` and public components such as
   `SeatLayerPickerMap`, `SeatLayerPickerHeader`, `SeatLayerPriceLegend`,
   `SeatLayerConfirmCard`, and `SeatLayerPickerCartSheet` in a host-owned
   hierarchy.

```kotlin
SeatLayerPickerScope(
    configuration = configuration,
    callbacks = SeatLayerPickerCallbacks(
        onCheckout = ::openCheckout,
        onClose = onClose,
    ),
) {
    SeatLayerPickerLifecycle()
    SeatLayerPickerBackHandler()

    Column(Modifier.fillMaxSize()) {
        SeatLayerPickerHeader(onClose)
        SeatLayerPriceLegend()
        Box(Modifier.weight(1f)) {
            SeatLayerPickerMap(Modifier.fillMaxSize())
            SeatLayerPickerMapControls()
            SeatLayerConfirmCard()
        }
        SeatLayerPickerCartSheet()
    }
}
```

See the [native picker reference](docs/native-picker.md) for the full component
catalogue, builder context, per-part styling, localization fallback, mandatory
test/attribution disclosure, headless state guarantees, and custom View setup.

## View and XML applications

Use `SeatLayerPickerView` for the exact ready-made tree in an existing View
screen:

```xml
<io.seatlayer.android.compose.SeatLayerPickerView
    android:id="@+id/picker"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

```kotlin
binding.picker.bind(
    lifecycleOwner = this,
    configuration = configuration,
    onCheckout = ::openCheckout,
    onReady = ::observeReady,
    onSnapshot = ::observeSnapshot,
    onClose = ::finish,
)
```

For fully custom Views, bind a `SeatLayerPickerStateHolder` to
`SeatLayerPickerMapView`, collect its `state`, and invoke its typed `controller`.

## Existing raw map API

The `0.2.x` low-level surface remains available and independent from the native
picker:

```kotlin
class RawMapActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var seatMap: SeatLayerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        seatMap = SeatLayerView(this)
        setContentView(seatMap)

        scope.launch {
            seatMap.controller.events.collect(::handleSeatLayerEvent)
        }
        scope.launch {
            seatMap.load(SeatLayerConfiguration(event = "ev_your_event_key"))
        }
    }

    override fun onDestroy() {
        seatMap.destroy()
        scope.cancel()
        super.onDestroy()
    }
}
```

`SeatLayerView` continues to negotiate protocol 1. The native picker uses a
separate protocol-2 profile, so adding Compose does not silently change an
existing raw integration. See the [0.3.0 migration guide](docs/migration-0.3.md)
for dependency choices, lifecycle differences, and an adoption checklist.

## Checkout and security boundary

The app selects and holds inventory. A trusted backend inspects and books the
hold after payment or order validation.

- Never ship a SeatLayer secret key in the APK, WebView, or configuration.
- Pass only the opaque checkout `holdId` and normal order context to the backend.
- Calculate payable totals from server-inspected hold items, not app input.
- Reuse a stable order id as the booking reference for safe retries.

Private events should use a renewable in-memory provider:

```kotlin
SeatLayerConfiguration(
    event = "ev_private",
    buyerAccessTokenProvider = { context ->
        buyerBackend.mintSeatLayerBuyerAccess(context.reason)
    },
)
```

The SDK loads only the immutable
`https://cdn.seatlayer.io/seatlayer-js@0.71.5/mobile.html` page. Web messages are
restricted to the exact HTTPS origin and main frame; external navigation,
file/content access, mixed content, popups, multiple windows, and third-party
cookies are blocked. The bridge does not use `addJavascriptInterface`.

## Headless picker behavior

`SeatLayerPickerStateHolder.state` is a `StateFlow` of immutable snapshots and
native presentation state. `SeatLayerPickerController` provides serialized
suspending commands for selection, filters, floors/sections, view modes,
camera, GA/tables, holds, best available, lifecycle refresh, checkout handoff,
undo, back, and close.

Snapshots are accepted only for the configured event and current session, and
only at increasing revisions. Optional malformed fields are ignored without
discarding a valid snapshot; invalid schema/session/revision/event identity is
rejected. Empty UI appears only when the runtime gives affirmative sold-out or
all-unavailable evidence.

## Optional WebView engine prewarm

For the best BOOK NOW latency, start the Android System WebView engine and warm
the immutable mobile runtime while the host's Event Details screen is visible:

```kotlin
lifecycleScope.launch {
    val result = SeatLayerPickerPrewarmer.prewarm(applicationContext)
    check(result.engineStarted)
}
```

The prewarmer loads only the pinned credential-free runtime page in a temporary
hardened WebView and destroys that WebView when the main document finishes or
times out. It never creates a picker session or receives an Activity, event
key, public key, buyer token, or hold. `rendererPageLoaded` reports whether the
document warm completed; a `false` value is a graceful page-warm fallback after
the engine itself started. Concurrent callers share one operation, cancelling
one coroutine cancels only that caller's wait, and a later raw or picker load
waits for any operation already in flight.

The `benchmark` module measures cold, warm, and prewarmed picker startup with a
release-like, non-debuggable sample. Record release evidence on physical
Android hardware with a current System WebView:

```bash
./gradlew :benchmark:connectedBenchmarkAndroidTest
```

Emulator runs are useful for functional checks but are not release timing
evidence.

## Build locally

```bash
./gradlew validate
./gradlew :sample:installDebug
```

`validate` runs focused unit tests, compiles the deterministic Compose
accessibility fixture and release-like Macrobenchmark, checks generated
locale/token source locks, lints and builds both artifacts, generates both
Maven POMs, verifies public API/raw ABI, builds the R8-minified consumer sample,
builds standalone raw Java/Kotlin and Compose apps from temporary Maven
coordinates on both the oldest and current supported build toolchains, verifies
that the raw dependency graph contains no Compose, and checks the retained
fixture checksum.

The external-consumer matrix is intentionally frozen rather than implied:

| Lane | JDK | Gradle | AGP | Kotlin/Compose plugin |
| --- | --- | --- | --- | --- |
| Oldest supported | 17 | 8.13 | 8.12.0 | 2.3.10 |
| Current | 17 | 9.6.1 | 9.2.1 built-in Kotlin | 2.3.10 |

Both lanes compile SDK `minSdk 24` raw Java/Kotlin and ready-made/custom
Compose/View consumers against `compileSdk 36`. The SDK itself uses Compose BOM
`2026.06.00`, Activity Compose `1.13.0`, Lifecycle `2.10.0`, and AndroidX
WebKit `1.16.0`.

The sample launcher mirrors the Flutter and React Native DesiPass demo: choose
a live event, open its details, tap **BOOK NOW**, use the picker, and receive a
buyer-safe checkout handoff. There is no credential form in the buyer journey.
For local development, the debug variant reads an ignored environment file at
build time; release and benchmark variants always compile empty DesiPass
values.

Use either `sample/.env.local`, the `DESIPASS_ENV_FILE` environment variable,
or the ignored Android `local.properties` file to point at an existing local
environment file:

```properties
# local.properties -- ignored by Git
desipass.envFile=/absolute/path/to/an/ignored/.env.local
```

The file may use `DESIPASS_GRAPHQL_URL` / `DESIPASS_API_KEY` or the existing
React Native names `EXPO_PUBLIC_DESIPASS_GRAPHQL_URL` /
`EXPO_PUBLIC_DESIPASS_API_KEY`. `sample/.env.example` keeps both values blank;
no private host or credential is present in tracked source. Never put a key in
a command line, tracked file, screenshot, or log. Rebuild the debug sample
after changing the local environment.

Every SDK integration path remains runnable through the explicit
`MainActivity` intent:

```bash
adb shell am start -n io.seatlayer.sample/.MainActivity \
  --es seatlayerIntegration custom-view \
  --es seatlayerEvent ev_your_event
```

Supported values are `ready-compose`, `branded-compose`, `custom-compose`,
`ready-view`, `custom-view`, and `raw-view`. The Java/Kotlin source examples
therefore compile the default widget, full branding/part replacement, a custom
Compose composition, the ready-made View host, a pure Android View composition
over the headless controller/map, and the retained raw protocol-1 view.

To launch the DesiPass journey explicitly:

```bash
adb shell am start \
  -n io.seatlayer.sample/.HostedValidationActivity
```

The same live list → details → **BOOK NOW** → picker → checkout journey can
exercise each supported complete/custom integration without changing the demo
or embedding credentials:

```bash
# Ready-made Compose picker (the default)
adb shell am start -n io.seatlayer.sample/.HostedValidationActivity \
  --es seatlayerHostedIntegration ready-compose

# Host-owned Compose layout over the public picker scope/components
adb shell am start -n io.seatlayer.sample/.HostedValidationActivity \
  --es seatlayerHostedIntegration custom-compose

# Ready-made picker hosted from an Android View
adb shell am start -n io.seatlayer.sample/.HostedValidationActivity \
  --es seatlayerHostedIntegration ready-view
```

An omitted or unknown `seatlayerHostedIntegration` value safely selects
`ready-compose`.

The debug-only host configuration is resolved locally and is never rendered or
logged. The list/detail API supplies only host event data; the details screen
prefetches renewable buyer access, and **BOOK NOW** opens the protocol-2 picker.
Prewarm loads only the immutable credential-free mobile runtime: it never
creates a hidden picker session and receives no event identity, buyer access,
or host credential. The host key is not passed to SeatLayer. Buyer tokens,
event keys, and opaque hold ids are likewise not rendered or logged; the
checkout evidence screen records only buyer-safe event, ticket count, currency,
and total. Never provide a SeatLayer secret key to this sample.

When intentionally changing public API, review and refresh the checked-in dumps
with `scripts/verify-public-api.sh --write`; `validate` rejects accidental API
or raw `0.2.x` ABI drift.

## SDK ecosystem

| Surface | Package or source |
| --- | --- |
| JavaScript | [`@seatlayer/js`](https://www.npmjs.com/package/@seatlayer/js) |
| React | [`@seatlayer/react`](https://www.npmjs.com/package/@seatlayer/react) |
| React Native | [`@seatlayer/react-native`](https://www.npmjs.com/package/@seatlayer/react-native) |
| Flutter | [`seatlayer`](https://pub.dev/packages/seatlayer) |
| iOS | [`seatlayer-ios`](https://github.com/seatlayer/seatlayer-ios) |
| Android | [`seatlayer-android`](https://github.com/seatlayer/seatlayer-android) |
| Server SDKs | [Node.js, Python, PHP, Ruby, .NET, Java, and Go](https://docs.seatlayer.io/server-sdk/install/) |

See [CHANGELOG.md](CHANGELOG.md). Releases follow semantic versioning.

## License

[MIT](LICENSE)
