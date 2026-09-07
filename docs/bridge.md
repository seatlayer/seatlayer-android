# Android bridge reference

SeatLayer Android exposes two intentionally separate hosted bridge profiles:

| Surface | Protocol | Native API |
| --- | --- | --- |
| Raw map | revision 1 | `SeatLayerView` and `SeatLayerController` |
| Native picker | revision 2 | `SeatLayerPickerStateHolder`, `SeatLayerPickerController`, and `SeatLayerPickerMapView` |

Adding the native picker does not renegotiate or change an existing raw
integration. Both profiles load the immutable
`seatlayer-js@0.84.1/mobile.html` runtime.

**Wire names are unchanged in 0.4.0.** The 0.4.0 release renames a number of
Kotlin symbols to the names every SeatLayer buyer SDK shares, but every
capability string, command name, event name and snapshot field on this page is
exactly what it was before. A runtime pinned to an older bridge sees no
difference, and a proxy or log that matches on these strings needs no change.
Where a Kotlin name and a wire name now differ — `setLimitedViewHidden` sends
`picker.setLimitedViewFilter`, `setMapInteractionEnabled` sends
`picker.setInteractionEnabled`, `set3DNavigationMode` sends
`picker.setVenue3DNavigationMode`, `setGeneralAdmissionQuantity` sends
`picker.holdGA`, `rejectCheckoutHandoff` sends `picker.rejectHandoff`,
`removeObject` sends `picker.removeCartLine`, and `refreshAccess` sends
`picker.refreshAvailability` — the wire name is the one on this page.

## Raw lifecycle

1. Create one `SeatLayerView`.
2. Collect `controller.events`.
3. Call `load(configuration)` from a coroutine.
4. Await `ReadyInfo` before sending commands.
5. Call `destroy()` when the owning screen is destroyed.

`destroy()` is terminal. Calling `load` again before destruction replaces the
active raw chart and rejects commands still awaiting replies.

The raw controller exposes `ready`, `bundle`, `events`, and `isReady`, plus the
existing selection, hold, GA, floor, accessibility, camera, and private-access
commands.

## Picker lifecycle

One `SeatLayerPickerStateHolder` owns one picker session and controller. A
ready-made `SeatLayerPicker` creates and closes it automatically. Custom
integrations bind the holder to one `SeatLayerPickerMapView` and collect
`stateHolder.state`.

Picker startup is:

1. Install the secure message listener and document-start native adapter.
2. Negotiate exactly protocol revision 2 and required native-picker
   capabilities.
3. Send `sys.init` with `surface.kind = picker`, `chromeOwner = native`, and
   renderer behavior options.
4. Accept `sys.ready` and its initial snapshot.
5. Apply only snapshots with the configured event key, current session id, and
   a strictly increasing revision.

Call `stateHolder.close()` for orderly shutdown. It serializes close, aborts
picker-owned inventory when ready, destroys the controller, and is idempotent.
`SeatLayerPickerMapView.close()` performs that sequence before destroying the
WebView.

## Picker capabilities

The handshake splits capabilities into two sets, and the split is the whole
contract: a required capability the runtime does not advertise fails startup,
an optional one it does not advertise withholds a surface in silence. A buyer
never meets a control whose command the runtime cannot answer.

**Required.** Ten are always required, and three more are required only when
the corresponding option is enabled:

| Capability | Required when |
| --- | --- |
| `picker-session-v2` | always |
| `picker-snapshot-v1` | always |
| `picker-actions-v1` | always |
| `native-picker-chrome-v1` | always |
| `native-chrome-contract-v1` | always |
| `checkout-handoff-v1` | always |
| `checkout-handoff-reject-v1` | always |
| `hold-ownership-v1` | always |
| `cart-line-remove-v1` | always |
| `table-quantity-v1` | always |
| `venue-3d-v1` | `enable3D` |
| `venue-3d-controls-v1` | `enable3D` |
| `seat-view-v1` | `enableSeatView` |

Thirty-four commands and the `picker.snapshot` event are required alongside
them; `enable3D` adds `picker.setBuyerView` and
`picker.setVenue3DNavigationMode`, and `enableSeatView` adds
`picker.openSeatView`.

**Optional.** Absent means the surface is not offered:

| Capability | What it unlocks |
| --- | --- |
| `native-seat-view-chrome-v1` | native panorama caption/badge instead of the runtime's own |
| `viewport-insets-v1` | pushing native chrome bounds into the map's camera |
| `floor-stack-v1` | the floor selector and strip |
| `chart-load-trace-v1` | typed chart-load traces on `chartLoads` |
| `availability-refresh-v1` | `refreshAccess()` on foreground |
| `access-needs-v1` | the runtime-authored accessibility sheet |
| `hold-selection-v1` | `holdSelection()` |
| `seat-view-thumbnail-v1` | the seat-view thumbnail and sightline metadata on a seat |
| `accessibility-focus-v1` | `focusAccessibilityFilter` and `focusNextAccessibleSection` |
| `section-access-counts-v1` | per-section accessible counts |

**Present-only reads.** Two capabilities gate no command at all. They say only
that the runtime puts a field in its snapshot, and the SDK decodes that field
when it arrives:

| Capability | Snapshot field it announces |
| --- | --- |
| `category-availability-v1` | `categories[].available` |
| `seat-screen-point-v1` | `selection[].screenPoint` |

`supportsCapability`, `supportsCommand`, and `supportsEvent` expose all three
sets to a host that draws its own chrome.

## Command-table-gated commands

Three camera and focus commands are newer than the required contract and are
gated on the `hello` command table alone, not on a capability string. Each is a
no-op on a runtime that does not list it: the native chrome still works, it
simply does not ask the map for the extra move.

| Command | Kotlin | What it does |
| --- | --- | --- |
| `picker.setSelectionFocus` | `setSelectionFocus` / `forgetSelectionFocus` | marks the seat a card is asking about as the candidate, and clears the mark with the card |
| `picker.setBlockedRegions` | `setBlockedRegions` | tells the map which rectangles native chrome covers, so it never parks a seat under them |
| `picker.frameSeat` | `frameSeat` | brings one seat into the free part of the map, at a given fraction of the visible height |

`picker.closeSeatView`, `picker.focusAccessibilityFilter` and
`picker.focusNextAccessibleSection` are gated the same way, the last two behind
`accessibility-focus-v1` as well.

## Picker events

| Event | What it carries |
| --- | --- |
| `picker.snapshot` | the authoritative state; required |
| `seatView.changed` | seat id, title, caption, badge, real/generated flag, drag hint |
| `seat.retap` | the buyer tapped a seat already in the cart; raises that seat's removal question |
| `telemetry.chartLoad` | a typed chart-load trace, with `chart-load-trace-v1` |

`seat.retap` is read only when the runtime advertises it. Without it, a tap on
a seat already in the cart is whatever the runtime does with it, and the SDK
raises no removal question of its own.

## Picker snapshots

The accepted schema is `seatlayer.picker.snapshot/1`. Schema, session id,
revision, and event key are structural identity fields; a malformed value
rejects the snapshot. Optional malformed collections or fields are skipped so
one future or corrupt optional value does not erase otherwise valid live state.

Snapshots include native-chrome truth for event/branding, categories, zones,
floors, sections, selection, confirmed cart lines, hold status, map rung and
filters, availability, optional 3D state, and optional seat-view state.

These additive fields are decoded when a runtime sends them and simply absent
when it does not:

| Field | What the native chrome does with it |
| --- | --- |
| `map.atVenueFit` | dims the whole-venue and zoom-out controls at the exact whole-venue pose |
| `map.canZoomIn` | retires the `+` control at the zoom ceiling |
| `sections[].accessibleFree` | the accessible count beside a section |
| `selection[].screenPoint` | where the seat is on screen, so a card can be placed off it |
| `selection[].accessibility`, `wheelchairSpaceType`, `restrictedView`, `obstructedView`, `premium`, and the organizer's note | the seat-note bands on the seat card, and the same words on the cart card |
| `selection[].seatViewThumbnail`, `sightlineMetres`, `seatViewConfidence` | the seat-view thumbnail, its distance, and how the view was produced |
| `cartCompletedFromSelection` | a cart the runtime completed from the current selection |
| error `code` `hold_selection_mismatch` | the hold no longer matches the cart, so Continue replaces it |

The error `code` is an open set: the API's own 409 reasons are promoted into
it. Never treat it as a closed enum.

Additive 3D position fields preserve old-runtime compatibility. When all
target/previous/next/3D-section keys are omitted,
`map.reportsView3DPosition` is `false`. When the keys are present, an explicit
`null` previous or next id is retained as an authored same-row boundary rather
than treated as missing data. The explicit 3D target and 3D-focused section are
independent from current selection and 2D section focus.

`seatView.changed` carries typed seat id, title, caption, badge, real/generated
flags, and drag hint. Renderer pixels and gestures remain runtime-owned; native
chrome consumes only the advertised metadata.

Picker categories, selections, and GA inventory use
`SeatLayerPickerCategoryTier`, which retains per-tier currency, restriction,
and buyer guidance. The older raw-map `CategoryTier` remains byte-for-byte
compatible with 0.2.x applications.

After ready, `SeatLayerPickerPhase.Ready.timing` reports monotonic
document-to-hello and document-to-ready spans without changing legacy
`ReadyInfo`. If the runtime advertises both `chart-load-trace-v1` and
`telemetry.chartLoad`, typed traces are emitted from
`SeatLayerPickerStateHolder.chartLoads`; unknown additive fields remain in the
trace's raw JSON.

The SDK does not treat missing availability evidence as sold out. Empty UI is
permitted only when the event reports sales closed or all known inventory is
affirmatively unavailable.

## Picker commands

All inventory mutations are suspending and serialized. Commands that return a
snapshot first wait for a newer revision; when a runtime response omits it, the
controller reconciles with `picker.getSnapshot`.

The public controller groups these behaviors:

- selection: select/deselect/clear, categories, tiers, table quantity, cart-line
  removal and undo;
- policy and filters: selectable objects, maximum selection, price/category,
  accessibility, limited-view seats;
- navigation: section focus, rung, venue overview, floor and all floors;
- map: theme, viewport insets, view mode, 2D/3D buyer view, seat-view open and
  capability-gated close,
  interaction state, camera and 3D navigation;
- inventory: GA, best available, selection hold, resume/extend, abort;
- lifecycle: foreground/background reconciliation and availability refresh;
- checkout: one in-flight handoff, exact-hold rejection on host failure, close
  and back reducers.

Tier confirmation is intentionally ordered: Android sends
`picker.setSeatTier` and waits for its accepted mutation before it sends the
seat confirmation command. A tier failure therefore cannot confirm the seat at
an unintended price.

Hardware and predictive Back use the same semantic reducer. In panorama the
controller sends additive `picker.closeSeatView` only when both seat-view
support and that exact command are advertised. On an older runtime it sends no
unknown command, keeps the session/target unchanged, and leaves the
runtime-owned close affordance authoritative.

Use `supportsCapability`, `supportsCommand`, and `supportsEvent` before invoking
an optional negotiated feature. Required enabled features fail during startup
instead of failing on a buyer action.

## Checkout ownership

`picker.continue` transfers a typed `SeatLayerPickerCheckoutHandoff` containing
the opaque hold id, expiry, currency, line items, and total. Only one handoff
may be in flight. The host callback is invoked exactly once.

If the host callback fails, Android invokes `picker.rejectHandoff` with the
exact transferred hold id and refreshes picker state. Closing a ready picker
uses `picker.abort` before renderer destruction. This prevents ambiguous hold
ownership between the picker and checkout host.

## Lifecycle reconciliation

The ready-made picker reports foreground/background state. On foreground, the
controller uses `picker.lifecycle`; when the runtime does not return a fresh
outcome it optionally calls `picker.refreshAvailability`, then obtains a fresh
snapshot. Hold-lapse recovery is modeled independently from transport errors.

The Compose renderer is movable between compact and wide adaptive branches, so
a host-handled rotation/resize does not recreate the WebView session. Activity
or process destruction is a different boundary: create a new session with
renewed buyer access and, when applicable, the opaque prior hold through
`initialHoldId`.

## Private buyer access

When a `buyerAccessTokenProvider` is configured, picker negotiation requires
native access-provider support. `access.token.request` invokes the provider away
from the UI thread and answers with `access.token.provide` or
`access.token.unavailable`.

Bearer values stay in memory. Provider exception text is intentionally not
sent to the hosted runtime, logs, snapshots, callbacks, or diagnostics.

## Errors and forward compatibility

Both profiles throw the sealed `SeatLayerException` family:

- `Transport`: WebView load, secure bridge, renderer, or timeout failure.
- `Incompatible`: no common protocol or a missing required capability.
- `Bridge`: structured runtime/command rejection.
- `Destroyed`: use after terminal teardown.

Unknown raw protocol-1 events remain `SeatLayerEvent.Unknown`; open value
classes preserve unknown strings. Picker snapshot optionals are additive and
tolerantly decoded while structural identity stays fail-closed.

Every command carries a correlation id and timeout. Replies arriving after a
timeout, reload, or destroyed session are discarded.

## Bridge security

The exact production page is:

```text
https://cdn.seatlayer.io/seatlayer-js@0.84.1/mobile.html
```

The AndroidX WebKit listener accepts only main-frame messages from the exact
`https://cdn.seatlayer.io` HTTPS origin; omitted port and explicit port 443 are
equivalent, and all other ports/paths/user-info/query/fragment forms are
rejected. Top-level navigation is locked to the exact page URL.

The implementation has no unrestricted `addJavascriptInterface`. File access,
content access, mixed content, automatic popups, multiple windows, and
third-party cookies are disabled. Renderer-process exit becomes a typed
transport failure instead of a silent blank view.

## Version pairing

Use matching versions of the Android core and Compose artifacts. 0.4.0 moves
the hosted renderer pin from `0.71.5` to `0.84.1` and keeps every bridge
contract on this page exactly as it was.

| Component | Version |
| --- | --- |
| Android core | `0.4.0` |
| Android Compose | `0.4.0` |
| Hosted SeatLayer mobile runtime | `0.84.1` |
| Raw bridge protocol | `1` |
| Native picker bridge protocol | `2` |
| Picker snapshot schema | `seatlayer.picker.snapshot/1` |
