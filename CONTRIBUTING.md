# Contributing

Use JDK 17, Node 22, and an Android SDK containing platform 36.

```bash
./gradlew validate
```

`validate` builds the external consumer projects under `consumer-smoke/` as a
**separate Gradle build**, which does not inherit the root build's SDK location.
Either export `ANDROID_HOME`, or create `local.properties` in *both* the root
and `consumer-smoke/`:

```bash
printf 'sdk.dir=%s\n' "$ANDROID_HOME" | tee local.properties consumer-smoke/local.properties
```

Both files are gitignored. Without the second one the gate runs for several
minutes and then fails at `verifyExternalConsumers` with "SDK location not
found", which looks like a build break and is not one. CI has `ANDROID_HOME` set
by the runner image and never needs either file.

Keep public API additions source-compatible where possible. New bridge fields
must decode loss-tolerantly because the native package and vendored Web SDK can
be upgraded independently.

Keep this public repository customer-facing. Do not add plans, handovers,
implementation audits/reviews, cross-platform comparison documents, manual QA
logs, evidence bundles, dated progress reports, or unreferenced screenshots.
Keep public product media in `docs/media/`, and keep regression artifacts only
where automated tests consume them. Run `bash scripts/check-public-repository.sh`
before opening a pull request.
