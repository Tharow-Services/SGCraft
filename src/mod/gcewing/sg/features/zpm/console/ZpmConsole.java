package gcewing.sg.features.zpm.console;

import static gcewing.sg.client.gui.SGGui.ZPMConsole;

import gcewing.sg.SGCraft;
import gcewing.sg.features.zpm.link.ZpmLinks;
import gcewing.sg.features.zpm.ZPMItem;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
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

public class ZpmConsole extends BlockContainer {

    public static final PropertyBool ZPM_LOADED = PropertyBool.create("zpm");

    public ZpmConsole() {
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
            // Taken out of its slot, with its energy, before it's dropped: left in, a screen still open on the console
            // could take it a second time
            ZpmConsoleTE zpmConsole = ZpmConsoleTE.at(world, pos);
            if (zpmConsole != null) {
                ItemStack zpm = zpmConsole.removeStackFromSlot(0);
                if (!zpm.isEmpty()) {
                    Block.spawnAsEntity(world, pos, zpm);
                }
            }
        }
        super.breakBlock(world, pos, state);
    }

    @Nullable
    @Override
    public TileEntity createNewTileEntity(final World world, final int meta) {
        return new ZpmConsoleTE(SGCraft.ZPMEnergyPerSGEnergyUnit);
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
        return this.getDefaultState().withProperty(BlockHorizontal.FACING, placer.getHorizontalFacing().getOpposite()).withProperty(ZPM_LOADED, false);
    }

    @Deprecated
    @Override
    public IBlockState getStateFromMeta(final int meta) {
        return this.getDefaultState().withProperty(BlockHorizontal.FACING, EnumFacing.byHorizontalIndex(meta)).withProperty(ZPM_LOADED, false);
    }

    @Override
    public int getMetaFromState(final IBlockState state) {
        boolean zpmLoaded = state.getValue(ZpmConsole.ZPM_LOADED);
        if (zpmLoaded) {
            return state.getValue(BlockHorizontal.FACING).getHorizontalIndex() + 4;
        } else {
            return state.getValue(BlockHorizontal.FACING).getHorizontalIndex();
        }
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) { //Render surrounding block that don't touch.
        return false; //
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, new IProperty[] {BlockHorizontal.FACING, ZPM_LOADED});
    }

    @Override
    protected boolean hasInvalidNeighbor(World worldIn, BlockPos pos) {
        return false;
    }

    // The ZPM it shows is the one in its slot, not the stored "zpm" state: that's set from whichever side last touched
    // the slot, so can be stale (a click with the Configurator, which opens no inventory to fix it, showed it empty)
    @Override
    public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity te = world instanceof ChunkCache ? ((ChunkCache) world).getTileEntity(pos, Chunk.EnumCreateEntityType.CHECK)
            : world.getTileEntity(pos);
        if (te instanceof ZpmConsoleTE) {
            return state.withProperty(ZPM_LOADED, ZpmConsoleTE.isValidFuelItem(((ZpmConsoleTE) te).getStackInSlot(0)));
        }
        return state;
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing side, float hx, float hy, float hz)  {
        world.notifyBlockUpdate(pos, state, state, 3);
        world.scheduleBlockUpdate(pos, state.getBlock(),0,0);
        ZpmConsoleTE console = ZpmConsoleTE.at(world, pos);
        if (console == null) {
            return false;
        }
        console.markDirty();

        if (!world.isRemote) {
            SGCraft.mod.openGui(player, ZPMConsole, world, pos);
        }
        return true;
    }

    @Override
    public ArrayList<ItemStack> getDrops(IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
        ArrayList<ItemStack> returnList = new ArrayList<ItemStack>();
        Item item = getItemDropped(state, ((World)world).rand, fortune);
        ItemStack zpm_console = new ItemStack(item, 1);
        returnList.add(zpm_console);

        return returnList;
    }

    @Override
    public boolean canDropFromExplosion(Explosion explosionIn) {
        return SGCraft.canHarvestSGBaseBlock;
    }

    @Override
    public boolean shouldCheckWeakPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return true;
    }
}
