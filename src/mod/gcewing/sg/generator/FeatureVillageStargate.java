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
import net.minecraft.world.biome.Biome;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureComponent;
import net.minecraft.world.gen.structure.StructureVillagePieces.PieceWeight;
import net.minecraft.world.gen.structure.StructureVillagePieces.Start;
import net.minecraft.world.gen.structure.StructureVillagePieces.Village;
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

        // Select platform blocks based on village biome
        this.biome = villagePiece.biome;
        this.platformBlock = FeatureStargate.getBiomePlatformblock (this.biome);
        this.randBlockHigh = FeatureStargate.getBiomeRandblockHigh (this.biome);
        this.randBlockLow = FeatureStargate.getBiomeRandblockLow (this.biome);
        this.stairs = FeatureStargate.getBiomeStairblock (this.biome);

        // Set up the stargate class
        this.Stargate = new FeatureStargate(this.startPiece, this.boundingBox);
        this.Stargate.setCoordBaseMode (EnumFacing.SOUTH);
        this.Stargate.spawnDirection = FeatureStargate.getOppDirection (facing);

        // Set up the options
        Random rand = new Random();
        // Whether this village gets its gate: decided once, here. It was rolled again each time a chunk the gate stands in
        // was filled in, so most villages that got a gate got only part of one.
        this.generateGate = rand.nextInt(100) < FeatureGeneration.villageAddonChance;
        this.Stargate.generateChevronUpgrade = rand.nextInt(100) < FeatureGeneration.villageChevronUpgradeChance;
        this.Stargate.generateZpmChest = rand.nextInt(100) < FeatureGeneration.villageZpmChestChance;
        this.Stargate.generateTokra = FeatureGeneration.villageSpawnTokra;

        ItemStack camoBlock = new ItemStack(FeatureStargate.getBiomeCamoblock (this.biome), 1, 0);
        for (int x = 0; x < 5; x++) {
            this.Stargate.gateCamo[x] = camoBlock;
        }
    }

    private int groundLevel = -1;

    @Override
    public boolean addComponentParts(World world, Random rand, StructureBoundingBox clip) {
        if (Stargate == null) {
            System.out.println("SGCraft Debug: Exception in FeatureVillageStargate class; Stargate object is null");
            return false;
        }
        Stargate.pass = pass;
        if(groundLevel < 0) {
            groundLevel = this.getAverageGroundLevel(world, clip);
            if(groundLevel < 0)
                return true;
            boundingBox.offset (0, groundLevel - boundingBox.minY, 0);
        }

        Stargate.updateBoundingBox (boundingBox);

        if (FeatureGeneration.villageAddon) {
            if (generateGate) {
                Stargate.GenerateSimpleStargatePlatform(world, clip, stairs, platformBlock, randBlockHigh, randBlockLow);
            }
        } else {
            Stargate.generateTokra = true;
            Stargate.chestPos = new BlockPos (boundingBox.minX + Stargate.chestX, boundingBox.minY + Stargate.chestY, boundingBox.minZ + Stargate.chestZ);
            Stargate.GenerateTokRa (world, clip);
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
