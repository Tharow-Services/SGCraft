//------------------------------------------------------------------------------------------------
//
//   SG Craft - Generate a stargate on top of an ocean monument
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.generator;

import net.minecraft.block.BlockPrismarine;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureComponent;

import java.util.Random;

public class FeatureOceanMonument extends FeatureStargate {
    public FeatureOceanMonument() {}

    public FeatureOceanMonument(StructureComponent base) {
        this.base = base;

        // Set up the options
        Random rand = new Random();
        generateStructure = rand.nextInt(100) < FeatureGeneration.oceanmonumentAddonChance;
        generateChevronUpgrade = rand.nextInt(100) < FeatureGeneration.oceanmonumentChevronUpgradeChance;
        generateZpmChest = rand.nextInt(100) < FeatureGeneration.oceanmonumentZpmChestChance;
        generateTokra = false;

        taintedZpm = rand.nextInt(100) < FeatureGeneration.genericTaintedZpm;

        if (FeatureGeneration.debugStructures)
            System.out.println("SGCraft: Creating FeatureOceanMonument with GenerateStructure: " + generateStructure);

        // Set up the building box
        StructureBoundingBox baseBox = base.getBoundingBox();
        centerPos = new BlockPos(baseBox.minX + (baseBox.maxX - baseBox.minX + 1) / 2, baseBox.minY + (baseBox.maxY - baseBox.minY + 1) / 2, baseBox.minZ + (baseBox.maxZ - baseBox.minZ + 1) / 2);
        int cx = centerPos.getX();
        int cz = centerPos.getZ();
        int bottom = baseBox.minY + 16;
        boundingBox = new StructureBoundingBox(cx - 4, bottom, cz - 4, cx + 4, bottom + 5, cz + 4);

        // Set up the various positions based on the direction
        spawnDirection = base.getCoordBaseMode();
        setCoordBaseMode (EnumFacing.SOUTH);

        if (spawnDirection == EnumFacing.SOUTH) {
            gateX = 3; gateY = 0; gateZ = 6; gateFaces = EnumFacing.SOUTH;
            dhdX = 0; dhdY = 1; dhdZ = 1; dhdFaces = EnumFacing.WEST;
            chestX = 0; chestY = 1; chestZ = 5; chestFaces = EnumFacing.EAST;
        } else if (spawnDirection == EnumFacing.NORTH) {
            gateX = 3; gateY = 0; gateZ = 0; gateFaces = EnumFacing.NORTH;
            dhdX = 0; dhdY = 1; dhdZ = 5; dhdFaces = EnumFacing.WEST;
            chestX = 0; chestY = 1; chestZ = 1; chestFaces = EnumFacing.EAST;
        } else if (spawnDirection == EnumFacing.EAST) {
            gateX = 6; gateY = 0; gateZ = 3; gateFaces = EnumFacing.EAST;
            dhdX = 1; dhdY = 1; dhdZ = 0; dhdFaces = EnumFacing.NORTH;
            chestX = 5; chestY = 1; chestZ = 0; chestFaces = EnumFacing.NORTH;
        } else if (spawnDirection == EnumFacing.WEST) {
            gateX = 0; gateY = 0; gateZ = 3; gateFaces = EnumFacing.WEST;
            dhdX = 5; dhdY = 1; dhdZ = 0; dhdFaces = EnumFacing.NORTH;
            chestX = 1; chestY = 1; chestZ = 0; chestFaces = EnumFacing.NORTH;
        }

        gatePos = new BlockPos (boundingBox.minX + gateX, boundingBox.minY + gateY, boundingBox.minZ + gateZ);
        ItemStack prismarine = new ItemStack (Blocks.PRISMARINE, 1);
        for (int x = 0; x < 5; x++)
            gateCamo[x] = prismarine;

        dhdPos = new BlockPos (boundingBox.minX + dhdX, boundingBox.minY + dhdY, boundingBox.minZ + dhdZ);

        chestPos = new BlockPos (boundingBox.minX + chestX, boundingBox.minY + chestY, boundingBox.minZ + chestZ);

        if ((generateStructure) && (FeatureGeneration.debugStructures))
            System.out.println ("SGCraft: Ocean Monument got a gate at " + centerPos + ", facing " + spawnDirection);
    }

    protected boolean addAugmentationParts(World world, Random rand, StructureBoundingBox clip) {
        if ((pass == 3) && (FeatureGeneration.debugStructures))
            System.out.printf("SGCraft: FeatureOceanMonument.addComponentParts in %s clipped to %s\n", getBoundingBox(), clip);

        IBlockState water = Blocks.WATER.getDefaultState();
        IBlockState seaLantern = Blocks.SEA_LANTERN.getDefaultState();
        IBlockState prisBrick = Blocks.PRISMARINE.getDefaultState().withProperty(BlockPrismarine.VARIANT, BlockPrismarine.EnumType.BRICKS);

        // Remove old monument top
        fillWithBlocks(world, clip, 0, 1, 0, 8, 5, 8, water, water, false);

        // Pillars
        setBlockState(world, prisBrick, 6, 1, 0, clip);
        setBlockState(world, prisBrick, 6, 2, 0, clip);
        setBlockState(world, prisBrick, 6, 3, 0, clip);
        setBlockState(world, prisBrick, 6, 1, 6, clip);
        setBlockState(world, prisBrick, 6, 2, 6, clip);
        setBlockState(world, prisBrick, 6, 3, 6, clip);
        setBlockState(world, prisBrick, 0, 1, 0, clip);
        setBlockState(world, prisBrick, 0, 2, 0, clip);
        setBlockState(world, prisBrick, 0, 3, 0, clip);
        setBlockState(world, prisBrick, 0, 1, 6, clip);
        setBlockState(world, prisBrick, 0, 2, 6, clip);
        setBlockState(world, prisBrick, 0, 3, 6, clip);

        // 'Roof'
        setBlockState(world, prisBrick, 1, 4, 1, clip);
        setBlockState(world, prisBrick, 1, 4, 5, clip);
        setBlockState(world, prisBrick, 5, 4, 1, clip);
        setBlockState(world, prisBrick, 5, 4, 5, clip);
        setBlockState(world, seaLantern, 2, 4, 2, clip);
        setBlockState(world, seaLantern, 2, 4, 4, clip);
        setBlockState(world, seaLantern, 4, 4, 2, clip);
        setBlockState(world, seaLantern, 4, 4, 4, clip);
        setBlockState(world, prisBrick, 2, 5, 2, clip);
        setBlockState(world, prisBrick, 2, 5, 3, clip);
        setBlockState(world, prisBrick, 2, 5, 4, clip);
        setBlockState(world, prisBrick, 3, 5, 2, clip);
        setBlockState(world, prisBrick, 3, 5, 4, clip);
        setBlockState(world, prisBrick, 4, 5, 2, clip);
        setBlockState(world, prisBrick, 4, 5, 3, clip);
        setBlockState(world, prisBrick, 4, 5, 4, clip);

        // Extra deco because we're uneven on the platform
        setBlockState (world, seaLantern, 0, 1, 7, clip);
        setBlockState (world, seaLantern, 7, 1, 7, clip);
        setBlockState (world, seaLantern, 7, 1, 0, clip);

        // Stargate
        GenerateStargate (world, clip, true);

        // DHD
        GenerateDHD (world, clip);

        // ZPM Chest
        GenerateChest (world, clip);

        // Tok'Ra does not spawn at the Ocean Monument. Poor blighter'd drown.
        //GenerateTokRa (world, clip);
        
        pass++;  // Reminder: this entire method is called 4 times during world generation.
        return true;
    }
}
