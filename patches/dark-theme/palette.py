#!/usr/bin/env python3
"""Generate the dark-theme remap hooks from one table.

The app paints from fixed colour constants in `com/wispr/design/ColorKt`, reached only through
one getter each (no field is read directly outside the class). So overriding a getter is enough
to repaint every use of that colour, and the table below is the whole theme.

    python3 tools/dark_palette.py   # prints the hook list as JSON

Only surfaces, borders and neutrals are remapped: the accent colours keep their identity, and
the values were chosen to keep text readable on the new backgrounds (dark surfaces ~#12-#2a,
raised text ~#f5).
"""
from __future__ import annotations

import json
from pathlib import Path

# name -> (current ARGB, dark ARGB, why it moves)
PALETTE = {
    # backgrounds: the app's page and card surfaces
    "Neutral50": (0xFFFAF9FA, 0xFF0E0E10, "page background"),
    "SurfaceLight": (0xFFF5F5F5, 0xFF121214, "generic surface"),
    "ProfileCardBackground": (0xFFF1F1F1, 0xFF1B1B1E, "card above the page"),
    "BottomNavBarBackground": (0xFFE6E6E8, 0xFF17171A, "bottom bar"),
    "PageNavigationBarBackground": (0xFFDCDCDD, 0xFF131316, "navigation bar"),
    "Neutral100": (0xFFEEEEF0, 0xFF1E1E22, "raised surface"),
    "SurfaceContainerHigh": (0xFFF3EDF7, 0xFF211C26, "material container"),
    # tinted surfaces: the lilac and cream backgrounds
    "Primary200": (0xFFF8F6FE, 0xFF1A1622, "tinted card"),
    "Primary300": (0xFFF7EBFF, 0xFF221A2E, "tinted card"),
    "Primary400": (0xFFF4E1FF, 0xFF2A1F38, "tinted card"),
    "M3Pink": (0xFFEADDFF, 0xFF2A2038, "material tint"),
    "SecondaryContainer": (0xFFCAC4D0, 0xFF332E3C, "material container"),
    "Dawn": (0xFFF0D7FF, 0xFF2B1A38, "dawn tint"),
    "Lumen": (0xFFFFFFEB, 0xFF2A2A1C, "lumen tint"),
    "PlayStoreReviewNudgeCream": (0xFFFEFDED, 0xFF292A1D, "cream nudge"),
    "UsageBarTrack": (0xFFE5DDF9, 0xFF241D33, "progress track"),
    "PlanCardHighlightBorder": (0xFFE1D2FB, 0xFF35284A, "plan highlight"),
    "ErrorCardBackground": (0xFFFFDCDB, 0xFF3A1E1E, "error surface"),
    "PlanPageBackground": (0xFFF1F1F1, 0xFF0E0E10, "plan page background"),
    "PlayStoreReviewNudgeBackground": (0xFFFFFFEB, 0xFF2A2A1C, "cream nudge, copied from Lumen"),
    # borders and dividers
    "CardBorder": (0xFFE2DEE4, 0xFF2E2935, "card border"),
    "DividerGray": (0xFFD9D9D9, 0xFF2B2B2E, "divider"),
    "BorderLight": (0xFFEBE9EC, 0xFF26232B, "light border"),
    "BorderDark": (0xFF27232E, 0xFFD5D0DC, "inverted border"),
    # neutrals: the ramp carries both text and quiet surfaces
    "Neutral200": (0xFFB0ABB2, 0xFF3A3640, "quiet surface"),
    "Neutral300": (0xFFD3DED6, 0xFF4A4E4A, "quiet surface"),
    "Neutral400": (0xFFC9C4CF, 0xFF5A5562, "quiet surface"),
    "Neutral500": (0xFF7F7B81, 0xFF9A959F, "secondary text"),
    "Neutral600": (0xFF656067, 0xFFB0ABB2, "secondary text"),
    "M3Gray": (0xFF49454F, 0xFFC9C4CF, "material secondary text"),
    "Neutral800": (0xFF312D37, 0xFFDAD5E0, "text"),
    "Neutral850": (0xFF1D1B20, 0xFFEBE7EF, "text"),
    "Neutral900": (0xFF191521, 0xFFF5F2F8, "primary text"),
    # indicators
    "IndicatorInactive": (0xFFC4C6CA, 0xFF5A5562, "inactive indicator"),
    "LoadingIndicatorColor": (0xFFAEACB0, 0xFF8A8690, "spinner"),
    "ShimmerBaseColor": (0xFFE0E0E0, 0xFF26262A, "shimmer"),
    "EmptyStateTextColor": (0xFFC10837, 0xFFFF6B8A, "empty-state text"),
    "SheetDragHandle": (0xFF000000, 0xFF3A3640, "bottom-sheet handle, unreadable on a dark sheet"),
}


def getter(name: str) -> str:
    return "get" + name[0].upper() + name[1:]


def hooks() -> list[dict]:
    out = []
    for name, (before, after, why) in PALETTE.items():
        out.append(
            {
                "id": f"palette.{name}",
                "class": "com/wispr/design/ColorKt",
                "why": f"{why}; every use of this colour comes through the getter.",
                "method": f".method public static final {getter(name)}()J",
                "anchor": (
                    f"    sget-wide v0, Lcom/wispr/design/ColorKt;->{name}:J\n\n"
                    f"    return-wide v0\n"
                ),
                "original": (
                    f"    sget-wide v0, Lcom/wispr/design/ColorKt;->{name}:J\n\n"
                    f"    return-wide v0\n"
                ),
                "replacement": f"    const-wide v0, 0x{after:08x}L\n\n    return-wide v0\n",
                "marker": f"patch:dark-theme:palette.{name}",
                "returns": "J",
            }
        )
    return out


KOTLIN = """/*
 * Copyright (C) 2026 V4inwald
 *
 * This file is part of wispr-flow-patches, GPL-3.0.
 *
 * Generated from patches/dark-theme/palette.py — edit that table, re-run it, do not edit here.
 */

package app.v4inwald.wisprflow.patches

/** Colour constant of `ColorKt`, and the value the dark palette gives it. */
internal val DARK_PALETTE: Map<String, String> = mapOf(
%s)
"""


def kotlin() -> str:
    lines = []
    for name, (before, after, _) in PALETTE.items():
        lines.append(f'    "{name}" to "0x{after:08x}L",')
    return KOTLIN % "\n".join(lines)


if __name__ == "__main__":
    target = Path(__file__).resolve().parent.parent / "src/main/kotlin/app/v4inwald/wisprflow/patches/DarkPalette.kt"
    target.write_text(kotlin(), encoding="utf-8")
    print(f"{target.name}: {len(PALETTE)} colours")
