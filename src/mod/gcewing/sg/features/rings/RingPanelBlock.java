package gcewing.sg.features.rings;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

// A ring control panel: a small Goa'uld panel on a wall. Pressing it starts the nearest ring platform within reach.
public class RingPanelBlock extends Block {

    public static final PropertyDirection FACING = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);

    // A plate on the wall behind it (the side it faces away from), 10 by 12 pixels, 1 deep
    private static final AxisAlignedBB NORTH = new AxisAlignedBB(3 / 16.0, 2 / 16.0, 15 / 16.0, 13 / 16.0, 14 / 16.0, 1);
    private static final AxisAlignedBB SOUTH = new AxisAlignedBB(3 / 16.0, 2 / 16.0, 0, 13 / 16.0, 14 / 16.0, 1 / 16.0);
    private static final AxisAlignedBB WEST = new AxisAlignedBB(15 / 16.0, 2 / 16.0, 3 / 16.0, 1, 14 / 16.0, 13 / 16.0);
    private static final AxisAlignedBB EAST = new AxisAlignedBB(0, 2 / 16.0, 3 / 16.0, 1 / 16.0, 14 / 16.0, 13 / 16.0);

    public RingPanelBlock() {
        super(Material.IRON, MapColor.GOLD);
        setHardness(2F);
        setSoundType(SoundType.METAL);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex();
    }

    // On the wall clicked, facing out from it; clicked on a floor or ceiling, facing whoever placed it
    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ,
            int meta, EntityLivingBase placer) {
        EnumFacing face = facing.getAxis().isHorizontal() ? facing : placer.getHorizontalFacing().getOpposite();
        return getDefaultState().withProperty(FACING, face);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
            EnumFacing side, float hitX, float hitY, float hitZ) {
        if (!world.isRemote && hand == EnumHand.MAIN_HAND) {
            Vec3d at = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            RingTransports.activate(player, RingTransports.platformNear(world, at, null));
        }
        return true;
    }

    // ---- Shape ----

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        switch (state.getValue(FACING)) {
            case SOUTH: return SOUTH;
            case WEST: return WEST;
            case EAST: return EAST;
            default: return NORTH;
        }
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return NULL_AABB;
    }

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
}
