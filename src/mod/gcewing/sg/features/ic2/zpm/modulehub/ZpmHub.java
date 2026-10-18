package gcewing.sg.features.ic2.zpm.modulehub;

import gcewing.sg.SGCraft;
import gcewing.sg.features.zpm.link.ZpmLinks;
import gcewing.sg.client.gui.SGGui;
import gcewing.sg.features.zpm.ZPMItem;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Explosion;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import java.util.ArrayList;

import javax.annotation.Nullable;

public class ZpmHub extends BlockContainer {

    public static final PropertyInteger ZPMS = PropertyInteger.create("zpms", 0, 3);

    public ZpmHub() {
        super(Material.IRON);
        setHardness(1.5f);
    }

    @Override
    public boolean canHarvestBlock(IBlockAccess world, BlockPos pos, EntityPlayer player) {
        return true;
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote) {
            ZpmLinks.unlink(world, pos); // off its gate's list too, not just gone from the world
            // Taken out of their slots, with their energy, before they're dropped: left in, a screen still open on the hub
            // could take them a second time
            ZpmHubTE zpmHub = ZpmHubTE.at(world, pos);
            if (zpmHub != null) {
                for (int slot = 0; slot < ZpmHubTE.numZpmSlots; slot++) {
                    ItemStack zpm = zpmHub.removeStackFromSlot(slot);
                    if (!zpm.isEmpty()) {
                        Block.spawnAsEntity(world, pos, zpm);
                    }
                }
            }
        }
        super.breakBlock(world, pos, state);
    }

    @Nullable
    @Override
    public TileEntity createNewTileEntity(final World world, final int meta) {
        return new ZpmHubTE();
    }

    @Override
    public boolean isFullBlock(IBlockState state) { //Render surrounding blocks which connect..
        return false;
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.MODEL;
    }

    @Deprecated
    @Override
    public IBlockState getStateForPlacement(final World world, final BlockPos pos, final EnumFacing facing, final float hitX, final float hitY, final float hitZ, final int meta, final EntityLivingBase placer) {
        return this.getDefaultState().withProperty(BlockHorizontal.FACING, placer.getHorizontalFacing().getOpposite()).withProperty(ZPMS, 0);
    }

    @Deprecated
    @Override
    public IBlockState getStateFromMeta(final int meta) {
        return this.getDefaultState().withProperty(BlockHorizontal.FACING, EnumFacing.byHorizontalIndex(meta)).withProperty(ZPMS, 0);
    }

    @Override
    public int getMetaFromState(final IBlockState state) {
       return state.getValue(BlockHorizontal.FACING).getHorizontalIndex();
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) { //Render surrounding block that don't touch.
        return false; //
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, new IProperty[] {BlockHorizontal.FACING, ZPMS});
    }

    @Override
    protected boolean hasInvalidNeighbor(World worldIn, BlockPos pos) {
        return false;
    }

    // The ZPMs it shows are those in its slots, not the stored "zpms" state: that's set from whichever side last
    // touched a slot, so can be stale (a click with the Configurator, which opens no inventory to fix it, showed none)
    @Override
    public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity te = world instanceof ChunkCache ? ((ChunkCache) world).getTileEntity(pos, Chunk.EnumCreateEntityType.CHECK)
            : world.getTileEntity(pos);
        if (te instanceof ZpmHubTE) {
            int zpms = 0;
            for (int i = 0; i < ZpmHubTE.numZpmSlots; i++) {
                if (((ZpmHubTE) te).getStackInSlot(i).getItem() == SGCraft.zpm) {
                    zpms++;
                }
            }
            return state.withProperty(ZPMS, zpms);
        }
        return state;
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing side, float hx, float hy, float hz)  {
        world.notifyBlockUpdate(pos, state, state, 3);
        world.scheduleBlockUpdate(pos, state.getBlock(),0,0);
        ZpmHubTE.at(world, pos).markDirty();
        if (!world.isRemote) {
            SGCraft.mod.openGui(player, SGGui.ZPMHub, world, pos);
        }
        return true;
    }

    @Override
    public ArrayList<ItemStack> getDrops(IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
        ArrayList<ItemStack> returnList = new ArrayList<ItemStack>();
        Item item = getItemDropped(state, ((World)world).rand, fortune);
        ItemStack zpm_hub = new ItemStack(item, 1);
        returnList.add(zpm_hub);

        return returnList;
    }

    @Override
    public boolean canDropFromExplosion(Explosion explosionIn) {
        return SGCraft.canHarvestSGBaseBlock;
    }
}
