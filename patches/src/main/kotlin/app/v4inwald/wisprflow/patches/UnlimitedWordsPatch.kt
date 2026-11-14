/*
 * Copyright (C) 2026 V4inwald
 *
 * This file is part of wispr-flow-patches, GPL-3.0.
 */

package app.v4inwald.wisprflow.patches

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.patch

private const val POLICY_CLASS = "Lcom/wispr/payment/wordlimit/WordLimitPolicy;"
private const val ENGINE_CLASS = "Lcom/wispr/payment/wordlimit/WordLimitEngine;"

/**
 * `computeState(Z I J)` maps the plan plus the weekly word count onto
 * Warning / Exhausted / HardCapped. `computeGate` turns the returned state into a
 * `DictationGate`, and `DictationStartGate` refuses the dictation on anything but `Allow`.
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
 * engine, which is the state a plan would have if the quota did not exist.
 */
@Suppress("unused")
val unlimitedWordsPatch =
    patch(
        name = "Unlimited words",
        description = "Removes the free-plan weekly word limit, so dictation keeps working past the quota.",
        default = true,
    ) {
        compatibleWith("com.wispr.flowapp" to "2.6.2")

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
