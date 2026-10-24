package dev.niccolo.simplifiedbec;

import java.util.ArrayList;
import java.util.List;

import net.minecraftforge.fluids.FluidStack;

import gregtech.api.enums.CondensateType;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBackend;
import gregtech.api.recipe.RecipeMapBuilder;
import gregtech.api.util.GTRecipe;
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

            GTRecipe copy = source.copy();
            copy.mFluidInputs = originals.toArray(new FluidStack[0]);
            // Power is multiplied by the controller's ProcessingLogic, using long EU/t.
            // Multiplication here would overflow GTRecipe's signed int on UXV recipes.
            copy.mFakeRecipe = false;
            copy.mHidden = false;

            try {
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
}
