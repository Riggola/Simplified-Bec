package dev.niccolo.simplifiedbec;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.GregTechAPI;
import tectech.thing.CustomItemList;

@Mod(
    modid = SimplifiedBECMod.MODID,
    name = "Simplified BEC",
    version = Tags.VERSION,
    acceptedMinecraftVersions = "[1.7.10]",
    dependencies = "required-after:gregtech;required-after:structurelib")
public final class SimplifiedBECMod {

    public static final String MODID = "simplifiedbec";
    public static final Logger LOG = LogManager.getLogger(MODID);

    public static SimplifiedBECController controller;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        SimplifiedBECConfig.load(event.getSuggestedConfigurationFile());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        int id = SimplifiedBECConfig.controllerId;
        if (GregTechAPI.METATILEENTITIES[id] != null) {
            throw new IllegalStateException("Simplified BEC controller ID " + id + " is already in use");
        }
        controller = new SimplifiedBECController(id, "multimachine.simplifiedbec", "Simplified BEC Assembler");
        // Provisional, intentionally simple recipe: repurpose the original BEC I/O Node controller.
        GameRegistry.addShapelessRecipe(controller.getStackForm(1L), CustomItemList.Machine_Multi_BECIONode.get(1));
        // GT5 registers additional BEC recipes during GTMod.onLoadComplete().
        // Register after *that* phase, not in our FML postInit, to include them all.
        GregTechAPI.sGTCompleteLoad.add(SimplifiedBECRecipes::register);
        LOG.info("Registered Simplified BEC controller as GregTech MTE {}", id);
    }
}
