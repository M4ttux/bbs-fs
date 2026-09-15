<div align="center">

# 🎚️ BBS Curve Fixer

**A Fabric addon that makes shaders fully animatable in the [BBS](https://www.mchorse.com/) machinima mod — on _stock_ BBS, no fork required.**

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1%20%C2%B7%201.20.4%20%C2%B7%201.21.1%20%C2%B7%201.21.11-brightgreen)](#-requirements)
[![Loader](https://img.shields.io/badge/Loader-Fabric-1976d2)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-17-e76f00)](https://adoptium.net/)
[![BBS](https://img.shields.io/badge/BBS-2.6%2B-7e57c2)](https://www.mchorse.com/)
[![License](https://img.shields.io/badge/License-MIT-f5c518)](LICENSE)

</div>

---

## What it does

BBS can already drive shader options with animation curves — but on stock BBS that pipeline has two sharp edges:

1. Only the handful of options a pack lists under `sliders=` are offered as curvable.
2. The "add curve" action shows a **flat, unsorted list** of raw option ids.

**Curve Fixer fixes both.** It widens what's curvable to *every* float option a pack exposes, and replaces the flat list with a proper, BBS-styled picker that mirrors the in-game Iris shader-options menu.

Everything lives in the addon — **base BBS is never modified** (mixins + events only), so it stays compatible across BBS updates.

---

## ✨ Highlights

- **🎛️ Every float option becomes curvable.** GLSL only requires *integers* to be compile-time constants (array sizes, loop bounds) — a float is always safe to drive from a per-frame uniform. So Curve Fixer makes **all** float options animatable instead of only the `sliders=` ones; integers stay limited to those the pack author explicitly exposed as sliders. Packs that surface most numeric options as cycling value boxes — e.g. **Photon** (≈ 2 sliders vs ~297 numeric defines) — go from *barely* animatable to *almost fully* animatable.

- **🔎 Smarter exclusion scan.** `#ifdef` / `#ifndef` guards test whether a macro is *defined*, not its value — so options behind them (e.g. Complementary's `DOF_FOCUS_DISTANCE`) stay curvable. Only true value-conditionals (`#if` / `#elif EXPR`) lock an option to a compile-time constant. Matching is **whole-identifier**, so `DOF_FOCUS` no longer wrongly drops its longer neighbour `DOF_FOCUS_DISTANCE`.

- **🗂️ A real picker UI.** The "add curve" action opens a stock-BBS-styled mirror of the Iris shader-options menu — sub-screens, columns, sliders, toggles, and profiles, laid out exactly as Iris shows them. Curvable options are clickable (**click = add a curve channel**), already-animated options carry a green outline, and non-curvable ones are shown for context. A one-click **Legacy list** button always falls back to BBS's stock picker.

- **🪶 Degrades gracefully.** Iris and RefreshedUI are both optional. No Iris → the picker steps aside and you get BBS's stock list. No RefreshedUI → cells render as plain stock boxes instead of rounded ones. Nothing crashes when they're absent.

---

## 📦 Requirements

| | |
|---|---|
| **Minecraft** | 1.20.1 · 1.20.4 · 1.21.1 · 1.21.11 |
| **Loader** | Fabric |
| **Java** | 17+ (21+ on MC 1.21.x, which requires it anyway) |
| **Required** | BBS `2.6` or newer (not pinned to a BBS version), Fabric API |
| **Optional** | **Iris** — enables the shader-options picker (and is what loads shaders in the first place) · **RefreshedUI** — rounds the picker's cells to match the refreshed theme |

---

## 🚀 Install

1. Install **Fabric** + **Fabric API**.
2. Drop **BBS 2.6+** and `curvefixer-<version>-<mc>.jar` into your `mods/` folder — or the single
   `curvefixer-<version>-universal.jar`, which covers 1.20.1, 1.20.4 and 1.21.1 (1.21.11 has its own jar).
3. *(Recommended)* add **Iris** (for the picker) and **RefreshedUI** (for rounded cells).
4. In BBS: open a film, add a **Curve** clip, and hit **add curve** — the shader-options picker opens.

---

## 🛠️ Build

One source tree, four Minecraft targets — pick the target per build:

```sh
./gradlew build                  # MC 1.20.1 (default)    -> build/libs/curvefixer-0.3.0-1.20.1.jar
./gradlew build -Pmc=1.20.4      # MC 1.20.4              -> build/libs/curvefixer-0.3.0-1.20.4.jar
./gradlew build -Pmc=1.21.1      # MC 1.21.1              -> build/libs/curvefixer-0.3.0-1.21.1.jar
./gradlew build -Pmc=1.21.11     # MC 1.21.11             -> build/libs/curvefixer-0.3.0-1.21.11.jar
./gradlew build -Pmc=universal   # one jar, 1.20.1–1.21.1 -> build/libs/curvefixer-0.3.0-universal.jar
./gradlew runClient              # dev client (MC 1.20.1)
./gradlew runClient -Pmc=1.21.1  # dev client (MC 1.21.1)
```

> **JDK 17 and JDK 21 required** — Gradle picks the toolchain per target (1.21.1 needs 21 to read Minecraft's Java 21 class files). The emitted bytecode is Java 17 on *every* target, which is what keeps the mixin configs on `compatibilityLevel: JAVA_17` and lets one jar load on both runtimes.
>
> The BBS jars in `libs/` are the build dependency (one per MC version). Iris and RefreshedUI are `compileOnly` — the addon only touches their stable, mapping-independent APIs, so they're never bundled and stay optional at runtime.

### Why a single jar can span three Minecraft versions

The whole addon touches exactly **one** vanilla member — `MinecraftClient.getInstance().isOnThread()` — and ships an empty access widener. Everything else it binds to is BBS, Iris or RefreshedUI, none of which are remapped. That one member is unchanged from 1.20.1 to 1.21.1, so the intermediary-mapped jar resolves everywhere. Adding a second vanilla binding is what would force the `universal` target to shrink.

---

## 🧩 How it hooks into BBS

| Seam | File | Mechanism |
|---|---|---|
| Make every float curvable | [`mixin/client/ShaderCurvesMixin`](src/client/java/org/qualet/curvefixer/mixin/client/ShaderCurvesMixin.java) | `@Overwrite` of the private `removeIrrelevantVariables` *(unchanged through BBS 2.6)* |
| Reroute "add curve" → picker | [`mixin/client/UICurveClipMixin`](src/client/java/org/qualet/curvefixer/mixin/client/UICurveClipMixin.java) | `@Inject(HEAD, cancellable)` on `offerCurveKeys` |
| Read the live Iris pack menu | [`iris/CurveFixerIris`](src/client/java/org/qualet/curvefixer/iris/CurveFixerIris.java) + [`ShaderMenu`](src/client/java/org/qualet/curvefixer/iris/ShaderMenu.java) | Iris-only, gated behind `isModLoaded("iris")` |
| The picker UI | [`client/ui/shader/UIShaderOptionPicker`](src/client/java/org/qualet/curvefixer/client/ui/shader/UIShaderOptionPicker.java) + cells | stock BBS UI elements |

**Design rule:** self-contained logic → plain classes · surgical BBS tweaks → injections (`@Inject` / `@ModifyExpressionValue` / `@Redirect`) · only a fully-rewritten private method uses `@Overwrite` (kept to exactly one, version-pinned).

---

## 📁 Layout

```
build.gradle, gradle.properties, settings.gradle    # Loom build (multi-version, -Pmc switch)
libs/                                                # BBS (per MC version) + Iris/RefreshedUI APIs (compileOnly)
src/main/java/org/qualet/curvefixer/
  CurveFixerAddon.java                               # bbs-addon entry (BBSAddonMod): source pack + settings
  mixin/BBSSettingsMixin.java                        # registers a settings group under personalization
  resources/CurveFixerAssetsSourcePack.java          # ISourcePack (asset override path, wired & ready)
src/client/java/org/qualet/curvefixer/
  client/ShaderCurvePicker.java                      # decides: refreshed picker vs stock list (Iris-gated)
  client/ui/shader/UIShaderOptionPicker.java         # the BBS-styled picker (+ RoundedCellSkin, cells)
  iris/CurveFixerIris.java, iris/ShaderMenu.java      # snapshot the live Iris menu into a neutral model
  mixin/client/ShaderCurvesMixin.java                # all-floats-curvable + whole-identifier filtering
  mixin/client/UICurveClipMixin.java                 # reroute "add curve" to the picker
```

---

## 📄 License

MIT © 2026 qualet — see [LICENSE](LICENSE).
