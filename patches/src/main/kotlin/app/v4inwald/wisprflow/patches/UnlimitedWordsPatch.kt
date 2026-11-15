/*
 * Copyright (C) 2026 V4inwald
 *
 * This file is part of wispr-flow-patches, GPL-3.0.
 */

package app.v4inwald.wisprflow.patches

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch

private const val POLICY_CLASS = "Lcom/wispr/payment/wordlimit/WordLimitPolicy;"
private const val ENGINE_CLASS = "Lcom/wispr/payment/wordlimit/WordLimitEngine;"

private val COMPATIBILITY_WISPR_FLOW = Compatibility(
    packageName = "com.wispr.flowapp",
    name = "Wispr Flow",
    description = "Dictation app that inserts text with an accessibility service.",
    // The app ships as a split bundle: a base APK alone fails INSTALL_FAILED_MISSING_SPLIT.
    apkFileType = ApkFileType.APKM,
    appIconColor = 0xFF1F1F1F.toInt(),
    signatures = setOf("0f0f93d294c479ccd0ddce1bad232af5640c1faaa468612869c2208d7714c432"),
    targets = listOf(AppTarget(version = "2.6.2", minSdk = 33)),
    isLegacy = false,
)

/**
 * `computeState(Z I J)` maps the plan plus the weekly word count onto
 * Warning / Exhausted / HardCapped. `computeGate` turns that state into a `DictationGate`, and
 * `DictationStartGate` refuses the dictation on anything but `Allow`.
 */
internal object WordLimitPolicyComputeStateFingerprint : Fingerprint(
    definingClass = POLICY_CLASS,
    name = "computeState",
    returnType = "Lcom/wispr/payment/wordlimit/WordLimitState;",
    parameters = listOf("Z", "I", "J"),
)

/**
 * `resolveLimited(J)` decides whether the engine treats the account as limited. Left alone it
 * re-derives a limited plan after every dictation, so the quota state comes back between two
 * refresh passes even when `computeState` has been neutralised.
 */
internal object WordLimitEngineResolveLimitedFingerprint : Fingerprint(
    definingClass = ENGINE_CLASS,
    name = "resolveLimited",
    returnType = "Ljava/lang/Boolean;",
    parameters = listOf("J"),
)

/**
 * Removes the free-plan weekly word limit.
 *
 * Both hooks return a constant as the first instruction of the method, which leaves the original
 * logic in place but unreachable: `WordLimitState$None` for every caller, and `false` for the
 * engine — the state a plan would have if the quota did not exist.
 */
@Suppress("unused")
val unlimitedWordsPatch =
    bytecodePatch(
        name = "Unlimited words",
        description = "Removes the free-plan weekly word limit, so dictation keeps working past the quota.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_WISPR_FLOW)

        execute {
            WordLimitPolicyComputeStateFingerprint.method.addInstructions(
                0,
                """
                    sget-object p0, Lcom/wispr/payment/wordlimit/WordLimitState${'$'}None;->INSTANCE:Lcom/wispr/payment/wordlimit/WordLimitState${'$'}None;
                    return-object p0
                """,
            )

            WordLimitEngineResolveLimitedFingerprint.method.addInstructions(
                0,
                """
                    sget-object p0, Ljava/lang/Boolean;->FALSE:Ljava/lang/Boolean;
                    return-object p0
                """,
            )
        }
    }
