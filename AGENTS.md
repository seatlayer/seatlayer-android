# Agent guidance

This is the public SeatLayer Android SDK.

## Public-repository hygiene — hard rule

- Commit only product source, tests, build/release automation, public examples,
  package metadata, and customer-facing integration, API, migration, or
  security documentation.
- Never commit planning documents, handovers, implementation audits or reviews,
  cross-SDK comparison matrices, manual QA journals, evidence bundles, dated
  progress reports, before/rejected captures, credentials, non-public hosts,
  non-public repository references, or developer-machine paths.
- Public product media belongs in `docs/media/`; regression images belong only
  in automated test-fixture locations. Do not use the Git repository as an
  evidence archive.
- Record verification in CI and release checks, not in tracked screenshots or
  narrative proof documents.
- Run `bash scripts/check-public-repository.sh` before committing or pushing.

1. Treat `docs.seatlayer.io` as the product-contract authority.
2. Preserve bridge protocol negotiation, command correlation, timeouts, stale
   event filtering, and unknown-event tolerance.
3. Keep booking and secret keys on an integrator's trusted backend.
4. Never reference non-public resources, credentials, or local developer paths
   in public source, history, examples, or artifacts.
5. The vendored Web SDK must be copied from a verified public release and its
   version and SHA-256 recorded in the README.
6. Run `./gradlew validate` before releasing.
