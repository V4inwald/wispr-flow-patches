# Dark theme

Forces the app's own interface into the dark colour scheme it already ships but never selects.

* **Package**: `com.wispr.flowapp`
* **Target version**: 2.8.7
* **Evidence**: [`analysis/wispr-flow-2.8.7.md`](../../analysis/wispr-flow-2.8.7.md) §4

## What the app does without the patch

There is no theme setting, and the app does not follow the system either: with Android in dark
mode, the app stays light. Every screen composes under `FlowAppTheme(dark, dynamic, content)`
(`com/wispr/design/ThemeKt`), and the two ways of reaching it both end light:

* `MainActivity` passes `dark = false` explicitly, so the system setting is never consulted;
* screens that omit the argument fall back to Compose's `isSystemInDarkTheme()`, which is only
  reached when the caller leaves the default in place.

The dark palettes are not missing — `DarkColorScheme` and `dynamicDarkColorScheme` are both
defined in the same class. They are simply never chosen.

## What the patch changes

Two instructions, one per path:

| Hook | Where | Effect |
|---|---|---|
| `FlowAppTheme.forceDark` | first instruction of `FlowAppTheme` | `const/4 p0, 0x1`, so an explicit `false` becomes `true` |
| `isSystemInDarkTheme` | first instruction of the Compose helper | `return true`, so the default is dark too |

The second hook is found through the body — reading `Configuration.uiMode` and testing the night
bit — because R8 renames that class and method on every release. It also covers the paywalls and
any component that follows the system.

## Limits

* **The interface only.** The floating bubble is a separate service window; it keeps its own
  appearance. Nothing about the dictation behaviour changes.
* **Composed colours, not hard-coded ones.** Screens that paint a literal light colour outside
  the theme will stay light. This is the one thing that needs a device to confirm.
* **Version-specific.** Both hooks are tied to 2.8.7 and fail rather than misapply elsewhere.
