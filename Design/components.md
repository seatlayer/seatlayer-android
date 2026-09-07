# Picker component catalogue

The SeatLayer buyer picker, described so a Swift, Kotlin or React Native
engineer can build it without reading Dart. Names here are the Dart API's
names, deliberately: the four SDKs should agree on what things are called.

Every token reference (`size.dockBarHeight`, `color.dark.surface`,
`motion.duration.sheet`) resolves in [`tokens.json`](./tokens.json).

This file is the catalogue: what each component is called, what it reads, and
which slot restyles it. [`picker-spec.md`](./picker-spec.md) is the full
specification — every state, every animation, every string and every capability,
in buyer order. Where the two disagree, the spec is newer.

## Corner radius: which surfaces are pills

**Decision, 2026-08-28, restated after the web-parity round.** Actions carry
`radius.button`, which is what the web picker's own buttons measure. Material's
default stadium button is therefore wrong for this design, and every action that
is not listed as a pill below overrides it.

`radius.button` is its own role, not a fraction of `radius.base`: a brand that
rounds its cards to 20 pt must not thereby grow pill buttons, and the
organizer's branding radius is never inherited by it. `radius.control` is the
same value under its own name, for the form controls that are not actions —
the best-seats selects and stepper.

**True pills** (`radius.pill` / `radius.chip`, 999):

- the hold countdown pill and the sales-closed pill in the header, and the
  header's close ring
- the price-legend chips, including `All prices`
- the Map | 3D segmented control, track and segments
- the test-mode chip
- the floor rail's track and its floor chips
- the dock's `‹ ›` steps and its `‹ Venue` way out
- the seat card's photo-strip pills — `View from here` and `3D` — and the
  flight chip that leaves the card
- every piece of 3D chrome: the back pill, the deck's nav chips, the caption
- the seat-view caption strip
- a toast's action, the access panel's action, and the booked overlay's seat
  list and `Back to map`

**`radius.button`:**  the seat card's `Cancel` and `✓ Add seat`, its
`See it in 3D` action and its decision-row `3D` square, its 3D inspection
chips and its tier rows; the sheet's `Hold seats & checkout`; the
accessibility sheet's rows; `Try again`; the prompts'
action pairs.

**`radius.peekButton` (12):** nothing on the phone sheet any more — the
collapsed bar's two doors are gone, and the sheet's one button takes
`radius.button` with every other primary action. The token stays for hosts and
for the wide checkout bar.

**`radius.control`:** the best-seats selects, its stepper and its action.

Round icon controls — the map buttons, the 3D seat stepper — are circles and
are unaffected. A cart card rounds to `size.cartCardRadius`; the
seat card to `radius.confirmCard`; the sheet to `radius.sheet`.

Dart: `SeatLayerPickerThemeData(buttonRadius:)` moves every `radius.button`
action at once, and the per-element slots — `primaryButtonStyle`,
`secondaryButtonStyle`, `continueButtonStyle`, `iconButtonStyle`, `chipShape` —
and each widget's `style:` parameter still win over it.

## Shared model

Every component reads one **picker snapshot** and calls back into a
**controller**. No component fetches anything itself.

Snapshot fields the catalogue refers to:

| Field | Meaning |
| --- | --- |
| `event` | name, venue, currency, branding, test mode |
| `map.rung` | `venue` (overview) or `seats` (a section is focused) |
| `map.focusedSectionId` | the focused section, or null |
| `map.isVenue3D` | whether the immersive scene is up |
| `map.categoryFilter` | the active price-chip filter |
| `sections[]` | `id`, `label`, `displayLabel`, `color`, `seatsLeft`, `priceMin`, `priceMax`, `zoneId` |
| `categories[]` | `key`, `label`, `color`, `priceMin` |
| `selection[]` | `SelectedSeat`: `label`, `sectionLabel`, `rowLabel`, `seatNumber`, `price`, `currency`, `objectType`, `tiers`, `screenPoint` |
| `cart` | `lines[]`, `ticketCount`, `cartTotal` |
| `hold` | `holdId`, `expiresAt`, `owner` |
| `access` | why access is limited, where it is — paused, revoked, expired, unverified |
| `capabilities` | which optional features are available — venue 3D, seat view, best available |
| `branding.attributionRequired` | whether the attribution line must render |

Two rules bind every component:

1. **The venue map owns the venue; native owns the chrome.** Everything in
   this catalogue is drawn natively, on top of the map.
2. **A row name may already contain its section.** Print
   `rowLabel` with the section prefix removed (Dart: `pickerRowLabel`), or the
   card reads `Stalls D · Row Stalls D C`.

## Layout

Measured off the picker's own container, never the device: phone below
`size.phoneBreakpoint`, wide at or above `size.wideBreakpoint`. The phone
composition is a column:

```
Header                                     size.headerHeight
PriceLegend band                           size.topRailHeight
┌ map surface (WebView) ──────────────────────────────────┐
│  TestModeBadge (top-left)   ViewModeControl (top-right) │
│  FloorStrip (left rail)                                 │
│  corner controls: accessibility ◦ zoom column           │
│  toast (bottom centre) · extend prompt: opt-in only     │
│  ConfirmCard / Venue3D chrome / status overlay          │
│  (no DockBar on any width by default — host opt-in)     │
└─────────────────────────────────────────────────────────┘
CartSheet (collapsed)     handle + foot + safe inset
```

The prices keep a band of their own between the header and the map: floated on
the map's top edge, the last chip was clipped under the Map/3D control and seat
numbers read through the gaps on a busy chart. The Map/3D control keeps the
map's top-right corner on the line below, where the test badge sits at the other
end.

The header, the rail and the sheet are rows of the same column, so the map
surface begins and ends where they do. Everything else in the diagram stands on
the map and is reported to the runtime as viewport insets — see
[`picker-spec.md`](./picker-spec.md) §2.3.

---

## Header

**Name** `SeatLayerPickerHeader` · **Style slot** `headerStyle` · **Instance
override** `style:`

- **Inputs** `event.name`, `event.venue`, `branding.logo`, `hold`.
- **States** compact (phone, one line) / full (wide, two lines); with and
  without a hold.
- **Anatomy** `size.headerHeight` tall, ground `color.*.surface`, elevation
  `elevation.header`. Left: brand tile `size.headerLogoSize`. Centre: event
  name, `type.headerTitle`, ellipsized. Right: the HoldPill, then the dismiss
  control.
- **Callbacks** `onClose`.
- **Commands** none.

## PriceLegend

**Name** `SeatLayerPriceLegend` · **Style slots** `legendChipStyle`,
`chipShape` · **Instance override** `style:`

- **Inputs** `categories[]`, `map.categoryFilter`, `event.currency`.
- **States** chip idle / selected / sold out; empty (the band is not drawn);
  not drawn at all while the immersive scene is up. The band takes the map
  chrome's palette, so it darkens with the scene.
- **Anatomy** a band of `size.topRailHeight` on `color.*.surface` with a
  hairline beneath, holding one horizontally scrolling row. Each chip:
  `size.legendChipHeight` of ink inside a `size.minimumHitTarget` reach, a dot
  of `size.legendChipDotSize`, then the amount, `type.legendChip`. Idle ground
  is `color.*.background` with a hairline; selected is the accent with
  `color.*.onAccent` and a ring on the dot. `All prices` is pinned first and
  never scrolls away. On the light theme the dot is the category colour mixed
  into the surface under a full-strength ring of it. The row CLOSES with one
  grey swatch and `strings.notAvailable` — the single inert disc the map paints
  for a seat nobody can take, whatever the reason. It is not a chip: no pill,
  no hairline, no press target, not a toggle to assistive technology. (It
  replaces a held/sold pair with a padlock and a diagonal.)
- **Callbacks** none.
- **Commands** `picker.setCategoryFilter { keys, focus }` — the first tap
  filters and frames that band; the second clears the filter and frames the
  whole venue. **`focus` is sent on both**: the unframed path leaves the camera
  inside the buyer's drill-in and the map returns washed out.

## SeatNotes

**Name** `SeatLayerSeatNotes` (rows from `seatLayerSeatNoteRows`) · **Style
slots** none — the bands take the surface's own tone tokens · **Spec** §3.8.9

- **Inputs** a seat's `accessibility[]`, `wheelchairSpaceType` and
  `commercial` (`restrictedView`, `obstructedView`, `premium`, `note`).
- **Model first.** `seatLayerSeatNoteRows` decides which rows a seat earns and
  in what order — accommodation types, the wheelchair provision, restricted
  view, obstructed view, premium, the organizer's note — and every surface
  draws that one list: the seat card as full-bleed bands under the category
  band, the cart card as the same rows in words
  (`seatLayerCartNoteLines`). Restricted and obstructed are SEPARATE rows; a wheelchair
  accommodation with a provision reported yields the provision row only; the
  organizer's sentence hangs under the first selling mark it explains, and is
  its own row when there is none.
- **Anatomy** full-bleed bands, no radius and no border, `size.notePadY` ×
  `size.notePadX`, a `size.noteIconSize` glyph with `size.noteIconGap` beside
  it, title `type.noteTitle` and organizer line `type.noteBody`, a hairline of
  `color.*.divider` at `opacity.noteHairline` on every join but the first.
  `compact` (the wide layout's tap card) uses the `noteCompact*` sizes.
- **Tones** neutral `color.*.text` at `opacity.noteNeutralWash`; caution
  `color.*.warning` at `opacity.noteToneWash` with `color.*.warnText`; premium
  `color.*.premium` with `color.*.premiumText`. Each ink is measured against
  the tinted band, not the surface it is mixed from.
- **Callbacks** none. It is a statement, not a control.

## SeatIcons

**Name** `SeatLayerSeatIcon` / `seatLayerSeatGlyphs` · **Spec** §3.8.9

Sixteen drawings — the twelve accommodations, `restrictedView`,
`obstructedView`, `premium` and `note` — plus the accessibility sheet's own
`contrast` disc. Authored in a 20-unit box, stroked at 1.45 units with round
caps and joins in the caller's ink, hidden from assistive technology. The path
data is transcribed verbatim from the runtime's shared set and is the contract:
a port transcribes the same strings rather than redrawing them, and never
substitutes an emoji or a platform icon. An unknown key draws nothing.

## MapControls

**Name** `SeatLayerPickerMapControls` · **Style slot** `iconButtonStyle`

- **Inputs** `map.isVenue3D`, `map.focusedSectionId`, `map.canZoomIn`,
  `map.canStepBack` (from `map.atVenueFit`, falling back to `map.canZoomOut`),
  `capabilities`. **Instance override** `accessibilityControl:` — the widget
  drawn at the head of the column; null draws
  `SeatLayerPickerAccessibilityFilters`. The drop-in layout passes whatever
  `builders.accessibilityFilters` returns, so replacing the control does not
  mean rebuilding the column.
- **States** phone corners / wide rail; the map-only controls stand down while
  the immersive scene is up.
- **Anatomy** round controls `size.mapControlSize`, the accessibility control
  `size.accessibilityControlSize`. ONE bottom-right column on both
  compositions — the ♿ control, then the zoom discs, `size.zoomColumnGap`
  apart — at `size.mapAnchorInset` from the map's edges. It lifts by
  `size.dockBarHeight` only where a host opted into a dock; by default no
  width mounts one. The accessible-section stepper sits beside the accessibility
  control, `size.accessStepGap` from it, on its inner side.
- **The ♿ control heads the column** (2026-09-06). It stood alone in the
  bottom-left region, opposite the stack of discs, and the wide composition
  drew it a second time in its side panel. Who can sit where is an earlier
  question than how close the camera is, so it is the disc above `+`, once,
  on both widths.
- **Phone column** exactly three, `size.zoomColumnGap` apart, top to bottom:
  the ♿ control, `+` (`picker.zoomIn`) and a framed dot,
  `strings.fitWholeVenue`, that shows the whole venue from any depth via
  `picker.overview`. **There is no `−` on the phone** (2026-09-06): its only
  rung of its own was among the seats, everywhere else the one step back is the
  whole venue, and two discs for one move read as a puzzle. Pinch steps out.
  `SeatLayerPickerZoomOutButton` stays public for a host's own composition.
- **`+` retires but keeps its slot** — inert once `map.rung` is `seats` or
  `map.canZoomIn` is false, drawn with its space maintained, because the column
  is anchored at its foot and a disc that left the tree pulled the ♿ disc down
  under the thumb reaching for it.
- **The whole-venue disc is never dimmed.** Snapshot camera facts go stale after
  a pinch, which changes no state; dimming the escape hatch on a stale reading
  stranded buyers. At the venue already the press is a harmless no-op.
- **Disabled** discs **dim in place** rather than appearing and disappearing
  under the thumb: the glyph steps back to `color.*.mutedText` at 55 per cent,
  the ring to the chrome hairline at 60 per cent, and the shadow is dropped.
  The ground never changes and there is **no opacity wash** — one read as a grey
  blot on the light map and vanished on the dark one.
- **While a seat card is up on the phone** the whole column fades to opacity 0
  over `motion.duration.crossfade` and goes inert. It fades rather than
  unmounting, so nothing else on the map moves.
- **Ground** `color.*.chrome` with a `color.*.chromeLine` hairline, from the
  **map chrome's** side, never the panel's `surface`/`divider` — those vanish
  into a dark map at 1.14:1. Light `chrome` is `#d6dce6` with the darker edge;
  dark `chrome` is `#556278` with the pale one. Dark separates by the fill (2.96:1 against the
  map), light by the edge (3.72:1 against the disc). Applies to every floating
  control, the accessibility disc and the Map/3D track included.
- **Wide** `+` and `−` under the same ♿ disc, and no fit control of its own:
  `SeatLayerPickerZoomToFitButton` left the rail on 2026-09-06 and stays a
  public component for a host that mounts it itself. The immersive scene keeps
  its Fit chip, which says `strings.fitWholeVenue` like the phone's disc.
- **Commands** `picker.zoomIn`, `picker.overview` (the phone's whole-venue
  disc), `picker.zoomOut` (the wide column's `−`),
  `picker.setAccessibilityFilters`,
  `picker.setColorblindSafe`, `picker.setBuyerView`.
- **Note** `SeatLayerPickerViewModeControl` (the Map/3D segmented control) is a
  member of this stack on wide layouts only; on a phone the top rail owns it.

## DockBar

**Name** `SeatLayerDockBar` · **Style slot** `dockBarStyle` · **Instance
override** `style:`

**Off by default on every width.** `dockBarFor` resolves `showDockBar ?? false`,
so no composition mounts a bar unless the host asks: the prev/next arrows bought
a two-tap version of a gesture the finger does better, and the bar's height plus
the home-indicator inset pushed every bottom-corner control up the screen. The
phone's way back is pinch-out past the melt point, the back-to-overview control
top-left, or the whole-venue disc at the foot of the map's column; the wide
layout keeps `−` as well. A host that wants the bar sets `showDockBar: true`;
the widget also mounts standalone. Everything below describes the bar where it
IS drawn.

- **Inputs** `sections[]` (with `accessibleFree`), `map.focusedSectionId`,
  `map.rung`, `map.accessibilityFilter`.
- **States** not mounted unless the host asked; hidden at rung
  `venue`; visible at rung `seats`; step controls
  disabled at the ends of `sections[]` (never wrapping around).
- **Anatomy** edge-to-edge, `size.dockBarHeight` plus the bottom safe area,
  elevation `elevation.dockBar`. Left: a 10 pt dot in the section's colour, the
  section name (`type.dockSection`, ellipsizes) and `N left`
  (`type.dockCount`, never ellipsizes; omitted when `seatsLeft` is unknown),
  followed by ` · ♿ N` under an active filter on `section-access-counts-v1`
  where this section was counted — absent counts stay silent, never `♿ 0`.
  Right: `‹ ›` section steps, then `‹ Venue`.
- **Motion** slides in over `motion.duration.dock`; the name cross-fades over
  `motion.duration.crossfade` when the focus changes.
- **Callbacks** `onSectionChanged(id)`, `onOverview`.
- **Commands** `picker.focusSection { id }`, `picker.overview`.

## ConfirmCard

**Name** `SeatLayerConfirmCard` · **Style slots** `confirmCardStyle`,
`primaryButtonStyle`, `secondaryButtonStyle`, `pillStyle` · **Instance
override** `style:`

- **Inputs** the newest unconfirmed `SelectedSeat`, `capabilities`
  (`seatView`, `venue3d`), and — on `seat-view-thumbnail-v1` — the seat's
  `seatViewThumb`, `sightlineMetres` and `seatViewConfidence`. Plus `mode:`
  (`SeatLayerConfirmCardMode.add` | `.remove`), which chooses the question.
- **States** with a photo (loading, arrived, never arrived), without one (no
  strip at all, and the 3D square in the decision row), 3D-only, with a sight
  line, with tiers, with seat notes; the confidence teaser or its passport chip
  inside 3D; committing; **removing** —
  the same card over a seat already in the cart, raised by a second tap on it
  (bridge event `seat.retap`, payload `{ seat }`, the seat still selected). The
  primary reads `strings.removeSeat` in `color.*.error` with a cross rather
  than a tick; Cancel, the drag, the tap outside and Back all KEEP the seat;
  and the seat stays counted in the cart the whole time it is asked about.
- **Commands** `picker.openSeatView`, `picker.showSeatIn3D`,
  `picker.deselect`, `picker.removeCartLine` (the remove answer), and —
  where the bundle advertises it — `picker.setSelectionFocus` with
  `{ seatId }` on open and `{ seatId: null }` on close, which is what makes the
  runtime ring the seat the card is asking about and pale its neighbours. Paint
  only: no busy state, safe read-only, and never raised as an error.
- **Backdrop** the map goes behind glass: a black veil at
  `opacity.confirmScrim` under a `size.confirmScrimBlur` blur, masked clear to
  `size.confirmScrimClearRadius` around the tapped seat and reaching full
  strength at `size.confirmScrimFeatherRadius`. A spotlight, not a curtain.
  Only drawn when there is a seat point to centre on, never takes a pointer
  event, and under reduced transparency drops the blur for
  `opacity.confirmScrimFlat`.
- **Anatomy** `size.confirmCardMaxWidth`, capped at the map width less
  `2 × size.confirmCardGutter`, radius `radius.confirmCard`, elevation
  `elevation.confirmCard`.
  1. Identity grid, `size.confirmIdentityHeight`: section, row and seat as
     three EQUAL centred cells, hairline-divided, keys at
     `size.confirmIdentityKeyFontSize` and values at
     `size.confirmIdentityValueFontSize`. Only a section longer than
     `size.confirmSectionShortMax` drops to
     `size.confirmIdentityLongSectionFontSize` and wraps to two lines.
  2. Category band, `size.confirmBandHeight`: the category colour itself, full
     bleed, no dot and no rail, with its ink chosen per colour (white where
     white clears 3:1, otherwise `#0B0F19`). The name at
     `size.confirmBandNameFontSize` and the price at
     `size.confirmBandPriceFontSize` — **and no remaining count**; the legend
     keeps that. Padding `size.confirmBandPadTop` / `…PadTrailing` /
     `…PadBottom` / `…PadLeading`, which is what keeps the price off the
     card's edge. In 3D, `size.confirmImmersiveBandPadY` / `…PadX` and
     `size.confirmImmersiveBandPriceFontSize`.
  3. Photo strip, `size.confirmPhotoHeight`, only where the seat names an
     authored photograph, carrying the `View from here` and `3D` pills and, in
     its trailing top corner, the sight line (`strings.sightline`,
     `size.confirmSightFont` / `confirmSightPadX` / `confirmSightPadY`). With
     no photograph the slot is not drawn at all — no rail, no caption — and a
     photograph that never arrives collapses it away over
     `motion.duration.thumbOut`.
  3a. Confidence teaser, 3D card only and only where the seat carries one:
     `size.confidenceTeaser*`, headline over `modeledTarget ?? reality`, with
     `strings.passport` in the readable accent at the trailing edge. Where a
     host can open the passport it is instead a chip on the inspection row.
  3b. Inspection row, 3D card only: one line of
     `size.confirmInspectChipHeight` chips —
     `strings.passport` (with an accent dot) and `strings.viewFromHere`
     (spoken as `strings.viewFromThisSeat`) — at
     `size.confirmInspectChipFontSize`.
  4. Seat notes, where the seat carries any, as full-bleed bands directly
     under the category band and ABOVE the tier chooser — see SeatNotes below.
     Then tiers (`size.confirmTierHeight`). The body owns no padding of its
     own; the tier chooser carries the gutter so the bands can reach both
     edges.
  5. Actions, `size.confirmActionHeight`: with no photo strip a 44 × 44 ghost
     square carrying a cube and `strings.venue3D` at
     `size.confirm3dSquareFontSize` opens the row, then `Cancel` at 34 % of the
     whole row and `✓ Add seat` at the rest, each in its own box at
     `radius.button` inside the card's gutter.
- **Placement** one home, and the map moves instead: a fixed bottom sheet
  `size.confirmCardRestInset` above the map's foot, for every seat. Nothing
  about the tap is read. While it is up, the picker reports the sheet's band
  (`size.confirmCardSeatGap` above the card's top edge) as the bottom viewport
  inset, so the runtime frames the venue into what is left clear above it. Full
  rule and constants: `picker-spec.md` §3.8.2.
- **Motion** the map dims to ink behind it (`SeatLayerPickerStyles.scrimColor`);
  the card springs up from the foot of the map with `motion.curve.spring` over
  `motion.duration.cardEnter`. `Add seat` invites once, breathes until touched,
  and then the add runs as ONE ordered chain: the **confirm sweep** fills the
  button under a drawn tick over `motion.duration.pressSweep` and the label
  turns to `Added`; a **chip in the category's colour carrying the seat label**
  flies from the card to the foot over
  `motion.durationOutsideBudget.confirmFlight` on `motion.curve.spring`; **on
  landing** the controller's `cartLanding` ends; the footer's count and total
  **swell 1 → 1.3 → 1** over `motion.duration.bump` with the ink lerping to the
  accent; and only THEN does the map's lift pull back. A map moving under a
  chip still in flight read as two motions fighting. Under reduced motion no
  chip is created, so nothing waits on a landing.
- **While the card is up** the sheet's handle disc is not drawn, the map's
  control column fades to opacity 0 and goes inert, the footer's count and
  total EXCLUDE the candidate (`confirmedCartLines`), and the footer's call to
  action stays a **disabled button with the label it already had** — not a
  sentence about the card.
- **Callbacks** `onConfirm`, `onCancel`, `onViewFromSeat`, `onShow3D`,
  `onSeatConfidence` (the teaser is a button only where a host takes it).
- **Commands** `picker.openSeatView`, `picker.showSeatIn3D`,
  `picker.deselect` on cancel. In `remove` mode: `picker.removeCartLine` on the
  primary — the same path the cart row's ✕ takes — and NOTHING on any of the
  ways out.

## HoverCard

**Web only. There is no native surface and nothing here is to be ported.**
No Dart name, no style slot, no token. Runtime source: `hoverCard.ts` (the DOM
element) and `hoverCardContent.ts` (the pure model). Spec: `picker-spec.md`
§3.8a.

- **Mouse only, by design** every update is gated on `pointerType === 'mouse'`,
  a touch or pen pointer hides the card, and a pointer-leave takes it down. A
  tap synthesizes `mousemove`/`mouseover`, never `pointermove`, so a touch
  sequence produces no card at all. It is decoration over a canvas that already
  announces every seat on the keyboard path: `aria-hidden`, no pointer events,
  no focus.
- **Modes** one reused card, switched on the renderer's own `seatsPickable()` —
  the same test that decides whether a click picks a seat or zooms. Above the
  line it describes the seat under the cursor, below it the section block.
- **Head** the labelled SECTION / ROW / SEAT cells shared with ConfirmCard,
  hairline-divided, so a hover is the first frame of the card a click opens
  rather than a differently shaped restatement of it. A section card's head is
  its name, centred over the bands. Rows carry category name and price only —
  availability counts were refused (2026-09-04): a per-category "12 left" on
  every hover is a pressure device, and the number most likely to be stale.
- **Category band** full bleed, edge to edge like a legend chip, no dot and no
  rail — bands inside a margin read as buttons on a panel rather than as one
  card about one place. Ink from `categoryBandInk()`, and **the direction is
  chosen by the fill, not by strongest contrast**: a light fill takes dark ink,
  a dark or saturated fill takes light ink, and either is kept when it clears
  the small-text bar on its own. Strongest contrast put black on the arena red
  (6.3 : 1 against white's 3.3 : 1), and the band read as a warning stripe
  rather than as a price.
- **Host stand-down** while the host has its own card up about that seat — the
  pinned "cannot be taken" explanation, or the confirm card — `hostCardOpen()`
  answers true, the model resolves to nothing and the map's card hides. Without
  it, clicking a sold or held seat produced two cards saying the same thing.
- **Placement** below-right of the cursor at 16 px; near a viewport edge it
  flips to the other side at the same offset rather than sliding under the
  pointer, and clamps at an 8 px margin only when it fits on neither side.

## InterruptCard

**One web shape, one native user.** The web picker uses a single interrupt
shape (`.sl-interrupt`) for the two moments where something has ended and there
is exactly one way on: hold expiry (`pickerHoldExpiredDialog.ts`) and session
recovery (`accessPanelElement` / `accessCopy` in `pickerFragments.ts`). Styles
in `pickerStyles.ts`. Values below are the web's own, in px — no token is
minted for a shape three of the four SDKs do not draw.

- **Veil** the scrim ink at 35 %, filling the widget root with 16 px of padding
  and the card centred in it. The map keeps painting behind it, so the buyer
  sees the venue they are coming back to rather than a blurred-out widget.
- **Card** `min(420px, 100%)`, 24 px padding (20 px on a narrow layout), 15 px
  radius, on the surface with a hairline and a deep shadow. Left-aligned — these
  carry a sentence and a fact, and centred body copy makes both harder to scan
  — and it scrolls rather than clipping its button when it cannot fit.
- **Title** 20 px, weight 700 (18 px narrow), with an inline glyph at text size
  rather than an accent disc, which gave a recoverable pause the weight of an
  error.
- **Body** 14 px in the muted colour. The one line that is fact rather than
  explanation — which seats were released — takes the text colour at weight 700.
- **Action, and there is exactly one.** A 44 px-minimum pill on the accent,
  trailing on a wide layout and **full width on narrow**, where it is the only
  thing to do and a right-aligned pill wastes the row that matters.
  **Every interrupt has exactly one primary action**: two access reasons once
  built none, which left the buyer behind a veil with nothing to press. On the
  hold dialog a press on the veil and Escape run that same action rather than
  dismissing into the state underneath, which is gone.

**What Flutter takes.** The session-recovery panel, and only that:
`SeatLayerPickerAccessPanel` (`picker-spec.md` §3.13.3) is this shape and keeps
the one-action rule, including for the two reasons that used to be dead ends.
**Hold expiry does not use it** — Flutter announces a lapse without blocking the
map; see `picker-spec.md` §3.13.7, "Divergence from the web picker".

## CartSheet

**Name** `SeatLayerCartSheet` · **Style slots** `sheetStyle`,
`continueButtonStyle` · **Instance overrides** `style:`,
`continueButtonStyle:`

- **Inputs** `cart`, `hold`, `capabilities.bestAvailable`, `event.currency`.
- **States** collapsed empty, collapsed with tickets, expanded empty (the
  best-seats form), expanded with tickets.
- **Anatomy** ONE surface: ground `color.*.background`, a hairline on its top
  edge and a shadow of `0 -8 26 -20`. Radius `radius.sheet`; `elevation.sheet`
  is available through `sheetStyle` and is 0 by default, because the shadow is
  drawn.
  - **Handle** a `size.sheetHandleWidth` × `size.sheetHandleHeight`
    (44 × 44) **disc**, not a pill: filled with `color.*.background`, lifted by
    its own shadow (black at 20 per cent, blur 10, offset 0 / 2), carrying a
    16-point chevron in `color.*.mutedText`, straddling the sheet's own top
    edge — `size.sheetHandleOverhang` (22) above it, `size.sheetHeadHeight`
    (16) inside. **No hairline and no band under it.** The chevron points up
    when collapsed and down when open, rotating over
    `motion.duration.chevron`. The whole band is the tap target and the
    sheet's drag runs under it. **Not drawn while a confirm card is up.** It is
    the cart's ONE named toggle in both states: `strings.expandCart` /
    `strings.collapseCart`, with the expanded state and its own tap action.
  - **Collapsed** the FOOTER BLOCK ONLY — head, foot, safe inset. No cards rest
    in the collapsed sheet: a list that unrolled itself on every add read as a
    panel the buyer had not opened. Never a fixed peek either: a surface
    clipped to a different height than the content it holds cuts the bottom off
    its own buttons.
  - **Cart region** the CartList in `size.cartTrayPadX` gutters, **height zero
    while collapsed**; opened, it shows up to `size.cartPeekMaxHeight` (189) —
    three cards and a sliver of the fourth — scrolling inside its own box, and
    also carries the closed-sales statement and, on an empty cart, the
    best-seats form. The extras stay laid out offstage while shut, so the sheet
    opens straight to its height.
  - **Foot** the lapse notice, the hold-ending cue, the inline action error,
    the total line, `size.footTotalGap` (8), the BookButton and the centred
    attribution, in `size.footPadX` (16) / `size.footPadTop` (4) /
    `size.footPadBottom` (2). A hairline above it **only when the sheet is open
    with tickets** — collapsed, the panel's own top edge is the only edge
    there is. Not restyled on a phone at all — one block on both widths. The
    attribution is absent entirely on a white-labelled account.
  - **Total line** `strings.noSeatsSelected` on an empty cart, else
    `strings.ticketCount` at `type.footTotalLabel` (13 / w600) with the total
    at `type.footTotalAmount` (17 / w700) in tabular figures on the trailing
    edge. **Collapsed and non-empty it grows a second, muted line** naming the
    seats — each line label with `-` replaced by ` · `, joined by a comma and
    two spaces (`204 · Q · 7,  204 · U · 13`), 12 / w600 in
    `color.*.mutedText`, one line, ellipsized, with an unfold glyph at 16 —
    and **tapping that line opens the sheet**. A live region; the count and
    total swell 1 → 1.3 → 1 over `motion.duration.bump` with the ink lerping to
    the accent, fired **when the flying chip lands**, never on the press.
    There is no `From <min>` anywhere: it stated a price and offered nothing
    to do about it.
- **Rules** the sheet never opens itself; any map tap while expanded collapses
  it, and so does a tap on a cart card. Count and total exclude a seat a
  confirm card is still asking about.
- **Motion** `motion.duration.sheet`; springs, not tweens (see
  `picker-spec.md` §3.9).
- **Commands** `picker.checkout` from the foot's own button.

## CartList

**Name** `SeatLayerCartList` · **Card** `SeatLayerCartCard`

- **Inputs** `cart.lines[]` joined to `selection[]`.
- **Anatomy** one card per ticket — the SAME card on every width — separated by
  `size.cartCardGap` (6). Each is at least `size.cartCardMinHeight` (52) on
  `color.*.surface`, hairline border, `size.cartCardRadius` (12), padded
  `12 / 4 / 4 / 4` (leading / top / trailing / bottom; the trailing side is
  tight because the action boxes carry their own touch floor): a category dot
  (9 pt, with a hairline so a pale category is still findable), a 10 pt gap,
  the name at 15 / w700 — the only part that ellipsizes — the position line
  under it at 13 / w600 in `color.*.mutedText` with tabular figures, joining
  row, seat and ticket type with ` · ` (`R · 6 · Lower Bowl`), then the amount
  at 15 / w800 in `color.*.text`, then the two actions. Both actions are
  18-point glyphs in `color.*.text` inside a TIGHT `size.minimumHitTarget`
  box: Material pads an icon button to 48 of its own accord, and four points
  per card is what puts the fourth card past the open cap.
- **Actions** the eye opens the view from that seat — drawn only where the host
  allows it, the runtime advertises `seatView` and the seat carries an authored
  photograph — then the ×. Cards may also be **swiped** toward the leading edge
  to remove.
- **Card tap** a press on the card's own face — not the × and not the eye —
  sends `picker.frameSeat { seatId, fraction: 0.5 }`; the sheet **stays where
  the buyer put it**, and the seat lands in the room the open sheet leaves. A
  line with no selected seat behind it is not pressable.
- **Notes** what the organizer said about the seat, said ONCE and in WORDS,
  under a hairline inside the card: accommodations, then the wheelchair
  provision (`strings.accessiblePhysicalSeat` /
  `strings.emptyWheelchairSpace`), then Restricted view, Obstructed view and
  Premium seat as SEPARATE lines, then the organizer's sentence — attached to
  the first selling mark, or its own `strings.organizerNote` line where there
  is none. Title at `type.cartNoteTitle`, the sentence at `type.cartNoteText`.
  No plate and no ground: the card is already a bordered ticket, and a tinted
  band inside one reads as a card inside a card. The icon rows belong to the
  seat card, not here.
- **Held cards** a wash of the accent at 7 per cent over the surface, a border
  blended 45 per cent toward the accent, and the dot becomes a lock — a lock is
  not a colour. Held cards are never swipeable; the × stays.
- **Rules** no folding and no `+N more`. Consecutive seats used to collapse
  into a run, which was a second rendering of one cart to keep in step; the
  open sheet caps and scrolls instead. Long text truncates with an ellipsis —
  there is no shorten-by-fact ladder (a known gap, spec §4.9).
- **States** a card the buyer has asked to remove is drawn at
  `opacity.removing` with its × inert and its swipe disabled, from the press
  until the snapshot that drops it (or the failure that puts it back). Any cell
  whose words change between snapshots cross-fades over
  `motion.duration.crossfade`; the rest of the card does not move. See
  `picker-spec.md` §3.13.
- **Commands** `picker.removeCartLine { label }`, sent silently — no toast and
  no undo, because the card leaving and the total moving are the whole answer.
  It carries its own busy action (`removingCartLine`) because it is the one
  inventory mutation that does not put the checkout call to action down.

## BookButton

**Name** `SeatLayerBookButton` · **Style slot** `primaryButtonStyle` (the
sheet merges `continueButtonStyle` over it) · **Instance override** `style:`

- **Inputs** `cart`, `hold`, busy state, and whether this width may offer the
  best-seats form (`onFindBestSeats`).
- **Anatomy** full width, `size.checkoutButtonHeight`, radius `radius.button`,
  `type.bookButton`. Carries its own label only — the total is on the line
  above it.
- **States** idle, busy (spinner), disabled with a reason, and — on a phone
  with an empty cart — an ENABLED `strings.findBestSeatsCta` that opens the
  sheet on the best-seats form (the form lives only in the open sheet). **While
  a confirm card is up the button keeps the label it already had and is simply
  disabled** — never a sentence about the card — and offers no finder. Disabled is a designed state — a
  surface-toned ground, muted ink, an inset hairline — not Material's own
  greys, which vanish on the dark scene sheet. The label ladder is in
  `picker-spec.md` §3.10.3.
- **Commands** `picker.checkout`, then `picker.rejectHandoff` if the host
  refuses the handoff, so a rejected hold is never stranded.

## Venue3D chrome

**Name** `SeatLayerVenue3D` · **Style slot** `pillStyle`

- **Inputs** the focused `SelectedSeat`, `map.isVenue3D`, `capabilities`.
- **States** live seat view / venue 360°; stepper disabled in venue mode.
- **Anatomy** one dark glass whatever the resolved mode is —
  `color.dark.immersiveGlass`, its border and ink, blurred by
  `size.immersiveGlassBlur`; captions use the deeper caption glass. Every piece
  is a pill. Top-left `‹ Back to venue` (`size.immersiveBackPillHeight`), drawn
  only while the buyer is sitting in an exact seat. Bottom: a caption chip
  naming the seat, then `‹` previous seat, `Open venue 360°`, `›` next seat, and
  recentre, as chips of `size.immersiveNavChipHeight`.
- **Motion** `motion.duration.immersive`.
- **Commands** `picker.showSeatIn3D`, `picker.openVenue360`,
  `picker.setBuyerView`, `picker.recentre3D`.

## HoldPill

**Name** rendered by `SeatLayerPickerHeader` (`showHoldPill`) · **Style slot**
`pillStyle`

- **Inputs** `hold.expiresAt`.
- **States** counting down; absent when there is no picker-owned hold — a hold
  handed to the host is the host's to display.
- **Anatomy** a true pill (`radius.pill`) with a timer glyph and `mm:ss`,
  `type.pill`.
- **Haptics** `holdCreated` on creation, `holdExpired` on expiry.

## TestModeBadge

**Name** `SeatLayerPickerTestModeIndicator`

- **Inputs** `event.mode`.
- **Rules** required chrome: it has no host switch, and exactly one may render.
  It steps below the immersive scene's own back control rather than under it.
- **Anatomy** a pill of `size.testChipHeight` at `radius.pill`, one recipe in
  both themes: warning ink on the warning colour at `opacity.warnPillWash` over
  the surface, a leading dot of `size.testChipDotSize`. Copy `strings.testMode`;
  the accessible name keeps `strings.testModeLong`. Amber, never the accent — an
  environment flag must not wear "buy" gold.
- **Contrast** the ink is measured against the **wash**, never the bare surface,
  and must clear 4.5:1 on it: the hue if it already reads, else the hue walked
  toward `color.*.text` until it clears, else a neutral chosen by contrast.
  Never a fixed blend — one produced a 2.3:1 pill on a mixed theme.

---

## Also in the catalogue

These carry no separate entry here because their whole description is their
specification. Names, slots and files:

| Component | Slot | Spec |
| --- | --- | --- |
| `SeatLayerFloorStrip` | `floorStripStyle` | §3.7 |
| `SeatLayerBestSeatsForm` | — | §3.11 — lives ONLY in the open sheet (the empty cart's way in is the footer's own full-width `strings.findBestSeatsCta`). A one-line `✦ strings.findSeatsTogether` title, truncating rather than wrapping, with an **ⓘ immediately after the title** that toggles a one-line `strings.closestGroupChosenInstantly` under it (animated size, muted 12 pt, named `strings.aboutBestSeats`). **No premium chip** — the snapshot carries no chart-level premium fact; see §4.9 |
| `SeatLayerPickerToast` / `…ToastQueue` / `…ToastLayer` | — | §3.12 |
| `SeatLayerPickerLoadingView` / `…ErrorView` / `…EmptyView` | — | §3.13.1–2 |
| `SeatLayerPickerAccessPanel` | — | §3.13.3 |
| `SeatLayerPickerSalesClosedStatement` / `…SalesClosedPill` | — | §3.13.4 |
| `SeatLayerPickerSoldOutOverlay` | — | §3.13.5 |
| `SeatLayerPickerExtendHoldPrompt` | one named `+5 min` step, once per hold, dismissable; **phone: off by default**, host opt-in via `SeatLayerPickerChromeOptions(showExtendHoldPrompt: true)`; wide keeps it | §3.13.8 |
| `SeatLayerPickerBookedOverlay` | — | §3.13.10 |
| `SeatLayerPickerGeneralAdmissionPrompt` / `…TablePrompt` | — | §3.13.11–12 |
| `SeatLayerPickerAccessibilityFilters` | — | §3.5, §4.10 — the head of the map's control column on both widths; switches apply on change, no apply step, the sheet stays open; opening it clears a pending seat card; the sheet is bounded to `size.accessSheetMaxHeightFraction` (floor `size.accessSheetMinHeight`) and scrolls inside it. Rows are a FIXED 50 high, closed by a hairline of `color.*.divider` at 35 per cent, and read **icon cell (`size.accessRowIconCell`) · `size.accessRowGap` · label (flexible, ellipsized) · ⓘ in a 36 column where the row has a note · count in a 68 column · `size.accessRowSwitchGap` · drawn switch**. The whole line toggles. The count is `N free` on a quiet ground of `color.*.text` at 6 per cent, tappable to jump where the runtime supports it; a sold-out provision stays, named and disabled, reading `0`. The two map switches (hide limited view, colourblind-friendly) sit under a `strings.viewGroupTitle` heading |
| `SeatLayerPickerAccessibleStepper` | — | §3.4.1 |
| `SeatLayerSeatViewChrome` | `seatViewChromeStyle` | §3.15 |
| `SeatLayerPickerAttribution` | — | §3.10.3 |
| `SeatLayerCheckoutCta` (the one label resolver) | — | §3.9, §3.10.3 |
| `SeatLayerTypeScale` / `seatLayerReadingOrder` / `seatLayerBoldWeight` | — | §4.10 |
