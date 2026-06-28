# BBS Curve Fixer

A Fabric addon for the **BBS** mod (`bbs_mod`), targeting Minecraft 1.20.1 and 1.20.4.
Built from the addon skeleton: it loads as-is and is the base the real Curve Fixer feature grows on.

- **mod id:** `curvefixer`
- **package:** `org.qualet.curvefixer`
- **MC / Java:** 1.20.1 & 1.20.4 / 17, Fabric Loom, split `main` + `client` source sets
- **BBS dep:** `libs/bbs-2.3.1-1.20.1.jar` / `libs/bbs-2.3.1-1.20.4.jar` (one per target)

## Layout

```
build.gradle, gradle.properties, settings.gradle   # Loom build (multi-version, -Pmc switch)
libs/bbs-2.3.1-1.20.1.jar, bbs-2.3.1-1.20.4.jar     # BBS, as a mod dependency (per MC version)
src/main/java/org/qualet/curvefixer/
  CurveFixerAddon.java                              # bbs-addon entry (BBSAddonMod)
  mixin/BBSSettingsMixin.java                       # example mixin: registers a settings group
  resources/CurveFixerAssetsSourcePack.java         # ISourcePack stub (serves nothing yet)
src/client/java/org/qualet/curvefixer/client/
  CurveFixerClient.java                             # client entry (ClientModInitializer)
  CurveFixerStrings.java                            # runtime l10n (en/ru)
src/main/resources/
  fabric.mod.json                                   # entrypoints + mixins + accessWidener
  curvefixer.mixins.json                            # common mixins (BBSSettingsMixin)
  curvefixer.client.mixins.json                     # client mixins (empty, ready)
  curvefixer.accesswidener                          # empty, ready
  assets/curvefixer/icon.png
```

## Build & run

One source tree, two Minecraft targets. Pick the target per build:

```sh
./gradlew build                  # -> build/libs/curvefixer-0.1.0-1.20.1.jar   (MC 1.20.1, default)
./gradlew build -Pmc=1.20.4      # -> build/libs/curvefixer-0.1.0-1.20.4.jar   (MC 1.20.4)
./gradlew build -Pmc=universal   # -> build/libs/curvefixer-0.1.0-1.20.x.jar   (one jar, 1.20.1–1.20.4)
./gradlew runClient              # dev client with the addon loaded (MC 1.20.1)
./gradlew runClient -Pmc=1.20.4  # dev client on MC 1.20.4
```

> Requires JDK 17. The BBS jars in `libs/` are built from the `bbs-fs` source — rebuild and update
> the `MC` matrix in `build.gradle` if you bump BBS versions.

## What it does out of the box

- Logs `CurveFixerAddon loaded` and `CurveFixerClient ... initialized` on startup (proof of life).
- Registers a `curvefixer` settings group (one `enabled` toggle) under BBS personalization.
- Registers an (empty) source pack so the asset-override path is wired and ready.

Everything else is a labelled placeholder to replace as the feature lands. See `CLAUDE.md` for the
extension guide and pointers to the `resfreshed-addon` reference project, which has full worked
examples of mixins, settings UI, and asset overrides.

## License

MIT — see `LICENSE`.
