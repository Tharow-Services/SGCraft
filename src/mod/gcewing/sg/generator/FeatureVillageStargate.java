//------------------------------------------------------------------------------------------------
//
//   SG Craft - Generate a Stargate in a Village
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.generator;
import gcewing.sg.generator.FeatureStargate;

import net.minecraft.block.state.IBlockState;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.biome.Biome;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureComponent;
import net.minecraft.world.gen.structure.StructureVillagePieces.PieceWeight;
import net.minecraft.world.gen.structure.StructureVillagePieces.Start;
import net.minecraft.world.gen.structure.StructureVillagePieces.Village;
import net.minecraft.world.gen.structure.template.TemplateManager;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.common.registry.VillagerRegistry.IVillageCreationHandler;

import java.util.List;
import java.util.Random;

public class FeatureVillageStargate extends Village {
    FeatureStargate Stargate;
    boolean generateGate;
    int pass = 0;
    Biome biome;

    IBlockState platformBlock;
    IBlockState randBlockHigh;
    IBlockState randBlockLow;
    Block stairs;

    public FeatureVillageStargate() {}

    public FeatureVillageStargate(Start villagePiece, int par2, Random par3Random, StructureBoundingBox par4StructureBoundingBox, EnumFacing facing) {
        super(villagePiece, par2);
        this.setCoordBaseMode(facing);
        this.boundingBox = par4StructureBoundingBox;
        this.biome = villagePiece.biome;

        // Set up the options
        Random rand = new Random();
        // Whether this village gets its gate: decided once, here. It was rolled again each time a chunk the gate stands in
        // was filled in, so most villages that got a gate got only part of one.
        this.generateGate = rand.nextInt(100) < FeatureGeneration.villageAddonChance;
        boolean chevronUpgrade = rand.nextInt(100) < FeatureGeneration.villageChevronUpgradeChance;
        boolean zpmChest = rand.nextInt(100) < FeatureGeneration.villageZpmChestChance;
        setUpStargate(chevronUpgrade, zpmChest, FeatureGeneration.villageSpawnTokra);
    }

    // Sets up the platform blocks and the stargate class from the village's biome, the way this piece faces and what was
    // rolled for it; when the piece is made, and again when it's loaded with its village from the world's save
    private void setUpStargate(boolean chevronUpgrade, boolean zpmChest, boolean tokra) {
        // Select platform blocks based on village biome
        this.platformBlock = FeatureStargate.getBiomePlatformblock (this.biome);
        this.randBlockHigh = FeatureStargate.getBiomeRandblockHigh (this.biome);
        this.randBlockLow = FeatureStargate.getBiomeRandblockLow (this.biome);
        this.stairs = FeatureStargate.getBiomeStairblock (this.biome);

        // Set up the stargate class
        this.Stargate = new FeatureStargate(this.startPiece, this.boundingBox);
        this.Stargate.setCoordBaseMode (EnumFacing.SOUTH);
        this.Stargate.spawnDirection = FeatureStargate.getOppDirection (getCoordBaseMode());

        this.Stargate.generateChevronUpgrade = chevronUpgrade;
        this.Stargate.generateZpmChest = zpmChest;
        this.Stargate.generateTokra = tokra;

        ItemStack camoBlock = new ItemStack(FeatureStargate.getBiomeCamoblock (this.biome), 1, 0);
        for (int x = 0; x < 5; x++) {
            this.Stargate.gateCamo[x] = camoBlock;
        }
    }

    // Saved with the village, so that a village only partly built when the server stops gets the same gate, or none,
    // when it's loaded again. (The way the piece faces and its ground level are saved by Minecraft.)
    @Override
    protected void writeStructureToNBT(NBTTagCompound compound) {
        super.writeStructureToNBT(compound);
        compound.setBoolean("GenerateGate", generateGate);
        compound.setBoolean("ChevronUpgrade", Stargate.generateChevronUpgrade);
        compound.setBoolean("ZpmChest", Stargate.generateZpmChest);
        compound.setBoolean("Tokra", Stargate.generateTokra);
        if (biome != null && biome.getRegistryName() != null)
            compound.setString("Biome", biome.getRegistryName().toString());
    }

    @Override
    protected void readStructureFromNBT(NBTTagCompound compound, TemplateManager templateManager) {
        super.readStructureFromNBT(compound, templateManager);
        // A village saved by an older SGCraft has none of this, and gets no gate: what was rolled for it wasn't kept
        generateGate = compound.getBoolean("GenerateGate");
        biome = compound.hasKey("Biome") ? ForgeRegistries.BIOMES.getValue(new ResourceLocation(compound.getString("Biome"))) : null;
        setUpStargate(compound.getBoolean("ChevronUpgrade"), compound.getBoolean("ZpmChest"), compound.getBoolean("Tokra"));
    }

    @Override
    public boolean addComponentParts(World world, Random rand, StructureBoundingBox clip) {
        Stargate.pass = pass;
        // averageGroundLvl is saved with the village, so the platform is only moved to the ground once
        if (averageGroundLvl < 0) {
            averageGroundLvl = this.getAverageGroundLevel(world, clip);
            if (averageGroundLvl < 0)
                return true;
            boundingBox.offset (0, averageGroundLvl - boundingBox.minY, 0);
        }

        Stargate.updateBoundingBox (boundingBox);

        if (FeatureGeneration.villageAddon) {
            if (generateGate) {
                Stargate.GenerateSimpleStargatePlatform(world, clip, stairs, platformBlock, randBlockHigh, randBlockLow);
            }
        } else {
            // The add-on is off, so nothing is built. (This used to ask for a Tok'Ra here, which was never spawned: it
            // waited for a fourth pass this piece never got. Spawning one now would be new, so it's left out.)
            return false;
        }

        pass++;
        return true;
    }

    public static class VillageManager implements IVillageCreationHandler {
        @Override
        public Village buildComponent(PieceWeight villagePiece, Start startPiece, List<StructureComponent> pieces, Random random, int p1, int p2, int p3, EnumFacing facing, int p5) {
            StructureBoundingBox box = StructureBoundingBox.getComponentToAddBoundingBox(p1, p2, p3, 0, 0, 0, 11, 7, 11, facing);
            return (!canVillageGoDeeper(box))||(StructureComponent.findIntersecting(pieces, box)!=null)?null: new FeatureVillageStargate(startPiece, p5, random, box, facing);
        }


        @Override
        public PieceWeight getVillagePieceWeight(Random random, int i) {
            return new PieceWeight(FeatureVillageStargate.class, 100, 1);
        }

        @Override
        public Class<?> getComponentClass() {
            return FeatureVillageStargate.class;
        }
    }
}
