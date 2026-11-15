/*
 * Copyright (C) 2026 V4inwald
 *
 * This file is part of wispr-flow-patches, GPL-3.0.
 */

package app.v4inwald.wisprflow.patches

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.returnEarly
import app.v4inwald.wisprflow.patches.Constants.COMPATIBILITY_WISPR_FLOW
import com.android.tools.smali.dexlib2.Opcode

private const val THEME_CLASS = "Lcom/wispr/design/ThemeKt;"

/**
 * `FlowAppTheme(dark: Boolean, dynamic: Boolean, content: …)` is the single theme every app
 * screen composes under. Several screens pass `dark = false` explicitly, so the parameter has to
 * be overwritten at the entry of the theme rather than left to its default.
 */
internal object FlowAppThemeFingerprint : Fingerprint(
    definingClass = THEME_CLASS,
    name = "FlowAppTheme",
    returnType = "V",
    parameters = listOf(
        "Z",
        "Z",
        "Lkotlin/jvm/functions/Function2;",
        "Lyo0;",
        "I",
        "I",
    ),
)

/**
 * `isSystemInDarkTheme()` from Compose, seen through its body rather than its name: R8 renames
 * the class and the method, but reading `Configuration.uiMode` and comparing it against the night
 * bit stays. Screens that omit the theme argument fall back to it, which would overwrite the
 * forced parameter further down.
 */
internal object IsSystemInDarkThemeFingerprint : Fingerprint(
    returnType = "Z",
    parameters = listOf("Lyo0;"),
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IGET,
            smali = "Landroid/content/res/Configuration;->uiMode:I",
        ),
    ),
)

private const val COLOR_CLASS = "Lcom/wispr/design/ColorKt;"

/**
 * Forces the app's own interface into a dark palette.
 *
 * Three operations, because the theme is reached three ways: screens that pass `dark = false`
 * are covered by overwriting the parameter, screens that leave it to the system by answering the
 * system question with `true`, and every screen that paints from the fixed palette by replacing
 * the colour each getter returns.
 */
@Suppress("unused")
val darkThemePatch =
    patch(
        name = "Dark theme",
        description = "Forces the app's own interface into its dark colour scheme, which the app never offers.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_WISPR_FLOW)

        execute {
            FlowAppThemeFingerprint.method.addInstructions(0, "const/4 p0, 0x1")

            IsSystemInDarkThemeFingerprint.method.returnEarly(true)

            // The palette: one getter per constant, so overriding the getter repaints every use.
            val colorClass = mutableClassDefBy(COLOR_CLASS)
            DARK_PALETTE.forEach { (constant, value) ->
                val getter = "get" + constant.replaceFirstChar { it.uppercase() }
                val method = colorClass.methods.firstOrNull { it.name == getter }
                    ?: error("ColorKt has no $constant")
                method.replaceInstruction(0, "const-wide v0, $value")
            }
        }
    }
