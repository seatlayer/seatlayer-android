# Agent guidance

This is the public SeatLayer Android SDK.

1. Treat `docs.seatlayer.io` as the product-contract authority.
2. Preserve bridge protocol negotiation, command correlation, timeouts, stale
   event filtering, and unknown-event tolerance.
3. Keep booking and secret keys on an integrator's trusted backend.
4. Never reference private SeatLayer repositories, internal hosts, credentials,
   or local developer paths in public source, history, examples, or artifacts.
5. The vendored Web SDK must be copied from a verified public release and its
   version and SHA-256 recorded in the README.
6. Run `./gradlew validate` before releasing.
