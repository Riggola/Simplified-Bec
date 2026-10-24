# Simplified BEC (GTNH 2.9 RC2) — experimental addon

Standalone Forge addon for GregTech 5 Unofficial. **The original GT5/BEC files are untouched.**

## What this first version does

- Registers a separate **Simplified BEC Assembler** controller (default GT MTE ID `32680`).
- Uses the existing **BEC I/O Node** shape (7×23×13) and casings, without requiring a connection to the normal BEC network.
- Allows standard GT input/output hatches and buses, including dual-input crafting hatches / **AE2 Crafting Input Buffer**, and normal or exotic energy hatches.
- Copies the recipes from GT5's `condensateAssemblingRecipes` into a *separate* NEI recipe map at post-initialization.
- Converts each entangled condensate fluid into its actual **source fluid**, preserving amount 1:1 (e.g. 144 mB entangled neutronium → 144 mB molten neutronium), with the same items, outputs and duration.
- Consumes **10×** the original recipe EU/t by default, configurable. It uses GT's **64-bit extended-power multiblock** and the ProcessingLogic EU multiplier instead of multiplying the signed 32-bit `GTRecipe.mEUt`; this is necessary for high-tier BEC recipes.
- Provisional shapeless controller recipe: one original BEC I/O Node controller → one Simplified BEC controller.

## Build

Requires internet on the machine building it and the Java toolchain requested by the GTNH Gradle setup.

On Windows (PowerShell or CMD, from this directory):

```bat
gradlew.bat build
```

On Linux:

```sh
./gradlew build
```

Copy the generated addon JAR from `build/libs/` into **both client and server** `mods` directories for the matching GTNH version. Don't install the developer/source JAR. Keep your normal GT5/GTNH JARs installed.

## Configuration

File: `config/simplifiedbec.cfg` (created after first run).

- `controllerId` — unique GregTech meta-tile ID, default `32680`. Change if it conflicts with another addon.
- `becEnergyMultiplier` — effective power multiplier, default `10` (valid 1–100). Recipes in NEI display the unmultiplied EU/t stored in GTRecipe; **the actual running multiblock consumes the configured multiplier**.

## Limitations / test checklist

**This is an experimental first version, not a tested release.** Compilation and runtime testing could not be performed in the preparation environment because Gradle distribution downloads were unavailable.

1. Verify that the game starts and the new controller appears in NEI.
2. Verify that converted recipes appear under `Simplified BEC Assembling` and show **base fluids**.
3. Form the I/O Node frame; use a normal casing in any otherwise-special LoS hatch position. Place input/output buses and an energy/maintenance hatch on **electromagnetically isolated casings**.
4. Confirm an AE2 Crafting Input Buffer works with patterns containing both item and fluid inputs. For reliable high-tier tests, use an exotic/multi-amp energy hatch with sufficient power.
5. Confirm in-game consumption is 10× the source recipe; some GUIs/NEI may show original EU/t because the multiplication happens in the processing logic.
6. Confirm it functions on the target **GTNH 2.9 RC2** installation; custom modpack patches or recipe-script changes may need more work.

Recipes added *after* Forge post-init are currently not included. No automatic migration of old BEC structures/patterns is performed. No custom controller texture or final balanced crafting recipe has been designed yet.

Original GT5 source reference: `GTNewHorizons/GT5-Unofficial` tag `5.09.54.194`.
