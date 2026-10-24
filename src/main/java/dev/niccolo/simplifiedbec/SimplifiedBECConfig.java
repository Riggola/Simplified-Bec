package dev.niccolo.simplifiedbec;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public final class SimplifiedBECConfig {

    public static int controllerId = 32680;
    public static int energyMultiplier = 10;
    public static int maxParallels = 30720;

    private SimplifiedBECConfig() {}

    public static void load(File file) {
        Configuration config = new Configuration(file);
        config.load();
        controllerId = config.getInt(
            "controllerId",
            Configuration.CATEGORY_GENERAL,
            controllerId,
            30000,
            32766,
            "Unique GT meta-tile-entity ID for the new controller. Change if another addon uses it.");
        energyMultiplier = config.getInt(
            "becEnergyMultiplier",
            Configuration.CATEGORY_GENERAL,
            energyMultiplier,
            1,
            100,
            "Multiplier applied to source BEC recipe EU/t. Default: 10.");
        maxParallels = config.getInt(
            "becMaxParallels",
            Configuration.CATEGORY_GENERAL,
            maxParallels,
            1,
            1000000,
            "Maximum parallel recipes. Actual count also depends on available materials, output space and hatch EU/t.");
        if (config.hasChanged()) config.save();
    }
}
