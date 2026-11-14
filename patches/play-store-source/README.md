# Play Store install source

Makes the app report that it was installed from the Play Store, which is what its bundled
Play licensing check accepts.

* **Package**: `com.wispr.flowapp`
* **Target version**: 2.6.2
* **Evidence**: [`WHY.md`](../../WHY.md), [`analysis/wispr-flow-2.6.2.md`](../../analysis/wispr-flow-2.6.2.md) §4.4

## Why it is needed

A patched build is re-signed with a local key. `com.pairip.licensecheck.LicenseClient` calls
the Play licensing service on every start, and that service answers against the Play signing
certificate — a local signature fails it by construction. The local fallback then decides the
outcome from the installer package name, and a side-loaded build reports something other than
`com.android.vending`, so the app ends on its licensing error dialog and does not run.

[`WHY.md`](../../WHY.md) covers the full chain and the argument for restoring the installer
rather than anything else.

## What the patch changes

Seven call sites, across five classes. The call is kept; only the value it returns is
replaced, with `com.android.vending`:

| Class | Method | Sites | Role |
|---|---|---|---|
| `com/pairip/licensecheck/LicenseClient` | `performLocalInstallerCheck` | 1 | the gate that decides whether the app runs |
| `com/wispr/ui/extension/PlayStoreInAppReviewKt` | `installerPackageName` | 1 | in-app review suppression token |
| `com/appsflyer/internal/AFg1zSDK` | `getRevenue` | 2 | attribution reads the initiating and installing installer |
| `com/appsflyer/internal/AFg1zSDK` | `AFAdRevenueData` | 1 | fallback for API levels without `InstallSourceInfo` |
| `com/google/firebase/FirebaseCommonRegistrar` | `lambda$getComponents$3` | 1 | Firebase Analytics `installer` user property |
| `io/sentry/android/core/ContextUtils` | `retrieveSideLoadedInfo` | 1 | crash-report tagging |

The signatures are `PackageManager.getInstallerPackageName(String)`,
`InstallSourceInfo.getInstallingPackageName()` and `InstallSourceInfo.getInitiatingPackageName()`.
Any other call to those methods in a future version is left alone by design: the hooks name
their class and method, so the patch never silently widens.

## What it does not change

* the signing certificate: the build stays locally signed, and the app is told so;
* `RevenueCat` / the `flow_pro` entitlement: a purchase still governs what the account owns;
* the quota logic, which is the [other patch](../unlimited-words/).

## Applying it

```bash
python3 scripts/apply_patches.py --decoded build/decoded --only play-store-source --dry-run
python3 scripts/apply_patches.py --decoded build/decoded --only play-store-source
python3 scripts/apply_patches.py --decoded build/decoded --only play-store-source --revert
```

Unlike the quota patch, this one cannot be dropped from a build: without it a re-signed APK
stops at the licensing dialog.

## Limits

* **Client-side claim only.** Play Integrity API verdicts are untouched; the app does not gate
  on one.
* **Version-specific.** The seven sites are pinned by class and method and by an exact count.
  A new version that adds, moves or removes a site fails the patch instead of applying a
  partial spoof.
* **The claim is only as good as the check.** If a future version verifies the signature on
  the client as well as the installer, this patch stops being sufficient — it does not become
  wrong, it becomes outgrown.
