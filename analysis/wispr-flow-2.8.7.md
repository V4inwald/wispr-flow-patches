# Wispr Flow 2.8.7 — what moved since 2.6.2

The patches were re-derived against **2.8.7** (versionCode 186, targetSdk 37). This note records
only the delta: what changed in the code the hooks touch, and why each hook still holds where it
does. The wider analysis of the app stays in [`wispr-flow-2.6.2.md`](wispr-flow-2.6.2.md) — its
findings about the dictation chain, the backend and the licensing design are unchanged.

**Artefact**: base APK of the 2.8.7 XAPK, SHA-256 `9707f18a43762c90f6837d79d9f986db32db009c8c897fbe1c6487028ba04653`
**Signing certificate**: `0f0f93d294c479ccd0ddce1bad232af5640c1faaa468612869c2208d7714c432` — unchanged.

---

## 1. The quota gate renames its argument

`WordLimitPolicy.computeState` now takes a `WeeklyWordLimits` object instead of a bare word count:

| 2.6.2 | 2.8.7 |
|---|---|
| `computeState(Z I J)` | `computeState(Z I Lcom/wispr/payment/wordlimit/WeeklyWordLimits; J)` |

`weeklyLimits.getHard()`, `getSoft()` and `getWarningThreshold()` are read inside, so the
thresholds are no longer inlined constants: they are per-account values from the backend. The
states and the branch order are otherwise identical, `HardCapped` → `Exhausted` → `Warning` →
`None`.

**The hook is unchanged in effect.** Returning `WordLimitState$None` as the first instruction still
short-circuits everything, and `computeGate` still maps `None` to `DictationGate$Allow`
(`WordLimitPolicy.smali`, `:cond_7` → `:cond_9`), so `DictationStartGate` still decides `Start`.
The anchor moved from `if-nez p1, :cond_0` to the `getClass()` null check that now opens the body.

`WordLimitEngine.resolveLimited(J)` is untouched and keeps its anchor.

## 2. The installer reads multiply and disappear from named classes

The PairIP gate is the same (`LicenseClient.performLocalInstallerCheck`, same
`getInstallingPackageName` comparison), but two things make per-class targeting untenable:

* **`FirebaseCommonRegistrar` no longer reads the installer at all.** Its former hook target, a
  synthetic lambda, is gone.
* **New readers appear in obfuscated classes.** `io/sentry/android/core/ContextUtils` is now
  `io/sentry/android/core/n0`, and the app grew `com/wispr/flowapp/analytics/InstallSourceTelemetry`,
  which normalises the installer into a `play`/`other` analytics property and reads
  `getPackageSource()` next to it. Two further readers sit in `ln0` and `j65`, classes whose names
  carry nothing.

Naming a class per site would mean re-deriving names that change every release. All ten call sites
were instead verified to query **this** package (`getPackageName()` or
`PackageInfo.packageName`), which makes a tree-wide answer safe.

| Signature | Sites | 2.6.2 location |
|---|---|---|
| `PackageManager.getInstallerPackageName` | 4 | `AFg1zSDK`, `ln0`, `j65`, Sentry `n0` |
| `InstallSourceInfo.getInstallingPackageName` | 4 | `LicenseClient`, `PlayStoreInAppReviewKt`, `InstallSourceTelemetry`, `AFg1zSDK` |
| `InstallSourceInfo.getInitiatingPackageName` | 1 | `AFg1zSDK` |
| `InstallSourceInfo.getPackageSource` | 1 | `InstallSourceTelemetry` |

`getPackageSource` returns an `int` and needs `const/4 …, 0x2`
(`PACKAGE_INSTALLER_PACKAGE_SOURCE_STORE`), which is what
`InstallSourceTelemetry.packageSourceValue` translates to `store`.

## 3. What this costs

The count is the guard: a release that adds or drops a site fails both patches instead of
answering half of them. A release that adds a site reading *another* package would be masked —
that is the trade for surviving obfuscation, and it is written down here so the next reader knows
to check it.
