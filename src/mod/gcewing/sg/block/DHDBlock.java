//------------------------------------------------------------------------------------------------
//
//   SG Craft - Stargate Controller Block
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.block;

import gcewing.sg.BaseBlock;
import gcewing.sg.BaseMod.ModelSpec;
import gcewing.sg.BaseOrientation;
import gcewing.sg.tileentity.DHDTE;
import gcewing.sg.tileentity.SGBaseTE;
import gcewing.sg.SGCraft;
import gcewing.sg.Vector3;
import gcewing.sg.client.gui.SGGui;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Explosion;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public class DHDBlock extends BaseBlock<DHDTE> {

    protected static String[] textures = {
        "dhd_top",
        "dhd_side",
        "stargateblock",
        "dhd_button_dim",
    };
    protected static ModelSpec model = new ModelSpec("block/dhd.smeg", new Vector3(0, -0.5, 0), textures);

    public DHDBlock() {
        super(Material.ROCK, DHDTE.class);
        setHardness(1.5F);
        setCreativeTab(CreativeTabs.MISC);
    }
    
    @Override
    public String[] getTextureNames() {
        return textures;
    }
    
    @Override
    public ModelSpec getModelSpec(IBlockState state) {
        return model;
    }
    
    @Override
    public IOrientationHandler getOrientationHandler() {
        return BaseOrientation.orient4WaysByState;
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.INVISIBLE;
    }
    
    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        if (SGBaseBlock.debugMerge)
            System.out.printf("DHDBlock.onBlockAdded: at %d\n", pos);
        checkForLink(world, pos);
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase player, ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, player, stack);
        checkForLink(world, pos);
    }

    @Override
    public boolean canHarvestBlock(IBlockAccess world, BlockPos pos, EntityPlayer player) {
        return SGCraft.canHarvestDHD;
    }

    @Override
    public boolean removedByPlayer(IBlockState state, World world, BlockPos pos, EntityPlayer player, boolean willHarvest) {
        if (!player.capabilities.isCreativeMode) {
            DHDTE cte = getTileEntity(world, pos);
            if (cte != null) {
                if (cte.isLinkedToStargate) {
                    SGBaseTE gte = cte.getLinkedStargateTE();
                    if (gte != null) {
                        if (gte.isGenerated && !gte.canPlayerBreakGeneratedGate) {
                            return false;
                        }
                        if (!gte.isGenerated && !gte.canPlayerBreakPlayerGate) {
                            return false;
                        }
                    }
                }
            }
        }
        return super.removedByPlayer(state, world, pos, player, willHarvest);
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        DHDTE cte = getTileEntity(world, pos);
        super.breakBlock(world, pos, state);
        if (cte == null) {
            System.out.printf("DHDBlock.breakBlock: No tile entity at %s\n", pos);
        }
        else if (cte.isLinkedToStargate) {
            SGBaseTE gte = cte.getLinkedStargateTE();
            if (gte != null)
                gte.clearLinkToController();
        }
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing side, float cx, float cy, float cz) {
        DHDTE localDHD = getTileEntity(world, pos);
        if (localDHD != null) {
            if (localDHD.isLinkedToStargate) {
                SGBaseTE localGate = localDHD.getLinkedStargateTE();
                if (localGate != null) {
                    if (cy <= 0.5) {
                        if (player != null && localGate.allowAdminAccess(player.getName())) {
                            if (!world.isRemote) {
                                SGCraft.mod.openGui(player, SGGui.DHDFuel, world, pos);
                                return true;
                            }
                        }
                    } else {
                        if (world.isRemote) { // Only call on the client, server call here not needed because it doesn't have a container.
                            if (player != null && localGate.allowGateAccess(player.getName())) {
                                SGCraft.mod.openGui(player, SGGui.SGController, world, pos);
                                return true;
                            }
                        } else {
                            return true; // Server expects return true here if client was successful, which we can assume it was.
                        }
                    }
                }
            }
        }

        return false;
    }
    
    public void checkForLink(World world, BlockPos pos) {
        //System.out.printf("DHDBlock.checkForLink at %s\n", pos);
        DHDTE te = getTileEntity(world, pos);
        if (te != null)
            te.checkForLink();
        else
            System.out.printf("DHDBlock.breakBlock: No tile entity at %d\n", pos);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new DHDTE(DHDTE.cfgMaxEnergyBuffer);
    }

    @Override
    public boolean canDropFromExplosion(Explosion explosionIn) {
        return SGCraft.canHarvestDHD;
    }

    @Override
    public BlockFaceShape getBlockFaceShape(IBlockAccess worldIn, IBlockState state, BlockPos pos, EnumFacing face) {
        return BlockFaceShape.UNDEFINED;
    }
}
