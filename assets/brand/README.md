# SceneDeck Brand — v1

Master artwork for the SceneDeck Android identity. Vectors are hand-authored;
the SVG here is the source of truth and `app/src/main/res/drawable/ic_scenedeck_logo.xml`
must be kept in sync with it.

## Concept

A stylized stack of three "deck" tiles — rounded rectangles fanned like cards
around a shared bottom pivot. The top tile glows **tally red** with a white
live dot: the deck is *on air*. Ground is deep-space indigo. Flat, geometric,
no skeuomorphism.

## Palette

| Role | Hex | Usage |
|---|---|---|
| Indigo (ground) | `#14101F` | launcher background, splash background, primary dark surface |
| Indigo deep | `#0D0A16` | radial ground gradient edge |
| Indigo glow | `#201A38` | radial ground gradient center |
| Violet (accent) | `#7C5CFF` | brand accent, back tile, stream-active accents |
| Violet light | `#A79BFA` | middle tile, secondary illustration strokes |
| Tally red | `#FF3B4E` | LIVE/program only (top tile `#FF5563`→`#F02A3F` gradient) |
| Preview green | `#34D399` | preview state only — never decorative |

Tally red and preview green carry product meaning (see
`docs/DESIGN_SYSTEM.md` §3): never use them decoratively.

## Files

- `logo.svg` — master logo, 512×512 viewBox, rounded-square ground (rx 112).
- Android copies (in `app/src/main/res/`):
  - `drawable/ic_scenedeck_logo.xml` — full logo vector.
  - `drawable/ic_launcher_foreground.xml` — mark only, centered in the 66dp
    adaptive-icon safe zone (108dp canvas).
  - `drawable/ic_launcher_monochrome.xml` — single-alpha themed icon (back/middle
    tiles as rings, top tile solid with punched-out dot).
  - `mipmap-anydpi-v26/ic_launcher.xml` / `ic_launcher_round.xml` — adaptive icon,
    background `@color/ic_launcher_background`.
  - `drawable/ill_disconnected.xml` — empty-state illustration seed (unplugged
    cable + "zzz"), violet/lavender on transparent.

## Usage rules

- Clear space around the mark: ≥ ¼ of the tile width on all sides.
- Minimum size: 24dp (monochrome variant below 32dp).
- Never recolor the tally-red top tile to any other hue; the red live tile is
  the identity.
- Never place the mark on light/busy backgrounds without the indigo ground.
- Don't add shadows, bevels, or outlines; the mark is flat.
- The live dot stays white; the glow may be dropped at small sizes.

## Play Store / export checklist (M8)

- [ ] Play Store hi-res icon: 512×512 PNG, 32-bit, full-bleed square with ground.
- [ ] Feature graphic: 1024×500 PNG — mark left-of-center on indigo ground,
      wordmark optional, no critical content within 60px of edges.
- [ ] Adaptive icon PNG fallbacks (if ever needed): mdpi…xxxhdpi from the
      foreground/background layers, not from the master SVG directly.
- [ ] Splash icon export: mark centered, fits inside a circle of ⅔ the drawable
      diameter (system masks to a circle).
- [ ] Monochrome icon legibility check against light and dark wallpapers
      (Android 13+ themed icons).
- [ ] Screenshots: phone + 7" and 10" tablet frames, dark theme first.
- [ ] Promo/tv banner (if TV build ships): 1280×720.
