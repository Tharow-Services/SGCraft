//------------------------------------------------------------------------------------------------
//
//   SG Craft - Generate a stargate on top of a jungle temple
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.generator;

import net.minecraft.block.state.IBlockState;
import net.minecraft.block.BlockStairs;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.block.BlockStairs.EnumShape;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureComponent;

import java.util.Random;

public class FeatureJungleTemple extends FeatureStargate {
    public FeatureJungleTemple() {}

    public FeatureJungleTemple(StructureComponent base) {
        this.base = base;

        // Set up the options
        Random rand = new Random();
        generateStructure = rand.nextInt(100) < FeatureGeneration.jungleAddonChance;
        generateChevronUpgrade = rand.nextInt(100) < FeatureGeneration.jungleChevronUpgradeChance;
        generateZpmChest = rand.nextInt(100) < FeatureGeneration.jungleZpmChestChance;
        generateTokra = FeatureGeneration.jungleSpawnTokra;

        taintedZpm = rand.nextInt(100) < FeatureGeneration.genericTaintedZpm;

        if (FeatureGeneration.debugStructures)
            System.out.println("SGCraft: Creating FeatureJungleTemple with GenerateStructure: " + generateStructure);

        // Set up the building box
        // Centered on top of the jungle temple
        StructureBoundingBox baseBox = base.getBoundingBox();
        centerPos = new BlockPos(baseBox.minX + (baseBox.maxX - baseBox.minX + 1) / 2, baseBox.minY + (baseBox.maxY - baseBox.minY + 1) / 2, baseBox.minZ + (baseBox.maxZ - baseBox.minZ + 1) / 2);
        int cx = centerPos.getX();
        int cz = centerPos.getZ();
        int bottom = baseBox.maxY + 10; // Re-calculated later
        boundingBox = new StructureBoundingBox(cx - 4, bottom, cz - 4, cx + 4, bottom + 6, cz + 4);

        spawnDirection = base.getCoordBaseMode();
        setCoordBaseMode (EnumFacing.SOUTH);

        if (spawnDirection == EnumFacing.SOUTH) {
            gateX = 2; gateY = 4; gateZ = 4; gateFaces = EnumFacing.WEST;
            dhdX = 5; dhdY = 4; dhdZ = 2; dhdFaces = EnumFacing.WEST;
            chestX = 5; chestY = 4; chestZ = 6; chestFaces = EnumFacing.EAST;
        } else if (spawnDirection == EnumFacing.NORTH) {
            gateX = 2; gateY = 4; gateZ = 4; gateFaces = EnumFacing.WEST;
            dhdX = 5; dhdY = 4; dhdZ = 2; dhdFaces = EnumFacing.WEST;
            chestX = 5; chestY = 4; chestZ = 6; chestFaces = EnumFacing.EAST;
        } else if (spawnDirection == EnumFacing.EAST) {
            gateX = 4; gateY = 4; gateZ = 2; gateFaces = EnumFacing.NORTH;
            dhdX = 2; dhdY = 4; dhdZ = 5; dhdFaces = EnumFacing.NORTH;
            chestX = 6; chestY = 4; chestZ = 5; chestFaces = EnumFacing.SOUTH;
        } else if (spawnDirection == EnumFacing.WEST) {
            gateX = 4; gateY = 4; gateZ = 2; gateFaces = EnumFacing.NORTH;
            dhdX = 6; dhdY = 4; dhdZ = 5; dhdFaces = EnumFacing.NORTH;
            chestX = 2; chestY = 4; chestZ = 5; chestFaces = EnumFacing.SOUTH;
        }

        gatePos = new BlockPos (boundingBox.minX + gateX, boundingBox.minY + gateY, boundingBox.minZ + gateZ);
        ItemStack cobbleBlock = new ItemStack(Blocks.COBBLESTONE, 1, 0);
        ItemStack mossyBlock = new ItemStack(Blocks.MOSSY_COBBLESTONE, 1, 0);
        int tempRand = 0;
        for (int x = 0; x < 5; x++) {
            tempRand = rand.nextInt(2);
            if (tempRand == 0)
                gateCamo[x] = cobbleBlock;
            else
                gateCamo[x] = mossyBlock;
        }

        dhdPos = new BlockPos (boundingBox.minX + dhdX, boundingBox.minY + dhdY, boundingBox.minZ + dhdZ);

        chestPos = new BlockPos (boundingBox.minX + chestX, boundingBox.minY + chestY, boundingBox.minZ + chestZ);

        if ((generateStructure) && (FeatureGeneration.debugStructures))
            System.out.println ("SGCraft: Jungle Temple got a gate at " + centerPos + ", facing " + spawnDirection);
    }

    protected boolean addAugmentationParts(World world, Random rand, StructureBoundingBox clip) {
        if ((pass == 3) && (FeatureGeneration.debugStructures))
            System.out.printf("SGCraft: FeatureJungleTemple.addComponentParts in %s clipped to %s\n", getBoundingBox(), clip);

        // Our base Y level is 5 below the temple's roof, which is 9 above the temple's floor across its middle. The
        // temple comes before this piece in the structure, so it has already been moved to the ground it stands on (when
        // the first of its chunks is generated). This used to look down the middle of the temple for its roof, which
        // made Minecraft generate the chunk there when it wasn't generated yet.
        if (firstY == -1) {
            if (base == null) {
                System.out.printf("SGCraft: FeatureJungleTemple.addComponentParts: no base\n");
                return true;
            }

            firstY = base.getBoundingBox().minY + 9 - 5;
            boundingBox.minY = firstY;

            gatePos = new BlockPos (boundingBox.minX + gateX, boundingBox.minY + gateY, boundingBox.minZ + gateZ);
            dhdPos = new BlockPos (boundingBox.minX + dhdX, boundingBox.minY + dhdY, boundingBox.minZ + dhdZ);
            chestPos = new BlockPos (boundingBox.minX + chestX, boundingBox.minY + chestY, boundingBox.minZ + chestZ);
        } else {
            boundingBox.minY = firstY;
        }

        IBlockState air = Blocks.AIR.getDefaultState();
        IBlockState cobble = Blocks.COBBLESTONE.getDefaultState();
        IBlockState mossy = Blocks.MOSSY_COBBLESTONE.getDefaultState();
        IBlockState cobbleStairsS = Blocks.STONE_STAIRS.getDefaultState().withProperty(BlockStairs.FACING, EnumFacing.SOUTH);
        IBlockState cobbleStairsE = Blocks.STONE_STAIRS.getDefaultState().withProperty(BlockStairs.FACING, EnumFacing.EAST);

        // Clear the area
        fillWithAir(world, clip, 1, 4, 1, 7, 7, 7);

        // Fill floorhole
        IBlockState id = null;
        for (int x = 3; x < 5; x++) {
            for (int z = 3; z < 6; z++) {
                id = cobble;
                if (rand.nextInt(2) > 0)
                    id = mossy;

                if ((spawnDirection == EnumFacing.SOUTH) || (spawnDirection == EnumFacing.NORTH))
                    setBlockState (world, id, x, 3, z, clip);
                else
                    setBlockState (world, id, z, 3, x, clip);
            }
        }

        // Access stairs
        if ((spawnDirection == EnumFacing.NORTH) || (spawnDirection == EnumFacing.SOUTH)) {
            setBlockState(world, cobbleStairsS, 7, 2, 4, clip);
            setBlockState(world, air, 7, 2, 5, clip);
            setBlockState(world, air, 7, 2, 6, clip);
            setBlockState(world, air, 7, 2, 7, clip);
            setBlockState(world, cobbleStairsS, 7, 1, 5, clip);
            setBlockState(world, cobbleStairsS, 7, 0, 6, clip);
        } else {
            setBlockState(world, cobbleStairsE, 4, 2, 7, clip);
            setBlockState(world, air, 3, 2, 7, clip);
            setBlockState(world, air, 2, 2, 7, clip);
            setBlockState(world, air, 1, 2, 7, clip);
            setBlockState(world, cobbleStairsE, 3, 1, 7, clip);
            setBlockState(world, cobbleStairsE, 2, 0, 7, clip);
        }

        // Stargate
        GenerateStargate (world, clip, true);

        // Build stair brim
        GenerateStargateStairs (world, clip, Blocks.STONE_STAIRS);

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
