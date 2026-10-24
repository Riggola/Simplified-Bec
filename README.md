# Simplified BEC (GTNH 2.9 RC2) — experimental addon

Standalone Forge addon for GregTech 5 Unofficial. **The original GT5/BEC files are untouched.**

## What this first version does

- Registers a separate **Simplified BEC Assembler** controller (default GT MTE ID `32680`).
- Uses the existing **BEC I/O Node** shape (7×23×13) and casings, without requiring a connection to the normal BEC network.
- Allows standard GT input/output hatches and buses, including dual-input crafting hatches / **AE2 Crafting Input Buffer**, and normal or exotic energy hatches.
- Copies **all available recipes** from GT5's `condensateAssemblingRecipes` into a *separate* NEI recipe map using GT5's own complete-load callback. This runs after GT5 registers late BEC recipes, not at Forge post-initialization.
- Reuses the BEC Assembler's original 16-item / 4-fluid NEI layout rather than the generic 3×3 recipe screen.
- Converts each entangled condensate fluid into its actual **source fluid**, preserving amount 1:1 (e.g. 144 mB entangled neutronium → 144 mB molten neutronium), with the same items, outputs and duration.
- Consumes **10×** the original recipe EU/t by default, configurable. It uses GT's **64-bit extended-power multiblock** and the ProcessingLogic EU multiplier instead of multiplying the signed 32-bit `GTRecipe.mEUt`; this is necessary for high-tier BEC recipes.
- Uses **1–15 physical Nanite Containment Hatches**, the same GregTech hatches as the original BEC Assembler, placed on the multiblock's Electromagnetically Isolated Casings. The source BEC's 30,720-nanite capacity applies. Nanites remain in their hatches and are **not consumed**.
- Automatically runs as many parallel recipes as permitted by **the ingredients actually received, available hatch EU/t, output capacity and configurable parallel cap** (30,720 by default). Uses the native GregTech parallel-input consumer rather than manufacturing extra outputs without inputs.
- Adapts the original **per-tick nanite progress algorithm**, including fractional-tick accumulation, the **minimum tier** across all installed nanites and the recipe's `NANITE_TIERS` phase requirements. Each game tick advances `floor((remainder + availableNanites) / activeParallels)` recipe ticks, provided the current phase's tier is met. **Nanites above the required tier receive no speed penalty**; nanites below it still block progress. Standard GregTech overclocking is disabled. Entangled condensate availability, clogging and control-hatch pauses are intentionally omitted.
- Provisional shapeless controller recipe: one original BEC I/O Node controller → one Simplified BEC controller.

### AE2 Crafting Input Buffer: important detail

Keep the **same normal processing pattern**, containing the source fluids and the required items. **Do not put nanites in it**: insert real nanites into the physical Nanite Containment Hatches, just like on the BEC Assembler. Requesting larger output quantities from AE2 makes AE2 send proportionally more recipe ingredients; GT's buffer can accumulate them and the controller will process as many copies in one operation as are available. One individual pattern push containing enough materials for only one copy cannot automatically draw extra materials from AE2 storage just because more power or nanites exist.

Example: if one recipe takes materials for 4 casings, requesting just 4 casings only provides those materials and consumes one recipe's inputs. Requesting 400 casings provides materials for 100 recipes; if the Crafting Input Buffer has already received them, the controller may run up to 100 parallels, provided the hatch EU/t and output side can handle it. With 30,720 real nanites at the exact required tier, those 100 parallels advance 307 recipe ticks on the first game tick and carry any remaining fractional progress into the next tick.

## Build

Requires internet on the machine building it and the Java toolchain requested by the GTNH Gradle setup.

On Windows (PowerShell or CMD, from this directory):

```bat
gradlew.bat clean build
```

On Linux:

```sh
./gradlew clean build
```

**Install only `build/release/SimplifiedBEC.jar`** in the `mods` directories of both client and server for the matching GTNH version. The `packagePlayableJar` task (included in `build`) copies this stable filename from RetroFuturaGradle's **reobfuscated `reobfJar` output**, never from the development `jar` output. Do **not** install a JAR from `build/libs/` merely because its filename looks correct: development JARs use mapped Minecraft method names and can crash normal GTNH (e.g. `NoSuchMethodError: NBTTagCompound.getLong`). Keep your normal GT5/GTNH JARs installed.

Before testing, remove the **old SimplifiedBEC JAR** from `mods` and make a backup of your test world. Keep exactly one copy of the addon in each `mods` directory.

## Configuration

File: `config/simplifiedbec.cfg` (created after first run).

- `controllerId` — unique GregTech meta-tile ID, default `32680`. Change if it conflicts with another addon.
- `becEnergyMultiplier` — effective power multiplier, default `10` (valid 1–100). Recipes in NEI display the unmultiplied EU/t stored in GTRecipe; **the actual running multiblock consumes the configured multiplier**.
- `becMaxParallels` — configurable safety cap for simultaneous recipes, default `30720`, valid 1–1,000,000; actual parallels are limited by available input resources, output space and hatch EU/t. This is separate from the 30,720 cap on stored nanites.

## Limitations / test checklist

**This is an experimental first version, not a tested release.** Compilation and runtime testing could not be performed in the preparation environment because Gradle distribution downloads were unavailable.

1. Verify that the game starts and the new controller appears in NEI.
2. Verify that converted recipes appear under `Simplified BEC Assembling` and show **base fluids**.
3. Form the I/O Node frame; use a normal casing in any otherwise-special LoS hatch position. Place input/output buses and an energy/maintenance hatch on **electromagnetically isolated casings**. Add **1 to 15 original Nanite Containment Hatches** to the same casing positions and fill them with actual nanites.
4. Confirm an AE2 Crafting Input Buffer works with patterns containing both item and fluid inputs. For reliable high-tier tests, use an exotic/multi-amp energy hatch with sufficient power.
5. Confirm in-game consumption is 10× the source recipe; some GUIs/NEI may show original EU/t because the multiplication happens in the processing logic.
6. Request 4 casings via AE2. Verify exactly one recipe's materials are consumed. Compare speed with 1, 2048 and 30720 nanites; verify that nanites above the required tier do not slow down crafting, while lower-tier nanites pause it.
7. Request 400 casings using the same pattern, and confirm the buffer can accumulate 100 sets of inputs, the controller runs multiple parallels, and consumes the matching number of item and fluid inputs without duplicating outputs.
8. Try an energy-starved hatch, a full output hatch and a large (>2^31 total EU/t) recipe to validate the automatic caps and 64-bit usage.
9. Confirm it functions on the target **GTNH 2.9 RC2** installation; custom modpack patches or recipe-script changes may need more work.

Recipes added *after* GT5's complete-load callback are not included. Compare the `source`, `registered`, and `not convertible` counts printed as `Simplified BEC recipes:` in `logs/latest.log`. No automatic migration of old BEC structures/patterns is performed. No custom controller texture or final balanced crafting recipe has been designed yet.

Original GT5 source reference: `GTNewHorizons/GT5-Unofficial` tag `5.09.54.194`.
