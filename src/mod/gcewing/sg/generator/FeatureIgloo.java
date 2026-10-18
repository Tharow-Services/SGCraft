//------------------------------------------------------------------------------------------------
//
//   SG Craft - Generate a stargate under an ice igloo
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.generator;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.block.BlockStoneBrick;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureComponent;

import java.util.Random;

public class FeatureIgloo extends FeatureStargate {
    public FeatureIgloo() {}

    public FeatureIgloo(StructureComponent base) {
        this.base = base;
        Random rand = new Random();
        generateStructure = rand.nextInt(100) < FeatureGeneration.iglooAddonChance;
        generateChevronUpgrade = rand.nextInt(100) < FeatureGeneration.iglooChevronUpgradeChance;
        generateZpmChest = rand.nextInt(100) < FeatureGeneration.iglooZpmChestChance;
        generateTokra = FeatureGeneration.iglooSpawnTokra;
        taintedZpm = rand.nextInt(100) < 10;

        // Igloo's get Pegasus gates. I don't know why.
        gateType = 2;

        if (FeatureGeneration.debugStructures)
            System.out.println("SGCraft: Creating FeatureIgloo with GenerateStructure: " + generateStructure);

        // Set up the building box
        // Randomize the location near the igloo
        StructureBoundingBox baseBox = base.getBoundingBox();
        centerPos = new BlockPos(baseBox.minX + (baseBox.maxX - baseBox.minX + 1) / 2, baseBox.minY + (baseBox.maxY - baseBox.minY + 1) / 2, baseBox.minZ + (baseBox.maxZ - baseBox.minZ + 1) / 2);
        int cx = centerPos.getX();
        int cz = centerPos.getZ();
        int bottom = 63; // Re-calculated later

        int randX = rand.nextInt (20);
        int negX = rand.nextInt (2);
        if (negX == 0)
            negX = -1;

        int randZ = rand.nextInt (20);
        int negZ = rand.nextInt (2);
        if (negZ == 0)
            negZ = -1;

        cx += (10 + randX) * negX;
        cz += (10 + randZ) * negZ;

        boundingBox = new StructureBoundingBox(cx - 4, bottom, cz - 4, cx + 4, bottom + 6, cz + 4);

        // Since we are not attaching to the Igloo itself, randomize the direction
        int shookashookaGateDirection = rand.nextInt (4);

        if (shookashookaGateDirection == 0) {
            spawnDirection = EnumFacing.NORTH;
        } else if (shookashookaGateDirection == 1) {
            spawnDirection = EnumFacing.SOUTH;
        } else if (shookashookaGateDirection == 2) {
            spawnDirection = EnumFacing.EAST;
        } else if (shookashookaGateDirection == 3) {
            spawnDirection = EnumFacing.WEST;
        }

        setCoordBaseMode (EnumFacing.SOUTH);

        ItemStack stoneBrick = new ItemStack(Blocks.STONEBRICK, 1, 0);
        for (int x = 0; x < 5; x++) {
            gateCamo[x] = stoneBrick;
        }
    }

    protected boolean addAugmentationParts(World world, Random rand, StructureBoundingBox clip) {

        if ((pass == 3) && (FeatureGeneration.debugStructures))
            System.out.printf("SGCraft: FeatureIgloo.addComponentParts in %s clipped to %s\n", getBoundingBox(), clip);

        // Find the lowest ground in our planned spawn area, use that as our base Y level. Only the part of the area being
        // generated now is looked at, as looking further would make Minecraft generate more chunks from inside this one.
        // This gives the gate a chance to spawn partially/entirely submerged or buried, for added fun.
        if (firstY == -1) {
            int lowY = getLowestSurfaceInClip (world, clip);
            if (lowY == -1)
                return true;

            // Don't let it get tooo deep
            if (lowY < 55)
                lowY = 55;

            boolean flush = rand.nextInt (100) <= 10;
            boolean morelow = rand.nextInt (100) <= 10;
            int randDown = 0;

            // FLush with the surrounding area means no stairs around the platform
            if (flush)
                randDown += 1;

            // Sometimes buried isn't buried enough.
            if ((!flush) && (morelow))
                randDown += rand.nextInt (4);

            boundingBox.minY = lowY - randDown;
            firstY = boundingBox.minY;
        } else
            boundingBox.minY = firstY;

        IBlockState stoneBrick = Blocks.STONEBRICK.getDefaultState();
        IBlockState stoneBrickCracked = Blocks.STONEBRICK.getDefaultState().withProperty(BlockStoneBrick.VARIANT, BlockStoneBrick.EnumType.CRACKED);
        IBlockState gravel = Blocks.GRAVEL.getDefaultState();
        GenerateSimpleStargatePlatform (world, clip, Blocks.STONE_BRICK_STAIRS, stoneBrick, stoneBrickCracked, gravel);

 /*
        // I haven't given up on this. One day it can be made to work.
        IBlockState air = Blocks.AIR.getDefaultState();
        IBlockState snow = Blocks.SNOW.getDefaultState();
        IBlockState stonebrick = Blocks.STONEBRICK.getDefaultState();

        IBlockState ladder = Blocks.LADDER.getDefaultState().withProperty(BlockLadder.FACING, EnumFacing.NORTH);
        IBlockState trapdoor = Blocks.TRAPDOOR.getDefaultState()
                                              .withProperty(BlockTrapDoor.FACING, EnumFacing.NORTH)
                                              .withProperty(BlockTrapDoor.HALF, BlockTrapDoor.DoorHalf.TOP);
        IBlockState torchE = Blocks.TORCH.getDefaultState().withProperty(BlockTorch.FACING, EnumFacing.EAST);
        IBlockState torchW = Blocks.TORCH.getDefaultState().withProperty(BlockTorch.FACING, EnumFacing.WEST);
        IBlockState torchS = Blocks.TORCH.getDefaultState().withProperty(BlockTorch.FACING, EnumFacing.SOUTH);

        // 9
        fillWithBlocks(world, clip, 0, 0, 0, 10, 6, 10, stonebrick, air, false);

        setBlockState (world, snow, 1, 1, 1, clip);

        setBlockState (world, air, 4, 0, 1, clip);

        // Light!
        setBlockState (world, torchE, 1, 3, 3, clip);
        setBlockState (world, torchE, 1, 3, 6, clip);
        setBlockState (world, torchW, 7, 3, 3, clip);
        setBlockState (world, torchW, 7, 3, 6, clip);
        setBlockState (world, torchS, 4, 3, 8, clip);

        // Access column
        IBlockState id = null;
        for (int y = 0; y <= (5+22); y++) {
            if (y == 28)
                id = snow;
            else
                id = stonebrick;

            if (y > 4) {
                setBlockState (world, id, 4, y, 0, clip);
                setBlockState (world, id, 3, y, 1, clip);
                setBlockState (world, id, 5, y, 1, clip);
                setBlockState (world, id, 4, y, 2, clip);
            }

            if ((y == 0) || (y > 4))
                setBlockState (world, air, 4, y, 1, clip);

            if (y == 28)
                setBlockState (world, trapdoor, 4, y, 1, clip);
            else if (y == 0) {
                // Check if Igloo has a basement
                if (world.getBlockState(new BlockPos (box.minX + 4, box.minY - 1, box.minZ + 1)).getBlock() != Blocks.LADDER)
                    setBlockState (world, stonebrick, 4, y, 1, clip);
                else
                    setBlockState (world, trapdoor, 4, y, 1, clip);
            }
            else
                setBlockState (world, ladder, 4, y, 1, clip);

        }

        // Stargate
        gateX = 5; gateY = 0; gateZ = 8;
        gatePos = new BlockPos (box.minX + gateX, box.minY + gateY, box.minZ + gateZ);
        System.err.println ("SGCraft: Gate Pos is " + gatePos);
        //gatePos = new BlockPos (1,1,1);
        ItemStack stoneBrick = new ItemStack(Blocks.STONEBRICK, 1);
        camo0 = stoneBrick; camo1 = stoneBrick; camo2 = stoneBrick; camo3 = stoneBrick; camo4 = stoneBrick;
        GenerateStargate (world, clip, spawnDirection, 2, true, true);

        // DHD
        dhdX = 7; dhdY = 1; dhdZ = 3;
        dhdPos = new BlockPos (box.minX + dhdX, box.minY + dhdY, box.minZ + dhdZ);
        System.err.println ("SGCraft: DHD Pos is " + dhdPos);
        //dhdPos = new BlockPos (2,2,2);
        GenerateDHD (world, clip, spawnDirection);

        // ZPM Chest
        chestX = 3; chestY = 1; chestZ = 3;
        chestPos = new BlockPos(box.minX + chestX, box.minY + chestY, box.minZ + chestZ);
        System.err.println ("SGCraft: Chest Pos is " + chestPos);
        //chestPos = new BlockPos (3,3,3);
        GenerateChest (world, clip, EnumFacing.SOUTH);

        // TokRa villager
        GenerateTokRa (world, clip, FeatureGeneration.iglooSpawnTokra);
        */

        pass++;  // Reminder: this entire method is called 4 times during world generation.

        return true;
    }
}
