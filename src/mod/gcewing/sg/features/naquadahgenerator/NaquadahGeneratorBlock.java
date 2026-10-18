package gcewing.sg.features.naquadahgenerator;

import gcewing.sg.features.naquadahgenerator.network.NaquadahGeneratorNetworkHandler;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

// A Naquadah generator: half a block tall, its arms out to either side ending in spheres, a crystal in its front. Its
// arms draw in when it's switched off (RUNNING false), as in the show. Its power and fuel are its tile entity's.
public class NaquadahGeneratorBlock extends Block {

    public static final PropertyDirection FACING = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);
    public static final PropertyBool RUNNING = PropertyBool.create("running");

    // Its outline, arms along x (facing north or south) and along z (facing east or west), arms out and in
    private static final AxisAlignedBB OUT_X = new AxisAlignedBB(0.01, 0, 0.36, 0.99, 0.5, 0.64);
    private static final AxisAlignedBB IN_X = new AxisAlignedBB(0.1, 0, 0.36, 0.9, 0.5, 0.64);
    private static final AxisAlignedBB OUT_Z = new AxisAlignedBB(0.36, 0, 0.01, 0.64, 0.5, 0.99);
    private static final AxisAlignedBB IN_Z = new AxisAlignedBB(0.36, 0, 0.1, 0.64, 0.5, 0.9);

    public final NaquadahGenerators.Mark mark;

    public NaquadahGeneratorBlock(NaquadahGenerators.Mark mark) {
        super(Material.IRON, MapColor.GRAY);
        this.mark = mark;
        setHardness(4F);
        setResistance(20F);
        setSoundType(SoundType.METAL);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH).withProperty(RUNNING, false));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, RUNNING);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta & 3)).withProperty(RUNNING, (meta & 4) != 0);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex() | (state.getValue(RUNNING) ? 4 : 0);
    }

    // Its crystal faces whoever placed it
    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ,
            int meta, EntityLivingBase placer) {
        return getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite());
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new NaquadahGeneratorTE();
    }

    // Its fuel drops when it's broken
    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof NaquadahGeneratorTE) {
            NaquadahGeneratorTE generator = (NaquadahGeneratorTE) te;
            for (int i = 0; i < NaquadahGeneratorTE.numFuelSlots; i++) {
                InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), generator.getInventory().getItemStack(i));
            }
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
            EnumFacing side, float hitX, float hitY, float hitZ) {
        if (!world.isRemote && hand == EnumHand.MAIN_HAND) {
            NaquadahGeneratorNetworkHandler.openScreen(player, pos);
        }
        return true;
    }

    // ---- Shape and light ----

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
        return BlockFaceShape.UNDEFINED;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        boolean alongX = state.getValue(FACING).getAxis() == EnumFacing.Axis.Z;
        boolean out = state.getValue(RUNNING);
        return alongX ? (out ? OUT_X : IN_X) : (out ? OUT_Z : IN_Z);
    }

    // Its lights give a little light while it's switched on (arms out)
    @Override
    public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        return state.getValue(RUNNING) ? 7 : 0;
    }
}
