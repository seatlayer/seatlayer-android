# Security

Report vulnerabilities privately through GitHub's security advisory form.

The Android application selects and holds inventory. A trusted backend must
inspect the hold, calculate payment from trusted data, and create the booking.
Never put a SeatLayer secret key in an APK, Android resource, manifest, WebView,
log, analytics event, or sample application.

The SDK exposes its bridge only to the app-owned
`https://appassets.androidplatform.net` origin through AndroidX WebKit. It does
not use the legacy unrestricted `addJavascriptInterface` API.
