//------------------------------------------------------------------------------------------------
//
//   SG Craft - Generate a stargate near a swamp hut
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.generator;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.block.BlockStoneBrick;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureComponent;
import net.minecraft.world.gen.structure.template.TemplateManager;

import java.util.Random;
import java.util.Collections;
import java.util.List;
import java.util.LinkedList;

public class FeatureSwampHut extends FeatureStargate {
    @Override
    protected void readStructureFromNBT(NBTTagCompound compound, TemplateManager templateManager) {}

    @Override
    protected void writeStructureToNBT(NBTTagCompound compound) {}

    public FeatureSwampHut() {}

    public FeatureSwampHut(StructureComponent base) {
        this.base = base;

        // Set up the options
        Random rand = new Random();
        generateStructure = rand.nextInt(100) < FeatureGeneration.swampAddonChance;
        generateChevronUpgrade = rand.nextInt(100) < FeatureGeneration.swampChevronUpgradeChance;
        generateZpmChest = rand.nextInt(100) < FeatureGeneration.swampZpmChestChance;
        generateTokra = FeatureGeneration.swampSpawnTokra;

        taintedZpm = rand.nextInt(100) < FeatureGeneration.genericTaintedZpm;

        if (FeatureGeneration.debugStructures)
            System.out.println("SGCraft: Creating FeatureSwampHut with GenerateStructure: " + generateStructure);

        // Set up the building box
        // Randomize the location near the swamp hut
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

        // Since we are not attaching to the Swamp Hut itself, randomize the direction
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

    @Override
    public boolean addComponentParts(World world, Random rand, StructureBoundingBox clip) {
        return generateStructure && addAugmentationParts(world, rand, clip);
    }

    protected boolean addAugmentationParts(World world, Random rand, StructureBoundingBox clip) {
        if ((pass == 3) && (FeatureGeneration.debugStructures))
            System.out.printf("SGCraft: FeatureSwampHut.addComponentParts in %s clipped to %s\n", getBoundingBox(), clip);

        if (base == null) {
            System.out.printf("SGCraft: FeatureSwampHut.addComponentParts: no base\n");
            return false;
        }

        // Check the four corners of our planned spawn area to find the lowest one, use that as our base Y level.
        // This gives the gate a chance to spawn partially/entirely submerged or buried, for added fun.
        if (firstY == -1) {
            List<Integer> cornerY = new LinkedList<Integer>();
            cornerY.add (world.getTopSolidOrLiquidBlock(new BlockPos (boundingBox.minX, boundingBox.minY, boundingBox.minZ)).getY());
            cornerY.add (world.getTopSolidOrLiquidBlock(new BlockPos (boundingBox.maxX, boundingBox.minY, boundingBox.minZ)).getY());
            cornerY.add (world.getTopSolidOrLiquidBlock(new BlockPos (boundingBox.minX, boundingBox.minY, boundingBox.maxZ)).getY());
            cornerY.add (world.getTopSolidOrLiquidBlock(new BlockPos (boundingBox.maxX, boundingBox.minY, boundingBox.maxZ)).getY());
            int lowY = Collections.min(cornerY);

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
        IBlockState stoneBrickMossy = Blocks.STONEBRICK.getDefaultState().withProperty(BlockStoneBrick.VARIANT, BlockStoneBrick.EnumType.MOSSY);
        IBlockState stoneBrickCracked = Blocks.STONEBRICK.getDefaultState().withProperty(BlockStoneBrick.VARIANT, BlockStoneBrick.EnumType.CRACKED);

        GenerateSimpleStargatePlatform (world, clip, Blocks.STONE_BRICK_STAIRS, stoneBrick, stoneBrickMossy, stoneBrickCracked);

        pass++;  // Reminder: this entire method is called 4 times during world generation.

        return true;
    }
}
