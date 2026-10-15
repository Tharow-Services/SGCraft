package gcewing.sg.block;

import gcewing.sg.tileentity.SGBaseTE;
import net.minecraft.block.Block;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.material.MaterialTransparent;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.Random;

import javax.annotation.Nullable;

/**
 * The light of an event horizon seen from behind a gate whose iris is closed. The iris covers the front of the gate,
 * so its ring stops glowing (block light shines every way) and these invisible blocks light the back instead.
 *
 * <p>They behave as air: nothing collides with them, they can't be clicked, any block can be placed into them, and
 * pistons and fluids destroy them. The gate places and removes them (see SGBaseTE.updateIrisLights), and each also
 * checks every few seconds that a gate still wants it, removing itself if not, so none are left behind when a gate's
 * chunk unloads, it is broken, or the game stops at the wrong moment.</p>
 */
public class EventHorizonLightBlock extends Block {

    public static final PropertyInteger LIGHT = PropertyInteger.create("light", 0, 15);

    // How often (ticks) each block checks it is still wanted
    private static final int CHECK_TICKS = 40;

    // Like air, but not Material.AIR itself: Minecraft never runs scheduled ticks for blocks of that
    private static final Material MATERIAL = new MaterialTransparent(MapColor.AIR) {
        {
            setReplaceable();
        }
    };

    public EventHorizonLightBlock() {
        super(MATERIAL);
        setDefaultState(blockState.getBaseState().withProperty(LIGHT, 0));
        setLightOpacity(0);
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, LIGHT);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(LIGHT, meta & 15);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(LIGHT);
    }

    @Override
    public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        return state.getValue(LIGHT);
    }

    // ---- Checking it is still wanted ----

    @Override
    public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote) {
            world.scheduleUpdate(pos, this, CHECK_TICKS);
        }
    }

    @Override
    public void updateTick(World world, BlockPos pos, IBlockState state, Random rand) {
        if (world.isRemote) {
            return;
        }
        if (SGBaseTE.wantsIrisLightAt(world, pos)) {
            world.scheduleUpdate(pos, this, CHECK_TICKS);
        } else {
            world.setBlockToAir(pos);
        }
    }

    // ---- Behaving as air (without claiming to be air, which could stop its scheduled checks running) ----

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.INVISIBLE;
    }

    @Nullable
    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return NULL_AABB;
    }

    @Override
    public boolean canCollideCheck(IBlockState state, boolean hitIfLiquid) {
        return false;
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
    public boolean isPassable(IBlockAccess world, BlockPos pos) {
        return true;
    }

    @Override
    public boolean isReplaceable(IBlockAccess world, BlockPos pos) {
        return true;
    }

    @Override
    public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
        return BlockFaceShape.UNDEFINED;
    }

    @Override
    public EnumPushReaction getPushReaction(IBlockState state) {
        return EnumPushReaction.DESTROY;
    }

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return Items.AIR;
    }

    @Override
    public void dropBlockAsItemWithChance(World world, BlockPos pos, IBlockState state, float chance, int fortune) {
    }
}
