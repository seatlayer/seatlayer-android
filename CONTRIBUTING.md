# Contributing

Use JDK 17 and an Android SDK containing platform 36.

```bash
./gradlew validate
```

Keep public API additions source-compatible where possible. New bridge fields
must decode loss-tolerantly because the native package and vendored Web SDK can
be upgraded independently.
