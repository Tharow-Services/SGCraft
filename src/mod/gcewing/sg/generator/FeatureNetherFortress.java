//------------------------------------------------------------------------------------------------
//
//   SG Craft - Generate a stargate inside a nether fortress
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.generator;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureComponent;

import java.util.Random;

public class FeatureNetherFortress extends FeatureStargate {
    public FeatureNetherFortress() {}

    public FeatureNetherFortress(StructureComponent base) {
        this.base = base;

        // Set up the options
        Random rand = new Random();
        generateStructure = rand.nextInt(100) < FeatureGeneration.netherAddonChance;
        generateChevronUpgrade = rand.nextInt(100) < FeatureGeneration.netherChevronUpgradeChance;
        generateZpmChest = rand.nextInt(100) < FeatureGeneration.netherZpmChestChance;
        generateTokra = FeatureGeneration.netherSpawnTokra;

        taintedZpm = rand.nextInt(100) < FeatureGeneration.genericTaintedZpm;

        if (FeatureGeneration.debugStructures)
            System.out.println("SGCraft: Creating FeatureNetherFortress with GenerateStructure: " + generateStructure);

        StructureBoundingBox baseBox = base.getBoundingBox();
        centerPos = new BlockPos(baseBox.minX + (baseBox.maxX - baseBox.minX + 1) / 2, baseBox.minY + (baseBox.maxY - baseBox.minY + 1) / 2, baseBox.minZ + (baseBox.maxZ - baseBox.minZ + 1) / 2);
        int cx = centerPos.getX();
        int cz = centerPos.getZ();
        int bottom = baseBox.minY + 4;
        boundingBox = new StructureBoundingBox(cx - 5, bottom, cz - 5, cx + 5, bottom + 7, cz + 5);

        spawnDirection = base.getCoordBaseMode();
        setCoordBaseMode (EnumFacing.SOUTH);

        if (spawnDirection == EnumFacing.SOUTH) {
            gateX = 5; gateY = 0; gateZ = 8; gateFaces = EnumFacing.SOUTH;
            dhdX = 5; dhdY = 1; dhdZ = 2; dhdFaces = EnumFacing.SOUTH;
            chestX = 1; chestY = 1; chestZ = 1; chestFaces = EnumFacing.NORTH;
        } else if (spawnDirection == EnumFacing.NORTH) {
            gateX = 5; gateY = 0; gateZ = 2; gateFaces = EnumFacing.NORTH;
            dhdX = 5; dhdY = 1; dhdZ = 7; dhdFaces = EnumFacing.NORTH;
            chestX = 1; chestY = 1; chestZ = 9; chestFaces = EnumFacing.SOUTH;
        } else if (spawnDirection == EnumFacing.EAST) {
            gateX = 8; gateY = 0; gateZ = 5; gateFaces = EnumFacing.EAST;
            dhdX = 2; dhdY = 1; dhdZ = 5; dhdFaces = EnumFacing.EAST;
            chestX = 1; chestY = 1; chestZ = 1; chestFaces = EnumFacing.EAST;
        } else if (spawnDirection == EnumFacing.WEST) {
            gateX = 2; gateY = 0; gateZ = 5; gateFaces = EnumFacing.WEST;
            dhdX = 7; dhdY = 1; dhdZ = 5; dhdFaces = EnumFacing.WEST;
            chestX = 9; chestY = 1; chestZ = 1; chestFaces = EnumFacing.WEST;
        }

        gatePos = new BlockPos (boundingBox.minX + gateX, boundingBox.minY + gateY, boundingBox.minZ + gateZ);
        ItemStack netherBrick = new ItemStack (Blocks.NETHER_BRICK, 1);
        for (int x = 0; x < 5; x++)
            gateCamo[x] = netherBrick;

        dhdPos = new BlockPos (boundingBox.minX + dhdX, boundingBox.minY + dhdY, boundingBox.minZ + dhdZ);

        chestPos = new BlockPos (boundingBox.minX + chestX, boundingBox.minY + chestY, boundingBox.minZ + chestZ);

        if ((generateStructure) && (FeatureGeneration.debugStructures))
            System.out.println ("SGCraft: Nether Fortress got a gate at " + centerPos + ", facing " + spawnDirection);
    
        pass = 3;   // Nether structure generate in 1 pass only, apparently
    }

    protected boolean addAugmentationParts(World world, Random rand, StructureBoundingBox clip) {
        if ((pass == 3) && (FeatureGeneration.debugStructures))
            System.out.printf("SGCraft: FeatureNetherFortress.addComponentParts in %s clipped to %s\n", getBoundingBox(), clip);

        IBlockState air = Blocks.AIR.getDefaultState();
        IBlockState netherbrick = Blocks.NETHER_BRICK.getDefaultState();
        IBlockState testBrick = Blocks.STONEBRICK.getDefaultState();

        // Main chamber
        fillWithBlocks(world, clip, 0, 0, 0, 10, 6, 10, netherbrick, air, false);

        // Doors
        for (int y = 1; y < 4; y++) {
            setBlockState (world, air, 0, y, 4, clip);
            setBlockState (world, air, 0, y, 5, clip);
            setBlockState (world, air, 0, y, 6, clip);

            setBlockState (world, air, 4, y, 0, clip);
            setBlockState (world, air, 5, y, 0, clip);
            setBlockState (world, air, 6, y, 0, clip);

            setBlockState (world, air, 10, y, 4, clip);
            setBlockState (world, air, 10, y, 5, clip);
            setBlockState (world, air, 10, y, 6, clip);

            setBlockState (world, air, 4, y, 10, clip);
            setBlockState (world, air, 5, y, 10, clip);
            setBlockState (world, air, 6, y, 10, clip);
        }

        // Stargate
        GenerateStargate (world, clip, true);

        // DHD
        GenerateDHD (world, clip);

        // ZPM Chest
        GenerateChest (world, clip);

        // TokRa villager
        GenerateTokRa (world, clip);

        pass++;  // Reminder: this entire method is called 4 times during world generation.
        return true;
    }
}
