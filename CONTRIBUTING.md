# Contributing

Use JDK 17 and an Android SDK containing platform 36.

```bash
./gradlew validate
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

The sample's Java/Kotlin source examples compile the default widget, full
branding/part replacement, a custom Compose composition, the ready-made View
host, a pure Android View composition over the headless controller/map, and the
retained raw `0.2.x` view.

When intentionally changing public API, review and refresh the checked-in dumps
with `scripts/verify-public-api.sh --write`; `validate` rejects accidental API
or raw `0.2.x` ABI drift.

The `benchmark` module measures picker start-up on a release-like build. Record
numbers on a physical device:
`./gradlew :benchmark:connectedBenchmarkAndroidTest`.

Keep public API additions source-compatible where possible. New bridge fields
must decode loss-tolerantly because the native package and vendored Web SDK can
be upgraded independently.

Keep this public repository customer-facing. Do not add plans, handovers,
implementation audits/reviews, cross-platform comparison documents, manual QA
logs, evidence bundles, dated progress reports, or unreferenced screenshots.
Keep public product media in `docs/media/`, and keep regression artifacts only
where automated tests consume them. Run `bash scripts/check-public-repository.sh`
before opening a pull request.
