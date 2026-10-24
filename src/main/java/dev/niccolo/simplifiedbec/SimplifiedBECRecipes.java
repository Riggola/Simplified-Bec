package dev.niccolo.simplifiedbec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import gregtech.api.enums.CondensateType;
import gregtech.api.enums.NaniteTier;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBackend;
import gregtech.api.recipe.RecipeMapBuilder;
import gregtech.api.recipe.RecipeMetadataKey;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeBuilder;
import gregtech.api.util.GTRecipeConstants;
import tectech.recipe.BECAssemblyFrontend;
import tectech.recipe.TecTechRecipeMaps;
import tectech.thing.gui.TecTechUITextures;

/** Translates already-registered BEC recipes without modifying the originals. */
public final class SimplifiedBECRecipes {

    public static final RecipeMap<RecipeMapBackend> RECIPES = RecipeMapBuilder.of("gt.recipe.simplified-bec")
        .maxIO(16, 1, 4, 0)
        // Match the original BEC assembler's 16-item / 4-fluid NEI layout.
        // Do not use the original backend: it hides entangled fluid inputs in metadata.
        .logo(TecTechUITextures.PICTURE_TECTECH_LOGO)
        .logoSize(18, 18)
        .logoPos(151, 63)
        .neiRecipeBackgroundSize(170, 90)
        .neiTransferRect(88, 8, 18, 72)
        .neiTransferRect(124, 8, 18, 72)
        .neiTransferRect(142, 26, 18, 18)
        .frontend(BECAssemblyFrontend::new)
        .neiHandlerInfo(builder -> builder.setDisplayStack(SimplifiedBECMod.controller.getStackForm(1L)))
        .build();

    private SimplifiedBECRecipes() {}

    public static void register() {
        int sourceCount = 0;
        int added = 0;
        int failed = 0;
        int withoutCondensate = 0;

        for (GTRecipe source : TecTechRecipeMaps.condensateAssemblingRecipes.getAllRecipes()) {
            sourceCount++;
            FluidStack[] entangled = source.getMetadata(GTRecipeConstants.CONDENSATE_INPUT);
            if (entangled == null || entangled.length == 0) {
                withoutCondensate++;
                continue;
            }

            List<FluidStack> originals = new ArrayList<>();
            boolean valid = true;
            for (FluidStack stack : entangled) {
                if (stack == null) {
                    valid = false;
                    break;
                }
                CondensateType type = CondensateType.getCondensateType(stack.getFluid());
                if (type == null || type.getSourceFluid() == null) {
                    valid = false;
                    break;
                }
                // Exactly 1 mB of source fluid for 1 mB of the corresponding entangled fluid.
                originals.add(
                    new FluidStack(
                        type.getSourceFluid()
                            .getFluid(),
                        stack.amount));
            }
            if (!valid) {
                failed++;
                continue;
            }

            try {
                GTRecipe copy = copyWithMetadata(source, originals.toArray(new FluidStack[0]));
                if (copy == null) {
                    failed++;
                    continue;
                }
                // Don't register a recipe that the controller will inevitably reject.
                if (copy.getMetadata(GTRecipeConstants.NANITE_TIERS) == null) {
                    throw new IllegalStateException("BEC recipe lost its nanite tier metadata");
                }
                if (RECIPES.add(copy) != null) added++;
                else failed++;
            } catch (RuntimeException exception) {
                failed++;
                SimplifiedBECMod.LOG.warn("Could not convert BEC recipe {}", source.mOutputs, exception);
            }
        }

        SimplifiedBECMod.LOG.info(
            "Simplified BEC recipes: {} source, {} registered, {} not convertible, {} without condensate; {}x EU/t",
            sourceCount,
            added,
            failed,
            withoutCondensate,
            SimplifiedBECConfig.energyMultiplier);
    }

    /**
     * GTRecipe.copy() deliberately resets metadata to EmptyRecipeMetadataStorage.
     * BEC recipes require NANITE_TIERS, so using copy() silently makes every
     * recipe fail the controller's validation even though it appears in NEI.
     * Rebuild the recipe with GTRecipeBuilder to retain those metadata values.
     */
    private static GTRecipe copyWithMetadata(GTRecipe source, FluidStack[] sourceFluids) {
        NaniteTier[] tiers = source.getMetadata(GTRecipeConstants.NANITE_TIERS);
        if (tiers == null || tiers.length != source.mInputs.length) {
            SimplifiedBECMod.LOG
                .warn("Skipping BEC recipe without valid nanite tiers: {}", Arrays.toString(source.mOutputs));
            return null;
        }

        GTRecipeBuilder builder = GTRecipeBuilder.builder()
            .itemInputsUnified(copyItems(source.mInputs), source.mInputChances)
            .itemOutputs(copyItems(source.mOutputs), source.mOutputChances)
            .fluidInputs(sourceFluids, source.mFluidInputChances)
            .fluidOutputs(copyFluids(source.mFluidOutputs), source.mFluidOutputChances)
            .duration(source.mDuration)
            .eut(source.mEUt)
            .special(source.mSpecialItems)
            .specialValue(source.mSpecialValue);

        if (!source.mEnabled) builder.disabled();
        if (source.mHidden) builder.hidden();
        if (source.mFakeRecipe) builder.fake();
        if (!source.mCanBeBuffered) builder.noBuffer();
        if (source.mNeedsEmptyOutput) builder.needsEmptyOutput();
        if (source.isNBTSensitive) builder.nbtSensitive();

        for (Map.Entry<RecipeMetadataKey<?>, Object> metadata : source.getMetadataStorage()
            .getEntries()) {
            // Entangled fluid metadata belongs to the original BEC only; the
            // simplified recipe consumes the converted source fluids directly.
            if (metadata.getKey() == GTRecipeConstants.CONDENSATE_INPUT) continue;
            restoreMetadata(builder, metadata.getKey(), metadata.getValue());
        }

        // No original BEC recipe category: register under the simplified map.
        return builder.build()
            .orElse(null);
    }

    private static <T> void restoreMetadata(GTRecipeBuilder builder, RecipeMetadataKey<T> key, Object value) {
        builder.metadata(key, key.cast(value));
    }

    private static ItemStack[] copyItems(ItemStack[] stacks) {
        return Arrays.stream(stacks)
            .map(stack -> stack == null ? null : stack.copy())
            .toArray(ItemStack[]::new);
    }

    private static FluidStack[] copyFluids(FluidStack[] stacks) {
        return stacks == null ? new FluidStack[0]
            : Arrays.stream(stacks)
                .map(stack -> stack == null ? null : stack.copy())
                .toArray(FluidStack[]::new);
    }
}
