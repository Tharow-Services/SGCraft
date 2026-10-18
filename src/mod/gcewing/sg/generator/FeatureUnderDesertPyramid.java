//------------------------------------------------------------------------------------------------
//
//   SG Craft - Generate a stargate inside a desert pyramid
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.generator;

import net.minecraft.block.BlockSandStone;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureComponent;
import net.minecraft.world.gen.structure.template.TemplateManager;

import java.util.Random;

public class FeatureUnderDesertPyramid extends FeatureStargate {
    @Override
    protected void readStructureFromNBT(NBTTagCompound compound, TemplateManager templateManager) {}

    @Override
    protected void writeStructureToNBT(NBTTagCompound compound) {}

    public FeatureUnderDesertPyramid() {}

    public FeatureUnderDesertPyramid(StructureComponent base) {
        this.base = base;

        // Set up the options
        Random rand = new Random();
        generateStructure = rand.nextInt(100) < FeatureGeneration.pyramidAddonChance;
        generateChevronUpgrade = rand.nextInt(100) < FeatureGeneration.pyramidChevronUpgradeChance;
        generateZpmChest = rand.nextInt(100) < FeatureGeneration.pyramidZpmChestChance;
        generateTokra = FeatureGeneration.pyramidSpawnTokra;

        taintedZpm = rand.nextInt(100) < FeatureGeneration.genericTaintedZpm;

        if (FeatureGeneration.debugStructures)
            System.out.println("SGCraft: Creating FeatureUnderDesertPyramid with GenerateStructure: " + generateStructure);

        // Set up the building box
        // Center on the temple's blue floor block, main chamber
        StructureBoundingBox baseBox = base.getBoundingBox();
        centerPos = new BlockPos(baseBox.minX + (baseBox.maxX - baseBox.minX + 1) / 2, baseBox.minY + (baseBox.maxY - baseBox.minY + 1) / 2, baseBox.minZ + (baseBox.maxZ - baseBox.minZ + 1) / 2);
        int cx = centerPos.getX();
        int cz = centerPos.getZ();
        int bottom = baseBox.minY;
        boundingBox = new StructureBoundingBox(cx - 3, bottom, cz - 3, cx + 3, bottom + 5, cz + 3);

        // Set up the various positions based on the direction
        spawnDirection = base.getCoordBaseMode();
        setCoordBaseMode (EnumFacing.SOUTH);

        if (spawnDirection == EnumFacing.SOUTH) {
            gateX = 3; gateY = 0; gateZ = 5; gateFaces = EnumFacing.SOUTH;
            dhdX = 3; dhdY = 1; dhdZ = 0; dhdFaces = EnumFacing.SOUTH;
            chestX = 0; chestY = 1; chestZ = -3; chestFaces = EnumFacing.NORTH;
        } else if (spawnDirection == EnumFacing.NORTH) {
            gateX = 3; gateY = 0; gateZ = 1; gateFaces = EnumFacing.NORTH;
            dhdX = 3; dhdY = 1; dhdZ = 6; dhdFaces = EnumFacing.NORTH;
            chestX = 0; chestY = 1; chestZ = 9; chestFaces = EnumFacing.SOUTH;
        } else if (spawnDirection == EnumFacing.EAST) {
            gateX = 5; gateY = 0; gateZ = 3; gateFaces = EnumFacing.EAST;
            dhdX = 0; dhdY = 1; dhdZ = 3; dhdFaces = EnumFacing.EAST;
            chestX = -3; chestY = 1; chestZ = 0; chestFaces = EnumFacing.EAST;
        } else if (spawnDirection == EnumFacing.WEST) {
            gateX = 1; gateY = 0; gateZ = 3; gateFaces = EnumFacing.WEST;
            dhdX = 6; dhdY = 1; dhdZ = 3; dhdFaces = EnumFacing.WEST;
            chestX = 9; chestY = 1; chestZ = 0; chestFaces = EnumFacing.WEST;
        }

        gatePos = new BlockPos (boundingBox.minX + gateX, boundingBox.minY + gateY, boundingBox.minZ + gateZ);
        ItemStack sandStone = new ItemStack (Blocks.SANDSTONE, 1);
        ItemStack sandStoneRed = new ItemStack (Blocks.RED_SANDSTONE, 1);
        gateCamo[0] = sandStone; gateCamo[1] = sandStone; gateCamo[2] = sandStoneRed; gateCamo[3] = sandStone; gateCamo[4] = sandStone;

        dhdPos = new BlockPos (boundingBox.minX + dhdX, boundingBox.minY + dhdY, boundingBox.minZ + dhdZ);

        chestPos = new BlockPos (boundingBox.minX + chestX, boundingBox.minY + chestY, boundingBox.minZ + chestZ);

        if ((generateStructure) && (FeatureGeneration.debugStructures))
            System.out.println ("SGCraft: Desert Temple got a gate at " + centerPos + ", facing " + spawnDirection);
    }

    @Override
    public boolean addComponentParts(World world, Random rand, StructureBoundingBox clip) {
        return generateStructure && addAugmentationParts(world, rand, clip);
    }

    protected boolean addAugmentationParts(World world, Random rand, StructureBoundingBox clip) {
        if ((pass == 3) && (FeatureGeneration.debugStructures))
            System.out.printf("SGCraft: FeatureUnderDesertPyramid.addComponentParts in %s clipped to %s\n", getBoundingBox(), clip);

        if (base == null) {
            System.out.printf("SGCraft: FeatureUnderDesertPyramid.addComponentParts: no base\n");
            return false;
        }

        IBlockState air = Blocks.AIR.getDefaultState();
        IBlockState sandstoneSmooth = Blocks.SANDSTONE.getDefaultState().withProperty(BlockSandStone.TYPE, BlockSandStone.EnumType.SMOOTH);
        IBlockState sandstoneChisled = Blocks.SANDSTONE.getDefaultState().withProperty(BlockSandStone.TYPE, BlockSandStone.EnumType.CHISELED);

        // Main chamber
        fillWithAir(world, clip, 1, 1, 1, 5, 5, 5);

        // Pillars
        setBlockState(world, sandstoneChisled, 6, 1, 0, clip);
        setBlockState(world, sandstoneSmooth, 6, 2, 0, clip);
        setBlockState(world, sandstoneChisled, 6, 3, 0, clip);

        setBlockState(world, sandstoneChisled, 6, 1, 6, clip);
        setBlockState(world, sandstoneSmooth, 6, 2, 6, clip);
        setBlockState(world, sandstoneChisled, 6, 3, 6, clip);

        setBlockState(world, sandstoneChisled, 0, 1, 0, clip);
        setBlockState(world, sandstoneSmooth, 0, 2, 0, clip);
        setBlockState(world, sandstoneChisled, 0, 3, 0, clip);

        setBlockState(world, sandstoneChisled, 0, 1, 6, clip);
        setBlockState(world, sandstoneSmooth, 0, 2, 6, clip);
        setBlockState(world, sandstoneChisled, 0, 3, 6, clip);

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
