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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizons.modularui.api.math.Alignment;
import com.gtnewhorizons.modularui.common.widget.DynamicPositionedColumn;
import com.gtnewhorizons.modularui.common.widget.FakeSyncWidget;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;

import gregtech.api.enums.ItemList;
import gregtech.api.enums.NaniteTier;
import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEExtendedPowerMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEHatchNanite;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrors;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeConstants;
import gregtech.api.util.IGTHatchAdder;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.OverclockCalculator;
import gregtech.api.util.shutdown.ShutDownReason;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import tectech.thing.metaTileEntity.multi.bec.MTEBECAssembler;
import tectech.thing.metaTileEntity.multi.structures.BECStructureDefinitions;

/**
 * Standalone BEC processor with original nanite containment hatches. Nanite
 * quantity and tier determine processing speed just as on the original BEC;
 * GT's parallel helper checks actual recipe input quantities and available EU.
 */
public final class SimplifiedBECController extends MTEExtendedPowerMultiBlockBase<SimplifiedBECController>
    implements ISurvivalConstructable {

    private static final String STRUCTURE_PIECE = "main";
    private static final int X_OFFSET = 3;
    private static final int Y_OFFSET = 11;
    private static final int Z_OFFSET = 0;

    /** The same cap used by the original assembler's fifteen nanite hatches. */
    private static final int MAX_NANITES = MTEBECAssembler.MAX_NANITES;
    private static final int MAX_NANITE_HATCHES = 15;

    private final List<MTEHatchNanite> naniteHatches = new ArrayList<>();
    private NaniteTier[] requiredNanites = {};
    private long naniteSubticks;
    private int parallelRecipesInProgress;
    /** Server-calculated real-time speed, synced to the client GUI at 0.01x precision. */
    private int naniteSpeedHundredths;

    private static final IStructureDefinition<SimplifiedBECController> STRUCTURE = StructureDefinition
        .<SimplifiedBECController>builder()
        .addShape(STRUCTURE_PIECE, BECStructureDefinitions.BEC_IO_NODE)
        .addElement('A', CoherencePreservingPlasmaConduit.asElement())
        .addElement(
            'B',
            buildHatchAdder(SimplifiedBECController.class)
                .atLeast(
                    InputBus,
                    OutputBus,
                    InputHatch,
                    OutputHatch,
                    Energy,
                    ExoticEnergy,
                    Maintenance,
                    NaniteHatchElement.INSTANCE)
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
        // Do not overclock the underlying BEC recipe. The original BEC uses
        // nanites to advance progress independently of voltage tiers.
        // Multiplication is done with long-EU calculations, not GTRecipe.mEUt.
        return new ProcessingLogic() {

            @Override
            protected CheckRecipeResult validateRecipe(GTRecipe recipe) {
                NaniteTier[] tiers = recipe.getMetadata(GTRecipeConstants.NANITE_TIERS);
                if (tiers == null || tiers.length == 0) return CheckRecipeResultRegistry.NO_RECIPE;
                return super.validateRecipe(recipe);
            }

            @Override
            protected OverclockCalculator createOverclockCalculator(GTRecipe recipe) {
                return super.createOverclockCalculator(recipe).setNoOverclock(true);
            }

            @Override
            protected CheckRecipeResult onRecipeStart(GTRecipe recipe) {
                CheckRecipeResult result = super.onRecipeStart(recipe);
                if (result.wasSuccessful()) {
                    // Same lifecycle as MTEBECIONode#setCurrentRecipe, but the
                    // standard Crafting Input Buffer handles all recipe inputs.
                    NaniteTier[] steps = recipe.getMetadata(GTRecipeConstants.NANITE_TIERS);
                    requiredNanites = steps == null ? new NaniteTier[0] : Arrays.copyOf(steps, steps.length);
                    parallelRecipesInProgress = getCurrentParallels();
                    naniteSubticks = 0;
                }
                return result;
            }
        }.setEuModifier(SimplifiedBECConfig.energyMultiplier);
    }

    @Override
    public int getMaxParallelRecipes() {
        return SimplifiedBECConfig.maxParallels;
    }

    @Override
    protected void setupProcessingLogic(ProcessingLogic logic) {
        super.setupProcessingLogic(logic);
        // GT's ParallelHelper chooses the minimum of this cap, recipe inputs,
        // output capacity and EU/t available. It only consumes real materials.
        logic.setMaxParallel(getTrueParallel());
    }

    @Override
    protected void setProcessingLogicPower(ProcessingLogic logic) {
        // The extended-power base consumes long EU/t. Total hatch throughput
        // is the power budget, not the voltage tier of one individual hatch.
        logic.setAvailableVoltage(getMaxInputEu());
        logic.setAvailableAmperage(1);
        logic.setAmperageOC(false);
        logic.setUnlimitedTierSkips();
    }

    @Override
    protected void incrementProgressTime() {
        naniteSpeedHundredths = 0;
        if (parallelRecipesInProgress <= 0 || requiredNanites.length == 0) return;

        // Original MTEBECAssembler: total all installed, valid containment
        // hatches (up to 30,720 nanites) and use the LOWEST provided tier.
        int naniteCount = 0;
        NaniteTier providedTier = null;
        for (MTEHatchNanite hatch : naniteHatches) {
            if (!hatch.isValid()) continue;
            NaniteTier tier = NaniteTier.fromStack(hatch.getItemStack());
            if (tier == null) continue;
            if (providedTier == null || tier.ordinal() < providedTier.ordinal()) providedTier = tier;
            naniteCount = Math.min(MAX_NANITES, naniteCount + Math.max(0, hatch.getItemCount()));
        }
        if (providedTier == null || naniteCount == 0) return;

        // Original MTEBECIONode: each recipe has a sequence of nanite-tier
        // phases, divided approximately evenly across its base duration.
        int currentStep = 0;
        int currentStepStart = 0;
        for (; currentStep < requiredNanites.length - 1; currentStep++) {
            int nextStart = currentStepStart + getPhaseLength(currentStep);
            if (mProgresstime < nextStart) break;
            currentStepStart = nextStart;
        }
        NaniteTier requiredTier = requiredNanites[currentStep];
        if (requiredTier == null || providedTier.tier < requiredTier.tier) return;

        // Actual effective progress per game tick, not the unscaled NEI/GT recipe duration.
        naniteSpeedHundredths = (int) Math.round(100.0 * naniteCount / parallelRecipesInProgress);

        // Adapted BEC I/O Node nanite-progress calculation. Nanites below the
        // required tier still block the phase (checked above), but nanites
        // ABOVE that tier no longer incur the original 2^tierDifference
        // speed penalty. Condensate slowdown and speed divisor are also 1.
        final long divisor = parallelRecipesInProgress;
        if (divisor <= 0) return;
        naniteSubticks += naniteCount;
        long fullTicks = naniteSubticks / divisor;
        naniteSubticks -= fullTicks * divisor;
        long nextProgress = (long) mProgresstime + fullTicks;

        // Original progress gate: don't cross into a higher-tier phase until
        // the available nanites meet that phase's tier requirement.
        int nextStart = currentStepStart;
        for (int i = currentStep; i < requiredNanites.length; i++) {
            if (i > currentStep && requiredNanites[i] != null && requiredNanites[i].tier > providedTier.tier) {
                nextProgress = Math.min(nextProgress, nextStart);
                break;
            }
            nextStart += getPhaseLength(i);
        }

        mProgresstime = (int) Math.min((long) mMaxProgresstime, nextProgress);
    }

    private int getPhaseLength(int phase) {
        int count = requiredNanites.length;
        int base = mMaxProgresstime / count;
        return base + (phase < mMaxProgresstime % count ? 1 : 0);
    }

    @Override
    protected void outputAfterRecipe() {
        parallelRecipesInProgress = 0;
        naniteSubticks = 0;
        naniteSpeedHundredths = 0;
        requiredNanites = new NaniteTier[0];
        super.outputAfterRecipe();
    }

    @Override
    public void stopMachine(ShutDownReason reason) {
        parallelRecipesInProgress = 0;
        naniteSubticks = 0;
        naniteSpeedHundredths = 0;
        requiredNanites = new NaniteTier[0];
        super.stopMachine(reason);
    }

    @Override
    public void saveNBTData(NBTTagCompound tag) {
        super.saveNBTData(tag);
        tag.setLong("simplifiedBecNaniteSubticks", naniteSubticks);
        tag.setInteger("simplifiedBecActiveParallels", parallelRecipesInProgress);
        NBTTagList tiers = new NBTTagList();
        for (NaniteTier tier : requiredNanites) {
            tiers.appendTag(new NBTTagString(tier == null ? "" : tier.name()));
        }
        tag.setTag("simplifiedBecRequiredNanites", tiers);
    }

    @Override
    public void loadNBTData(NBTTagCompound tag) {
        super.loadNBTData(tag);
        naniteSpeedHundredths = 0;
        naniteSubticks = Math.max(0L, tag.getLong("simplifiedBecNaniteSubticks"));
        parallelRecipesInProgress = Math
            .max(0, Math.min(SimplifiedBECConfig.maxParallels, tag.getInteger("simplifiedBecActiveParallels")));
        NBTTagList tiers = tag.getTagList("simplifiedBecRequiredNanites", 8);
        List<NaniteTier> parsed = new ArrayList<>();
        for (int i = 0; i < Math.min(128, tiers.tagCount()); i++) {
            try {
                parsed.add(NaniteTier.valueOf(tiers.getStringTagAt(i)));
            } catch (IllegalArgumentException ignored) {
                parsed.clear();
                break;
            }
        }
        requiredNanites = parsed.toArray(new NaniteTier[0]);
    }

    @Override
    public boolean supportsInputSeparation() {
        return true;
    }

    @Override
    public boolean supportsVoidProtection() {
        return true;
    }

    private String getNaniteSpeedText() {
        return String.format(Locale.ROOT, "Nanite speed: %.2fx", naniteSpeedHundredths / 100.0);
    }

    @Override
    protected MTEMultiBlockBaseGui<?> getGui() {
        return new SimplifiedBECGui(this);
    }

    /** MUI2 (default in GTNH 2.9): one line, keeping GT's separate progress widget intact. */
    private static final class SimplifiedBECGui extends MTEMultiBlockBaseGui<SimplifiedBECController> {

        private SimplifiedBECGui(SimplifiedBECController controller) {
            super(controller);
        }

        @Override
        protected void registerSyncValues(PanelSyncManager syncManager) {
            super.registerSyncValues(syncManager);
            syncManager.syncValue(
                "simplifiedBecNaniteSpeed",
                new IntSyncValue(
                    () -> multiblock.naniteSpeedHundredths,
                    value -> multiblock.naniteSpeedHundredths = value));
        }

        @Override
        protected IWidget createShutdownDurationWidget(PanelSyncManager syncManager) {
            LongSyncValue shutdownDuration = (LongSyncValue) syncManager.getSyncHandlerFromMapKey("shutdownDuration:0");
            return IKey.dynamic(() -> {
                if (multiblock.mMaxProgresstime > 0) return multiblock.getNaniteSpeedText();

                Duration time = Duration.ofSeconds(shutdownDuration.getValue());
                return StatCollector.translateToLocalFormatted(
                    "GT5U.gui.text.shutdown_duration",
                    time.toHours(),
                    time.toMinutes() % 60,
                    time.getSeconds() % 60);
            })
                .asWidget()
                .fullWidth()
                .marginBottom(2)
                .setEnabledIf(
                    widget -> multiblock.mMaxProgresstime > 0
                        || (multiblock.shouldDisplayShutDownReason() && !baseMetaTileEntity.isActive()
                            && !baseMetaTileEntity.isAllowedToWork()));
        }
    }

    /** MUI1 compatibility for packs that disable MUI2 globally. */
    @Override
    protected void drawTexts(DynamicPositionedColumn screenElements, SlotWidget inventorySlot) {
        super.drawTexts(screenElements, inventorySlot);
        screenElements.widget(
            new FakeSyncWidget.IntegerSyncer(() -> naniteSpeedHundredths, value -> naniteSpeedHundredths = value));
        screenElements.widget(
            TextWidget.dynamicString(this::getNaniteSpeedText)
                .setSynced(false)
                .setTextAlignment(Alignment.CenterLeft)
                .setEnabled(widget -> mMaxProgresstime > 0));
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        return new MultiblockTooltipBuilder().addMachineType("Simplified Bose-Einstein Assembler")
            .addInfo("Processes BEC recipes using their original source fluids")
            .addInfo("AE2 Crafting Input Buffer supported as an input bus")
            .addInfo("Energy cost: " + SimplifiedBECConfig.energyMultiplier + "x original BEC recipe EU/t")
            .addInfo("Physical Nanite Containment Hatches (1-15), maximum " + MAX_NANITES + " nanites")
            .addInfo("Nanite count controls speed; higher-tier nanites have no penalty")
            .addInfo("Nanites below the recipe's required tier block progress")
            .addInfo("Automatic parallels: limited by input, output and energy")
            .addInfo("No condensate network or LoS link required")
            .beginStructureBlock(7, 23, 13, true)
            .addController("BEC I/O Node controller position")
            .addCasing("BEC I/O Node casings", "Use the original I/O Node frame", false)
            .addInputBus("1+", "Electromagnetically Isolated Casings", 1)
            .addOutputBus("1+", "Electromagnetically Isolated Casings", 1)
            .addInputHatch("0+", "Electromagnetically Isolated Casings", 1)
            .addOutputHatch("0+", "Electromagnetically Isolated Casings", 1)
            .addEnergyHatch("1+ (normal or exotic)", "Electromagnetically Isolated Casings", 1)
            .addMaintenanceHatch("1", "Electromagnetically Isolated Casings", 1)
            .addInfo("Nanite Containment Hatches: 1-15 on isolated casings")
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
        if (naniteHatches.isEmpty()) errors.add(StructureErrors.missingHatch(ItemList.Hatch_Nanite.get(1L)));
        if (naniteHatches.size() > MAX_NANITE_HATCHES) {
            errors.add(StructureErrors.tooManyHatches(ItemList.Hatch_Nanite.get(1L), MAX_NANITE_HATCHES));
        }
    }

    @Override
    public void clearHatches() {
        super.clearHatches();
        naniteHatches.clear();
    }

    /** Accepts the very same GregTech Nanite Hatch as the original BEC Assembler. */
    private enum NaniteHatchElement implements IHatchElement<SimplifiedBECController> {

        INSTANCE;

        @Override
        public List<? extends Class<? extends IMetaTileEntity>> mteClasses() {
            return Collections.singletonList(MTEHatchNanite.class);
        }

        @Override
        public IGTHatchAdder<? super SimplifiedBECController> adder() {
            return (machine, tile, texture) -> {
                if (!(tile.getMetaTileEntity() instanceof MTEHatchNanite naniteHatch)) return false;
                machine.naniteHatches.add(naniteHatch);
                naniteHatch.updateTexture(texture);
                naniteHatch.updateCraftingIcon(machine.getMachineCraftingIcon());
                return true;
            };
        }

        @Override
        public String getDisplayName() {
            return ItemList.Hatch_Nanite.getDisplayName();
        }

        @Override
        public long count(SimplifiedBECController machine) {
            return machine.naniteHatches.size();
        }
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
