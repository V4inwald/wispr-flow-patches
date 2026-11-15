/*
 * Copyright (C) 2026 V4inwald
 *
 * This file is part of wispr-flow-patches, GPL-3.0.
 */

package app.v4inwald.wisprflow.patches

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.all.misc.fix.changepackageinstaller.changePackageInstallerPatch

private const val PLAY_STORE = "com.android.vending"

private val COMPATIBILITY_WISPR_FLOW = Compatibility(
    packageName = "com.wispr.flowapp",
    name = "Wispr Flow",
    description = "Dictation app that inserts text with an accessibility service.",
    // The app ships as a split bundle: a base APK alone fails INSTALL_FAILED_MISSING_SPLIT.
    apkFileType = ApkFileType.APKM,
    appIconColor = 0x1F1F1F,
    signatures = setOf("0f0f93d294c479ccd0ddce1bad232af5640c1faaa468612869c2208d7714c432"),
    targets = listOf(AppTarget(version = "2.6.2", minSdk = 33)),
)

/**
 * Makes the app report that it was installed from the Play Store.
 *
 * The app ships Google Play PairIP: `com.pairip.application.Application.attachBaseContext` calls
 * `LicenseClient.checkLicense()` on every start, the Play licensing service answers against the
 * Play signing certificate, and a locally re-signed build fails that check by construction. The
 * local fallback then asks the installer package name, which a side-loaded install answers
 * differently, and the app stops on its licensing dialog before any patched code runs.
 *
 * The hooks come from the patches library instead of being re-implemented here: it replaces the
 * result of every `getInstallerPackageName` / `getInstallingPackageName` /
 * `getInitiatingPackageName` / `getPackageSource` call site, which covers the PairIP gate and the
 * analytics and crash-report tagging that would otherwise record a side-load.
 */
@Suppress("unused")
val playStoreSourcePatch =
    bytecodePatch(
        name = "Play Store install source",
        description = "Reports the app as installed from the Play Store, so the bundled Play licensing check accepts a re-signed build.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_WISPR_FLOW)

        dependsOn(
            changePackageInstallerPatch { PLAY_STORE },
        )
    }
