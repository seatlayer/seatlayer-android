# SeatLayer buyer picker — implementation specification

The buyer picker is one design with four implementations. The Flutter package
in this repository is the reference: it is built entirely from
[`tokens.json`](./tokens.json), and the iOS (SwiftUI), Android (Compose) and
React Native SDKs are expected to be built from the same file plus this
document, without reading Dart.

This document is the contract. [`components.md`](./components.md) is the
catalogue of names and slots that goes with it; where the two disagree, this
document is newer. Every number, colour, duration, curve, haptic strength and
buyer-facing string cited here is a **token name**, and every token name
resolves in `tokens.json`. Literals appear only where the design deliberately
has no token — a percentage tint, a hairline, a ratio — and each of those is
called out as a fixed recipe constant.

Names in **Dart file** rows are the reference implementation's files, given so
a porting engineer can find the behaviour if a sentence here is ambiguous. They
are not part of any public contract.

---

## 1. Principles

**1.1 One snapshot model.** Every surface in the picker reads one *picker
snapshot* and calls back into one *controller*. No component fetches, computes
availability, or holds its own copy of the cart. A snapshot carries `event`,
`map`, `sections[]`, `categories[]`, `zones[]`, `selection[]`, `cart`, `hold`,
`access`, `capabilities` and `branding`. When two surfaces would disagree, they
are reading different things and one of them is wrong.

**1.2 Native chrome over a web-drawn map.** The venue itself — seats, sections,
labels, the 3D scene — is drawn by the SeatLayer runtime inside a web view. Every
other pixel in this document is drawn natively, over it or beside it. The
runtime never draws buyer chrome on a native host: a runtime advertising
`native-chrome-contract-v1` is told at `init` to suppress its own panel, rail,
dock, tray and seat-view words, and the native SDK owns them from that moment.

**1.3 The web picker's phone layout is the floor, and its numbers are the
tokens.** The design was not invented natively. Every size, weight, radius,
duration and curve in `tokens.json` was extracted from the web picker's phone
stylesheet, so a buyer who meets the product on the web and then in an app meets
one thing. A native port may not "improve" a number; it may only add what a
native platform can do that a browser cannot.

**1.4 Native adds physics, haptics and gestures on top.** The web picker
animates with fixed-duration tweens. A native picker answers a finger with a
simulation: the cart sheet and a swiped row settle from wherever the finger left
them, at the speed it left them at (`motion.physics.*`). Native also adds the
haptic vocabulary in `haptics` and platform gestures — swipe-to-open, swipe-to-
remove, swipe-down-to-dismiss. Nothing in this layer may change *what* happens,
only how it feels.

**1.5 Everything is a token or a style slot.** A host retunes the picker
without forking it. Sizes come from a layout token set, colours and type from a
theme, per-element looks from named style slots, and every buyer-facing string
from an overridable string set. A port that hard-codes a number has broken the
contract even if it looks right.

**1.6 Reduced motion is a first-class state.** When the viewer asks for less
movement, every duration in `motion.duration` collapses to zero. Motion that has
no reduced form — the fly-to-cart indicator, a staggered arrival, the seat
card's invitation — is **skipped**, not played instantly (`motion.reducedMotion`).
Sequenced work must not wait on a skipped animation: the seat card commits on
the press and departs on the same tick under reduced motion.

**1.7 44 pt is the floor.** `size.minimumHitTarget` is the smallest touch
target anywhere in the picker. Ink may be smaller — a price chip is
`size.legendChipHeight` of ink, a remove glyph is `size.denseRemoveSize` — but
the target that receives the press is never below the floor. Where ink and
target differ, the larger target is centred on the ink and must not steal
presses from a neighbour.

**1.8 Both themes, always.** `color.light` and `color.dark` are complete ground
sets. A mode owns only the ground roles; the accent, the accent ink, the font
and the host radius are the organizer's brand and are identical in both modes.
Three surfaces deliberately ignore the resolved mode and are always dark: the 3D
scene chrome (`color.dark.immersive*`), the seat card's photo-strip pills, and
the seat-view caption — each floats over imagery that can be any colour.

---

## 2. Layout and breakpoints

### 2.1 Two compositions, keyed off the container

Measure the **picker's own container width**, never the device or window:

| Composition | Rule |
| --- | --- |
| compact (phone) | container width below `size.phoneBreakpoint` |
| wide | container width at or above `size.wideBreakpoint` |

Between the two the compact composition holds. Everything in §3 describes the
compact composition unless a row says otherwise; the wide composition moves the
cart into a side pane and the Map/3D control into the corner-control stack, and
otherwise reuses the same components.

### 2.2 The compact column

```
Header                                     size.headerHeight  + top safe area
Price rail band                            size.topRailHeight          (row)
┌ map surface (runtime web view) ─────────────────────────────────────────┐
│  top-left anchor    test chip · 3D back pill                            │
│  top-right anchor   Map | 3D control                                    │
│  left-rail anchor   floor rail                                          │
│  bottom-left        (empty on a phone by default)                       │
│  bottom-right       ♿ control, then the zoom discs                      │
│  bottom-centre      toast · hold-extend prompt                          │
│  seat card / prompts / status overlays                                  │
│  (no section dock on any width by default — host opt-in; see 3.6)       │
└─────────────────────────────────────────────────────────────────────────┘
Cart sheet (collapsed)          handle + foot + bottom safe area
```

The header, the price rail and the cart sheet are **rows of the same column**:
the map surface begins and ends where they do. Everything else in the diagram is
**stacked on the map**.

### 2.3 Viewport insets

The runtime frames a section, a floor or a fitted venue inside the rectangle it
believes it owns. Chrome that stands *on* the map must therefore be reported to
it, or a focused section lands half underneath the dock. A runtime advertising
`viewport-insets-v1` accepts a report of the bands the native chrome covers.

Reported (they stand on the map):

- the test chip, and the 3D back pill it steps below
- the Map | 3D control
- the floor rail
- the section dock **only where a host has opted in** — it has no phone form of
  its own — plus the lift it then applies to the bottom anchors
- the immersive scene's own top and bottom chrome

Not reported (they are rows, and the map surface already ends at their edge):

- the header
- the price rail band
- the cart sheet, collapsed or open
- the **seat card**, on a runtime that can pan a seat (`picker.frameSeat`,
  runtime 0.80.2+): the card's band is folded into the pan instead (§3.8.2).
  Only an older runtime gets it as an inset.

Rules for the reporter: coalesce to at most one report per frame, never send
the same numbers twice, floor negative or non-finite sides to zero, and forget
what was sent when a runtime goes away — a fresh runtime frames against its
whole surface until it is told otherwise. Dart file:
`lib/src/picker/picker_viewport_report.dart`.

### 2.4 Chrome over the map takes the whole touch

Everything in 2.3's "reported" list stands *on* the map, and the map is a
platform view on every mobile platform. One physical tap on a control drawn
over it must reach the control and nothing else — never the control and a seat.

The hazard is platform-level, not layout-level. On iOS the embedded web view
lives in the engine's touch-intercepting view, which holds each touch until the
gesture layer says who won it: given to the map it is released to the web view,
given to someone else it is swallowed, and **given to nobody it is delivered to
the web view anyway**. Hiding the map from hit testing is therefore the leak,
not the fix — the map has to take part in the contest and lose it.

Every port holds three rules:

1. The map takes part in gesture resolution for **every** touch inside its
   band, including touches that landed on chrome. It must never be excluded
   from hit testing while chrome is up.
2. The map claims eagerly — on press, before any movement, which is what its
   pan and pinch feel is built on — **except** where the same press landed on
   chrome, where it resigns and the platform view is told it lost.
3. Chrome over the map must be a control that competes for the touch. A
   passive observer that watches touches without claiming them leaves the map
   as the only claimant, and the tap lands twice again.

The controls this covers are every one in 2.3's reported list plus the corner
discs (3.5), the Map | 3D control (3.3), the test chip (3.4), the floor rail
(3.7), toast actions (3.12) and the buyer-facing state prompts (3.13) — that
is, anything at all that is drawn inside the map's rectangle.

Surfaces that take the whole map — the seat card, loading and error states —
additionally tell the runtime to stop accepting input at all
(`picker.setInteractionEnabled`), so the map is quiet as well as unclaimed
while a decision is open.

**The second guard, on the runtime's side (2026-09-05).** The three rules
above are necessary and, on iOS 26, not sufficient: measured on the
simulator, the platform view is told it lost 134 ms before the finger lifts
and WKWebView is handed the touch regardless, so a tap on a corner disc still
selected the seat under it. A guard sent on pointer-down cannot win that race
(a bridge round trip is ~180 ms against a 24 ms tap). So every piece of
chrome standing on the map also reports its **rectangle** — in the map's own
logical px, from the map surface's top-left — and a runtime advertising
`picker.setBlockedRegions` in its `hello` command table swallows any pointer
sequence that starts inside one, before its gesture machine or canvas sees it.

Rules for the reporter, the same shape as the insets: the whole list on every
send, replaced not merged; at most one send per frame; never the same list
twice; a control that unmounts takes its rectangle with it; the list is
cleared (`[]`) when the composing layout leaves; and nothing is sent to a
runtime whose command table lacks it. Every control in this section's list
carries the region: the corner discs and their columns, the Map | 3D control,
the test chip, the floor rail, the floor selector, the dock and the wide
layout's rail. The region draws nothing and takes no pointer of its own — the
control underneath still competes for the touch exactly as rules 1–3 require.

Dart files: `lib/src/seat_layer_map_chrome.dart` (the stack, the scope and the
recognizer), `lib/src/picker/picker_blocked_regions.dart` (the region, the
registry and the command), `lib/src/picker/picker_adaptive_layout.dart` and
`lib/src/picker/picker_map_controls.dart` (which compose them).

### 2.5 Safe areas

The bottom safe inset is absorbed once, by whichever surface owns the bottom
edge at that moment:

- open sheet — the sheet's own container carries it, and every height ceiling
  adds the same amount so no content box shrinks
- collapsed — the same container carries it, and it is the foot that sits
  above it in both states. Padding the foot instead would shorten the pay
  button inside it rather than lifting it off the home indicator
- section dock — where a host has opted into one, the dock carries it, and the
  lift it applies to the bottom anchors is `size.dockBarHeight` plus the same
  inset. With no dock — the default on every width — the bottom anchors sit at
  `size.mapAnchorInset` from the map's own bottom edge and nothing is reported

---

## 3. Components, in buyer order

Each section gives: anatomy, tokens, states, copy, motion, gestures and haptics,
accessibility semantics, the snapshot fields it reads and the capabilities that
gate them, and the reference file.

### 3.1 Header

The header's hold pill is drawn for as long as a hold is live (owner call,
2026-09-05): it is the picker's ONE clock. There is no hold clock anywhere on
the cart sheet, in either state, and no "display buffer" on it — the pill shows
the full server hold. This deliberately departs from the web's
`data-peek-clock` rule, under which the pill stepped aside while a peek bar
carried the clock; there is no peek bar any more (§3.9), and a clock inside a
button read as clutter on the phone.

**Name** `SeatLayerPickerHeader` · **slot** `headerStyle` · **file**
`lib/src/picker/picker_header.dart`

**Anatomy.** A row of `size.headerHeight`, on the picker's own ground — the
header's own style slot, which defaults to `color.*.surface` with `color.*.text`
on it; a ground and its ink are always resolved as a pair, never from two
independent tokens (a host that darkens `background` for the map must not
lose the event name) — with a hairline of `color.*.divider` under it. Left: the
brand mark, a square of `size.headerLogoSize` at `radius.headerLogo`, filled
with the accent and carrying either the organizer's logo image (cover-fitted) or
the first letter of the brand or event name in `color.*.onAccent`. Centre: the
event name at `size.headerNameFontSize` w700, one line, ellipsized. Right, in
order: the hold pill, the sales-closed pill, the close ring.

The venue-and-date meta line is **not drawn** on a phone. The event name alone
is the identity.

**Close ring.** A circle of `size.headerCloseSize` at `radius.pill`, hairline
`color.*.divider`, glyph in `color.*.mutedText`; the press target is expanded to
`size.minimumHitTarget` centred on it. Copy `strings.close`.

**Hold pill.** Drawn for as long as a live hold exists, whoever owns it
(owner call 2026-09-05: the clock is always in the header; a host that draws
its own clock turns it off with the `showHoldPill` option). A true pill (`radius.pill`), a
dot and `m:ss` in `type.pill`, tabular figures, with a `size.minimumHitTarget`
reach. Resting look is the accent mixed lightly into the surface with accent-
toned ink; while expiring it inverts to the full accent with `color.*.onAccent`
and its dot pulses. Copy `strings.heldFor`. It is never hidden while the hold
lives — the countdown is said once, here (owner call, 2026-09-05).

**Sales-closed pill.** Same pill geometry, deliberately neutral — the text
colour on a lightly text-tinted surface, never the accent. Copy
`strings.salesClosedPill`. A padlock glyph leads it.

**States.** compact / wide; with and without a hold; expiring; sales closed;
event name known from the host before the runtime reports it (the runtime's name
wins once it arrives, and the swap must not be visible as a second load).

**Motion.** Pill entrance uses `motion.duration.enter` on
`motion.curve.easeEnter`, sliding a few points from the trailing edge with a
slight scale-up. The expiring dot pulse is a slow infinite breath and is skipped
under reduced motion.

**Haptics.** `haptics.holdCreated` when a hold appears, `haptics.holdEnding`
when the countdown crosses into its final minute, `haptics.holdExpired` when it
runs out. The first snapshot of a session fires none of them
(`haptics.note`).

**Accessibility.** The header is a banner. The event name is a heading. The
countdown updates politely, not assertively — it must not interrupt a screen
reader every second; announce it on the minute and on the expiring transition.

**Snapshot.** `event.name`, `event.venue`, `event.salesClosed`, `branding.logoUrl`,
`branding.brandName`, `hold.active`, `hold.expiresAt`, `hold.owner`.

### 3.2 Price rail, chips and All prices

**Name** `SeatLayerPriceLegend` · **slots** `legendChipStyle`, `chipShape` ·
**file** `lib/src/picker/picker_legend.dart`

**Anatomy.** A band of its own between the header and the map, height
`size.topRailHeight`, on `color.*.surface` with a `color.*.divider` hairline
beneath. It is not floated over the map: on a busy chart the seat numbers read
through the gaps, and the last chip clipped under the Map/3D control. Inside it,
one horizontally scrolling row of chips, indented by the rail's own 10 pt of
horizontal margin (12 on wide).

The band takes the **map chrome's** palette, so it darkens with the 3D scene,
and it is not drawn at all while the immersive scene is up.

**Chips.** Height `size.legendChipHeight` of ink inside a
`size.minimumHitTarget` reach, `radius.chip`, ground `color.*.background`,
hairline `color.*.divider`, label at `type.legendChip` with tabular figures. The
chip's padding is 7 leading and 9 trailing, not symmetric. A leading dot of
`size.legendChipDotSize`. Selected chips take the accent ground and
`color.*.onAccent`, and the dot gains a ring in the ink colour so the colour key
survives the inversion.

On the **light** theme the dot is drawn as the category colour mixed into the
surface with a full-strength ring of the category colour — matching how the map
tints sections on light. That ring is 1.5 pt drawn **outside** the dot and takes
no layout room, so a `size.legendChipDotSize` swatch of 7 measures 10 pt of ink.
Dark keeps the flat dot. This is a fixed recipe, not a token.

**Amount rule.** A single price prints as itself; equal minimum and maximum
print once; otherwise `{min}+` via `strings.fromPrice`'s sibling formatting. A
category with no configured price shows its **name** instead of an amount.

**All prices.** Always the first chip, pinned so it never scrolls away, and
inset a further 1 pt inside the rail's own margin. The way out of a filter wears
the band's own `color.*.surface` at w750, where a named category wears
`color.*.background` at w800. Copy `strings.allPrices`. It is selected whenever
no filter narrows the map, and pressing it clears both the band filter and any
pinned category. While the scroller has scrolled, the pinned
chip carries a halo of the band ground so chips slide *under* it rather than
through it; at rest the halo is off so the first price keeps its rounded end.

**Framing — both directions carry it.** Turning a band **on** frames that
band's seats. Turning a band **off** — by `All prices` or by re-pressing the lit
chip — frames the **whole venue**: every section on screen, section focus
released. Not the pose the band was entered from, and not the section the buyer
had drilled into before that.

Both exits must ask the runtime for the framed path
(`setCategoryFilter(…, focus: true)`). The unframed path applies the filter and
leaves the camera alone, which strands the buyer inside their drill-in while the
block melt runs underneath seats drawn at full strength — the map returns washed
out, its seats behind grey shells. Restoring only the camera, or camera plus
section, were both tried and both read as a failure: the honest reading of "all
prices" is the venue.

One exception the runtime owns: while the accessibility filter is still on,
clearing the band returns to *that* filter's framing rather than to the whole
venue — the map must keep answering the filter that is still active.

**Edge fade.** The scroller masks `size.legendRailEdgeFade` of its leading edge
once scrolled, and of its trailing edge while more chips remain. Mirrored for
right-to-left.

**The key at the end.** One grey swatch, `strings.notAvailable`, after the last
price. The map paints ONE inert grey disc for every seat a buyer cannot take —
someone else's hold, a sale, a seat never put on sale — so the key explains it
once. It used to be two entries with two drawings, a padlock for "held" and a
diagonal for "sold", which taught a buyer a distinction they can act on either
way. It is deliberately **not a chip**: it filters nothing, so it wears no
pill, no hairline and no press target, and it is not a toggle to assistive
technology.

**Sold out.** A sold-out category keeps its chip, disabled and struck through —
removing it would silently rewrite the price ladder the buyer was reading.
An **absent** availability figure means *unknown*, never zero, and is never
struck.

**States.** idle / selected / sold out / disabled; empty (the band is not
drawn); over the immersive scene (not drawn).

**Motion.** On first reveal the chips stagger in on `motion.duration.stagger`
between children, each on `motion.curve.easeEnter`. Filtering does not re-animate
the rail.

**Accessibility.** Each chip is a toggle with a pressed state; its label is the
category name and amount, plus a sold-out suffix where it applies. The rail is
a single group named for ticket prices.

**Snapshot.** `categories[]` (`key`, `label`, `color`, `priceMin`, `priceMax`,
`available`, `notForSale`), `map.categoryFilter`, `event.currency`. Live free
counts are the runtime's `category-availability-v1` reporting; a count of zero is
treated as *not reported* rather than as sold out, because a figure the buyer
cannot trust is worse than no figure.

**Command.** `picker.setCategoryFilter { keys, focus }` — the first press
filters and drills in, the second clears.

### 3.3 Map | 3D control

**Name** `SeatLayerPickerViewModeControl` · **file**
`lib/src/picker/picker_map_controls.dart`

Built only where the event offers 3D **and** the device can render it.

**Anatomy.** A segmented track at `radius.pill` on `color.*.surface` with a
hairline and a soft shadow, height `size.viewModeControlHeight`, holding two
segments of `size.viewModeButtonMinWidth` × `size.viewModeButtonHeight` at
`size.viewModeLabelFontSize`, letter-spaced, in `color.*.mutedText`. The
segments sit on a 3 pt bed **inside** the track's own line — the hairline is
part of the track's measured height, not painted over it. The active segment
takes the accent ground and `color.*.onAccent`.

On a phone it lives in the map's **top-right** corner, on the line below the
price rail — the rail owns the band, the control owns the corner. On wide it
joins the corner-control stack.

**Copy.** Left `strings.mapView` (accessible name `strings.flat2dMap`); right
`strings.venue3D` (accessible name `strings.interactive3dVenueView`). The group
is named `strings.venueView`.

**Behaviour.** Prefetch the 3D bundle once on first hover, focus or press-down,
so the switch is instant. Report the control's band as a viewport inset.

**Snapshot.** `map.isVenue3D`, `capabilities` (`venue3d`, plus the runtime's
`venue-3d-v1`). **Command** `picker.setBuyerView`.

### 3.4 Test chip

**Name** `SeatLayerPickerTestModeIndicator` · **file**
`lib/src/picker/picker_status_views.dart`

Required chrome for a test event. It has no host switch, and exactly one may
render.

**Anatomy.** One recipe in both themes: height `size.testChipHeight`,
`radius.pill`, label at `size.testChipFontSize` w700 in warning ink, ground the
warning colour at `opacity.warnPillWash` over the surface, hairline the warning
colour at half strength, leading dot of `size.testChipDotSize` in
`color.*.warning` inside a hard 3 pt halo of the warning at `.22`, drawn with no
blur. The chip's padding is 8 leading and 10 trailing. Amber, never the accent
— an environment flag must not wear "buy" gold.

**Ink — measured against the wash, not the surface.** The chip is not painted
on the surface; it is painted on the wash *over* it, which is a different and
always-warmer colour. Resolve the ink in three steps, in order of how much of
the brand hue they keep:

1. the warning hue itself, when it already clears **4.5:1** on its own wash;
2. the hue walked toward `color.*.text` in 0.05 steps from 0.15 until it clears;
3. a neutral ink chosen by contrast between `#172033` and `#EEF1F8`, when the
   hue cannot get there at all.

Step 3 is the one a fixed blend has no answer for: on a mid-tone ground no mix
of a mid-tone gold and a mid-tone ink clears 4.5:1, and a walk with no fallback
runs out and returns the ground's own ink. Giving up the hue is the right trade
— the chip is a safety notice, and a legible neutral beats an illegible brand
colour. A fixed blend measured against the bare surface produced a 2.3:1 chip
on a mixed theme (a light host theme over a chart saved dark).

The walk stops at the **first** candidate that clears, so the chip keeps as much
amber as the floor allows rather than driving to maximum contrast.

**Copy.** Sentence case `strings.testMode`; the accessible name keeps
`strings.testModeLong`; the description is `strings.testModeExplained`.

**Placement.** The map's top-left corner in both 2D and 3D, at
`size.mapAnchorInset`. It steps down by the back pill's height plus
`size.mapAnchorGap` **only while the immersive scene's back pill is actually
drawn** — not merely whenever the scene is up.

**Snapshot.** `event.mode`.

### 3.5 Map corner controls

**Name** `SeatLayerPickerMapControls` · **slot** `iconButtonStyle` · **file**
`lib/src/picker/picker_map_controls.dart`

Every floating control belongs to one of seven **anchor regions** — top-left,
top-centre, top-right, left-rail, bottom-left, bottom-centre, bottom-right —
inset `size.mapAnchorInset` from the map's edges, with `size.mapAnchorGap`
between members. Regions do not receive presses; their children do. Nothing
free-floats.

**One column, bottom-right, on both compositions** (owner call, 2026-09-06).
The ♿ filter disc heads it; the zoom discs follow. It used to stand alone in
the bottom-left region — one control facing a stack of them, in the corner the
floor rail already owns — and read as something the layout had forgotten. Who
can sit where is an earlier question than how close the camera is, so it is the
disc above `+`.

**The phone column is three discs, and only three**, top to bottom:

| Disc | Name | Command | Retires when |
|---|---|---|---|
| ♿ | `strings.accessibility` / `strings.displayOptions` | opens the accessibility sheet (§4.10) | never |
| `+` | `strings.zoomIn` | `picker.zoomIn` | `map.rung` is `seats`, or `map.canZoomIn` is false |
| framed dot | `strings.fitWholeVenue` | `picker.overview` | never — it is always live |

Every disc is `size.mapControlSize` across, the ♿ disc
`size.accessibilityControlSize`, and the column's gap is
`size.zoomColumnGap`; the head disc's stepper rides beside it at
`size.accessStepGap` (§3.4.1).

**There is no `−` disc on the phone** (owner call, 2026-09-06). It only ever
had a rung of its own while the buyer was already among the seats: at a
section's own frame the one step back is the whole venue, and that is the disc
below it. Two discs for one move reads as a puzzle, and a disc that only
sometimes has something to do reads as a control that only sometimes works.
**Pinch steps out**, and the whole-venue disc goes home from any depth.
`SeatLayerPickerZoomOutButton` stays a public component for a host composing
its own column; mounted, it is live only while `map.rung` is `seats` and
`map.canStepBack` is not false.

**The whole-venue disc is never dimmed.** The camera facts in the snapshot are
only as fresh as the last state change, and a pinch changes no state — so
dimming this disc on a stale "already at the venue" reading stranded a buyer
with the venue unframed and nothing left to press. At the venue already, the
press is a harmless no-op. **A port must never dim the escape hatch.**

**`+` retires, and keeps its slot.** A `+` that cannot zoom in is the
broken-map reading, so it goes inert once `map.rung` is `seats` or
`map.canZoomIn` is false. It is drawn with its space maintained — Flutter's
`Visibility(maintainSize: true, maintainState: true, maintainAnimation: true)`
— because the column is anchored at its foot: a disc that left the tree
altogether pulled the ♿ disc down under the thumb already reaching for it.
Reserve the slot; do not reflow the column.

**A retired or disabled disc looks retired, and its ground never changes.**
These controls dim in place rather than disappearing, which only works as an
answer if the buyer can see that it is one. The glyph steps back to
`color.*.mutedText` at 55 per cent, the ring to the chrome hairline at 60 per
cent, and **the shadow is dropped** — it is no longer lifted off the map,
because it is no longer a thing to press. The disc keeps its full ground in
both themes: a wash over the whole disc read as a grey blot on the light map
and vanished on the dark one. There is no opacity wash on a disabled disc.

**Ground — a disc's own, never the panel's.** Every floating control takes
`color.*.chrome` with a `color.*.chromeLine` hairline, resolved from the **map
chrome's** side rather than the picker panel's, so the controls darken with the
immersive scene. Light `chrome` is `#d6dce6` carrying the darker edge; dark
`chrome` is `#556278` carrying the pale one. The panel's `surface`/`divider`
are the colours of a plate the map is drawn *beside*; over the venue they
disappear into it, and a translucent dark surface on a dark map measured
1.14:1 — a dark blob on dark.

The two sides carry the boundary in different halves, which is why this is two
tokens rather than a stronger opacity:

| Side | What separates | Measured |
|---|---|---|
| Dark | the **fill** | 2.96:1 disc against the map |
| Light | the **edge** | 3.72:1 against the disc, 3.17:1 against the map |

White is already as far from a light map as a colour can get in that direction
and is still only 1.17:1 from it, so on light the fill can never be the answer.

This covers every floating control on the map, the accessibility control and
the Map/3D track included — not only the corner discs.

**The column steps out of the way of a seat card.** While a card is up on the
phone the whole control stack fades to opacity 0 over
`motion.duration.crossfade` and is made inert: a column of discs poking out
beside the sheet asking about a seat reads as clutter, and none of them may be
pressed while it asks. It fades — it is not unmounted — so nothing else on the
map moves.

**The wide composition** draws the same ♿ disc at the head, then `+` and `−`,
and **no fit control of its own** (2026-09-06). It carried a plain fit-to-screen
button until then; two ways to frame the same flat venue on one composition is
one too many, and the one that went is the one a pinch already does. The
immersive scene keeps its own Fit chip (§3.14), which frames a camera rather
than a map, and says `strings.fitWholeVenue` like the phone's disc.
`SeatLayerPickerZoomToFitButton` stays a public component for a host that wants
it back in a composition of its own.

Back-to-overview is a different question and keeps its own condition: it renders
only while a section is actually framed — top-left on the phone, in the column
on the wide layout.

**Dock lift.** Only where a host has opted into a section dock. While one is
mounted and neither a seat card nor the immersive scene is up, the three bottom
regions lift by the dock's height plus the bottom safe inset. By default no
composition mounts a dock (§3.6), the lift is zero, and the corner controls
rest at `size.mapAnchorInset`.

#### Accessibility control (head of the control column)

A circle of `size.accessibilityControlSize` at `radius.pill`, taking the same
`color.*.chrome` ground and `color.*.chromeLine` hairline as every other
floating disc, ink `color.*.text`, glyph drawn at 21, with a soft shadow. With
filters on, the ink turns to the accent and the disc carries a badge with the
number of active provisions. The **same disc on both compositions**: it
floats on the map either way, so the wide side does not get a labelled button
of its own, and the wide panel no longer repeats the control under the
best-seats card. The glyph is **drawn**, never an emoji: a
wheelchair mark where the chart authors accessible seats, otherwise a
display-options glyph. Names: `strings.accessibility`, or
`strings.displayOptions` where the chart offers no provisions.

**The sheet is bounded, and scrolls inside the bound.** A chart carrying the
whole vocabulary gives it twelve accommodation rows plus the limited-view and
colourblind switches; an unbounded scroll-controlled sheet answers that by
taking the entire screen, and the map the buyer is filtering disappears behind
the filter. The bound is
`size.accessSheetMaxHeightFraction` of the screen, floored at
`size.accessSheetMinHeight` and never taller than the screen itself, and the
row list scrolls within it. (The web bounds its popover to the room above the
♿ button *inside the map*, for the mirror-image reason: a panel measured
against the window opens straight through the ticket sheet below it.)

Its sheet opens upward from the button and contains switch rows:

- one row per access need the chart actually authors, in the runtime's order,
  each with its live free count — `strings.accessFreeCount`, or **`0`** with
  the row disabled when the last space goes. A figure, not a sentence: the
  count column carries numbers on every other row, "Not available" needed a
  chip to hold it and made the sold-out row the loudest line on the sheet, and
  the dimmed switch beside it is already what says the provision cannot be
  had. The absence is still spoken — the count is part of the row's name.
  (`strings.notAvailable` stays the picker's one word for a seat nobody can
  take, on the legend and the seat card; `strings.accessNoneLeft` is retired.)
  A count that is *not counted* shows no number and is never disabled. **A provision the venue has
  but has sold out of stays on the sheet and goes dark; a provision it never had
  is absent** — "this venue has none" and "these are taken" are different
  answers. A wheelchair row carries the note `strings.companionSeatsNote` where
  the chart has companion places — **behind an ⓘ**, not on a second line of the
  row: see "one line per row" below.
- then, **under a group heading** `strings.viewGroupTitle` — 11 / w700 in
  `color.*.mutedText`, letter-spaced .6, inset 8 with 18 above and 4 below —
  the two switches that act on the MAP rather than on seats:
  `strings.hideLimitedView`, only where some seat is restricted or obstructed,
  and `strings.colorblindSafe`. The heading is what stops those two from
  reading as two more provisions at the bottom of the provision list; it is
  drawn only where at least one of the two rows exists. Neither of them
  reserves a count column.

**Every row wears a drawing, and it is the drawing that row is about.** Each
provision row takes its own glyph from the shared per-attribute set (§3.8.9) by
the runtime's own key — one wheelchair standing for twelve different provisions
told the buyer nothing about which row was which. The limited-view switch wears
the mark those seats carry (`restrictedView`), and the colour row wears a
**contrast disc**, which is authored beside the shared set rather than in it:
that row recolours the map, it does not choose seats, so it must not wear a
seat's mark or an eye. Glyphs are `size.accessRowIconSize` inside the
`size.accessRowIconCell` cell, in `color.*.mutedText`, and hidden from
assistive technology — the row says the same fact in words.

**Every switch applies as it is flipped, and there is no apply step.** The
command goes to the runtime out of the row's own handler, exactly as the web
menu has always done it (`pickerAccessibilityMenu.wire()` calls
`setColorblindSafe` / `setLimitedViewHidden` / `applyFilter()` straight from
the click). A switch IS the action, so staging the flips behind an
`Apply filters` button asked twice for one decision — and left a buyer who
dragged the sheet away, the gesture that closes every other sheet, with a map
that had ignored everything they just did. **The sheet stays open** after a
toggle: a buyer with more than one need flips more than one row. Its drag
handle and its scrim are the only ways out, plus the count-as-jump below.

**Opening the sheet clears a pending seat card.** Only one decision surface may
hold the screen; the web picker clears them all before another goes up
(`SeatPicker.clearDecisionSurfaces`). An unanswered confirm card is cancelled
first — the seat given back and marked answered, exactly as an outside tap on
the card does it — so the sheet never comes up over a dimmed question the buyer
can neither read nor answer.

**One line per row.** A row used to be able to grow a second, muted line under
its label — the companion sentence — and with twelve provisions that turned a
sheet of switches into a page of prose. The sentence rides an ⓘ beside the
label now (`size.accessNoteIconSize`, muted, accent while open, inside a
`size.minimumHitTarget` target) and opens *under* the row that owns it, so the
line is a line whether or not the buyer has asked for it. The ⓘ carries the
sentence as its own accessible name, so it is spoken either way, and it is a
separate node from the row's switch because the two do different things. The
label itself is `maxLines: 1` and truncates: a translation that runs long
shortens rather than wrapping.

**The line reads icon · name · ⓘ · count · switch**, and the ⓘ shares ONE cell
with the label: it explains the words beside it, not the switch at the far end.
The count keeps a column of its own so every figure lines up down the sheet,
with `size.accessRowSwitchGap` holding it clear of the switch.

The names are the SHORT ones. The runtime's own taxonomy labels
(`picker.access.*`) are written for a seat popup, where "Designated aisle /
transfer seat" is the honest length; every place this SDK prints a provision is
a single line in a narrow column. The English defaults in `design/tokens.json`
have always been short, and the locale extractor takes the other thirty-six
from `picker.accessShort.*` so they agree.

Row anatomy, leading to trailing, at **one fixed height of 50 for every row on
the sheet** — a sheet whose rows breathe differently reads as three lists in a
trench coat:

| Cell | Width | Contents |
| --- | --- | --- |
| icon | `size.accessRowIconCell` (20) | the attribute's own drawing at `size.accessRowIconSize`, in `color.*.mutedText` |
| gap | `size.accessRowGap` (10) | — |
| label + ⓘ | flexible | the short name at `size.accessRowLabelFontSize` / w600 in `color.*.text`, one line, ellipsized; then, only where the row has a note, the ⓘ in a 36-wide column |
| count | 68, trailing-aligned | `strings.accessFreeCount`, or `0`; wide enough for "999 free" |
| gap | `size.accessRowSwitchGap` (12) | — |
| switch | `size.accessSwitchWidth` × `size.accessSwitchHeight` | drawn, not the platform's |

Side padding is `size.accessRowPaddingX`, the press ripple takes
`radius.button`, and each row is closed by a hairline of `color.*.divider` at
35 per cent — the last row of a group carries none. **The whole line is the
control**, so the buyer aims at the words rather than at the toggle. The switch
is a track at `radius.pill` with a knob of `size.accessSwitchKnob`; off is the
muted colour at low opacity, on is the accent. Disabled rows dim and stop
responding, and a sold-out provision keeps its name, its icon and its `0`.

The whole sheet is bounded to the screen (`size.accessSheetMaxHeightFraction`,
floored at `size.accessSheetMinHeight`, never taller than the screen) and the
provision list scrolls **inside** that bound, so the two map switches and the
title stay put.

Filters combine as a **union**; the whole union is sent on every flip. Turning
one on moves the camera to the matching
seats — the runtime's own flight since 0.77.1, see 3.13. The choice survives the
session where the host allows it, and is restored only for provisions that are
still free.

**The count as a jump.** Where the runtime advertises `accessibility-focus-v1`
and a provision has a positive count, its trailing `strings.accessFreeCount`
becomes a **button**: a stadium chip at `radius.pill` of height
`size.accessStepHeight` with `size.accessStepPaddingX` of side padding, on a
quiet ground of `color.*.text` at 6 per cent — not the accent, because twelve
pink pills down a sheet were the loudest thing on it — with the figure in
`color.*.text`, drawn inside a `size.minimumHitTarget` target so the number does
not have to be aimed at. The number itself does not move or change size when it
becomes pressable; a count that is only a fact is the same figure in
`color.*.mutedText` with no chip around it. Pressing
it turns that provision's switch on if it was off, applies the filter, **closes
the sheet** — the one control on it that does — and takes the first step of the
accessible-section tour
(3.4.1). Name: `label, strings.accessFreeCount(n),
strings.accessJumpFirstSection`. The row's own toggle stays a separate node —
the two do different things. Where the count is not pressable it is the static
muted figure it has always been.

This is where the web and the phone diverge on purpose. The web's menu is a
popover over the map, so its `12 free` button steps the camera with the menu
still open; a modal bottom sheet covers the map, so the phone hands the walk to
a control that lives where the map is visible.

Opening staggers the rows on `motion.duration.stagger`; **closing is immediate**
and deliberately has no exit animation.

Capability: the per-chart need list and its counts require `access-needs-v1`;
an older runtime gets the full static list from `strings.access*`. The
colourblind row additionally requires the runtime to advertise its own
colourblind-safe support. Commands `picker.setAccessibilityFilters`,
`picker.setColorblindSafe`. File: `lib/src/picker/picker_accessibility.dart`.

##### 3.4.1 Accessible-section stepper (beside the control)

**Name** `SeatLayerPickerAccessibleStepper` · **file**
`lib/src/picker/picker_accessibility.dart` · state in
`lib/src/picker/picker_accessibility_focus.dart`

`♿ 2 of 6 ›` — a stadium pill sitting **beside** the accessibility control at
the head of the bottom-right control column, `size.accessStepGap` from it, on
its inner side so the column's own right edge stays the discs'. Height
`size.accessStepHeight` drawn inside a `size.minimumHitTarget` target, side
padding `size.accessStepPaddingX`, ground `color.*.surface`, hairline
`color.*.divider`, glyph in the accent, chevron in the muted ink, figure at
`type.accessStep` (`size.accessStepFontSize`, weight 800, tabular).

Drawn only while an accessibility filter is active **and** the runtime
advertises `accessibility-focus-v1`. Pressing it sends
`picker.focusNextAccessibleSection { types }` with the active provisions and
redraws from the returned `step`:

- a step → `strings.accessibleStep(index, total)`;
- before the first step → `strings.accessibleSections(count)` counted from the
  sections whose `accessibleFree` holds a positive entry for an active
  provision, where `section-access-counts-v1` is advertised. Where it is not,
  the pill draws the glyph and chevron with no figure until the first step
  answers — a runtime that flies but does not count still has a tour;
- a `null` step → the pill **goes**. Nothing matches, and that is an answer,
  not a failure;
- where the counts ARE reported and none is positive, the pill is never drawn.

A filter the buyer changes abandons the walk; the pill returns to its first
appearance. Name: `strings.accessibleStep(...)` (or nothing) followed by
`strings.accessJumpNextSection`.

**Motion.** The runtime owns the camera and already does the right thing under
reduced motion. The pill itself only ever cross-fades one figure into the next,
on `motion.duration.crossfade`.

**Haptics.** None of its own: the walk changes the focused section, so the
dock's existing `haptics.sectionFocused` fires from the snapshot that follows.

`strings.accessibleStep` and `strings.accessibleSections` are **native-only** —
the web has no such sentence, because its popover never leaves the map — as are
`strings.accessJumpFirstSection` and `strings.accessJumpNextSection`.

#### Zoom column (bottom-right)

A column of round controls of `size.mapControlSize` at `radius.pill`, gap
`size.zoomColumnGap`, ground `color.*.chrome` with a `color.*.chromeLine`
hairline. The glyphs are drawn at 20 — an arm of 11.33 and a stroke of 1.33,
not 14 across at a stroke of 2.

The wide composition draws `+` and `−`. The phone draws `+` and the
whole-venue disc and **no `−`** — see "The phone column is three discs" above.
Both columns are headed by the ♿ disc.

`strings.fitWholeVenue` is the one name for "put the whole thing on screen",
shared with the immersive scene's Fit chip. `strings.fitVenue` survives as the
label of the retired `SeatLayerPickerZoomToFitButton`, and takes the same
runtime key, so a host that mounts that component itself is not left with a
word the rest of the picker no longer uses.

**Commands.** `picker.zoomIn`, `picker.overview` (the phone's whole-venue
disc), and `picker.zoomOut` for the wide column's `−`.

An overview thumbnail (minimap) is **not built on a phone**, and neither are
level-of-detail rung pills: measured against the map's top edge they restated
what the pinch had already done and covered most of it.

### 3.6 Section dock

**Name** `SeatLayerDockBar` · **slot** `dockBarStyle` · **file**
`lib/src/picker/picker_dock_bar.dart`

**THE DOCK IS OFF BY DEFAULT ON EVERY WIDTH.** `dockBarFor` resolves
`showDockBar ?? false`, so no composition — phone or wide — mounts a bar unless
the host asks for one with `showDockBar: true`. It also remains mountable
standalone. Nothing below applies to a default picker of any width.

Focusing a section used to dock a bar onto the map's bottom edge — "406 · 302
seats left ‹ › ‹ Venue". "Pinch-out and the zoom-out stepper already walk a
buyer back to the venue, so the prev/next arrows bought a two-tap version of a
gesture the finger does better", and "the bar's 52px plus the home-indicator
inset pushed every bottom-corner control up the screen to make room for it".
The per-section "N seats left" is therefore **not shown by default** anywhere.

The way back on the phone is: **pinch out past the melt point** — the runtime
releases the focus, F3 — the **back-to-overview control** top-left while a
section is framed, or the **whole-venue disc** at the foot of the map's control
column (§3.5). The wide layout keeps `−` as well.

A host that sets the switch gets the bar back exactly as specified below, and
the dock lift in §3.5 applies while it is mounted.

**Anatomy.** A **full-width bar pinned to the map's bottom edge**, never a
floating pill: edge to edge, height `size.dockBarHeight` plus
the bottom safe inset, square corners, a hairline on its top edge only, opaque
`color.*.surface` (never translucent), elevation `elevation.dockBar`. On the
light theme it additionally carries a real separating shadow above and below and
a slightly stronger top line, because a light bar on a light map has no edge of
its own.

Contents, leading to trailing:

1. A dot of `size.dockDotSize` in the section's colour, inset
   `size.dockLeadingInset`.
2. The section name at `size.dockNameFontSize` with the weight of
   `type.dockSection` (its size field is not read here), taking the free
   space, ellipsizing.
3. The seats-left count at `type.dockCount` / `size.dockCountFontSize`, which
   **collapses before the name does** and may never be clipped.
4. Previous / next section steps, `size.dockNavWidth` × `size.dockNavHeight`,
   gap `size.dockNavGap`, glyphs at `size.dockNavIconSize`, at `radius.pill`
   with a hairline. Disabled at the ends of the section list — they never wrap
   around.
5. The labelled way out: height `size.dockBackHeight`, `radius.pill`, ground
   `color.*.surface`, hairline the accent mixed into the divider, ink the
   accent-toned text colour, chevron at `size.dockBackChevronSize`, label
   `strings.overview` at `size.dockBackFontSize`. A tinted ground was measured
   below contrast and removed; the ground is the plain surface.

**Price is deliberately absent** from the dock. The rail answers cost.

**Count fit ladder.** Measure after mount and on every resize, and degrade
**full → short → hidden**: `strings.seatsLeftInSectionOne` /
`…Other` first, then `strings.seatsLeft`, then nothing. The full count always
stays in the bar's accessible name. Where `sections[].seatsLeft` is unknown, no
count is drawn.

**Matching spaces.** While an accessibility filter is on and the runtime
advertises `section-access-counts-v1`, the focused section's matching free
spaces are appended to the count as ` · ♿ N`, in the same style — summed over
the active provisions from `sections[].accessibleFree`. The suffix rides on
both rungs of the ladder, so it is measured with the count rather than
discovered after layout, and it joins the accessible name too. A section with
**no entry** for any active provision was not counted and says nothing: absent
is not zero, and a bar that drew `♿ 0` would tell a buyer the section is full
when the truth is that nobody counted it.

**States.** Not mounted at all unless the host asked for it. Where it
is mounted: hidden at the venue rung; visible when a section is focused; hidden
while a seat card owns the bottom edge; hidden in the immersive scene.

**Motion.** Slides up on `motion.duration.dock`. A pan onto a new section
**updates in place** and cross-fades the name and count on
`motion.duration.crossfade` rather than rebuilding. Departure uses
`motion.duration.exit`.

**Haptics.** `haptics.sectionFocused` when the focused section changes to a real
section. Returning to the overview fires nothing — the buyer already felt the
tap that took them there, and leaving a place is not the same news as arriving.

**Accessibility.** The bar is a group named for the focused section, carrying
the full seats-left sentence whatever the visible ladder chose. The steps are
buttons named `strings.previousSection` / `strings.nextSection`.

**Snapshot.** `sections[]` (`id`, `label`, `displayLabel`, `color`,
`dominantCategoryKey`, `seatsLeft`, `accessibleFree`), `map.rung`,
`map.focusedSectionId`, `map.accessibilityFilter`.
Prefer `dominantCategoryKey` over a copied colour when painting the dot: the key
survives a colourblind-safe palette and a category recolour.
**Commands** `picker.focusSection { id }`, `picker.overview`.

### 3.7 Floor rail

**Name** `SeatLayerFloorStrip` · **slot** `floorStripStyle` · **file**
`lib/src/picker/picker_floor_strip.dart`

Drawn only on a multi-floor venue, and never in the immersive scene. It flows in
the **left-rail** region, starting at the map's own top edge and stepping below
the Map/3D control where that is drawn.

**Anatomy.** A track at `radius.pill` on the surface at high opacity over a
blur, hairline `color.*.divider`, padding `size.floorRailPadding`, gap
`size.floorRailGap`, horizontally scrollable. Each chip: height
`size.floorChipHeight`, padding `size.floorChipPaddingX`, label at
`size.floorChipFontSize`, `radius.pill`; idle ink `color.*.mutedText`,
selected the accent ground with `color.*.onAccent`. An info glyph of
`size.floorInfoSize` closes the track, inside a `size.minimumHitTarget` target,
and is drawn only where the host takes an `onFloorInfo` action for it — a
control that opens nothing is not drawn.

`strings.allFloors` is the first chip where the runtime offers an all-floors
view.

**Capability.** `floor-stack-v1`. Without it the floor list is not offered.

**Accessibility.** A group named for floors; each chip a toggle with a pressed
state.

**Snapshot.** the runtime's floor list and the active floor.
**Command** `picker.setFloor`.

### 3.8 Seat card

**Names** `SeatLayerConfirmCard` (+ its parts and actions) · **slots**
`confirmCardStyle`, `primaryButtonStyle`, `secondaryButtonStyle`, `pillStyle`,
`scrimColor` · **files** `lib/src/picker/picker_confirm_card.dart`,
`picker_confirm_card_parts.dart`, `picker_confirm_card_actions.dart`,
`picker_confirm_card_placement.dart`

The card is the picker's one native *moment*: the map dims behind it, it springs
in from the seat's side, and it asks one question.

#### 3.8.1 Box

Width `size.confirmCardMaxWidth`, capped at the map width less
`2 × size.confirmCardGutter`. Corner `radius.confirmCard`, elevation
`elevation.confirmCard`, ground `color.*.surface`, hairline a mix of the divider
toward the text so the card has an edge on both themes.

Behind it, the map goes **behind glass with a hole in it**. The card rests over
a map that is otherwise fully legible, so the moment of decision competed with
several thousand other seats: the runtime's per-seat paling already recedes the
candidate's neighbours (opacity .16 outside the focused section, a six-seat-pitch
disc on a section-less chart, neighbours keeping full ink and numbers), but
nothing quieted the MAP.

So while a seat card is up the map takes a black veil at
`opacity.confirmScrim` behind a `size.confirmScrimBlur` blur, masked by a
radial gradient that is fully clear to `size.confirmScrimClearRadius` around
the tapped seat and reaches full strength at
`size.confirmScrimFeatherRadius`. The hole is what makes this a spotlight
rather than a curtain — the buyer is being asked about one seat and can still
see it. The feather is what stops the hole reading as a drawn circle.

Three rules bind it:

- **Only with a seat to spotlight.** No anchor — a prompt about no one seat, or
  the immersive scene, where the seat IS the picture — means no glass.
- **It never takes a pointer event.** A press over the glass reaches the map
  underneath and cancels the card, exactly as a press on bare map does.
- **Reduced transparency drops the blur** and deepens the veil to
  `opacity.confirmScrimFlat`, so the map still recedes without being smeared.
  Legibility is a setting, not a taste. Flutter has no direct reading of it, so
  high contrast stands in.

`scrimColor` stays a style slot for a host that wants a flat wash over the
whole map instead; its default is transparent. While the card is up, the rest of the chrome is *paused*: the
anchors dim and stop receiving presses, and the cart sheet dims and goes inert.
The **bottom-centre** region is the exception — it comes forward at full opacity
above the card, because it carries the toast that is the *reply* to the tap, and
it stays press-through so it can never steal Cancel or Add seat.

#### 3.8.1a Selection focus — the runtime paints the candidate

The glass quiets the map; it does not tell the buyer which seat the question is
about. A tapped seat is in `selection` from the moment it is tapped, and every
selected seat is drawn alike, so on a busy chart the seat under the spotlight
was a plain ring among the ones the buyer had already settled.

So the card names its seat to the runtime. **On open** it sends
`picker.setSelectionFocus` with `{ seatId }`; **on close** it sends the same
command with `{ seatId: null }`. Both questions focus — an add card and a
remove card each stand over one seat the buyer has to find — and only one card
is ever up, so only one seat is ever the candidate.

What the runtime paints for a focused seat: a 4 pt ring on the seat itself, a
halo answering the canvas around it, and its neighbours paled. Nothing else. The
command selects nothing, holds nothing and moves no camera, so it is safe on a
read-only picker and carries **no busy action** — a buyer must not watch the
chrome grey out because a card opened. It reports no state either: the reply is
`{}`, and the runtime drops the focus itself when the seat leaves the selection,
so the native side only ever mirrors what it last asked for.

It is gated on the bundle advertising `picker.setSelectionFocus` in its own
`hello` command table — the command table IS the whole contract, since nothing
in the snapshot changes — and an older runtime is simply never asked. One that
advertises it and still answers `unsupported_command` is swallowed rather than
raised: the seat is painted the way it was before, and there is nothing there
for a buyer to act on. Every other failure surfaces as usual.

Flutter: `controller.setSelectionFocus(seatId)` in
`lib/src/picker/picker_selection_focus.dart`, driven from
`picker_seat_answers.dart` beside the two questions themselves. The send is
coalesced — the card's seat is reported from the chrome's build, which runs on
every frame — and deferred to a microtask, so a command never publishes state
in the middle of a build.

#### 3.8.2 Placement — a fixed sheet, and the map moves

Three constants, all tokens:

| Token | Meaning |
| --- | --- |
| `size.confirmCardRestInset` | daylight between the card and the map's foot |
| `size.confirmCardSeatGap` | daylight between the card's top edge and the band the map is framed into |
| `size.confirmCardTopInset` | the closest the card may come to the map's top |

The card has **one home**. It is the bottom sheet the buyer already expects,
`restInset` above the foot of whatever the picker's own chrome has left of the
map, on every tap, for every seat — nothing about the tapped seat is read when
it is placed. `topInset` and `bottomInset` are the bands that chrome stands on,
so the sheet never slides under the dock or behind the floor rail; a card
taller than the band it lives in keeps its top edge rather than climbing past
`topInset`.

Keeping the seat and its card together is then the **map's** job, and it is
done the way the web sheet does it — by a **pan**, never a zoom:

```
band     = mapHeight - cardTop + seatGap     // measured from the map's foot
clear    = mapHeight - chromeTop - band      // what the sheet leaves
fraction = 0.48 * clear / (mapHeight - chromeTop - chromeBottom)
cmd picker.frameSeat { seatId, fraction, gestures? } → { dy, gestures }
```

The runtime pans so the seat rests at `fraction` of the band the reported
insets leave (`chromeTop` … `mapHeight − chromeBottom`), x untouched, zoom
untouched; the sheet is folded into the fraction rather than reported as an
inset, because an inset re-frames the whole section and changes the zoom. The
first frame is sent the frame after the card is measured — its height depends
on the seat's own facts, a photo strip or the rail that stands in for it,
tiers, note bands — and re-sent after every snapshot the runtime publishes so a
glide that lands with the card up is followed; the runtime answers `dy: 0`
when the seat is already in place. The reply's `gestures` — the runtime's
count of the BUYER's own camera moves — is handed back on every later frame,
and the runtime declines once it has moved on. When the card leaves — Cancel,
Add seat or an outside tap alike — one restore pans the map back by the sum
of every lift, with the same count, so a buyer who has since taken the wheel
is left where they are. A card replaced by another seat's card without a
dismiss in between keeps the first lift standing and adds to it; the one
restore at the end puts the map back where the buyer had it. Withheld in the
immersive scene, which frames itself. Dart file: `lib/src/picker/picker_seat_lift.dart`.

**Why, and what was tried.** The card used to move to the seat: first tracking
the tapped seat's own y, then choosing between a resting and a raised home. On
a 440×956 phone that read badly in both forms. The seat is a small ring inside
a small hole in the glass, the card is at the foot of a tall map, and the two
halves of one question could be some 600 pt apart — the buyer had to hunt for
the seat they had just tapped, and with the sliding form the buttons moved out
from under the thumb that had just used them. Web 0.80.0 answers the same
complaint from the other end, and this is now the same answer: pin the card and
move the map.

**On an older runtime** (before 0.80.2, no `picker.frameSeat` in the `hello`
command table) the lever is the viewport inset instead — `bottom:
max(chromeBottom, band)` — and what the runtime does with it is
`refitCurrentView`: it re-frames the focused section into the band left clear,
so the seat lands *inside* the clear band rather than at a constant fraction
of it, and a pinch the buyer had made inside the section is refit away with
it. That was the closest the bridge allowed until the pan primitive landed;
it is kept only as the fallback.

The sheet rises from the foot of the map, always — one home, one entrance. The
**spotlight hole** in the glass is what ties it back to the seat it is asking
about; the hole is cut in the map surface's own coordinates — the glass fills
the same box as the map, so nothing is added for the chrome above it — from
`selection[].screenPoint` **plus the pan standing over that snapshot**.

That sum is the whole geometry rule, and the second half of it is easy to
lose. `screenPoint` is computed when the runtime BUILDS a snapshot, and
`picker.frameSeat` publishes none — it is camera only, no revision, no
selection — so the reported point is where the seat sat BEFORE the lift. Cut
the hole at the reported point alone and it lands one lift band below the
seat, showing its neighbours while the seat itself stays under the blur (seen
on device, iOS 26.5). So the lift reports what it has panned since the
snapshot the chrome is reading (`PickerSeatLift.anchorDy`), the hole moves by
it, and the count resets the moment a newer snapshot arrives — that snapshot's
points already stand where the map now is, and adding the pan again would push
the hole as far above the seat as it used to sit below it. It follows the
re-frames too: the 350/800 ms re-asks and every later revision each move the
hole with the seat.

**Capability.** The seat's screen point requires `seat-screen-point-v1`
(`selection[].screenPoint`). It no longer decides where the card goes — the
sheet has one home either way — but it is what cuts the spotlight hole around
the seat and what shows that the re-frame landed. A runtime without it gets the
same card over flat glass, which is a correct state, not a degraded one.

#### 3.8.3 Anatomy, 2D

1. **Identity grid**, minimum height `size.confirmIdentityHeight`. Three
   labelled cells — section, row, seat — of **equal width**, all centred,
   divided by hairlines, with a hairline under the grid. Eyebrows are
   `size.confirmIdentityKeyFontSize` at w800, letter-spaced a tenth of their
   own size, uppercase, in `color.*.mutedText`; values are
   `size.confirmIdentityValueFontSize` at w800 (the web's 850, rounded to the
   nearest weight a platform can name) in `color.*.text`, one line, ellipsized.
   **Only a section longer than `size.confirmSectionShortMax` characters**
   drops to `size.confirmIdentityLongSectionFontSize` and may wrap to two
   lines: a numbered section such as `209` reads as one line of equals with the
   row and the seat, while a venue phrase such as `Upper Grand Circle` needs
   the room. Where there is no section the grid becomes two equal centred
   cells. The cell set is chosen first — section only where the seat names one,
   row only where it has one, the place always — and a cell that is present but
   empty prints an em dash. Copy: `strings.sectionWord`,
   `strings.rowWord`, `strings.seatWord`, `strings.placeWord` — the row eyebrow
   follows the object's own word (Row, Table, Booth). **Print the row with its
   section prefix stripped**, or the card reads `Stalls D · Row Stalls D C`.
2. **Category band**, minimum height `size.confirmBandHeight`. Ground is the
   **category colour itself, full bleed** — no dot, no tint, no leading rail:
   a nine-point disc beside an eleven-per-cent wash said the colour twice and
   loudly enough neither time, and this is the colour the legend and the map
   already speak. Because the ground is authored, the ink is **chosen per
   colour**: white wherever white clears a 3:1 contrast ratio against the
   category colour, and `#0B0F19` otherwise, so a pale yellow or a light grey
   category keeps its name. Those two candidates are fixed; the band never
   manufactures a colour of its own.

   It prints **two things and nothing else**, both in that ink: the category
   name at `size.confirmBandNameFontSize` w800, which takes the room and is
   the one that ellipsises; and the price at the trailing edge at
   `size.confirmBandPriceFontSize` w800 in tabular figures, which never
   truncates. Padding is `size.confirmBandPadTop` /
   `…PadTrailing` / `…PadBottom` / `…PadLeading` (12 / 16 / 12 / 14 on a wide
   card, 11 / 16 / 11 / 14 on a phone), so **the price keeps at least
   `size.confirmBandPadTrailing` from the card's own trailing edge** — it was
   sitting on it. Inside the 3D scene the band gives a few points back
   (`size.confirmImmersiveBandPadY` × `…PadX`) and the price steps down to
   `size.confirmImmersiveBandPriceFontSize`.

   **There is no remaining count on the band.** "6,600 left" beside the name
   said nothing a buyer choosing one seat could act on, and it pushed the
   price into the card's edge. The **legend** carries the count, where a buyer
   comparing categories is actually looking, and the dock carries the focused
   section's. `strings.seatsLeft` therefore has no confirm-card caller;
   `strings.onlyLeft` remains available to a host that wants a scarcity
   telling of its own somewhere else.
3. **Photo strip**, height `size.confirmPhotoHeight`, full-bleed inside the
   card's corner, drawn **only where the seat names an authored photograph**
   (`selection[].seatViewThumb`, capability `seat-view-thumbnail-v1`); a
   generated stand-in is never offered from the card, though 3D and 360° are
   unchanged. Over it, in
   the trailing bottom corner, a group of pills of `size.confirmPillHeight` at
   `radius.pill` on a dark plate with white ink in **both** themes: `View from
   here` (`strings.viewFromHere`) and `3D` (`strings.venue3D`, accessible name
   `strings.seeItIn3D`). The sight line sits in the trailing top corner on the
   same plate. If the image never arrives the strip animates away and takes the
   slot with it. §3.8.7 has the whole of it.
4. **No photo, no strip.** Where the seat names no authored photograph the
   phone card draws **nothing** in this slot — no rail, no frame, no caption.
   A 44 pt bar holding one control was dead height on a card that already
   covers a third of the phone, and the 3D way in moves into the decision row
   instead (item 7). The sight line rides the photograph or is not printed:
   off a photograph there is no plate for it to survive on.
5. **Tier picker**, only where the seat has more than one tier. Legend
   `strings.ticketType`; rows of `size.confirmTierHeight` at `radius.button`,
   name, note and price; the selected row takes an accent hairline, an accent
   tint and an accent rail. A single tier renders as a guidance line
   (`strings.tierCompanionGuidance` and its kin), never as a one-option choice.
   File `lib/src/picker/picker_ticket_tiers.dart`.
6. **Seat notes** — everything the organizer has marked on this seat, as
   full-bleed BANDS directly under the category band and above the tier
   chooser. One row per attribute, in one fixed order, each with the shared
   drawing for that attribute. The whole of it is §3.8.9; two things belong
   here. They are bands, not plates: no radius, no border, no inset, a hairline
   on each join, so the popup stays the stack of bands it already is rather
   than growing four small cards inside itself. And they sit **above** the tier
   chooser — below it they read as a footnote to the price list instead of as
   facts about the seat. Consequently the card's body owns no padding of its
   own; the tier chooser carries the gutter itself.
7. **Actions**, height `size.confirmActionHeight`, each in its own rounded box
   at `radius.button` inside the card's gutter — not a bar fused to the card's
   bottom edge, which read as the frame rather than as things to press. Where
   there is **no photograph and 3D is on offer**, the row opens with a 44 × 44
   square before Cancel: a ghost button on the accent at 12 % over the surface
   with a divider hairline, a cube glyph over `strings.venue3D` at
   `size.confirm3dSquareFontSize` w800, accessible name `strings.seeItIn3D`.
   It is absent with a photograph (the strip's pill wins) and inside the 3D
   scene. Cancel then takes **34 % of the whole row** — the square and its gap
   come out of what Add seat had — and carries a hairline and muted ink; Add
   seat takes the rest, filled with the accent. Nothing in the row may wrap.
   Copy: `strings.cancel`; `strings.addSeat` for a seat and `strings.select` for
   a booth, table or general-admission unit. **No price on the button** — the
   price is already in the band above.

`See it in 3D` as a **full-width action** at `radius.button` is the wide
composition's form; the phone uses the strip pill, or the decision-row square
where there is no strip.

#### 3.8.4 Motion

| Moment | Duration token | Curve | What moves |
| --- | --- | --- | --- |
| entrance | `motion.duration.cardEnter` | `motion.curve.spring` | opacity in, a small rise and scale-up, growing from the seat's direction |
| invitation — sweep | `motion.durationOutsideBudget.inviteSweep` after `motion.durationOutsideBudget.inviteDelay`, once | `motion.curve.easeEnter` | a soft band of the button's own ink sweeps across `Add seat` |
| invitation — breathe | `motion.durationOutsideBudget.inviteBreathe`, from `motion.durationOutsideBudget.inviteBreatheDelay`, repeating | ease in and out | a halo grows and fades with a ~2 % scale |
| invitation stops | — | — | on the first touch or key anywhere on the card, or focus on the button |
| press sweep | `motion.duration.pressSweep` | `motion.curve.easeEnter` | the button fills with its own ink from the leading edge |
| press tick | `motion.duration.pressSweep` | `motion.curve.easeEnter` | the check mark draws itself |
| label swap | immediate | — | `strings.addSeat` → `strings.added` |
| card exit | `motion.duration.exit` after the sweep | `motion.curve.easeExit` | opacity out, a small fall and scale-down |
| flight chip | `motion.durationOutsideBudget.confirmFlight` | `motion.curve.spring` | a small pill in the category colour carrying the seat label flies from the card to the cart summary |

**The add choreography runs in one fixed order**, and each beat waits for the
one before it (web 0.84.1):

1. the press lands — `haptics.seatConfirmed`, the button locks, and the
   inventory command goes out;
2. the **confirm sweep** fills the button with its own ink under a drawn tick,
   `motion.duration.pressSweep`, and the label turns to `strings.added`;
3. only then does the card fly: a **chip in the category's own colour carrying
   the seat's label** leaves the card's centre for the foot and travels for
   `motion.durationOutsideBudget.confirmFlight` on `motion.curve.spring`,
   while the card itself is dismissed;
4. **on landing** the chip tells the controller the flight is over
   (`beginCartLanding` / `endCartLanding` in
   `lib/src/picker/picker_seat_answers.dart`, read as `cartLanding`);
5. the landing releases the footer's **swell**: the count and the total scale
   1 → 1.3 → 1 over `motion.duration.bump`, anchored on their own edges, with
   the ink lerping from `color.*.text` to the accent and back;
6. and only after that does the **map's lift pull back** —
   `PickerSeatLift.release` is gated on `cartLanding` in
   `picker_adaptive_layout.dart`. A map moving under a chip still in flight
   read as two motions fighting.

The chip is a labelled pill, not an anonymous dot: what left the card is a
specific seat, and the buyer should be able to read which one on its way to a
summary that has already counted it. It appears at just over half size, reaches
full size a fifth of the way along, and shrinks as it lands; its ink is black
or white chosen against the category colour, which nobody picked for
legibility. Dart: `_FlyingSeat` in
`lib/src/picker/picker_confirm_card_actions.dart`.

**Commit-on-press ordering is fixed.** The cart, the totals and every snapshot-
derived surface update on the **press tick**. Only the card's *departure*, the
footer swell and the map's pull-back are sequenced behind it. The button locks
on the press without losing focus, and a second press while it is committing is
ignored. Under reduced motion the invitation never starts, the sweep and tick do
not play, the flight chip is not created at all — so no landing is begun and
nothing waits on one — and the card departs on the press without waiting.

The **flight target** is measured *before* the count reflows: on a phone that is
the foot's total line, aimed 24 points above the foot of the screen.

**While the card is up, the rest of the phone stands down.** The sheet's handle
disc is not drawn at all — it was sitting half under the card's scrim, and the
card is the only question on the screen until it is answered. The map's control
column fades to opacity 0 and goes inert (§3.5). The footer's count and total
**exclude the candidate**: a tapped seat is in the runtime's `selection` — and
therefore in the raw cart — from the moment it is tapped, so everything a buyer
reads as a commitment is counted from `confirmedCartLines`,
`confirmedTicketCount` and `confirmedCartTotal`
(`lib/src/picker/picker_seat_answers.dart`). And the footer's call to action
stays a **disabled button carrying the label it already had** — not a sentence
about the card: the card is the question, and the buyer's cart should still
read as their cart underneath it. Not even the finder is offered while the card
asks.

While a card is up and a toast is showing, the whole bottom-centre region moves
so the message sits **above** the card — unless that would push it within a
short distance of the map's top, in which case it stays where it is.

#### 3.8.4a Remove — the same card, asking the opposite question

A second tap on a seat that is **already in the cart** used to drop it in
silence: the ring went, the cart emptied and the total moved, with no card, no
notice and nothing to undo it with. A buyer checking which seat they had picked
lost it by looking at it.

A runtime that speaks it now **keeps the seat selected** and reports the tap as
the bridge event `seat.retap`, payload `{ seat: <one selection[] entry> }` — no
`selection.changed` comes with it, and it is not coalesced. The chrome raises
the SAME card over the seat in `SeatLayerConfirmCardMode.remove`: same box,
same placement, same spotlight glass, same identity grid, band, photograph and
inspection row. Only the primary answer changes.

- **Primary** reads `strings.removeSeat` ("Remove seat", the runtime's own
  `picker.removeSeat`, so both pickers say it the same way in every locale). It
  is painted in `color.*.error` rather than the accent, and it carries a cross
  where the add card draws its tick — a tick is the wrong promise on a remove.
  Pressing it takes the seat back out down the **same path the cart's ✕ uses**
  (`picker.removeCartLine`), and the seat is then answered for.
- **Cancel keeps the seat.** So does the tap outside, the downward drag and the
  platform's back gesture: they are ways out of the question, not answers to
  it. A stray press on the map must never be the thing that empties a cart.
- **The seat stays counted the whole time.** It is not a candidate — the buyer
  already owns it — so unlike an unanswered add it keeps its ticket, its line
  and its money in `confirmedCartLines`, `confirmedTicketCount` and
  `confirmedCartTotal` until the question is actually answered.

An unanswered ADD outranks a retap, and a retap of the seat an add card is
already open about is ignored: that card is asking about this very seat, and
turning its own question round under the buyer's finger is a worse surprise
than the silence it replaces. A read-only picker never raises it at all.

The controller reports the candidate as `seatAwaitingRemoval` and puts the
question away with `dismissSeatRemoval()`; both live in
`lib/src/picker/picker_seat_answers.dart` beside `seatAwaitingConfirmation`.
The remove card focuses its seat exactly as the add card does (§3.8.1a) — the
buyer has to find the seat they are being asked to give back too.

#### 3.8.5 Gestures and haptics

- Tap outside the card, on the dimmed map, gives the seat back.
- A downward drag past a comfortable flick distance, or a fast downward flick
  however short, dismisses it. (The distance and velocity are native tuning
  constants, not tokens; the rubber-band feel comes from
  `motion.physics.rubberBand`.)
- `haptics.cardArrived` when the card lands, `haptics.seatConfirmed` on Add
  seat, `haptics.cardCancelled` on any dismissal, `haptics.ticketRemoved` on
  Remove (§3.8.4a).
- In the remove state the drag and the tap outside still dismiss the card, but
  they leave the seat where it is.

#### 3.8.6 Accessibility

The card is a dialog: it names itself, it scopes the route, everything painted
before it is hidden from assistive technology, and dismissing returns focus to
the map region. The identity reads as one sentence — section, row, seat,
category, price — not as six unlabelled cells, and that sentence IS the
dialog's name. Both answers are also custom actions on the card, so a rotor can
say yes or no without hunting for the buttons.

The first focusable is `Add seat`, not `Cancel`, though `Cancel` is drawn
first: the focus lands on the answer the card exists to collect. Escape and the
platform's back gesture both give the seat back — except in the remove state
(§3.8.4a), where they keep it. Both answers are custom actions under whichever
word the primary is currently offering. The card's type is clamped at
`type.scaleClamp.card`, and its action row's height grows with that scale
rather than clipping the word the card exists to offer. §4.10 has the whole
picture, of which this is one surface.

#### 3.8.7 The photograph, the sight line and the confidence teaser

Mirrors the web phone card's §7.6 and its confidence teaser
(`seatConfidenceConfirmHtml`). Everything here needs the capability
`seat-view-thumbnail-v1`; without it the card is exactly the card described
above, and the fields are not read even when they are present.

**The wire.** Each `selection[]` entry may carry:

| Field | Meaning |
| --- | --- |
| `seatViewThumb` | `{reference, kind}` — an EVENT-SCOPED API path `/pub/events/{key}/assets/{asset}`, and what kind of image it is (`real` today, decoded as an open string) |
| `sightlineMetres` | metres to the stage, already rounded the way the web prints it; only on charts with a stage |
| `seatViewConfidence` | `{headline, model, reality, coverage, provenance, freshness, limitations[], modeledTarget?}` — buyer-safe copy the runtime resolved |

All three are present-only. `seatViewThumb` is absent unless the runtime's own
predicate accepted the reference, and **absent means there is no photograph**,
never a licence to draw a stand-in. The older top-level `seatViewKind` is
emitted by no runtime; ports that carry it should fall back to
`seatViewThumb.kind`.

**Fetching it.** The reference is not an image URL a view can load on its own:
a private event answers that path only for the buyer's bearer. Every port
fetches the bytes itself — `GET {apiBase}{reference}` with
`Authorization: Bearer …`, no cookies — validating first that the path matches
`^/pub/events/([^/]+)/assets/([a-zA-Z0-9._-]+)$` **and** that the event segment
is the event the picker is showing; anything else is refused without a request.
A small LRU (12 references) holds the bytes for the session, concurrent readers
of one reference share one request, and the bearer is cached until thirty
seconds before its stated expiry, re-minted through the host's provider with
the refresh reason `asset`. Every failure — a bad reference, 403, 404, no
bearer, a dead network — is "no photograph"; nothing throws into the card and
nothing about the bearer is ever logged.

**Drawing it.** While the bytes are in flight the strip is the neutral gradient.
On arrival the image fills it (cover) over `motion.duration.crossfade`. On a
miss the strip collapses away entirely over `motion.duration.thumbOut`
(160 ms, height and opacity) and the 3D square appears in the decision row, so
the way into the scene is still one tap away and the card is a row shorter. The
reference is evicted so reopening the seat tries again. Under reduced motion
both transitions are instant.

**Sight line.** Copy `strings.sightline` ("≈ {m} m to stage"), with the metre
figure printed as the runtime rounded it. On the photograph it is a pill in the
trailing TOP corner, inset 6, on the photo plate and ink, `size.confirmSightFont`
at w700, radius `radius.pill`, padding `size.confirmSightPadY` ×
`size.confirmSightPadX`. **With no photograph it is not printed at all** on the
phone: the strip it rides leaves the card, and a lone measured line is not
worth a row of its own. It is shown whenever `sightlineMetres` exists AND there
is a photograph to put it on.

**Confidence teaser.** 3D card only, exactly as on the web: outside the scene
there is no model on screen to be honest about. It takes **one of two forms**.
Where the host can open the passport (`onSeatConfidence`, §4.9) it is a chip on
the inspection row — an accent dot and `strings.passport`, nothing else. Where
nothing can be opened it stays the static teaser described here, because the
headline and the detail ARE the information and a chip saying only `Passport`
beside a dead target would say nothing and do nothing. Full width, minimum
height
`size.confidenceTeaserMinHeight`, top margin `size.confidenceTeaserTop`,
padding `size.confidenceTeaserPadY` × `size.confidenceTeaserPadX`, radius
`size.confidenceTeaserRadius`, a hairline of the accent at 35 % over the
divider, ground the accent at 7 % over the surface. Leading column: `headline`
at `size.confidenceTeaserHeadFont` w700, one line, ellipsis; under it
`modeledTarget ?? reality` at `size.confidenceTeaserDetailFont` in
`color.*.mutedText`, one line, ellipsis. Trailing: `strings.passport` at
`size.confidenceTeaserBadgeFont` w700 in the **readable accent** — the accent
itself where it clears 4.5:1 on both the surface and the ground, otherwise
blended toward the text ink until it does (the web's `--sl-accent-text`).

**Accessibility.** The image is decorative and carries no label: the pill says
what it opens. The sight line is plain text. The teaser is a button ONLY where
the host can act on it (§4.9); otherwise it is a static row with no button
semantics and no focus stop.

**Snapshot.** the newest unconfirmed `selection[]` entry (`label`,
`sectionLabel`, `rowLabel`, `seatNumber`, `price`, `currency`, `objectType`,
`tiers`, `screenPoint`, and on `seat-view-thumbnail-v1` also `seatViewThumb`,
`sightlineMetres`, `seatViewConfidence`), the matching `categories[]` entry,
`capabilities` (`seatView`, `venue3d`).
**Commands** `picker.openSeatView`, `picker.showSeatIn3D`, `picker.deselect`.

#### 3.8.8 The card inside the 3D scene

Inside the venue scene the card asks the same question, in its own dimensions.
Width `size.confirmCardImmersiveMaxWidth`, resting inset
`size.confirmCardImmersiveRestInset`, cells padded
`size.confirmImmersiveCellTop` / `…CellSide` / `…CellBottom`, values at
`size.confirmImmersiveValueFontSize` and a long section at
`size.confirmImmersiveSectionFontSize`.

**There is no photo strip.** The venue is already the picture, so the one view
the buyer has not had is the one from the seat, and that — with the passport
where a host can open it — is the card's **inspection row**: ONE line of
compact chips of `size.confirmInspectChipHeight`, gap 6, at `radius.button`,
ground the accent at 12 % over the surface with a divider hairline, labels at
`size.confirmInspectChipFontSize` w800 in `color.*.text`, each chip sharing the
row's width equally.

| Chip | Visible | Spoken | Mark |
| --- | --- | --- | --- |
| passport | `strings.passport` | `strings.passport` | a 7 pt accent dot before the word |
| view from the seat | `strings.viewFromHere` | `strings.viewFromThisSeat` | none — the seat is named twice directly above it |

The row is followed by the same Cancel / Add seat row as the 2D card, and the
3D square (§3.8.3 item 7) is **never** drawn here. This is what makes the
scene's card roughly a hundred points shorter than the stack of full-width
44 pt bars it replaced, which used to cover the section the buyer had just
flown into.

**The web's `Save to compare` chip has no port.** Nothing in
`seatlayer.picker.snapshot/1` carries a compare set, and a control that cannot
say anything true is worse than an absent one.

#### 3.8.9 Seat notes — one row model, one icon set

A seat can carry twelve accommodations, a wheelchair provision, three selling
marks and the organizer's own sentence. Five surfaces used to answer "what is
special about this seat?" with their own subset and their own emoji, and they
disagreed: the tap card showed a wheelchair line and ONE limited-view line in
which restricted beat obstructed — so a seat behind both a rail and a pillar
reported only the rail — the phone card showed a different pair, the cart
showed markers, and premium reached two surfaces out of five.

**The answer is a model, and the surfaces only draw it.** Dart:
`seatLayerSeatNoteRows` in `lib/src/picker/picker_seat_notes.dart`, mirroring
`core/seatNotes.ts` in the runtime row for row. Ports implement the same
function, not the same screen.

Rows, in this order, and the order is not discovery order:

1. one row per `accessibility[]` key the chart reports, in the runtime's own
   order, named through `strings.accessNeeds[key]` (the runtime's
   `picker.access.*` dictionary);
2. the wheelchair provision — `strings.emptyWheelchairSpace` for
   `wheelchairSpaceType: 'no-seat'`, `strings.accessiblePhysicalSeat` for
   `'seat-present'`. A `wheelchair` accommodation with a provision reported
   yields the PROVISION row only: "empty wheelchair space" already says
   everything "wheelchair space" would, and more precisely;
3. `strings.restrictedView`, and
4. `strings.obstructedView` — **separate rows**, never collapsed;
5. `strings.premiumSeat`;
6. the organizer's sentence. It attaches as a second line under the FIRST
   selling mark on the seat, because an organizer writing "pillar at the aisle
   end" is explaining the restriction rather than adding a fact. With no mark
   to explain it becomes its own row, titled `strings.organizerNote`.

A key this build has no name for is dropped rather than printed raw; two seats
with the same attributes always produce the same list.

**Tone** is the only thing that varies, and it varies in the ground as well as
the ink:

| tone | band | title ink |
| --- | --- | --- |
| access, note | `color.*.text` at `opacity.noteNeutralWash` over the surface | `color.*.text` |
| warn | `color.*.warning` at `opacity.noteToneWash` | `color.*.warnText` |
| premium | `color.*.premium` at `opacity.noteToneWash` | `color.*.premiumText` |

Measured on the tint each pair actually paints on, not on the surface it is
mixed from — the raw amber measures about 1.8:1 on a white card. Measured:
light 5.46:1 (warn) and 5.67:1 (premium); dark 8.24:1 and 8.03:1. The neutral
row is 13.5:1 and 12.0:1. The organizer's second line
is `color.*.mutedText` walked `opacity.noteBodyInk` of the way to the text, for
the same reason. The glyph takes the muted ink on a neutral row and the title's
ink on a toned one.

**Geometry.** Padding `size.notePadY` × `size.notePadX`, glyph
`size.noteIconSize` with `size.noteIconGap` beside it, title `type.noteTitle`,
organizer line `type.noteBody`. The join between two rows is a hairline of
`color.*.divider` at `opacity.noteHairline`; there is none above the first row,
which sits flush against the category band. The **compact** form is the wide
layout's tap card, which is narrower and floats over the map: leading inset
`size.noteCompactPadLeading`, trailing `size.noteCompactPadX`, vertical
`size.noteCompactPadY`, glyph `size.noteCompactIconSize`, and the type comes
down a rung (`type.noteTitleCompact`, `type.noteBodyCompact`).

**The icons.** Sixteen drawings — the twelve accommodations, the three selling
marks and the organizer's `note` — transcribed verbatim from the runtime's
shared set (`core/render-assets/seatTypeIcons.ts`), which is the designer's own
artwork. Dart: `lib/src/picker/picker_seat_icons.dart`. Authored in a 20-unit
box, stroke-only at 1.45 units with round caps and joins, and inheriting the
row's ink, so a tone is one colour away rather than a second icon set. **No
emoji and no platform icon**: an emoji arrives in a colour, weight and baseline
the host font decides and no theme can reach, and the nearest Material icon is
not the drawing the map and the web popups use. A key this build does not know
draws nothing rather than a broken box. A port transcribes the same path data;
redrawing a glyph "close enough" is how the surfaces drifted apart in the first
place.

**The cart chip is deliberately NOT this block.** It is the one surface that is
not a popup — a bordered ticket in a narrow list, where a tinted band reads as
a card inside a card and wraps a two-word title onto two lines. It says the
same attributes in words on one line (§3.10).

**Unavailable seats raise no card at all**, so they raise no notes either: see
§3.8.10.

#### 3.8.10 No card over a seat nobody can take

A seat that is sold, blocked, or in another buyer's hold is **inert**. The
engine swallows the tap — no pinned card with a reason in 2D, no state card in
the scene — and the native side keeps the same rule for the case an older
runtime still reports such a tap: `selection[].status` of `booked` or `blocked`
never raises a card, and `held` raises one only while the picker's own hold is
active, because that is when the buyer's own seats report as held and the card
is what offers them back. A status this build does not recognise is left alone:
an unknown word is not a reason to swallow a seat the runtime selected, and
every runtime shipped before the field omits it entirely.

There is therefore **no "held by another buyer" and no "already booked" card**
anywhere in the picker, and no string for one. A reason the buyer can do
nothing about is worse than being left on the map.

### 3.8a Hover card and tooltip — web only, nothing to port

The web runtime draws a card beside the cursor naming whatever it is over:
the section block below the seats rung, the seat itself above it
(`engine/render/hoverCard.ts` for the DOM, `hoverCardContent.ts` for the pure
model). It has no Dart name, no style slot and no token, and **the native SDKs
implement nothing for it.**

**Mouse only, by construction.** Every update is gated on
`pointerType === 'mouse'`, a touch or pen pointer hides the card, and a
pointer-leave takes it down. A tap synthesizes `mousemove`/`mouseover`, never
`pointermove`, so a touch sequence can never raise one. It is decoration over a
canvas that already has a keyboard path — `aria-hidden`, no pointer events, no
focus — and it adds nothing a keyboard user does not already have. A platform
without a hovering pointer is therefore not missing a feature; the seat card
(§3.8) is the whole answer to "what is this seat" on touch.

**What it says**, for the record rather than for porting:

- **Two modes, one reused card.** The switch is the renderer's own
  `seatsPickable()` — the same test that decides whether a click picks a seat
  or zooms — so the card can never describe a seat the buyer cannot hit.
- **The head is the seat card's head:** the labelled SECTION / ROW / SEAT cells
  of §3.8.3, hairline-divided, so a hover reads as the first frame of the card
  a click opens rather than a differently shaped restatement of it. A section
  card's head is just the section name. Rows are category name and price only.
- **A full-bleed category band**, edge to edge like a legend chip, its ink from
  `categoryBandInk()` — chosen by the FILL, not by strongest contrast. Under
  strongest-contrast the arena red took black ink (6.3 : 1 against white's
  3.3 : 1) and the band read as a warning stripe rather than as a price.
- **It stands down for the host.** While the host has its own card up about the
  same seat — the pinned "cannot be taken" explanation, or the confirm card —
  `hostCardOpen()` answers true, the model resolves to nothing and the card
  hides, so one seat never gets two cards.

The hover card draws the seat-note block too (§3.8.9), in its compact form.
That is a consequence of the shared model, not a second thing to port: there is
no hovering pointer to raise it on a phone, and the seat card is the whole
answer to "what is this seat" on touch.

Full entry: `components.md` › HoverCard.

### 3.9 The collapsed sheet

**Name** `SeatLayerCartSheet` · **slots** `sheetStyle`, `continueButtonStyle`
· **file** `lib/src/picker/picker_cart_sheet.dart`

**THE COLLAPSED SHEET IS THE FOOTER BLOCK, AND NOTHING ELSE** (owner call
2026-09-06): the total line, the call to action and the by-line, in one set of
rules shared with the wide panel. **The cards wait behind the handle.** A list
that unrolled itself every time a seat was added read as a panel the buyer had
not opened, so the collapsed cart region has height zero (`_collapsedCart = 0`)
and every add leaves the sheet exactly as it was.

It used to be a bespoke one-liner — `From €25 · Find seats` with nothing
picked, `2 tickets · Continue €60` once seats existed — with its own markup,
its own resolver and its own type scale, restating in fifty points of chrome
what the foot eight lines below already said. Two summaries of one cart is two
things to keep in step, and they drifted: the hold clock moved between the pill
and the header as the sheet opened, and a buyer with seats already held could
not see the button that takes their money, because the foot that carries it was
hidden at peek. **Ports must not reintroduce a peek bar**, and must not rest
cards in the collapsed sheet either.

**One surface.** Ground `color.*.background` — the PANEL's ground, not the
card's, or the cart cards inside have nothing to sit on. A hairline on the top
edge and **nothing above it**: an upward shadow read as a grey band over the
map, a few points of nothing between the venue and the handle. `radius.sheet`.
`elevation.sheet` stays available through `sheetStyle` and is 0 by default.

**The handle is a disc, not a pill** (web 0.84.0). `size.sheetHandleWidth` ×
`size.sheetHandleHeight` — 44 × 44 — a circle filled with
`color.*.background`, lifted by its own shadow (black at 20 per cent, blur 10,
offset 0 / 2) and carrying a 16-point chevron in `color.*.mutedText`. It
straddles the sheet's own top edge the way a drawer handle sits on a drawer:
`size.sheetHandleOverhang` (22) above the edge, `size.sheetHeadHeight` (16)
inside it, half over the map. **No hairline and no band under it** — an arrow
between two lines read as a band, not a handle. The chevron points **up when
the sheet is collapsed and down when it is open**, rotating over
`motion.duration.chevron` on `motion.curve.easeEnter`, so the state and the
control that changes it are one thing. The disc keeps its own size whatever
strip the head lends it, and it is **not drawn at all while a confirm card is
up** (§3.8.4).

A port that draws the disc outside its parent's own box must make the parent
take the overhang into its height, or the upper half is neither painted nor
pressable.

**Collapsed height is the height of the foot** — head, foot and the bottom safe
inset. Never a fixed peek: a surface clipped to a *different* height than the
content it holds cuts the bottom off its own buttons, which is exactly what a
50 px clip under a 58 px head did on the web. Below the collapsed height there
is nowhere to shrink, so an over-drag translates the whole surface off the
bottom edge instead and the spring puts it back.

**The total line** is the whole summary while the sheet is shut.

- `strings.noSeatsSelected` on an empty cart; `strings.ticketCount` at
  `type.footTotalLabel` (13 / w600) with the total on the trailing edge at
  `type.footTotalAmount` (17 / w700) in tabular figures once there is one.
- **While collapsed and non-empty, a second, muted line under it names the
  seats**: each cart line's label with `-` replaced by ` · `, joined by a comma
  and two spaces — `204 · Q · 7,  204 · U · 13` — at 12 / w600 in
  `color.*.mutedText`, one line, ellipsized, 3 points under the total row, with
  an unfold glyph at 16 on the trailing edge. "2 tickets" alone told a buyer
  they had bought something and not what, and the handle above was the only way
  to find out. The glyph is a hint, not the sheet's toggle — the handle stays
  the cart's one named control.
- **Tapping that line opens the sheet**, so the cards are one press from the
  summary that named them. Open, the line is not pressable and the seat list is
  not drawn: the cards are saying it better.
- It is a live region, and the count and total swell once on
  `motion.duration.bump` when they change — but only when the flying chip
  lands (§3.8.4), never on the press.

**There is no `From <min>` anywhere.** It stated a price and offered nothing to
do about it, on the one line a buyer reads to find out what they are about to
pay. `fromAmount` / `fromPriceText` and every path that carried them are gone.

**The foot** is the lapse notice, the hold-ending cue, the inline action error,
the total line, `size.footTotalGap` (8), the BookButton and the centred
attribution, inside `size.footPadX` (16) / `size.footPadTop` (4) /
`size.footPadBottom` (2). It carries a hairline on its top edge **only when the
sheet is open and there are tickets above it** — the collapsed sheet has
nothing above the foot but the panel's own top edge, and a second hairline a
few points under the first read as two lines for one edge. It is **not
restyled on a phone at all**; the only narrow-only difference left is the WORD
on the button (§3.10.3).

**Accessibility.** There is exactly ONE named toggle for the sheet, in both
states: the handle, with a button role, the expanded state, its own tap action
and `strings.expandCart` / `strings.collapseCart`. Never two — two controls
over one action read as two different things to press. The seat line's tap is
an affordance on the live region, not a second named control.

**Gestures.** The whole handle band is the tap target and the sheet's own drag
runs under it. A drag up past a small threshold opens; a drag down past it
collapses; a press with almost no movement toggles. A tap on the map collapses
an open sheet, and so does a tap on a cart card (§3.10.2).

Springs, not tweens: `motion.physics.sheetSpringMass`, `…Stiffness`,
`…Damping`, with `motion.physics.sheetFlingVelocity` as the threshold at which
a flick decides on its own and `motion.physics.rubberBand` for over-drag. The
value the physics moves is the cart region's height ABOVE what the collapsed
sheet already shows — which is now zero — so peek stays zero and the detent
table below is unchanged.

**Snapshot.** `cart.ticketCount`, `cart.cartTotal`, `cart.lines[]`, `hold`,
`event.currency`, `event.salesClosed`, `capabilities.bestAvailable`.

### 3.10 The open sheet, cart cards and foot

#### 3.10.1 The sheet

Content-height, growing ticket by ticket, capped at:

| State | Ceiling |
| --- | --- |
| open with tickets | the lesser of `size.sheetMaxHeightFraction` of the height and `size.sheetMaxHeight`, plus the safe inset |
| open with an empty cart | the lesser of `size.emptyTrayMaxHeightFraction` and `size.emptyTrayMaxHeight`, plus the safe inset |
| a native-only full detent | `size.sheetFullHeightFraction` |
| collapsed | the head and the foot, plus the safe inset |

Opening reveals the cart itself and adds the closed-sales statement and, on an
empty cart, the best-seats form. The open cart region is capped at
`size.cartPeekMaxHeight` (189) — **three whole cards and a sliver of the
fourth**: `size.cartTrayPadTop` + three `size.cartCardMinHeight` cards + their
`size.cartCardGap` gaps + the next card's edge — and scrolls inside its own box.
Ten tickets used to grow the sheet until it owned the phone. On a short device
the sheet's own ceiling wins instead, the region shrinks and scrolls, and the
button is never what gets cut off.

The extras stay LAID OUT while the sheet is shut — offstage, not unmounted — so
the sheet always knows how tall it would open to and opens straight to it rather
than springing to a guess and correcting.

**The sheet never opens itself.** Only the buyer opens it — the handle, a
swipe, the collapsed seat line (§3.9), or the empty cart's `Find best seats`.
It collapses again when the buyer taps the map. A cart card framing its own
seat (§3.10.2) leaves the sheet where the buyer put it.

#### 3.10.2 Cart cards

**Name** `SeatLayerCartList` / `SeatLayerCartCard` ·
**file** `lib/src/picker/picker_cart_list.dart`

ONE CARD PER TICKET, AND THE SAME CARD ON EVERY WIDTH (owner call 2026-09-06:
"cards should be the same design as desktop"). The phone used to draw a second
cart — a bordered plate of `44` pt hairline-divided lines, with consecutive
seats folded into runs behind a `+N more`. It saved real pixels and it cost the
sheet its coherence: a bordered list on its own surface between a band of
chrome and a shadowed foot reads as three blocks stacked in a panel rather than
as one panel. **The run model, the fold and the `+N more` are gone**; the open
sheet caps the list and scrolls instead.

Each card is at least `size.cartCardMinHeight` (52) on `color.*.surface`, with
a hairline border, corner `size.cartCardRadius` (12) and padding
`12 / 4 / 4 / 4` (leading / top / trailing / bottom) — the trailing side is
tight because the two action boxes carry their own touch floor. Cards are
separated by `size.cartCardGap` (6). Contents, on one baseline:

- a 9 pt category dot with a hairline of the ink at 22 per cent, so a pale
  category on the panel's own surface is still a disc you can find, then a
  10 pt gap;
- the **name** at 15 / w700 in `color.*.text` — the section, or the ticket type
  where the chart has no sections — the only part that ellipsizes;
- under it the **position line** at 13 / w600 in `color.*.mutedText` with
  tabular figures: the row, the seat and the ticket type joined by ` · `
  (`R · 6 · Lower Bowl`), one line, ellipsized. The type joins the line only
  when it is not already the name of the card;
- the amount at 15 / w800 in `color.*.text`, tabular, which never truncates;
- the **eye**, then the **×**, drawn as 18-point glyphs in `color.*.text`
  inside TIGHT `size.minimumHitTarget` boxes. Tight, not a minimum: the
  platform's own icon button pads itself to 48, and four points per card is
  what puts the fourth card past the open cap. The eye is drawn only where the
  host allows it, the runtime advertises `seatView`, and the seat carries an
  authored photograph.

**A press on the card's own face — not on the × and not on the eye — takes the
buyer to the seat.** It sends `picker.frameSeat { seatId, fraction: 0.5 }`, so
the map pans the seat to the middle of the band the chrome leaves. **The sheet
stays where the buyer put it**: the phone map sits above the sheet in the same
column, so the seat lands in the room the open sheet leaves it, and a buyer can
check one seat after another without reopening the cart each time. A card
whose line carries no selected seat is not pressable.

**Notes.** What the organizer said about the seat, said ONCE and in WORDS, on
the card, under a hairline of the divider at 72 per cent inside it, with
`size.cartNotePadTop` above and below the rule and `size.cartNoteGap` between
lines. The rows are exactly the rows of §3.8.9 — `seatLayerCartNoteLines` maps
`seatLayerSeatNotesFor` one to one — so the order is fixed: accommodations,
then the wheelchair provision, then **Restricted view**, **Obstructed view**
and **Premium seat** as separate lines, then the organizer's own sentence.
Title at `type.cartNoteTitle` in `color.*.text`, sentence after it at
`type.cartNoteText` in `color.*.mutedText`.

No plate, no ground and no icon: the card is already a bordered ticket, a
tinted band inside one reads as a card inside a card, and the column is narrow
enough that a plate's padding wrapped "Restricted view" onto two lines. **The
glyph bands belong to the seat card (§3.8.9), not here** — the card used to
carry markers AND rows, and since both drew the same icon set the line read as
the same fact printed twice.

**Held cards** wear a wash of the accent at 7 per cent over the surface and a
border blended 45 per cent toward the accent, and the dot becomes a **lock** —
a lock is not a colour. A held card is never swipeable; its × stays, because
hiding it left a buyer back from checkout with a washed card and no way to
change anything, and the runtime's refusal is drawn as a state with a way out.

**Motion.** A card arrives on `motion.duration.enter` with a small rise and
scale-up, staggered across a set that lands together. A card whose removal has
been asked for fades to `opacity.removing` in the same frame as the press and
its × goes inert, against a decision that has already been taken; the mark is
dropped by the snapshot that no longer carries the line, or restored if the
mutation fails. A swipe on a card settles from the finger, committing past
`motion.physics.swipeCommitFraction` of its width or above
`motion.physics.swipeFlingVelocity`; the red plate under it is clipped to the
card's own corner.

**Haptics.** `haptics.ticketRemoved`.

**Nothing is said.** A removal is silent: no toast, and no Undo. The card has
gone from the tray, the total has moved and the checkout action has recounted,
so a sentence naming what the buyer just did adds nothing. A removal that
*fails* still speaks, through the inline action error.

**Commands.** `picker.deselect { label }`, the runtime's `cart-line-remove-v1`
for a line the selection cannot name, and `picker.frameSeat` for the card tap.

#### 3.10.3 Foot

Order: the lapse notice if there is one, the hold-ending cue, the inline action
error, the total line, the checkout button, then the attribution. The whole
foot is drawn in both sheet states, and it carries a hairline above it **only
while the sheet is open with tickets in it** (§3.9).

**Checkout button** — `SeatLayerBookButton`, slot `primaryButtonStyle` (the
sheet merges its own `continueButtonStyle` over it). Full width inside the
foot's gutters, height `size.checkoutButtonHeight`, corner `radius.button`,
`type.bookButton`, accent ground with `color.*.onAccent`. It carries **its own
label only**; the total is on the line above it. Disabled is a designed state,
not a Material grey: a surface-toned ground, muted ink, an inset hairline.

Label ladder, in priority order:

1. sales closed → disabled, `strings.salesClosedCta`
2. a general-admission prompt is unanswered → disabled,
   `strings.confirmYourTickets`
3. a seat card is open → the button **keeps the label the ladder would give it
   with no card up** and is simply disabled, and it offers no finder either.
   The card is the question; the footer says nothing about it, so the buyer's
   cart still reads as their cart underneath.
4. securing → disabled, spinner, `strings.securingSeats`
5. opening checkout → disabled, spinner, `strings.openingCheckout`
6. the selection breaks the event's rules → disabled,
   `strings.chooseMore` / `strings.removeTickets…` / `strings.adjustSelection`
7. **an empty cart on a surface that may offer the finder** → ENABLED,
   `strings.findBestSeatsCta`, and a press opens the sheet on the best-seats
   form. The wide foot's `Select seats` is a disabled label telling a buyer to
   use the map beside it, which is fair on a width that shows both at once; on
   a phone the foot IS the sheet, so a full-width disabled button would be the
   largest thing on screen saying no. Gated exactly as the tray's own card is —
   best-available enabled, not read-only, no existing hold — because a door
   into an empty room is worse than none.
8. a hold exists and more are pending → `strings.secureMoreAndCheckout`
9. a hold exists, nothing pending → `strings.continueToCheckout`
10. tickets, no hold → `strings.holdAndCheckout`
11. nothing picked, and no finder to offer → disabled, `strings.selectSeats`

**Commands.** `picker.checkout`, then `picker.rejectHandoff`
(`checkout-handoff-reject-v1`) if the host refuses the handoff, so a rejected
hold is never stranded. Checkout phases run idle → holding → checkout and each
phase ends where the work ends, never on a timer.

**Attribution** — `SeatLayerPickerAttribution`, height
`size.attributionHeight`, `type.attribution`, centred at the **foot of the
sheet**, where a phone's rounded corner cannot clip it. It reads the map
chrome's palette, not the picker's, so its words survive on the dark scene
sheet. Copy `strings.poweredBy`. Drawn when `branding.attributionRequired`; it is
**absent entirely on a white-labelled account**, hidden server-side with no
host switch.

The total is said ONCE, on the foot's own line, and there is no separate
"secured" strip on a phone: the header pill is the clock, and each held card's
× releases one seat.

### 3.11 Best-seats form

**Name** `SeatLayerBestSeatsForm` · **file** `lib/src/picker/picker_best_seats.dart`

Shown in the sheet while the cart is empty, and hidden the moment the cart has
anything — once a phone cart has tickets it stays a cart. It stands on one
plate: padding 8, corner 11, a hairline of the accent at `.34` over
`color.*.divider`, and a diagonal wash of the accent from `.05` to `.11` over
`color.*.surface`.

**It exists only inside the OPEN sheet.** The collapsed sheet is the foot
alone, so the empty cart's way in is the footer's own call to action
(`strings.findBestSeatsCta`, §3.10.3) — pressing it opens the sheet on this
form. Two entry points for one action is the duplication the web round removed.

**Row order** — one decision per row, with the semantic order unchanged so
focus order still follows the page:

0. the title line, with its ⓘ and the explanation it opens
1. ticket type, full width
2. venue zone, full width, **only where the venue has zones**
3. the quantity stepper, leading, on the last row
4. the action, trailing, on the same row

**There is no premium quick-pick chip.** The web card offers one; the bridge
snapshot carries no chart-level premium fact for the native side to gate it on,
and a chip that cannot know whether the chart has premium seats would be
guessing. Listed as a known gap in §4.9 — do not fake it.

Names take full lines; the narrow fixed-width stepper shares the last one. Rows
are 6 apart, and so are the stepper and the action beside it.

**Title line** — a `✦` in the accent at 14 with a 6 pt gap, then
`strings.findSeatsTogether` at 12.5 / w800 in `color.*.text`, `maxLines: 1`
with an ellipsis, then an **ⓘ button immediately after the title** — not at the
far end of the row, because it explains the words beside it. Pressing it
toggles a one-line explanation, `strings.closestGroupChosenInstantly`, **under
the title** at 12 in `color.*.mutedText`, revealed with an animated size change
over `motion.duration.crossfade`. The glyph is 15, muted when shut and the
accent when open; its accessible name is `strings.aboutBestSeats` and it
carries its expanded state. SHORT title on purpose: the long form ("Find the
closest seats together") is a sentence, and in the 300-point column it wrapped
onto a second line above a card whose whole point is that it is compact. The
words are the only part of the line that may shrink, and they shrink by
truncating. The card still needs the line even though the action below says
what pressing it does — while the action is busy it says
`strings.findingBestSeats` instead, and nothing else on the card names the
feature.

**Selects** — height `size.bestSeatsSelectHeight`, corner `radius.control`,
ground `color.*.surface`, hairline `color.*.divider`, label at
`type.bestSeatsSelect`, with a drawn chevron. Copy `strings.anyTicketType`,
`strings.anyVenueZone`, and the chart's own names. Where there is exactly one
category the select is omitted and takes no row.

**Stepper** — width `size.bestSeatsStepperWidth`, height
`size.minimumHitTarget`, corner `radius.control`, hairline, with a tabular value
between two filled 34 pt keys at a 7 pt corner, their glyphs 14 at w800. Copy
`strings.fewerTickets`, `strings.moreTickets`.

**Action** — height `size.minimumHitTarget`, corner `radius.control`, accent
ground, `type.bestSeatsGo`, with a leading `✦` flourish at 13. Copy
`strings.findBestSeatsOne` / `strings.findBestSeatsOther`; busy is
`strings.findingBestSeats` with a spinner, keeping the accent at slight
transparency rather than going grey. Disabled uses the same designed disabled
language as the checkout button.

**Replace-confirm face.** Where the buyer already has manual picks, the form
asks before replacing them, as an alert with a two-button row. The manual
tickets are removed **only after** a new group is secured.

**Capability.** `capabilities.bestAvailable`.

**Motion.** Each returned seat pops in on `motion.duration.pop`, staggered by
`motion.duration.stagger`.

### 3.12 Toasts

**Names** `SeatLayerPickerToast`, `…ToastQueue`, `…ToastCard`, `…ToastLayer` ·
**file** `lib/src/picker/picker_toast.dart`

**Anatomy.** One centred card in the bottom-centre region, lifted by the dock
when it is up and moved above the seat card when one is open. Ground
`color.*.surface`, hairline, corner 16 pt (a fixed recipe), ink `color.*.text`,
type at `type.peekSummary` weight, and it **wraps** — a toast is a sentence, not
a chip. With an action it becomes a row: the sentence, then a pill at
`radius.pill` on the accent.

**Tones** change only the border: neutral takes `color.*.divider`, error takes
`color.*.error` and shakes once, warning takes the accent, success takes a green.

**Motion.** Rises on `motion.duration.toast` on `motion.curve.easeEnter`, dwells
for `motion.durationOutsideBudget.toastDwell`, then leaves and resets to
neutral. One queue: a second toast replaces the first rather than stacking.

**Accessibility.** A polite live region. A toast is never the only place a
consequence is stated — anything that outlives 4 seconds also has a persistent
form (see 3.13.7).

### 3.13 Buyer-facing states

**Booked overlay is a host option.** `options.showBookedOverlay` (default
true = web) — a host with its own confirmation screen sets it false; the sale
is still known (`bookedHandoff`, `onBooked`), only the telling is the host's.
Ports: same option, same default.

**Removing a line from a held cart is slow by nature, and the sheet no longer
waits with the buyer.** `picker.removeCartLine` re-holds the rest of the cart on
the server; on the pilot the sheet sat busy (CTA greyed, row unchanged) for
~1.7 s and then swapped `103 · A · 9–10` to `103 · A · 10` in one frame. The
latency is the server's. Everything else was ours, and is now built:

- **The press is answered by the row.** In the same frame as the × (or a
  committed swipe), the line is marked *removing*: it fades to
  `opacity.removing` over `motion.duration.crossfade`, its × goes inert, and
  it can no longer be swiped. The mark is dropped by the first snapshot that
  no longer carries the line; a mutation that fails restores the row and the
  failure is stated by the inline action error as before.
- **The answer is the row, not a sentence.** `haptics.ticketRemoved` fires
  before the command is sent, so the gesture is confirmed under the finger
  rather than whenever the server finishes. Nothing else is said; a removal
  that then *fails* restores the row and states itself through the inline
  action error.
- **The removal does not grey the sheet.** `busyAction` for this one command is
  `removingCartLine`, and it is the only busy action that does not block
  checkout: the call to action stays live and says what it always says.
  Pressing it during a removal is safe because inventory mutations are
  serialised — `picker.continue` is sent *after* `picker.removeCartLine`,
  against the cart the buyer can see. Every other control that goes down on a
  busy state still does.
- **Changed cells cross-fade, the list does not.** When a line's own words
  change between snapshots — `9–10` → `10`, `2 × €25` → nothing, `€50` → `€25`
  — only the cell that changed swaps, over `motion.duration.crossfade`, keyed
  on the words themselves. The sheet's count (`2 tickets` → `1 ticket`) swaps
  the same way, alongside the swell it already had. Under reduced motion there
  is no cross-fade at all: the new words are simply there.

Ports: same four rules. `opacity.removing` and the `removingCartLine` busy
action are token and contract, not Dart.

**Card after a hold.** A hold present when the picker starts, or created by
a best-available pick, adopts its seats as answered on arrival; a card that is
already open keeps its question. Every seat tapped after that is asked about
— a live hold never silences the card (it did until 0.6.2, which turned every
second tap into a silent add). Ports: adopt-on-arrival + ask-every-tap.

**Best-available landing.** Since runtime 0.77.1 `picker.bestAvailable` lands
the camera itself: once the hold is made the map frames the found seats with
their neighbours, falling back to the section centroid, and it does so under
reduced motion too. **Native calls nothing after the command.** The interim
that framed the section of the newly added seats is gone; the web's own pops
and chip flights stay web-only.

**Seat removed.** No toast. See §3.10.2's removal rules: the tray is the answer.

**Accessibility filter flight.** Since runtime 0.77.1
`picker.setAccessibilityFilter` takes the web menu's own focus path: turning a
filter ON flies to the matching spaces, or holds the sections rung with the
spread hint where they span the venue; turning it OFF keeps the camera.
Price-chip counts and the minimap follow whichever door the filter came
through. **Native calls nothing after the command** — the interim
`picker.overview` is gone. `picker.focusAccessibilityFilter` re-runs the same
flight without toggling, for a buyer who has panned away from it.

The same is true of the other camera moves under a filter, engine-side and
with nothing to call: a section tap, `picker.focusSection` and the dock's own
arrows frame a section's matching spaces at pickable depth when it holds any,
and frame it as before when it does not.

Capability `accessibility-focus-v1` gates all three. An older runtime applies
the filter and leaves the camera where it was, so every control below is
withheld from it rather than degraded.

**Booked ("You're all set").** Never shown on the hand-off: a buyer on the way
to pay has not paid. It appears only when the handed-off hold settles to
booked — the hold vanishes from the snapshot with no `hold.expired` announced
first — which is the web picker's `detectBooked` rule. The expiry is read off
the bridge on the same hop as the snapshot that follows it, so the order on the
wire (expiry, then snapshot) is the order the decision sees. A host hears the
same moment once through `onBooked(handoff)`. Port note: RN / iOS / Android
must gate their success screen the same way; showing it on hand-off was the
bug a pilot host hit when a buyer backed out of checkout without paying.

Every state below is a **designed state**, not a set of disabled controls.

#### 3.13.1 Loading

`SeatLayerPickerLoadingView` — `lib/src/picker/picker_status_views.dart`.
Fills the map area on `color.*.background`. It draws the **venue silhouette**,
not a spinner: three concentric seating shells around a stage, in the accent at
low opacity, breathing slowly under a diagonal sweep, with a thin indeterminate
progress strip at the top of the map. The sentence
`strings.loading` is announced but not drawn.

Reveal sequence, once the runtime has framed the map: the loading surface fades
out over `motion.duration.exit`-scale time, a sweep passes over the shells for
`motion.durationOutsideBudget.shellSweep`, the rail chips stagger in, and the
cart sheet rises last; the whole arrival is done by
`motion.durationOutsideBudget.revealDelay`. Under reduced motion the loading
surface is simply removed — no fade, no sweep, no stagger.

The load itself: adopt a prewarmed runtime page only **after** the picker has
been laid out, so the chart's first paint is at its final size; hold the loading
surface until the runtime reports that it has framed the map inside the native
chrome, with a short backstop for a runtime that never answers.
`chart-load-trace-v1` carries what the load cost, for the host's own analytics;
the SDK logs and sends nothing.

#### 3.13.2 Error

`SeatLayerPickerErrorView`. Replaces the loading content, on the same ground:
a title `strings.mapDidNotLoad`, a line `strings.checkConnection`, and an action
`strings.retry` at `radius.button` on the accent. Retry fully remounts the
picker rather than patching a half-built one.

#### 3.13.3 Access

`SeatLayerPickerAccessPanel` — `lib/src/picker/picker_states.dart`. A veiled
overlay over the whole picker: the ground at high opacity over a blur, a centred
card at `radius.base`-scale corner on `color.*.surface` with a hairline and a
deep shadow, a circular icon badge in an accent tint, a title, a body and an
action pill.

**Every reason has exactly one action.** Two of these used to have none, which
left the buyer behind a panel with nothing to press and only the header's close
to get out of it. A recovery that might not work is still a way forward; a dead
end is not.

| Reason | Title | Body | Action |
| --- | --- | --- | --- |
| paused | `strings.accessPausedTitle` | `strings.accessPausedCopy` | `strings.retry` |
| revoked | `strings.accessRevokedTitle` | `strings.accessRevokedCopy` | `strings.accessRefresh` |
| expired (no token, provider failed) | `strings.accessExpiredTitle` | `strings.accessExpiredCopy` | `strings.accessRefresh` |
| unverified (default) | `strings.accessUnverifiedTitle` | `strings.accessUnverifiedCopy` | `strings.accessRefresh` |

`strings.accessRefresh` is its own word, not `strings.retry`: that one is the
paused screen's "Try again", and one string cannot carry two verbs. Refresh is
worth offering even to a revoked link — on the public-key path a fresh
bootstrap IS the recovery, on a grant path it re-resolves the grant, and a link
that is still revoked simply shows this panel again, which is the honest
answer.

**What the button does — in place first, always.** `refreshAccess()`
re-bootstraps the session and re-reads the chart through the live runtime, so
the map never goes away and the buyer keeps their camera and their picks. Only
if that fails does the picker fall back to `retry()`, which destroys the
runtime and mounts a fresh one — and **only when no host is listening**. A host
that passed `onAccessUnavailable` has been told already and may be running its
own recovery; remounting under it would destroy that.

The paused screen keeps its plain `retry()`: nothing is wrong with the session
there, the organizer has simply stopped selling.

All four are set as plain text and never parsed as markup. While retrying, the
icon spins and the action is disabled. Under reduced motion the card is simply
present, with no entrance.

A polite live region.

#### 3.13.4 Sales closed

Said in five places, each a different job:

- header — `strings.salesClosedPill`, neutral
- cart region — the tray statement below, drawn only in the open sheet
- checkout button — disabled, `strings.salesClosedCta`
- toast on any attempted action — `strings.salesClosedToast`, warning tone
- **tray statement** (`SeatLayerPickerSalesClosedStatement`) — a card on a
  neutral text-tinted ground with `strings.salesClosed`,
  `strings.salesClosedCopy`, and the event's own date line

Neutral throughout, never the accent.

#### 3.13.5 Sold out

`SeatLayerPickerSoldOutOverlay`. A veil over the map on the ground at high
opacity over a blur, centred: an uppercase letter-spaced eyebrow — the brand or
event name, falling back to `strings.soldOutEyebrow` — then a large
`strings.soldOutTitle`, then `strings.soldOutCopy` in `color.*.mutedText`.

The predicate: **every seated category's live free count is zero, there is at
least one seated category, and the chart has no general-admission areas.** It
clears live. Informational only — there is no waitlist.

#### 3.13.6 Hold pill and countdown

The clock is `m:ss`, floored at zero, ticking twice a second so a second never
appears to skip. It is the picker's only clock. `is-expiring` begins at
one minute remaining. The countdown **stops the moment a read finds the hold
gone**, even when the snapshot in hand still describes a live one: it was read
before the reconciliation that ended it, and a clock still running over released
seats is the one thing that can leave a buyer reassured right up to a failed
checkout.

#### 3.13.7 Hold expired, and hold lapsed

A plain expiry is `strings.holdExpired`, warning tone — but it is deferred by
one tick, because a richer telling may cancel it.

When a refresh can explain the lapse (`availability-refresh-v1`), the telling is
counted on what the offer would re-take:

| Shape | Copy | Tone | Action |
| --- | --- | --- | --- |
| all recoverable | `strings.holdLapsedStillFreeOne` / `…Other` | warning | `strings.reselectSeatsOne` / `…Other` |
| some taken | `strings.holdLapsedSomeTakenOne` / `…Other`, counted on how many are **gone** | warning | same |
| none recoverable | `strings.holdLapsedAllTakenOne` / `…Other` | error | — |

Because a toast is gone in four seconds, the same sentence is repeated as a
**persistent line in the cart sheet** on a warning-tinted ground with the same
action. The recoverable set comes from the refresh outcome, never from local
memory. A seat sold between the offer and the tap is a real race and is reported
the way every failed hold is: the seats stay selected and unheld, never claimed.
`strings.holdLapsedTitle` / `strings.holdLapsedBody` /
`strings.seatsNotRecovered` cover the fuller telling.

`haptics.holdExpired` fires from the runtime's own expiry signal, never from a
snapshot: a snapshot only shows a hold going inactive, and a buyer releasing
their seats deliberately must not feel like a loss.

**Divergence from the web picker (decided 2026-09-04).**

Web 0.79.0 answers an expiry with a BLOCKING interrupt card over the live map:
a veil at 35 %, a card at 24 px padding (20 px on a narrow layout), a title at
20 px / 18 px weight 700, and one full-width action — `Choose seats again` —
which drops the dead selection, re-reads availability and returns the buyer to
the venue. It closes any other card or dialog first, and a press on the veil or
Escape does the same thing as the button rather than dismissing into the broken
state. Catalogue entry: `components.md` › InterruptCard.

Flutter deliberately does not, and keeps the non-blocking model described
above. `SeatLayerHoldLapseNotice` (`lib/src/picker/picker_hold_lapse.dart`)
says it on two surfaces and blocks neither: the persistent line in the cart
sheet, where the buyer's tickets were and where they will look for them, and a
toast for a buyer who is looking at the map instead.
`reselectLapsedSeats()` (`lib/src/picker/seat_layer_picker_controller.dart`) is
the `Choose seats again` analogue — it re-selects what the refresh still calls
free and re-holds it, and reports the race honestly when it fails.
`SeatLayerPickerOptions(announceHoldLapse: false)` hands the whole moment to a
host that would rather own it, and `onHoldExpired` fires either way.

**Why the divergence stands.** A dialog was considered and rejected. The buyer
has usually just come back from somewhere else, and a modal is a second thing
to dismiss before they can see whether their seats are still there — over a map
that is still entirely usable, and a lapse that is often fully recoverable. A
host may also be running its own recovery for this moment, which a blocking
surface the SDK draws on top would step on.

**Porting SDKs follow Flutter here, not web.** Swift, Kotlin and React Native
build the cart-sheet line, the toast and the reselect action, and no interrupt
card.

#### 3.13.8 Need more time

`SeatLayerPickerExtendHoldPrompt`. A card in the bottom-centre region above the
toast: `strings.seatsHeldForNeedMoreTime` with a live `m:ss`, a pill
`strings.addMinutes(5)` → `strings.addingEllipsis`, and a `strings.close`
dismiss at `size.minimumHitTarget`. Due while the hold has under a minute left
and the booked overlay is not up — and mounted only where the layout asks for
it, which by default is the wide layout alone.

**Decision (2026-09-04): not shown on the phone by default.** The phone picker
gives the buyer ONE timer — the header's hold countdown
(`SeatLayerPickerHoldCountdown`), which fills with the accent, breathes its
status light, and counts the last minute out second by second to a screen
reader. A card that arrives over the map inside that last minute is a second
decision at the worst moment, and a countdown that can always be pushed back is
not a deadline. If the hold does lapse, the non-blocking recovery of §3.13.7 —
`SeatLayerHoldLapseNotice` and `reselectLapsedSeats` — offers the seats back.

The card is not deleted: it stays a public component and a **host opt-in**, via
`SeatLayerPickerChromeOptions(showExtendHoldPrompt: true)`. The option is
layout-resolved like the other nullable chrome controls — `null` means auto,
which is **off on the phone and on in the wide layout**, where the card sits
beside the clock rather than over the map.

**Porting SDKs follow Flutter here.** Swift, Kotlin and React Native default the
prompt off on phone-width layouts and expose the same host opt-in. The web
narrow layout still shows the floating pill by default; that is the current
divergence, and it is the web side that is out of step.

**Where a host opts it back in, it stays a floating pill.** Native is always
narrow, and docking it beside the hold clock at 390 px collapses the header's
own information from 198 px to 64 px against a venue name that needs 201 px.
The pill is the only shape that fits.

**One named step.** One tap asks for a fixed five minutes, and the button
prints the number. The offer used to send the host's whole configured hold
window behind a button reading only "Add time" — neither the copy nor the buyer
knew the amount, and "Add time" beside a countdown reads like an invitation to
choose one. The server keeps the last word: an organizer's configured TTL wins
over a requested one, the new expiry is `max(current, now + ttl)` so an extend
can never shorten a hold, and the server caps how many extensions one hold may
have. What the buyer reads afterwards is the countdown, not this step.

**One per hold, three endings.**

| Outcome | What happens |
|---|---|
| Granted | `strings.moreTimeAdded`, success tone; the control retires |
| Refused | **Silence**; the control retires |
| Transport failure | `strings.couldNotAddMoreTime`, warning tone; the control stays |

A buyer who can keep asking has been handed a way to sit on inventory by reflex
rather than by decision, and a countdown that can always be pushed back is not
a deadline — so the control does not come back for this hold. A refusal is not
a fault: a hold resumed from the host carries extensions this picker never
offered, and there is no sentence the buyer needs about it. A transport failure
decided nothing, so it alone is offered again.

Dismissing spends nothing. It retires the control for this hold the same way,
because an offer with no refusal is a thing in the way.

Retirement is cleared when there is no hold — the only honest boundary, since
snapshots deliberately never carry the hold id. A fresh selection after a lapse
therefore gets its own extension rather than inheriting the last one's.

#### 3.13.9 Seat taken

`strings.seatJustTakenByAnother`, error tone, for one seat;
`strings.seatsJustTaken` for a bulk conflict. While a seat card is up this toast
is the *reply to the tap*: its region comes forward at full opacity above the
card, and stays press-through.

#### 3.13.10 Booked overlay

`SeatLayerPickerBookedOverlay`. Covers the whole picker on `color.*.background`:
a round accent badge that pops and draws a check; `strings.allSetTitle`; a
subtitle of the ticket count plus `strings.confirmedAndOnWay`; a scrolling list
of the seats as pills; and a way back, `strings.backToMap`, at `radius.pill`,
focused on the next frame. The copy rises in sequence behind the badge.

#### 3.13.11 General-admission prompt

`SeatLayerPickerGeneralAdmissionPrompt` — `lib/src/picker/picker_prompts.dart`.
On a phone this is a **bottom sheet** (on wide, an anchored popover), one focus
trap either way. A scrim, then a sheet with rounded top corners on
`color.*.surface`: an uppercase eyebrow, a large title, a subtitle, a close
control, then one row per area with a stepper, a total row, an optional hint,
and a full-width action pair whose bottom padding absorbs the safe inset.
Copy `strings.generalAdmission`, `strings.placesAvailable`,
`strings.addTickets`, `strings.continueWithTotal`, `strings.removeWord`.
Steppers use `strings.fewerTickets` / `strings.moreTickets`. Springs in on
`motion.duration.sheet` with `motion.curve.spring`; still under reduced motion.

#### 3.13.12 Table prompt

`SeatLayerPickerTablePrompt`. The same bottom-sheet shape. Eyebrow, title,
`strings.chooseGuestsCopy`, a summary grid, `strings.numberOfGuests` over a wide
stepper, a range caption `strings.chooseMinMaxGuests`, and the action pair
`strings.cancel` with `strings.selectTable` / `strings.updateTable`. Labels
`strings.fewerGuests` / `strings.moreGuests`. Accessible name for the whole
sheet: `strings.chooseTableGuests`. Capability `table-quantity-v1`.

#### 3.13.13 Seats already in checkout (N1, owner decision 2026-09-05: option B)

The hold belongs to the host from the moment it is handed off, and the runtime
refuses to grow or shrink it from the picker: `hold_owned_by_host` (a cart
control), `hold_selection_mismatch` (Continue with a selection the hold does
not cover), `hold_already_active` (a second hold). None of these is a failure
the buyer can read as one, so the inline action bar says the STATE instead —
`strings.holdInCheckoutTitle` / `strings.holdInCheckoutBody` with one action,
`strings.releaseAndChangeSeats`, which rejects the handoff
(`picker.rejectHandoff { holdId }`) so the seats go back on sale and the buyer
picks again; the notice clears when the release lands. With no handoff to
give back (`hold_already_active` on a picker-owned hold) the bar says
`strings.holdAlreadyHeldTitle` / `strings.holdAlreadyHeldBody` and offers
nothing but dismiss. The runtime's own sentence is never shown. Option A — the
picker extending a host-owned hold — was not taken: it would change who owns
the hold after handoff, which is checkout's contract, not the picker's.

Dart file: `lib/src/picker/picker_errors.dart`.

### 3.14 3D chrome

**Name** `SeatLayerVenue3D` · **slot** `pillStyle` · **file**
`lib/src/picker/picker_venue_3d.dart`

**Glass recipe.** All 3D chrome wears one **dark glass** whatever the resolved
mode is, because it floats over a rendered venue:
ground `color.dark.immersiveGlass`, hairline
`color.dark.immersiveGlassBorder`, ink `color.dark.immersiveGlassInk`, blur
`size.immersiveGlassBlur`. Captions use the deeper
`color.dark.immersiveCaption`, `…Border`, `…Ink` with
`size.immersiveCaptionBlur`. A shared helper draws it once so no surface
re-mixes it.

**Back pill** — top-left, height `size.immersiveBackPillHeight`, `radius.pill`,
label at `size.immersiveBackFontSize`, chevron at `size.immersiveBackIconSize`.
Copy `strings.backToVenue`. Drawn **only while the buyer is sitting in an exact
seat** — it is the way out of a seat, not out of the scene.

**Badge stacking.** The test chip owns the same corner. It steps below the back
pill by the pill's height plus `size.mapAnchorGap`, and only while the pill is
actually drawn.

**Deck** — the bottom row: a caption chip naming the seat, then previous seat,
`strings.openVenue360`, next seat, and recentre. Chips are
`size.immersiveNavChipHeight` tall with `size.immersiveNavChipPaddingX` padding
and `size.immersiveNavChipFontSize` labels, at `radius.pill`; the close control
is `size.immersiveNavCloseSize`. Every one keeps a `size.minimumHitTarget`
reach. Copy `strings.previousSeat`, `strings.nextSeat`, `strings.recentre`.
The stepper is disabled in venue mode.

**Caption** — `size.immersiveCaptionFontSize` on the caption glass at
`radius.chip`, naming the seat.

**Nav mode** — `SeatLayerPicker3DNavigationModeButton`: a two-state control for
orbit versus pan, `strings.orbitMode` / `strings.panMode`, with the drag hints
`strings.rotateVenue` / `strings.moveVenue`.

**Motion.** The chrome settles on `motion.duration.immersive`. The recentre
control arrives with a single spin on `motion.curve.spring`.

**Capabilities.** `venue-3d-v1` for the scene, `venue-3d-controls-v1` for the
deck. Both, plus `capabilities.venue3d`, must hold.

**Commands.** `picker.showSeatIn3D`, `picker.openVenue360`,
`picker.setBuyerView`, `picker.recentre3D`.

### 3.15 Seat-view chrome

**Name** `SeatLayerSeatViewChrome` · **slot** `seatViewChromeStyle` · **file**
`lib/src/picker/picker_seat_view_chrome.dart`

The native caption strip drawn over the 2D view-from-seat panorama: the seat's
identity, `strings.viewFromYourSeat`, a disclosure badge and a drag hint, on the
caption glass at `radius.chip`.

**Capability.** `native-seat-view-chrome-v1`. A runtime advertising it is asked
at `init` to suppress its own words. Consequently, a host that hides this strip
**owns the disclosure itself** — turning the native strip off does not give the
words back to the runtime.

---

## 4. Porting checklist

Platform-neutral concerns every SDK must solve the same way.

**4.1 Tokens generator.** `tokens.json` is the input, not a reference. Generate
a typed constant set per platform at build time and read every number through
it. Never transcribe a value into a view. The generated file is checked in and
regenerated, never hand-edited. (Flutter: `lib/src/picker/picker_tokens.g.dart`.)

**4.2 Style slots.** Expose the same named slots so one element can be restyled
without replacing the widget that draws it: primary action, secondary action,
the sheet's own button, icon buttons, chip shape, legend chip, floor strip, seat-view
chrome, dock bar, confirm card, sheet, header, pill, and the map scrim colour.
Each slot is *partial* — an unset field keeps the spec's value. Each element that
owns a slot also takes a per-instance override that wins over the theme.

**4.2a Typography is inherited on exactly one platform.** Every text style in
the picker passes the theme's `fontFamily`, which is **null** by default. On
Flutter a null family merges with the `DefaultTextStyle` above the picker, so
the picker silently takes the host app's typography. A platform with no such
inheritance — React Native, SwiftUI, Compose — draws its own system face
instead, and the same screen measures differently. Neither is a bug; the
contract is that a host which wants its own face passes `fontFamily` on the
theme, explicitly, on every platform.

**4.2b A painted border and a boxed border are not the same border.** A Flutter
`DecoratedBox` (or a `Material(shape:)`) paints its line without taking room
from the child, so every padding this document states beside a hairline is
measured from the **outer** edge. A platform whose border boxes its content
must subtract the line from that padding — the test chip's 8 and 10 (§3.4) and
the legend chip's 7 and 9 (§3.2) become 7/9 and 6/8 — or the element comes out
two points wide with its word a point late. The exception is stated where it
applies: where the spec says the bed is measured **inside** the line, as the
Map/3D track's 3 pt bed is (§3.3), the line is already part of the measured
height and nothing is subtracted.

**4.3 String overrides.** Every buyer-facing string in `strings` is overridable,
individually, without replacing the set. Counted strings need one/other forms
(and the plural categories the platform's own locale rules require). Access-need
names are overridable by need. The picker ships localized defaults; a host
override always wins.

**4.4 Reduced motion.** One accessor decides. Read the platform's own
"reduce motion" setting and route every duration through it, so a surface cannot
forget. Motion with no reduced form is skipped, and anything sequenced behind it
must run immediately instead.

**4.5 Haptics gate.** One host switch turns the whole vocabulary off, and the
platform's own setting turns it off again. Firing is best-effort and failure is
swallowed at the call site of the platform channel, not at the caller — these
calls fail asynchronously, and nothing that depends on the buyer's seats may
fail because a phone declined to buzz. Decide *which* cue in a pure policy
object, separate from the code that fires one, and honour the seeding rule: the
first snapshot of a session fires nothing.

**4.6 Safe areas.** One surface owns the bottom inset at any moment (§2.5).
Height ceilings add the inset rather than eating into content. The top inset
belongs to the header.

**4.6a One touch, one surface.** The map is a platform view on every mobile
platform, and the native chrome drawn over it is not. Hold §2.4's three rules on
the port: the map takes part in gesture resolution for every touch inside its
band, it claims eagerly except where the press landed on chrome, and chrome over
the map is always a control that competes for the touch. Verify it on a device —
a tap on a corner disc must step the camera and select nothing.

**4.7 Prewarm, adopt-after-layout, reveal-after-framing.** Three separate
things, in this order:

1. *Prewarm* — a host may start a runtime page ahead of time; it lives on a
   short TTL and is discarded under memory pressure.
2. *Adopt after layout* — claim the warm page only once the picker has a size.
   Adopting before layout answers the runtime's hello with an init at zero size,
   so the chart paints small and then re-fits in front of the buyer.
3. *Reveal after framing* — keep the loading surface up until the runtime
   reports it has framed the map inside the native chrome, with a short backstop
   for a runtime that never answers, then fade.

A host may also name the event before the runtime does, so the header does not
swap its title a second after opening.

**4.8 Hold ownership.** Two owners, and they behave differently:

- **picker-owned** — the native chrome shows the countdown pill, may extend it,
  and releases seats when a line is removed.
- **host-owned** — a hold handed in by the host is verified with the server and
  always treated as host-owned. Native controls never release or mutate it —
  no extend, no release from a cart row — but the header's countdown pill is
  still drawn (§3.1), because the buyer's time is running whoever holds it.

The hold id itself is delivered **only** at the checkout handoff
(`checkout-handoff-v1`); ordinary snapshots never expose that booking
capability. If the host refuses the handoff, reject it
(`checkout-handoff-reject-v1`) so the hold is not stranded. Keep booking
credentials off the device. Capability `hold-ownership-v1`, and
`hold-selection-v1` for holding a named selection.

### 4.9 Known gaps waiting on runtime fields

These are designed but cannot be built until the runtime reports the data. Do
not fake them, and do not design around their absence as if it were permanent.

- **Previous-price and sold-out chips on the price rail.** The web rail strikes
  a previous price and keeps a sold-out category's chip disabled. Natively this
  needs a per-category previous price and a trustworthy free count that
  distinguishes *zero* from *not reported*; today a zero is read as unknown.
- **Held versus pending counts for `Secure more`.** `strings.secureMoreAndCheckout`
  needs how many of the buyer's tickets are already held and how many are still
  pending. Until the split is reported the label falls back to
  `strings.continueToCheckout`.
- **The camera's fit pose, `map.atVenueFit`.** The two back-out discs
  (§3.5) dim from it, and the SDK reads it from the snapshot's `map` object as
  an optional boolean. A runtime that does not report it leaves the field
  absent, and the SDK falls back to `map.canZoomOut` rather than reading the
  absence as a pose. That fallback is honest but coarse: `canZoomOut` is
  computed from whether seats are the visible layer, so every camera a pinch
  leaves between the venue fit and the seats reads as "already home" and both
  discs dim with the venue still not framed. Absent must never be read as
  `false`, and the SDK must never infer a pose from scale, rung or what happens
  to be on screen — only a pose the engine reports is a pose.
- **A premium quick-pick chip on the best-seats card.** The web card offers one
  as a shortcut; the bridge snapshot carries **no chart-level premium fact**, so
  the native side cannot tell a chart with premium seats from one without, and a
  chip that offered premium on a chart that has none would be a door into an
  empty room. It needs a per-chart "this chart has premium seats" flag on the
  snapshot before it can be drawn. §3.11.
- **Shortening a cart line by fact.** The web cart drops the least useful part
  of a position line when the column runs out of room; the SDK has no measured
  ladder for it and **truncates with an ellipsis** instead, on both the name and
  the position line. That is honest but coarser than the web: it can lose the
  seat number rather than the venue phrase. §3.10.2.
- **3D section stops.** The immersive scene's caption can name a stop, but the
  list of stops a venue offers is not in the snapshot, so the deck cannot offer
  section-to-section travel.
- **Opening the confidence passport.** The teaser itself is built (§3.8.7), but
  the passport it names is a modal the RUNTIME owns and the bridge exposes no
  command to open it. Until one exists the teaser is a button only where a host
  has taken `onSeatConfidence` and can present the disclosure itself; with no
  host it is a static information row. A control that opens nothing is worse
  than a line of text.
- **Save to compare on the 3D seat card.** The web card's compare action reads
  and writes comparison state the runtime keeps for itself; the snapshot
  reports neither the saved set nor a command to change it, so the native card
  does not draw the action. It is not a styling gap: a compare button that
  cannot say what is already saved would be lying.
- **Per-seat accessibility nodes on the map.** The venue is drawn on a canvas
  inside a web view, and a canvas exposes nothing to VoiceOver or TalkBack. The
  map is therefore ONE named region with a hint that says where the controls
  that pick a seat are (§4.10), which is honest but is not the design: a buyer
  who cannot see the screen should be able to move seat by seat through a
  section. Building that needs two things from the runtime — `seat-screen-point-v1`
  for each seat's rectangle, so a native node can be placed over it, and a
  `picker.listSeatsInView` command that answers with the seats currently drawn,
  their identity, price and state. With both, the native side can mount a real
  accessibility node per seat over the web view and hand its activation back as
  an ordinary tap. Neither exists today, and nothing may pretend they do: a
  fabricated seat list read out over a map that does not match it is worse than
  a region that says it cannot be explored.

### 4.10 Accessibility

The picker is one screen with many surfaces, and most of what a buyer who is
not looking at it needs is a property of the WHOLE screen rather than of any
one component: the order the surfaces are read in, which of them speak without
being asked, how far the type may grow, where focus goes when a surface hands
the screen back. Those are specified here, once, and every port owes all of
them. Per-component notes stay with their component in §3.

**One reading order.** Assistive technology walks the picker in the buyer's own
order — header, price rail, map, the chrome standing on the map, the section
dock where a host opted into one, any prompt, notices, the cart — and never in
paint order. By default there is no dock on any width, so that rung is simply empty
and the map's chrome is read straight into the prompts and the cart. The phone
composition is a column with a stack in the middle, and a stack's paint order
puts the dock between two halves of the map's chrome and the seat card after
the toast that answers it. Every surface therefore declares its place:

| Order | Surface |
| --- | --- |
| 100 | header |
| 200 | price rail, and the Map/3D control that shares its band |
| 300 | the map, and everything stacked on it |
| 400 | map chrome: floor rail, test chip, corner controls, 3D and seat-view chrome |
| 500 | section dock (host opt-in; no width mounts one by default) |
| 600 | seat card, general-admission and table prompts |
| 700 | toasts, hold prompts, buyer-facing state overlays |
| 800 | cart sheet |

Two rules make this work in practice. Sibling surfaces are either **all**
ordered or none are — a group with some ordered members falls back to geometry
for the rest, which is how the map came to be read before the prices. And the
order is declared once, at the composition root: each component is also
mountable on its own, where there is no order to join. Dart file:
`lib/src/picker/picker_a11y.dart`.

**What each surface says.**

- *Map* — one node, named `strings.venueMap` with the venue's name, and a hint
  (`strings.venueMapHint`) that says the seats are picked with the controls
  around it. See the runtime gap in §4.9: naming a region that cannot be
  explored is the honest form of a canvas, not the intended design.
- *Live regions*, and only these three among the surfaces that are always up
  (the dock's is there only where a host opted into a dock; a default picker
  has two): the foot's total line, the section dock's name and seats-left, and the
  hold countdown. Each changes without the buyer touching it, and none says so
  any other way. A surface that ARRIVES unasked — a buyer-facing state, a hold
  notice, a toast, the best-seats count — is live as well; a notice that is part
  of a surface's own statement (the card's seat-note bands) is not.
- *The hold countdown is throttled.* `m:ss` read aloud is a time of day. The
  pill announces `strings.holdMinutesLeft` at each minute mark, and
  `strings.holdSecondsLeft` for every second of the last minute, where the
  buyer is owed the count. Unchanged text is not re-announced, so the throttle
  IS the policy — a live region fed a running clock speaks once a second for a
  quarter of an hour.
- *Toasts* are announced outright as well as being live regions: a toast that
  arrives and leaves inside four seconds, inside a cross-fade, is a window a
  live region cannot be relied on to catch.
- *The seat card is a dialog*: it names itself with the identity sentence
  (section, row, seat, category, price — §3.8.6), it scopes the route, and both
  answers are custom actions on it so a rotor can say yes or no without hunting
  for the buttons. While it is up, everything painted before it — the map, its
  chrome, the dock — is hidden from assistive technology, exactly as a modal
  route hides the page under it. Toasts and the cart stay audible: the toast is
  the answer to the press, and the cart is what the seat is being added to.
- *Every control is a button* with a name, and a state where it has one:
  pressed, selected, expanded, toggled.

**A surface that lists everything must still fit the screen.** Twelve
accommodation rows is the widest case the picker has, and it is the case a
wheelchair user meets. The accessibility sheet answers it with four rules, all
of them portable:

- it is **bounded to the screen** — `size.accessSheetMaxHeightFraction`,
  floored at `size.accessSheetMinHeight`, never taller than the screen — and
  the provision list **scrolls inside** that bound rather than the sheet
  growing;
- **every row is one line at one fixed height (50)**, short name truncating
  rather than wrapping, with any sentence it needs behind an ⓘ that opens
  *under* the row it belongs to;
- **the whole line toggles**, so the target is the words and not the
  20-point switch at the end of it, and each row's count keeps its own
  trailing column so the figures line up down the sheet;
- **a provision the venue has but has sold out of stays**, named, dimmed and
  reading `0` — removing it would claim the venue has no such seats, which is
  a different fact — and the two switches that recolour or filter the MAP sit
  under their own `strings.viewGroupTitle` heading so they do not read as two
  more provisions.

Unbounded and multi-line, the same list took the whole screen on a phone and
put its first two rows — the wheelchair and companion filters, the two rows the
sheet exists for — off the top of the web's popover entirely. The full anatomy
is in §3.5.

**Dynamic type.** Every string scales with the platform's text-size setting, to
a ceiling per surface (`type.scaleClamp` — rail, dock, peek and card at 1.3;
sheet rows and buyer-facing states at 1.6). A clamp is a statement about a
layout, not a preference: past it the surface clips, and a buyer who cannot read
a clipped price is worse off than one reading a slightly smaller one. Prompts
and dialogs have no clamp: they own the screen and they scroll.

Heights follow the type. Every fixed height around text — the rail band and its
chips, the dock bar, the collapsed cart, cart rows, the best-seats controls, the
card's action row — is `base × the surface's clamped scale`, so the box grows
with what is in it. Two consequences a port must not miss: the dock's reported
viewport band (§2.3) has to be the height it actually draws, or a focused
section lands under a dock that grew; and at the platform default of 1.0 every
one of these is unchanged, which is what keeps the goldens still.

**Focus.** The seat card's first focusable is `Add seat`, not `Cancel` — the
drawn order is the other way round, and the two orders are allowed to disagree.
Escape, and the platform's back gesture, give the seat back. When a decision
surface hands the screen back — accepted or cancelled — focus returns to the
map region rather than falling to the top of the tree, and a toast's action
returns focus to whatever had it before the press.

**Bold text.** The platform's `bold text` setting moves every weight in the
picker two steps up, clamped at 900. Flutter honours the setting in exactly one
widget, so the picker asks for itself; a port on a platform that applies it
automatically should let the platform do it rather than double it.

**Reduced motion** is §1.6 and `motion.reducedMotion`, and it is deliberately
not repeated here: one setting with two homes is a setting a surface will read
from the wrong one.

---

## 5. Capability index

| Capability | What it unlocks |
| --- | --- |
| `native-chrome-contract-v1` | the runtime suppresses its own buyer chrome; native owns header, rail, dock, tray, prompts |
| `viewport-insets-v1` | native reports the bands its chrome covers, so framing lands inside them |
| `seat-screen-point-v1` | the seat's screen point, so the glass behind the fixed seat card leaves that seat clear and follows it when reporting the sheet's band re-frames the map |
| `category-availability-v1` | live per-category free counts behind the rail's strike-through, the card's `N left` and the sold-out predicate |
| `access-needs-v1` | the chart's own access needs, in the runtime's order, with live free counts |
| `accessibility-focus-v1` | the filter's own camera flight, `picker.focusAccessibilityFilter`, and the accessible-section tour behind the sheet's count chip and the map's stepper |
| `section-access-counts-v1` | `sections[].accessibleFree`, behind the stepper's "N sections" and the dock's `· ♿ N` |
| `availability-refresh-v1` | a re-read of live availability on resume, and the outcome that explains a lapsed hold |
| `chart-load-trace-v1` | what the load cost, handed to the host's analytics |
| `native-seat-view-chrome-v1` | the runtime suppresses its seat-view words; native draws the caption strip |
| `floor-stack-v1` | the floor list and the active floor for the floor rail |
| `venue-3d-v1`, `venue-3d-controls-v1` | the immersive scene and its deck |
| `seat-view-v1` | the 2D view-from-seat panorama |
| `seat-view-thumbnail-v1` | the seat's authored photograph, its distance to the stage and its confidence disclosure (§3.8.7) |
| `table-quantity-v1` | the table guest-count prompt |
| `hold-ownership-v1`, `hold-selection-v1` | who owns a hold, and holding a named selection |
| `checkout-handoff-v1`, `checkout-handoff-reject-v1` | the hold crossing to the host, and giving it back |
| `cart-line-remove-v1` | removing a cart line the selection cannot name |

A capability the runtime does not advertise is a feature that is **not offered**,
never a feature that fails.

Three commands carry no capability string, because they change nothing a
snapshot reports; their presence in the `hello` **command table** is the whole
contract, and a runtime answering `unsupported_command` for one of them anyway
leaves nothing on screen for the buyer to read (runtime 0.80.2+):

| Command | What it unlocks |
| --- | --- |
| `picker.setSelectionFocus { seatId \| null }` | the seat a card is asking about is painted as the candidate — thick double ring, halo, neighbours paled (§3.8.2) |
| `picker.setBlockedRegions { rects }` | the runtime's own tap guard under native chrome over the map (§2.4) |
| `picker.frameSeat { seatId, fraction?, animate?, gestures? }` | the pan that keeps a tapped seat above the fixed sheet at unchanged zoom, and its restore (§3.8.2) |
