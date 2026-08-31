# Contributing

Use JDK 17 and an Android SDK containing platform 36.

```bash
./gradlew validate
```

Keep public API additions source-compatible where possible. New bridge fields
must decode loss-tolerantly because the native package and vendored Web SDK can
be upgraded independently.

Keep this public repository customer-facing. Do not add plans, handovers,
implementation audits/reviews, cross-platform comparison documents, manual QA
logs, evidence bundles, dated progress reports, or unreferenced screenshots.
Keep public product media in `docs/media/`, and keep regression artifacts only
where automated tests consume them. Run `bash scripts/check-public-repository.sh`
before opening a pull request.
