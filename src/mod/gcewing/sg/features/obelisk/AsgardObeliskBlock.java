package gcewing.sg.features.obelisk;

import gcewing.sg.SGCraft;
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
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Random;

// The Asgard transport obelisk of Cimmeria (SG-1, Thor's Hammer): a stone pillar two blocks tall, Thor's hammer carved
// on top and a glowing crystal in its face. Obelisks are linked in pairs; touching the crystal of one beams the player
// to the other (see AsgardObelisks). The bottom half holds the link (AsgardObeliskTE); the top is the rest of it.
public class AsgardObeliskBlock extends Block {

    public static final PropertyDirection FACING = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);
    public static final PropertyBool UPPER = PropertyBool.create("upper");

    // The two kinds of obelisk, each linking only with its own kind: Thor's hammer on a grey stone pillar, and a tapering
    // sandstone obelisk carved with knotwork. Each has its gem at its own height and depth (sixteenths of a block, on the
    // top half), and its own outline.
    public enum Type {
        HAMMER(5, 2.3, box(2, 14), box(1, 15)),
        CELTIC(8, 4.9, box(1, 15), box(4, 12));

        public final double gemY, gemZ;
        final AxisAlignedBB lowerBox, upperBox;

        Type(double gemY, double gemZ, AxisAlignedBB lowerBox, AxisAlignedBB upperBox) {
            this.gemY = gemY / 16;
            this.gemZ = gemZ / 16;
            this.lowerBox = lowerBox;
            this.upperBox = upperBox;
        }

        private static AxisAlignedBB box(double from, double to) {
            return new AxisAlignedBB(from / 16, 0, from / 16, to / 16, 1, to / 16);
        }
    }

    public final Type type;

    public AsgardObeliskBlock() {
        this(Type.HAMMER);
    }

    public AsgardObeliskBlock(Type type) {
        super(Material.ROCK, MapColor.STONE);
        this.type = type;
        setHardness(3F);
        setResistance(15F);
        setSoundType(SoundType.STONE);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH).withProperty(UPPER, false));
    }

    // ---- Its two halves ----

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, UPPER);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta & 3)).withProperty(UPPER, (meta & 4) != 0);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex() | (state.getValue(UPPER) ? 4 : 0);
    }

    // Its crystal faces whoever placed it
    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ,
            int meta, EntityLivingBase placer) {
        return getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite());
    }

    // It needs room for its top half
    @Override
    public boolean canPlaceBlockAt(World world, BlockPos pos) {
        return super.canPlaceBlockAt(world, pos) && world.getBlockState(pos.up()).getBlock().isReplaceable(world, pos.up());
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        world.setBlockState(pos.up(), state.withProperty(UPPER, true), 3);
        // Whoever placed it owns it: they may always use and configure it
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof AsgardObeliskTE && placer instanceof EntityPlayer) {
            ((AsgardObeliskTE) te).owner = placer.getUniqueID();
            ((AsgardObeliskTE) te).ownerName = placer.getName();
            te.markDirty();
        }
    }

    // Either half goes when the other does
    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos fromPos) {
        boolean upper = state.getValue(UPPER);
        BlockPos other = upper ? pos.down() : pos.up();
        IBlockState otherState = world.getBlockState(other);
        if (otherState.getBlock() != this || otherState.getValue(UPPER) == upper) {
            world.destroyBlock(pos, !upper); // only the bottom half drops the obelisk
        }
    }

    // Breaking the top half breaks the bottom, which drops the obelisk (not in creative)
    @Override
    public void onBlockHarvested(World world, BlockPos pos, IBlockState state, EntityPlayer player) {
        if (state.getValue(UPPER)) {
            BlockPos lower = pos.down();
            if (world.getBlockState(lower).getBlock() == this) {
                world.destroyBlock(lower, !player.capabilities.isCreativeMode);
            }
        }
        super.onBlockHarvested(world, pos, state, player);
    }

    @Override
    public Item getItemDropped(IBlockState state, Random random, int fortune) {
        return state.getValue(UPPER) ? Items.AIR : Item.getItemFromBlock(this);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return !state.getValue(UPPER);
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new AsgardObeliskTE();
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
        return state.getValue(UPPER) ? type.upperBox : type.lowerBox;
    }

    // Its gem is round: the corners of its texture are clear (it shines while beaming someone; see AsgardObeliskRenderer)
    @SideOnly(Side.CLIENT)
    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    // ---- Use ----

    // Touching the red gem beams the player to the linked obelisk; sneaking, clicking anywhere on it links this obelisk
    // to another
    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
            EnumFacing side, float hitX, float hitY, float hitZ) {
        if (!world.isRemote && hand == EnumHand.MAIN_HAND) {
            BlockPos lower = state.getValue(UPPER) ? pos.down() : pos;
            TileEntity te = world.getTileEntity(lower);
            if (te instanceof AsgardObeliskTE) {
                AsgardObeliskTE obelisk = (AsgardObeliskTE) te;
                if (SGCraft.configurator != null && player.getHeldItemMainhand().getItem() == SGCraft.configurator) {
                    ObeliskNetworkHandler.openConfigurator(player, obelisk); // its own configurator screen
                } else if (player.isSneaking()) {
                    AsgardObelisks.link(player, (AsgardObeliskTE) te);
                } else if (onGem(state, side, hitX, hitY, hitZ, type)) {
                    AsgardObelisks.transport(player, (AsgardObeliskTE) te);
                }
            }
        }
        return true;
    }

    // How far round the gem's middle a click counts (blocks)
    private static final double gemReach = 2 / 16.0;

    // Whether a click on the obelisk was on its gem: the top half, the face the gem is on, within reach of the gem
    private static boolean onGem(IBlockState state, EnumFacing side, float hitX, float hitY, float hitZ, Type type) {
        EnumFacing facing = state.getValue(FACING);
        if (!state.getValue(UPPER) || side != facing) {
            return false;
        }
        double across = facing.getAxis() == EnumFacing.Axis.Z ? hitX : hitZ;
        double dx = across - 0.5, dy = hitY - type.gemY;
        return dx * dx + dy * dy <= gemReach * gemReach;
    }
}
