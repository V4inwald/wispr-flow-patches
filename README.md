# Wispr Flow patches

Patches for the **Wispr Flow** Android app (`com.wispr.flowapp`), published in two forms:

1. a **standalone** reference implementation (Python + smali) that anyone can read, run and
   verify;
2. a **Morphe patch bundle** built from the same findings, so the patches can be applied on
   device with [Morphe](https://morphe.software).

The repository contains **no application code**: no APK, no DEX, no decompiled sources. It
carries tooling, the reverse-engineering evidence behind each hook, and the patches themselves.

| Patch | What it changes | Standalone | Morphe port | Target |
|---|---|---|---|---|
| [`unlimited-words`](patches/unlimited-words/) | stops the free-plan weekly word quota from gating dictation | yes | planned | 2.6.2 |
| [`play-store-source`](patches/play-store-source/) | reports the app as installed by the Play Store | yes | planned | 2.6.2 |

Both are needed on a re-signed build: the app ships Play licensing, and without the second
patch it refuses to run. [`WHY.md`](WHY.md) explains that chain and why spoofing the installer
is the honest fix rather than a workaround.

## Requirements

* Linux x86-64 (the bootstrap fetches Linux builds)
* Python 3.11+
* JDK 17 — fetched by `scripts/bootstrap.sh`; nothing to install system-wide if you have one
* The **XAPK** (or the base APK plus its config splits) of the version you want to patch

An app bundle's base APK is not installable on its own: it declares
`android:requiredSplitTypes`, and a base-only install session fails with
`INSTALL_FAILED_MISSING_SPLIT`. `scripts/preflight.py` refuses that input and names the missing
split types.

## Usage

```bash
scripts/bootstrap.sh --keystore             # JDK, apktool, jadx, signing tools, local key
scripts/build.sh /path/to/wispr-flow.xapk   # patch, build, sign every split, verify
adb uninstall com.wispr.flowapp
adb install-multiple build/bundle/*.apk     # all pieces, one session, one signature
```

`scripts/build.sh` refuses to finish quietly. If the injected instruction is not present in
the dex that ends up inside the APK, if a split is missing, or if the bundle ends up with two
different signers, it reports a failure instead of handing over a broken build.

## How a patch is described

Each patch is a manifest under `patches/<name>/patch.json` plus a README. A hook is one of two
kinds:

* **`anchor`** — replaces one literal sequence of smali instructions with another, used when a
  method's whole logic is short-circuited. The original instructions stay below the injected
  block, unreachable, so the diff reads as a prefix.
* **`callsite`** — matches an `invoke-*` on a given signature and replaces the `move-result*`
  that follows it, leaving the call in place. Used when the same API is read from several
  classes and every read must be answered the same way.

Every hook carries a `why` and states how many sites it expects. Hooks are idempotent, refuse
to apply when the anchor is missing or the site count differs, and `--revert` restores the
original bytes — `scripts/apply_patches.py --revert` undoes a build.

## Repository layout

```
analysis/     reverse-engineering notes and the small tools that produced them
patches/      the patches, as manifests shared by this pipeline and the Morphe port
scripts/      bootstrap, preflight, decompile, apply, repack, sign, verify
tools/        downloaded toolchain and local keystore (gitignored)
tests/        unit tests over synthetic fixtures — no APK, no toolchain needed
```

## Scope and limits

These patches are for **personal, interoperable use on devices you own, on a copy of the app
you obtained yourself**. They change client-side logic only: no server is spoofed, nothing is
redistributed, and no entitlement is invented. See [`DISCLAIMER.md`](DISCLAIMER.md).

What is claimed here is what is observed offline: unit tests, a full build, a check of the
injected instructions in the built dex, and a check that the signed bundle is complete and
single-signed. Device behaviour — does the app launch, does dictation survive past the quota —
is the one thing no tool in this container can certify, and is not claimed.

## Licence

[Mozilla Public License 2.0](LICENSE). MPL-2.0 is compatible with the GPLv3 used by Morphe, so
this code can be reused or upstreamed there.
