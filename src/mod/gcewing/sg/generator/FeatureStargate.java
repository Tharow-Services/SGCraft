//------------------------------------------------------------------------------------------------
//
//   SG Craft - General/Shared code used by the rest of the SGCraft worldgen system
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.generator;

import gcewing.sg.BaseOrientation;
import gcewing.sg.SGCraft;
import gcewing.sg.block.SGRingBlock;
import gcewing.sg.features.zpm.ZPMItem;
import gcewing.sg.tileentity.DHDTE;
import gcewing.sg.tileentity.SGBaseTE;
import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockStoneBrick;
import net.minecraft.block.BlockSandStone;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.EnumFacing;
import net.minecraft.block.BlockStairs.EnumShape;
import net.minecraft.util.math.BlockPos;

import net.minecraft.init.Biomes;
import net.minecraft.world.biome.Biome;

import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureComponent;
import net.minecraft.world.gen.structure.template.TemplateManager;
import net.minecraftforge.fml.common.registry.VillagerRegistry;

import java.util.Random;

public class FeatureStargate extends StructureComponent {
    StructureComponent base;

    // Generation options
    boolean generateStructure = false;
    boolean generateChevronUpgrade = false;
    boolean generateZpmChest = false;
    boolean generateTokra = false;

    boolean taintedZpm = false;
    boolean generateSubmerged = false;
    boolean generateBuried = false;
    boolean generateRuined = false;

    // Gate info
    int gateType = 1;
    int gateX = 0, gateY = 0, gateZ = 0;
    BlockPos gatePos = null;
    EnumFacing gateFaces = null;
    ItemStack gateCamo[] = new ItemStack[5];

    // DHD info
    int dhdX = 0, dhdY = 0, dhdZ = 0;
    BlockPos dhdPos = null;
    EnumFacing dhdFaces = null;

    // Chest info
    int chestX = 0, chestY = 0, chestZ = 0;
    BlockPos chestPos = null;
    EnumFacing chestFaces = null;

    // General/Misc info
    int pass = 0;
    BlockPos centerPos = null;
    int firstY = -1;
    EnumFacing spawnDirection = null;

    public FeatureStargate() {}

    public FeatureStargate(StructureComponent base) {
        super(0);
        this.base = base;
    }

    public FeatureStargate(StructureComponent base, StructureBoundingBox box) {
        super(0);
        this.base = base;
        this.boundingBox = box;
    }

    @Override
    protected void readStructureFromNBT(NBTTagCompound compound, TemplateManager templateManager) {}

    @Override
    protected void writeStructureToNBT(NBTTagCompound compound) {}

    @Override
    public boolean addComponentParts(World world, Random rand, StructureBoundingBox clip) {
        return generateStructure && addAugmentationParts(world, rand, clip);
    }

    protected boolean addAugmentationParts(World world, Random rand, StructureBoundingBox clip) {
        return true;
    }

    public void updateBoundingBox (StructureBoundingBox box) {
        this.boundingBox = box;
    }

    // This is a quick one-stop-shop function to generate a Stargate, DHD and Chest/Tokra on a simple platform.
    // Set up the spawnDirection and boundingBox before calling it.
    public void GenerateSimpleStargatePlatform (World world, StructureBoundingBox clip, Block stairBlock, IBlockState platformBlock, IBlockState randHighBlock, IBlockState randLowBlock) {
        int sizeX = 8;
        int sizeZ = 8;

        StructureBoundingBox box = getBoundingBox();

        IBlockState air = Blocks.AIR.getDefaultState();

        IBlockState stairsN = stairBlock.getDefaultState().withProperty(BlockStairs.FACING, EnumFacing.NORTH);
        IBlockState stairsS = stairBlock.getDefaultState().withProperty(BlockStairs.FACING, EnumFacing.SOUTH);
        IBlockState stairsE = stairBlock.getDefaultState().withProperty(BlockStairs.FACING, EnumFacing.EAST);
        IBlockState stairsW = stairBlock.getDefaultState().withProperty(BlockStairs.FACING, EnumFacing.WEST);
        IBlockState stairsNE = stairsN.withProperty(BlockStairs.SHAPE, BlockStairs.EnumShape.OUTER_LEFT);
        IBlockState stairsSW = stairsS.withProperty(BlockStairs.SHAPE, BlockStairs.EnumShape.OUTER_RIGHT);

        IBlockState id = null;

        fillWithBlocks (world, clip, 0, -4, 0, 8, -1, 8, platformBlock, platformBlock, false);

        Random rand = new Random();

        // Platform First layer
        int minX = 0, maxX = sizeX;
        int minZ = 0, maxZ = sizeZ;
        int useY = 0;
        int randBrick = 0;
        Block edge = null, edge2 = null;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                edge = null;
                id = null;

                //  Handle corners
                if ((x == minX) && (z == minZ)) {
                    edge = world.getBlockState(new BlockPos (box.minX + minX, box.minY + useY, box.minZ + minZ - 1)).getBlock();
                    edge2 = world.getBlockState(new BlockPos (box.minX + minX - 1, box.minY + useY, box.minZ + minZ)).getBlock();
                } else if ((x == maxX) && (z == minZ)) {
                    edge = world.getBlockState(new BlockPos (box.minX + maxX, box.minY + useY, box.minZ + minZ - 1)).getBlock();
                    edge2 = world.getBlockState(new BlockPos (box.minX + maxX + 1, box.minY + useY, box.minZ + minZ)).getBlock();
                } else if ((x == minX) && (z == maxZ)) {
                    edge = world.getBlockState(new BlockPos (box.minX + minX - 1, box.minY + useY, box.minZ + maxZ)).getBlock();
                    edge2 = world.getBlockState(new BlockPos (box.minX + minX, box.minY + useY, box.minZ + maxZ + 1)).getBlock();
                } else if ((x == maxX) && (z == maxZ)) {
                    edge = world.getBlockState(new BlockPos (box.minX + maxX + 1, box.minY + useY, box.minZ + maxZ)).getBlock();
                    edge2 = world.getBlockState(new BlockPos (box.minX + maxX, box.minY + useY, box.minZ + maxZ + 1)).getBlock();
                }

                if ((edge != null) && (edge2 != null)) {
                    if ((edge == Blocks.WATER) || (edge2 == Blocks.WATER))
                        id = randHighBlock;
                    else if ((edge != Blocks.AIR) || (edge2 != Blocks.AIR))
                        id = randLowBlock;
                }

                if (id == null) {

                    //  Check if edges are flush
                    edge = null;
                    if (z == minZ)
                        edge = world.getBlockState(new BlockPos (box.minX + x, box.minY + useY, box.minZ + minZ - 1)).getBlock();
                    else if (z == maxZ)
                        edge = world.getBlockState(new BlockPos (box.minX + x, box.minY + useY, box.minZ + maxZ + 1)).getBlock();
                    else if (x == minX)
                        edge = world.getBlockState(new BlockPos (box.minX + minX - 1, box.minY + useY, box.minZ + z)).getBlock();
                    else if (x == maxX)
                        edge = world.getBlockState(new BlockPos (box.minX + maxX + 1, box.minY + useY, box.minZ + z)).getBlock();

                    if (edge != null) {
                        if (edge == Blocks.WATER)
                            id = randHighBlock;
                        else if (edge != Blocks.AIR)
                            id = randLowBlock;
                    }
                }

                // Handle edges
                if (id == null) {
                    if ((x == minX) && (z > minZ) && (z < maxZ)) {
                        id = stairsE;
                    } else if ((x == maxX) && (z > minZ) && (z < maxZ)) {
                        id = stairsW;
                    } else if ((z == minZ) && (x > minX) && (x < maxX)) {
                        id = stairsN;
                    } else if ((z == maxZ) && (x > minX) && (x < maxX)) {
                        id = stairsS;
                    } else if ((x == minX) && (z == minZ)) {
                        id = stairsNE;
                    } else if ((x == minX) && (z == maxZ)) {
                        id = stairsSW;
                    } else if ((x == maxX) && (z == minZ)) {
                        id = stairsNE;
                    } else if ((x == maxX) && (z == maxZ)) {
                        id = stairsSW;
                    } else {
                        randBrick = rand.nextInt (40);
                        if (randBrick < 15)
                            id = randHighBlock;
                        else if (randBrick < 19)
                            id = randLowBlock;
                        else
                            id = platformBlock;
                    }
                }

                if (id != null) {
                    setBlockState (world, id, x, useY, z, clip);
                }
            }
        }

        // Sort out locations
        if (spawnDirection == EnumFacing.NORTH) {
            gateX = 4; gateY = 1; gateZ = 6; gateFaces = EnumFacing.SOUTH;
            dhdX = 6; dhdY = 1; dhdZ = 2; dhdFaces = EnumFacing.SOUTH;
            chestX = 2; chestY = 1; chestZ = 2; chestFaces = EnumFacing.SOUTH;
        } else if (spawnDirection == EnumFacing.SOUTH) {
            gateX = 4; gateY = 1; gateZ = 2; gateFaces = EnumFacing.NORTH;
            dhdX = 6; dhdY = 1; dhdZ = 6; dhdFaces = EnumFacing.NORTH;
            chestX = 2; chestY = 1; chestZ = 6; chestFaces = EnumFacing.NORTH;
        } else if (spawnDirection == EnumFacing.EAST) {
            gateX = 2; gateY = 1; gateZ = 4; gateFaces = EnumFacing.WEST;
            dhdX = 6; dhdY = 1; dhdZ = 2; dhdFaces = EnumFacing.WEST;
            chestX = 6; chestY = 1; chestZ = 6; chestFaces = EnumFacing.EAST;
        } else if (spawnDirection == EnumFacing.WEST) {
            gateX = 6; gateY = 1; gateZ = 4; gateFaces = EnumFacing.EAST;
            dhdX = 2; dhdY = 1; dhdZ = 2; dhdFaces = EnumFacing.EAST;
            chestX = 2; chestY = 1; chestZ = 6; chestFaces = EnumFacing.WEST;
        }

        gatePos = new BlockPos (box.minX + gateX, box.minY + gateY, box.minZ + gateZ);
        dhdPos = new BlockPos (box.minX + dhdX, box.minY + dhdY, box.minZ + dhdZ);
        chestPos = new BlockPos (box.minX + chestX, box.minY + chestY, box.minZ + chestZ);

        // Stargate
        GenerateStargate (world, clip, true);

        GenerateStargateStairs (world, clip, stairBlock);

        // DHD
        GenerateDHD (world, clip);

        // ZPM Chest
        GenerateChest (world, clip);

        // TokRa villager
        GenerateTokRa (world, clip);

    }

    public void GenerateStargateStairs (World world, StructureBoundingBox clip, Block stairBlock) {
        IBlockState stairsN = stairBlock.getDefaultState().withProperty(BlockStairs.FACING, EnumFacing.NORTH);
        IBlockState stairsS = stairBlock.getDefaultState().withProperty(BlockStairs.FACING, EnumFacing.SOUTH);
        IBlockState stairsE = stairBlock.getDefaultState().withProperty(BlockStairs.FACING, EnumFacing.EAST);
        IBlockState stairsW = stairBlock.getDefaultState().withProperty(BlockStairs.FACING, EnumFacing.WEST);
        IBlockState stairsNE = stairsN.withProperty(BlockStairs.SHAPE, EnumShape.OUTER_LEFT);
        IBlockState stairsSW = stairsS.withProperty(BlockStairs.SHAPE, EnumShape.OUTER_RIGHT);

        int minX = gateX - 1;
        int minZ = gateZ - 3;
        int maxX = gateX + 1;
        int maxZ = gateZ + 3;

        if ((gateFaces == EnumFacing.NORTH) || (gateFaces == EnumFacing.SOUTH)) {
            minX = gateX - 3; minZ = gateZ - 1; maxX = gateX + 3; maxZ = gateZ + 1;
        }

        // Establish stairbrim
        IBlockState id = null;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                id = null;

                if ((x > minX) && (x < maxX) && (z == minZ))
                    id = stairsN;
                else if ((x > minX) && (x < maxX) && (z == maxZ))
                    id = stairsS;
                else if ((z > minZ) && (z < maxZ) && (x == minX))
                    id = stairsE;
                else if ((z > minZ) && (z < maxZ) && (x == maxX))
                    id = stairsW;
                else if ((x == minX) && (z== minZ))
                    id = stairsNE;
                else if ((x == maxX) && (z == maxZ))
                    id = stairsSW;
                else if ((x == minX) && (z == maxZ))
                    id = stairsSW;
                else if ((x == maxX) && (z == minZ))
                    id = stairsNE;

                if (id != null)
                    setBlockState (world, id, x, gateY, z, clip);
            }
        }
    }

    public void recalcGatePos () {
        if (boundingBox != null)
            gatePos = new BlockPos (boundingBox.minX + gateX, boundingBox.minY + gateY, boundingBox.minZ + gateZ);
    }

    public void GenerateStargate (World world, StructureBoundingBox clip, boolean gateVertical) {
        boolean debug = false;

        if ((pass == 3) && (FeatureGeneration.debugStructures))
            debug = true;

        IBlockState id = null;
        IBlockState air = Blocks.AIR.getDefaultState();
        IBlockState sgBase = SGCraft.sgBaseBlock.getDefaultState().withProperty(BaseOrientation.Orient4WaysByState.FACING, gateFaces);
        IBlockState[] sgRings = new IBlockState[2];
        sgRings[0] = SGCraft.sgRingBlock.getDefaultState();
        sgRings[1] = sgRings[0].withProperty(SGRingBlock.VARIANT, 1);

        boolean orientNS = false;

        if ((gateFaces == EnumFacing.NORTH) || (gateFaces == EnumFacing.SOUTH))
            orientNS = true;

        if (debug)
            System.out.println("Stargate built at: " + gatePos);

        if (gateVertical) {
            for (int i = -2; i <= 2; i++) {
                for (int j = 0; j <= 4; j++) {
                    if (i == 0 && j == 0) {
                        id = sgBase;
                    } else if (i == -2 || i == 2 || j == 0 || j == 4) {
                        id = sgRings[(i + j + 1) & 1];
                    } else {
                        id = air;
                    }

                    if (orientNS) {
                        setBlockState(world, id, gateX + i, gateY + j, gateZ, clip);

                    } else {
                        setBlockState(world, id, gateX, gateY + j, gateZ + i, clip);

                    }
                }
            }
        } else {
            System.out.println("Stargate attempted to spawn Horizontal, but that code is not done yet!");
            return;
        }

        SGBaseTE te = null;
        if (gatePos != null)
            te = (SGBaseTE)world.getTileEntity(gatePos);

        if (te != null) {
            if (generateChevronUpgrade) {
                te.hasChevronUpgrade = true;

                if (debug)
                    System.out.println("Stargate granted chevron upgrade.");
            }

            // Add decoration so base looks solid
            for (int x = 0; x < 5; x++) {
                if (gateCamo [x] != null)
                    te.getInventory().setInventorySlotContents(x, gateCamo[x].copy());
            }

            te.gateType = gateType;

            if (debug)
                System.out.println("Stargate is type " + gateType);

            te.markChanged();

            if (te.homeAddress != null) {
                GeneratorAddressRegistry.addAddress(te.getWorld(), te.homeAddress);
                te.isGenerated = true; // Tag gate as being generated here.
            }

            if ((te.homeAddress == null) && (pass >= 3)) {
                System.err.println("Something bad happened!!! please report to Dockter:  unable to assign home address during generation");
            }
        } else if (pass == 3) {
            System.err.println ("SGCraft: FeatureGeneration is done and Stargate TE was null! That's bad. gatePos " + gatePos + " and spawnDirection " + spawnDirection);
        }
    }

    public void GenerateDHD (World world, StructureBoundingBox clip) {
        IBlockState dhd = SGCraft.sgControllerBlock.getDefaultState().withProperty(BaseOrientation.Orient4WaysByState.FACING, dhdFaces);
        boolean debug = false;

        if ((pass == 3) && (FeatureGeneration.debugStructures))
            debug = true;

        setBlockState(world, dhd, dhdX, dhdY, dhdZ, clip);

        if (debug)
            System.out.println("DHD built at: " + dhdPos);

        DHDTE dhdte = null;
        if (dhdPos != null)
            dhdte = (DHDTE)world.getTileEntity(dhdPos);

        if (dhdte != null) {
            ItemStack naquadahPieces = new ItemStack(SGCraft.naquadah, 3);
            dhdte.getInventory().setInventorySlotContents(0, naquadahPieces);
        } else if (pass == 3) {
            System.err.println ("SGCraft: FeatureGeneration is done and DHD TE was null! That's bad. dhdPos " + dhdPos + " and spawnDirection " + spawnDirection);
        }
    }

    public void GenerateChest (World world, StructureBoundingBox clip) {
        IBlockState air = Blocks.AIR.getDefaultState();
        IBlockState chest = Blocks.CHEST.getDefaultState().withProperty(BlockChest.FACING, chestFaces);
        boolean debug = false;

        if ((pass == 3) && (FeatureGeneration.debugStructures))
            debug = true;

        if (generateZpmChest) {
            if (SGCraft.zpm == null) {
                return;
            }

            setBlockState(world, air, chestX, chestY, chestZ, clip);
            setBlockState(world, chest, chestX, chestY, chestZ, clip);

            TileEntityChest chestTE = null;
            if (chestPos != null)
                chestTE = (TileEntityChest) world.getTileEntity(chestPos);

            if (chestTE != null) {
                if (debug)
                    System.out.println("Generating ZPM Chest at: " + chestPos);

                ItemStack zpm = new ItemStack(SGCraft.zpm, 1);

                if (zpm != null) {
                    NBTTagCompound tag = zpm.getTagCompound();
                    if (tag == null)
                        tag = new NBTTagCompound();

                    zpm.setTagCompound(tag);
                    tag.setDouble(ZPMItem.ENERGY, Integer.MAX_VALUE);
                    tag.setBoolean(ZPMItem.LOADED, false);

                    if (taintedZpm)
                        zpm.addEnchantment(Enchantment.getEnchantmentByID(51), 1);

                    if (chestTE.isEmpty())
                        chestTE.getSingleChestHandler().insertItem(0, zpm, false);
                }
            } else if (pass == 3) {
                System.err.println ("FeatureGeneration is done and ZPM Chest TE was null! That's bad. chestPos: " + chestPos + " and spawnDirection " + spawnDirection);
            }
        }
    }

    public void GenerateTokRa (World world, StructureBoundingBox clip) {
        if (generateTokra && pass == 3) {
            if ((chestPos == null) && (FeatureGeneration.debugStructures)) {
                System.err.println ("Tried to spawn a Tok'Ra but chestPos was null!");
                return;
            }

            EntityVillager entityvillager = new EntityVillager(world);
            entityvillager.setLocationAndAngles((double)chestPos.getX() + 0.5D, (double)chestPos.getY() + 2, (double)chestPos.getZ() + 0.5D, 0.0F, 0.0F);
            entityvillager.setProfession(VillagerRegistry.getId(SGCraft.tokraProfession));
            entityvillager.finalizeMobSpawn(world.getDifficultyForLocation(new BlockPos(entityvillager)), (IEntityLivingData)null, false);
            world.spawnEntity(entityvillager);
        }
    }

    public static Block getBiomeStairblock (Biome biome) {
        if (biome == Biomes.DESERT)
            return Blocks.SANDSTONE_STAIRS;
        else if (biome == Biomes.PLAINS)
            return Blocks.STONE_BRICK_STAIRS;
        else if (biome == Biomes.SAVANNA)
            return Blocks.STONE_STAIRS;
        else if (biome == Biomes.TAIGA)
            return Blocks.STONE_BRICK_STAIRS;
        else
            return Blocks.STONE_BRICK_STAIRS;
    }

    public static Block getBiomeCamoblock (Biome biome) {
        if (biome == Biomes.DESERT)
            return Blocks.SANDSTONE;
        else if (biome == Biomes.PLAINS)
            return Blocks.STONEBRICK;
        else if (biome == Biomes.SAVANNA)
            return Blocks.COBBLESTONE;
        else if (biome == Biomes.TAIGA)
            return Blocks.STONEBRICK;
        else
            return Blocks.STONEBRICK;
    }

    public static IBlockState getBiomePlatformblock (Biome biome) {
        if (biome == Biomes.DESERT)
            return Blocks.SANDSTONE.getDefaultState().withProperty(BlockSandStone.TYPE, BlockSandStone.EnumType.SMOOTH);
        else if (biome == Biomes.PLAINS)
            return Blocks.STONEBRICK.getDefaultState();
        else if (biome == Biomes.SAVANNA)
            return Blocks.COBBLESTONE.getDefaultState();
        else if (biome == Biomes.TAIGA)
            return Blocks.STONEBRICK.getDefaultState();
        else
            return Blocks.STONEBRICK.getDefaultState();
    }

    public static IBlockState getBiomeRandblockHigh (Biome biome) {
        if (biome == Biomes.DESERT)
            return Blocks.SANDSTONE.getDefaultState().withProperty(BlockSandStone.TYPE, BlockSandStone.EnumType.SMOOTH);
        else if (biome == Biomes.PLAINS)
            return Blocks.STONEBRICK.getDefaultState().withProperty(BlockStoneBrick.VARIANT, BlockStoneBrick.EnumType.MOSSY);
        else if (biome == Biomes.SAVANNA)
            return Blocks.STONE.getDefaultState();
        else if (biome == Biomes.TAIGA)
            return Blocks.STONEBRICK.getDefaultState().withProperty(BlockStoneBrick.VARIANT, BlockStoneBrick.EnumType.CRACKED);
        else
            return Blocks.STONEBRICK.getDefaultState();
    }

    public static IBlockState getBiomeRandblockLow (Biome biome) {
        if (biome == Biomes.DESERT)
            return Blocks.SANDSTONE.getDefaultState().withProperty(BlockSandStone.TYPE, BlockSandStone.EnumType.CHISELED);
        else if (biome == Biomes.PLAINS)
            return Blocks.STONEBRICK.getDefaultState().withProperty(BlockStoneBrick.VARIANT, BlockStoneBrick.EnumType.CRACKED);
        else if (biome == Biomes.SAVANNA)
            return Blocks.GRAVEL.getDefaultState();
        else if (biome == Biomes.TAIGA)
            return Blocks.GRAVEL.getDefaultState();
        else
            return Blocks.STONEBRICK.getDefaultState();
    }

    public static int getSurfaceLevel (World world, BlockPos loc) {
        BlockPos startPos = world.getTopSolidOrLiquidBlock (loc);
        Block check = null;
        int curX = startPos.getX();
        int curY = startPos.getY();
        int curZ = startPos.getZ();

        int maxTries = 10;
        for (int tries = 0; tries <= maxTries; tries++){
            if (world.getBlockState (new BlockPos (curX, curY + tries, curZ)).getBlock() != Blocks.WATER)
                return (curY + tries);
        }

        return (curY + maxTries);
    }

    // Because World.getTopSolidOrLiquidBlock lies like a snake's belly in a wagon rut.
    public static int getJungleTempleTop (World world, BlockPos centerPos) {
        int curX = centerPos.getX();
        int curY = centerPos.getY();
        int curZ = centerPos.getZ();
        Block temp = null;

        for (int tries = 255; tries >= 0; tries--) {
            temp = world.getBlockState (new BlockPos (curX, tries, curZ)).getBlock();

            if ((temp == Blocks.MOSSY_COBBLESTONE) || (temp == Blocks.STONE_STAIRS) || (temp == Blocks.COBBLESTONE))
                return (tries);
        }

        System.err.println ("No jungle temple found! whoops.");
        return (-1);
    }

    public static EnumFacing getOppDirection(EnumFacing origDir) {
        if (origDir == EnumFacing.NORTH) {
            return EnumFacing.SOUTH;
        } else if (origDir == EnumFacing.SOUTH) {
            return EnumFacing.NORTH;
        } else if (origDir == EnumFacing.EAST) {
            return EnumFacing.WEST;
        } else {
            return EnumFacing.EAST;
        }
    }
}
