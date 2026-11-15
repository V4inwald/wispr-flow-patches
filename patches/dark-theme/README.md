# Dark theme

Repaints the app's own interface dark. The app has no theme setting, and it does not follow the
system either: with Android in dark mode it stays light.

* **Package**: `com.wispr.flowapp`
* **Target version**: 2.8.7
* **Evidence**: [`analysis/wispr-flow-2.8.7.md`](../../analysis/wispr-flow-2.8.7.md) §4

## Why the Compose theme is not enough

Every screen composes under `FlowAppTheme(dark, dynamic, content)` (`com/wispr/design/ThemeKt`),
and its dark palettes exist (`DarkColorScheme`, `dynamicDarkColorScheme`). Forcing that flag was
the first attempt, and it changed nothing visible: the app's interface does not paint from
`MaterialTheme`.

It paints from `com/wispr/design/ColorKt`, a class of **60 fixed constants** — `Neutral50` to
`Neutral900`, `SurfaceLight`, `BottomNavBarBackground`, `CardBorder`, `ProfileCardBackground`…
98 interface files outside the bubble read them, always through one getter each; no field is read
directly. `res/values-night/` holds only AppCompat styles, and `values/colors.xml` has no night
variant. There is no dark theme to switch on, which is why the app offers no setting.

## What the patch does

Three groups of hooks:

| Group | Count | Effect |
|---|---|---|
| `FlowAppTheme.forceDark` | 1 | `const/4 p0, 0x1`, so a screen passing `dark = false` becomes dark |
| `isSystemInDarkTheme` | 1 | `return true`, so screens leaving the argument to the system agree |
| `palette.*` | 38 | each colour getter returns a dark value instead of the constant |

The first two keep the few Material components that do read the scheme (the segmented picker on
the Style screen is one) in step with the rest.

**The palette is one table.** [`palette.py`](palette.py) holds every colour: its current value,
its dark value, and what it paints. The script writes the hook list here and
`DarkPalette.kt` in the patch sources, so the two implementations cannot drift. Edit the table,
re-run `python3 patches/dark-theme/palette.py`, and the manifest must be regenerated the same way.

Only surfaces, borders, neutrals and indicators are remapped. Accents (`AccentPurple`, `LinkBlue`,
`BetaGreen`, `StatsOrange`, `flowBubbleColor`…) keep their identity, so a green success badge stays
green; the neutrals carry the text, and their ramp is inverted so body text ends up light
(`Neutral900` `#191521` → `#F5F2F8`).

## Limits — read before reporting a bug

* **This is a repaint, not the vendor's dark theme.** There isn't one to reuse. Some contrast
  choices are ours, and a screen that paints a literal colour outside this palette will stay
  light.
* **The bubble is untouched.** It is a separate service window; the user asked for the app
  interface only.
* **It needs a device to judge.** The build checks that the instructions are in the dex and that
  the palette table is applied; it cannot say whether the result looks right.
* **Version-specific.** `ColorKt`, `FlowAppTheme` and the obfuscated `isSystemInDarkTheme` are tied
  to 2.8.7. Each hook fails loudly rather than misapplying elsewhere.
