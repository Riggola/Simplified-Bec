package dev.niccolo.simplifiedbec;

import static gregtech.api.casing.Casings.CoherencePreservingPlasmaConduit;
import static gregtech.api.casing.Casings.CondensateGuidanceCoil;
import static gregtech.api.casing.Casings.CondensateTransformativeCoil;
import static gregtech.api.casing.Casings.ConflictInducementCasing;
import static gregtech.api.casing.Casings.ElectromagneticWaveguide;
import static gregtech.api.casing.Casings.ElectromagneticallyIsolatedCasing;
import static gregtech.api.casing.Casings.FineStructureConstantManipulator;
import static gregtech.api.casing.Casings.PeaceEnforcementCasing;
import static gregtech.api.enums.HatchElement.Energy;
import static gregtech.api.enums.HatchElement.ExoticEnergy;
import static gregtech.api.enums.HatchElement.InputBus;
import static gregtech.api.enums.HatchElement.InputHatch;
import static gregtech.api.enums.HatchElement.Maintenance;
import static gregtech.api.enums.HatchElement.OutputBus;
import static gregtech.api.enums.HatchElement.OutputHatch;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEExtendedPowerMultiBlockBase;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.MultiblockTooltipBuilder;
import tectech.thing.metaTileEntity.multi.structures.BECStructureDefinitions;

/**
 * Standalone processor using the existing BEC I/O Node shell. The LoS position
 * is a regular casing here; the BEC network and nanite hatches are not used.
 */
public final class SimplifiedBECController extends MTEExtendedPowerMultiBlockBase<SimplifiedBECController>
    implements ISurvivalConstructable {

    private static final String STRUCTURE_PIECE = "main";
    private static final int X_OFFSET = 3;
    private static final int Y_OFFSET = 11;
    private static final int Z_OFFSET = 0;

    private static final IStructureDefinition<SimplifiedBECController> STRUCTURE = StructureDefinition
        .<SimplifiedBECController>builder()
        .addShape(STRUCTURE_PIECE, BECStructureDefinitions.BEC_IO_NODE)
        .addElement('A', CoherencePreservingPlasmaConduit.asElement())
        .addElement(
            'B',
            buildHatchAdder(SimplifiedBECController.class)
                .atLeast(InputBus, OutputBus, InputHatch, OutputHatch, Energy, ExoticEnergy, Maintenance)
                .casingIndex(ElectromagneticallyIsolatedCasing.getTextureId())
                .hint(1)
                .buildAndChain(ElectromagneticallyIsolatedCasing.asElement()))
        .addElement('C', FineStructureConstantManipulator.asElement())
        .addElement('D', ConflictInducementCasing.asElement())
        .addElement('E', PeaceEnforcementCasing.asElement())
        .addElement('F', CondensateTransformativeCoil.asElement())
        .addElement('G', CondensateGuidanceCoil.asElement())
        .addElement('H', ElectromagneticWaveguide.asElement())
        .addElement('1', FineStructureConstantManipulator.asElement())
        .build();

    public SimplifiedBECController(int id, String name, String localName) {
        super(id, name, localName);
    }

    public SimplifiedBECController(String name) {
        super(name);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity tile) {
        return new SimplifiedBECController(mName);
    }

    @Override
    public IStructureDefinition<SimplifiedBECController> getStructureDefinition() {
        return STRUCTURE;
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return SimplifiedBECRecipes.RECIPES;
    }

    @Override
    protected ProcessingLogic createProcessingLogic() {
        // Applied with long-EU calculations; avoids overflow of GTRecipe.mEUt on UXV crafts.
        return new ProcessingLogic().setEuModifier(SimplifiedBECConfig.energyMultiplier);
    }

    @Override
    public boolean supportsInputSeparation() {
        return true;
    }

    @Override
    public boolean supportsVoidProtection() {
        return true;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        return new MultiblockTooltipBuilder().addMachineType("Simplified Bose-Einstein Assembler")
            .addInfo("Processes BEC recipes using their original source fluids")
            .addInfo("AE2 Crafting Input Buffer supported as an input bus")
            .addInfo("Energy cost: " + SimplifiedBECConfig.energyMultiplier + "x original BEC recipe EU/t")
            .addInfo("No condensate network, nanites, or LoS connection required")
            .beginStructureBlock(7, 23, 13, true)
            .addController("BEC I/O Node controller position")
            .addCasing("BEC I/O Node casings", "Use the original I/O Node frame", false)
            .addInputBus("1+", "Electromagnetically Isolated Casings", 1)
            .addOutputBus("1+", "Electromagnetically Isolated Casings", 1)
            .addInputHatch("0+", "Electromagnetically Isolated Casings", 1)
            .addOutputHatch("0+", "Electromagnetically Isolated Casings", 1)
            .addEnergyHatch("1+ (normal or exotic)", "Electromagnetically Isolated Casings", 1)
            .addMaintenanceHatch("1", "Electromagnetically Isolated Casings", 1)
            .toolTipFinisher();
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity tile, ForgeDirection side, ForgeDirection front, int color,
        boolean active, boolean redstone) {
        // Reuses GT5U's original BEC I/O casing. Custom controller artwork can be added later.
        return new ITexture[] { CoherencePreservingPlasmaConduit.getCasingTexture() };
    }

    @Override
    public void checkMachine(IGregTechTileEntity tile, ItemStack stack, List<StructureError> errors) {
        if (!checkPiece(STRUCTURE_PIECE, X_OFFSET, Y_OFFSET, Z_OFFSET, errors)) return;
        checkHasInputBus(errors);
        checkHasOutputBus(errors);
        if (mEnergyHatches.isEmpty() && mExoticEnergyHatches.isEmpty()) checkHasEnergyHatch(errors);
        checkHasMaintenanceHatch(errors);
    }

    @Override
    public void construct(ItemStack stack, boolean hintsOnly) {
        buildPiece(STRUCTURE_PIECE, stack, hintsOnly, X_OFFSET, Y_OFFSET, Z_OFFSET);
    }

    @Override
    public int survivalConstruct(ItemStack stack, int budget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        return survivalBuildPiece(STRUCTURE_PIECE, stack, X_OFFSET, Y_OFFSET, Z_OFFSET, budget, env, false, true);
    }
}
