# Device verification

The unit and screenshot tests run on the JVM. They prove layout, colour, token
values and state transitions on a simulated canvas, and they are fast enough to
run on every change. They cannot prove anything about the real Android
runtime — there is no WebView in them, no touch dispatch, no window insets.

Three properties of the picker therefore have to be verified on a device or
emulator before a release:

1. **Chrome standing over the map must swallow its own taps.** The map renders
   in a WebView; the native controls float above it. A tap on a control that
   also reaches the canvas underneath selects whatever seat happens to sit
   there. The guard is the blocked-region list the SDK sends to the runtime.
   Goldens cannot see it: they have no WebView and no hit testing.
2. **Window insets and edge-to-edge behaviour.** The status bar, navigation
   bar, cutouts and gesture insets exist only on a real window.
3. **That the hosted runtime loads, renders and drives the bridge end to end.**
   A JVM test talks to a fake bridge. Only a device proves the real page boots,
   the handshake completes, and commands and events flow both ways.

Two scripts support that verification.

## `device-walk.sh` — drive the app and capture the frames

```
scripts/device-walk.sh [--serial <device>] [--out <dir>] [--check-only]
```

The script installs the sample app, opens it on an event, and walks the buyer
journey step by step, writing one PNG per step into the output directory
(default `artifacts/device-walk/`, which is not tracked by git).

The walk: overview (discs, price rail, test chip, Map|3D) → blocked-region
probe → focus a section → tap a seat → the confirm card → Cancel → tap and add
→ retap the seat → the remove card → the accessibility sheet → toggle a filter
→ find seats → hold and continue → back → add a second seat → continue again so
the hold is replaced → let the hold expire → 3D → the view from a seat.

Every wait polls for a condition — a control appearing in the view hierarchy, a
snapshot arriving in the log, the hierarchy going two dumps without changing.
There are no blind sleeps, so a slow first load fails by timeout naming the step
it was waiting on rather than quietly capturing the wrong frame.

### Environment

Nothing that identifies an event, a backend or a buyer is committed. The script
refuses to start when a required variable is unset, and never prints any of
their values.

| Variable | Required | What it supplies |
| --- | --- | --- |
| `SEATLAYER_WALK_EVENT` | yes | the event the sample app opens |
| `SEATLAYER_WALK_API_BASE` | yes | the API base URL the picker talks to |
| `SEATLAYER_WALK_PUBLIC_KEY` | no | a key-scoped event's public key |
| `SEATLAYER_WALK_TOKEN` | no | a private event's buyer access token |
| `SEATLAYER_WALK_AVD` | when no device is attached | the AVD to boot |
| `SEATLAYER_WALK_APK` | no | an already-built sample APK (default: the debug build output) |
| `SEATLAYER_WALK_OUT` | no | where frames are written |
| `SEATLAYER_WALK_INTEGRATION` | no | which sample configuration to open (default: the ready-made Compose picker) |
| `SEATLAYER_WALK_HOLD_TTL_SECONDS` | no | the event's hold window; `0` skips the expiry step |
| `SEATLAYER_WALK_SECTION_X/Y`, `_SEAT_X/Y`, `_SEAT2_X/Y` | no | where on the map to tap, as a fraction of the screen |
| `SEATLAYER_WALK_EXPECT_SIZE`, `_EXPECT_DENSITY` | no | the screen the reference frames were captured on |
| `SEATLAYER_WALK_LABELS` | no | an alternative locale strings file for the control labels |
| `SEATLAYER_WALK_PYTHON` | no | the Python interpreter to use |

Taps on native chrome are located by label: the script reads the English labels
out of `Design/locale_strings.json`, so renaming a string in the design source
keeps the walk working. Taps on the map itself are geometric — the map is a
canvas with nothing in the view hierarchy to aim at — so the fractions above are
specific to the chart the walk runs against and may need adjusting for another
one. Labels are treated as regular expressions; a label containing regex
punctuation may need escaping.

### Prerequisites

* `adb` on `PATH` (Android platform tools), and `emulator` too if the script is
  to boot an AVD itself.
* Python 3 for the hierarchy parsing (standard library only).
* An AVD at the phone size the screenshot tests use — a Pixel 5 profile,
  API 35 — created from a **system image that ships a WebView provider**
  (a Google APIs image). An image without one cannot render the map at all;
  the script checks for the package and stops with that message rather than
  timing out on a blank screen.
* A built sample APK. The script does not run Gradle; build it once with
  `./gradlew :sample:assembleDebug`.

`--check-only` runs every prerequisite and environment check and exits without
touching a device. Use it to confirm a machine is ready.

### The blocked-region probe

Immediately after the overview frame, while the cart is still empty, the walk
taps the exact centre of each control that stands over the map — the
accessibility disc, the whole-venue disc, the zoom disc — and asserts that the
app's own selection count did not change. The count is read from the snapshots
the sample app logs, so the assertion is on the picker's state, not on pixels.

**What it proves:** the tap was consumed by the control and did not fall through
to the WebView canvas, which is exactly the failure the blocked-region list
prevents and exactly the failure no JVM test can see.

**What it does not prove:** that the blocked list is correct at every camera
state (the probe runs at the overview rung only), that it is re-sent quickly
enough when insets or the layout change mid-gesture, or that the control's own
action fired. The walk steps that follow cover the actions.

## `pixel-compare.py` — score the frames against a reference

```
scripts/pixel-compare.py <captured.png> <reference.png> [--out diff.png]
scripts/pixel-compare.py <captured-dir> <reference-dir> [--out diff-dir]
```

In directory mode it pairs frames by file name. For each pair it aligns the two
images by the whole-pixel offset that minimises the mean absolute luma
difference, marks every pixel still differing by more than the tolerance,
and reports:

* `diff_pct` — the share of marked pixels, which catches broad drift such as a
  wrong surface colour or a shifted background;
* `largest_block` — the height and width of the largest connected group of
  marked pixels, which catches what a percentage hides: a missing badge or a
  moved control is a tiny share of a big screen but one solid patch;
* `heatmap` — a PNG showing where the difference sits;
* `offset` — how far the frames had to be nudged to line up.

It prints JSON and exits non-zero if any pair fails.

### Thresholds

Defaults: **`diff_pct` below 1.5 %** and **no connected block wider or taller
than 6 px**, with a per-pixel luma tolerance of 24/255 and an alignment search
of ±6 px. These are the numbers the React Native parity round settled on for
the same kind of comparison; they pass text antialiasing (which scatters as
isolated pixels) and fail a control that is absent or displaced. Override with
`--max-diff-pct`, `--max-block-px`, `--tolerance` and `--max-shift`.

Requires Pillow and numpy.

### Reading a result

A pass is: every step captured, the blocked-region probe green, and every frame
under both thresholds against the reference frame for the same step. A frame
whose `diff_pct` is small but whose `largest_block` is large is the interesting
failure — open the heatmap and look at that block; it is usually one element
missing, mis-sized, or a few pixels off.

Frames only compare pixel-for-pixel when both were captured at the same screen
size and density. The walk warns when the device does not match the expected
size.
