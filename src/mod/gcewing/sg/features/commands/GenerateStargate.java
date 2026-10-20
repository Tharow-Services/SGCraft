//------------------------------------------------------------------------------------------------
//
//   SG Craft - General/Shared code used by the rest of the SGCraft worldgen system
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.features.commands;

import gcewing.sg.BaseOrientation;
import gcewing.sg.SGCraft;
import gcewing.sg.block.SGRingBlock;
import gcewing.sg.features.zpm.ZPMItem;
import gcewing.sg.generator.FeatureGeneration;
import gcewing.sg.generator.GeneratorAddressRegistry;
import gcewing.sg.tileentity.DHDTE;
import gcewing.sg.tileentity.SGBaseTE;
import net.minecraft.block.*;
import net.minecraft.block.BlockStairs.EnumShape;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureComponent;
import net.minecraft.world.gen.structure.template.TemplateManager;
import net.minecraftforge.fml.common.registry.VillagerRegistry;

import java.util.Random;

public class GenerateStargate {

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
    public void setPass(int i) {
        this.pass=i;
    }

    BlockPos spawnPos = null;
    EnumFacing spawnDirection = null;

    public GenerateStargate() {}

    public GenerateStargate(BlockPos pos, EnumFacing facing) {
        super();
        this.spawnPos=pos;
        this.spawnDirection=facing;
    }
    protected BlockPos getOffsetPos(BlockPos pos) {
        int x, y, z;
        switch (spawnDirection) {
            case NORTH: x=1;y=0;z=1; break;
            case SOUTH: x=0;y=0;z=1; break;
            default: x=0;y=0;z=0;
        }
        return new BlockPos(this.spawnPos.getX()+pos.getX()+x, this.spawnPos.getY()+pos.getY()+y, this.spawnPos.getZ()+pos.getZ()+z);
    }
    protected void fillWithBlocks(World worldIn, int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, IBlockState boundaryBlockState, IBlockState insideBlockState, boolean existingOnly)
    {
        for (int i = yMin; i <= yMax; ++i)
        {
            for (int j = xMin; j <= xMax; ++j)
            {
                for (int k = zMin; k <= zMax; ++k)
                {
                    if (!existingOnly || worldIn.getBlockState(getOffsetPos(new BlockPos(j, i, k))).getMaterial() != Material.AIR)
                    {
                        if (i != yMin && i != yMax && j != xMin && j != xMax && k != zMin && k != zMax)
                        {
                            worldIn.setBlockState(getOffsetPos(new BlockPos(j, i, k)), insideBlockState);
                        }
                        else
                        {
                            worldIn.setBlockState(getOffsetPos(new BlockPos(j, i, k)), boundaryBlockState);
                        }
                    }
                }
            }
        }
    }

    // This is a quick one-stop-shop function to generate a Stargate, DHD and Chest/Tokra on a simple platform.
    // Set up the spawnDirection and boundingBox before calling it.
    public void GenerateSimpleStargatePlatform (World world, Block stairBlock, IBlockState platformBlock, IBlockState randHighBlock, IBlockState randLowBlock) {
        int sizeX = 8;
        int sizeZ = 8;

        IBlockState air = Blocks.AIR.getDefaultState();

        IBlockState stairsN = stairBlock.getDefaultState().withProperty(BlockStairs.FACING, EnumFacing.NORTH);
        IBlockState stairsS = stairBlock.getDefaultState().withProperty(BlockStairs.FACING, EnumFacing.SOUTH);
        IBlockState stairsE = stairBlock.getDefaultState().withProperty(BlockStairs.FACING, EnumFacing.EAST);
        IBlockState stairsW = stairBlock.getDefaultState().withProperty(BlockStairs.FACING, EnumFacing.WEST);
        IBlockState stairsNE = stairsN.withProperty(BlockStairs.SHAPE, EnumShape.OUTER_LEFT);
        IBlockState stairsSW = stairsS.withProperty(BlockStairs.SHAPE, EnumShape.OUTER_RIGHT);

        IBlockState id = null;

        fillWithBlocks (world, 0, -4, 0, 8, -1, 8, platformBlock, platformBlock, false);



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
                    edge = world.getBlockState(new BlockPos (this.spawnPos.getX() + minX, this.spawnPos.getY() + useY, this.spawnPos.getZ() + minZ - 1)).getBlock();
                    edge2 = world.getBlockState(new BlockPos (this.spawnPos.getX() + minX - 1, this.spawnPos.getY() + useY, this.spawnPos.getZ() + minZ)).getBlock();
                } else if ((x == maxX) && (z == minZ)) {
                    edge = world.getBlockState(new BlockPos (this.spawnPos.getX() + maxX, this.spawnPos.getY() + useY, this.spawnPos.getZ() + minZ - 1)).getBlock();
                    edge2 = world.getBlockState(new BlockPos (this.spawnPos.getX() + maxX + 1, this.spawnPos.getY() + useY, this.spawnPos.getZ() + minZ)).getBlock();
                } else if ((x == minX) && (z == maxZ)) {
                    edge = world.getBlockState(new BlockPos (this.spawnPos.getX() + minX - 1, this.spawnPos.getY() + useY, this.spawnPos.getZ() + maxZ)).getBlock();
                    edge2 = world.getBlockState(new BlockPos (this.spawnPos.getX() + minX, this.spawnPos.getY() + useY, this.spawnPos.getZ() + maxZ + 1)).getBlock();
                } else if ((x == maxX) && (z == maxZ)) {
                    edge = world.getBlockState(new BlockPos (this.spawnPos.getX() + maxX + 1, this.spawnPos.getY() + useY, this.spawnPos.getZ() + maxZ)).getBlock();
                    edge2 = world.getBlockState(new BlockPos (this.spawnPos.getX() + maxX, this.spawnPos.getY() + useY, this.spawnPos.getZ() + maxZ + 1)).getBlock();
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
                        edge = world.getBlockState(new BlockPos (this.spawnPos.getX() + x, this.spawnPos.getY() + useY, this.spawnPos.getZ() + minZ - 1)).getBlock();
                    else if (z == maxZ)
                        edge = world.getBlockState(new BlockPos (this.spawnPos.getX() + x, this.spawnPos.getY() + useY, this.spawnPos.getZ() + maxZ + 1)).getBlock();
                    else if (x == minX)
                        edge = world.getBlockState(new BlockPos (this.spawnPos.getX() + minX - 1, this.spawnPos.getY() + useY, this.spawnPos.getZ() + z)).getBlock();
                    else if (x == maxX)
                        edge = world.getBlockState(new BlockPos (this.spawnPos.getX() + maxX + 1, this.spawnPos.getY() + useY, this.spawnPos.getZ() + z)).getBlock();

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
                    world.setBlockState(new BlockPos(x,useY,z), id);
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

        gatePos = new BlockPos (this.spawnPos.getX() + gateX, this.spawnPos.getY() + gateY, this.spawnPos.getZ() + gateZ);
        dhdPos = new BlockPos (this.spawnPos.getX() + dhdX, this.spawnPos.getY() + dhdY, this.spawnPos.getZ() + dhdZ);
        chestPos = new BlockPos (this.spawnPos.getX() + chestX, this.spawnPos.getY() + chestY, this.spawnPos.getZ() + chestZ);

        // Stargate
        GenerateStargate (world, true);

        GenerateStargateStairs (world, stairBlock);

        // DHD
        GenerateDHD (world);


    }

    public void GenerateStargateStairs (World world, Block stairBlock) {
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
                    world.setBlockState(new BlockPos(x,gateY,z), id);
            }
        }
    }

    public void GenerateStargate (World world, boolean gateVertical) {

        IBlockState id = null;
        IBlockState air = Blocks.AIR.getDefaultState();
        IBlockState sgBase = SGCraft.sgBaseBlock.getDefaultState().withProperty(BaseOrientation.Orient4WaysByState.FACING, gateFaces);
        IBlockState[] sgRings = new IBlockState[2];
        sgRings[0] = SGCraft.sgRingBlock.getDefaultState();
        sgRings[1] = sgRings[0].withProperty(SGRingBlock.VARIANT, 1);

        boolean orientNS = (gateFaces == EnumFacing.NORTH) || (gateFaces == EnumFacing.SOUTH);


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
                        world.setBlockState(new BlockPos(gateX + i, gateY + j, gateZ),id);
                    } else {
                        world.setBlockState(new BlockPos( gateX, gateY + j, gateZ + i), id);

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
            te.hasChevronUpgrade=true;

            // Add decoration so base looks solid
            for (int x = 0; x < 5; x++) {
                if (gateCamo [x] != null)
                    te.getInventory().setInventorySlotContents(x, gateCamo[x].copy());
            }

            te.gateType = gateType;

            te.markChanged();

            if (te.homeAddress != null) {
                GeneratorAddressRegistry.addAddress(te.getWorld(), te.homeAddress);
                te.isGenerated = true; // Tag gate as being generated here.
            }

            if (te.homeAddress == null) {
                System.err.println("Something bad happened!!! please report to Dockter:  unable to assign home address during generation");
            }
        } else {
            System.err.println ("SGCraft: FeatureGeneration is done and Stargate TE was null! That's bad. gatePos " + gatePos + " and spawnDirection " + spawnDirection);
        }
    }

    public void GenerateDHD (World world) {
        IBlockState dhd = SGCraft.sgControllerBlock.getDefaultState().withProperty(BaseOrientation.Orient4WaysByState.FACING, dhdFaces);

        world.setBlockState(new BlockPos(dhdX, dhdY, dhdZ),dhd);


        System.out.println("DHD built at: " + dhdPos);

        DHDTE dhdte = null;
        if (dhdPos != null)
            dhdte = (DHDTE)world.getTileEntity(dhdPos);

        if (dhdte != null) {
            ItemStack naquadahPieces = new ItemStack(SGCraft.naquadah, 3);
            dhdte.getInventory().setInventorySlotContents(0, naquadahPieces);
        } else {
            System.err.println ("SGCraft: FeatureGeneration is done and DHD TE was null! That's bad. dhdPos " + dhdPos + " and spawnDirection " + spawnDirection);
        }
    }
}
