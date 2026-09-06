# Design source

The four files beside this one are the SeatLayer buyer picker's shared design
source, copied verbatim from the reference SDK. They are not edited here.

| File | What it is |
| --- | --- |
| `tokens.json` | every colour, size, radius, elevation, opacity, type role, duration, spring, curve, haptic cue and English string the native chrome draws |
| `locale_strings.json` | the runtime's own wording for those strings, in 37 locales |
| `components.md` | the name every SDK gives each part, so the four agree |
| `picker-spec.md` | what each part does, and what deliberately does not exist |
| `source-lock.json` | the SHA-256 of the first three, so a silent edit cannot pass |

Every number and every word in `:seatlayer-compose` comes from here through the
generators, never by hand:

```sh
node tool/generate-picker-tokens.mjs      # SeatLayerPicker*Tokens.g.kt
node tool/generate-picker-locales.mjs     # SeatLayerPickerStrings.g.kt + locales
```

Both take `--check` and fail without writing, which is what CI runs. The Gradle
task `verifyPickerDesign` proves the design files match `source-lock.json` and
that every generated file's header carries those same digests, so a generated
file that was never regenerated cannot pass as a fresh one.

To take a new design revision: copy the files, update `source-lock.json` to the
new digests, run both generators, and commit the result together.
