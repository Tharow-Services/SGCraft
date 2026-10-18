//------------------------------------------------------------------------------------------------
//
//   SG Craft - Stargate base block
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.block;

import static gcewing.sg.BaseOrientation.Orient4WaysByState.FACING;

import gcewing.sg.BaseBlockUtils;
import gcewing.sg.BaseConfiguration;
import gcewing.sg.BaseOrientation;
import gcewing.sg.tileentity.SGBaseTE;
import gcewing.sg.tileentity.SGRingTE;
import gcewing.sg.SGCraft;
import gcewing.sg.util.GateShape;
import gcewing.sg.util.GateType;
import gcewing.sg.util.SGState;
import gcewing.sg.Trans3;
import gcewing.sg.Vector3;
import gcewing.sg.client.gui.SGGui;
import net.minecraft.block.*;
import net.minecraft.block.material.*;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.*;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.*;
import net.minecraft.init.*;
import net.minecraft.item.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;

import gcewing.sg.BaseMod.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SGBaseBlock extends SGBlock<SGBaseTE> {

    public static boolean debugMerge = false;
    static int explosionRadius = 10;
    static boolean fieryExplosion = true;
    static boolean smokyExplosion = true;
    static boolean debug = false;

    protected static String[] textures = {"stargateblock", "stargatering", "stargatebase_front"};
    protected static ModelSpec model = new ModelSpec("block/sg_base_block.smeg", textures);

    // The type of gate this base builds, or null for the type the configuration gives; and, for a
    // base of its own type, its textures
    protected final GateType gateType;
    protected final String[] typedTextures;
    protected final ModelSpec typedModel;
    
    public static void configure(BaseConfiguration config) {
        explosionRadius = config.getInteger("stargate", "explosionRadius", explosionRadius);
        fieryExplosion = config.getBoolean("stargate", "explosionFlame", fieryExplosion);
        smokyExplosion = config.getBoolean("stargate", "explosionSmoke", smokyExplosion);
    }
    
    public SGBaseBlock() {
        this(null, null);
    }

    // A base that builds gates of the given type, its front showing the given texture
    public SGBaseBlock(GateType gateType, String frontTexture) {
        super(Material.ROCK, SGBaseTE.class);
        setHardness(1.5F);
        setCreativeTab(CreativeTabs.MISC);
        this.gateType = gateType;
        typedTextures = frontTexture == null ? null : new String[] {textures[0], textures[1], frontTexture};
        typedModel = typedTextures == null ? null : new ModelSpec("block/sg_base_block.smeg", typedTextures);
    }
    
    @Override
    public boolean canRenderInLayer(IBlockState state, BlockRenderLayer layer) {
        return true; // So that translucent camouflage blocks render correctly
    }

    @Override
    public IOrientationHandler getOrientationHandler() {
        return BaseOrientation.orient4WaysByState;
    }
    
    @Override
    public String[] getTextureNames() {
        return typedTextures != null ? typedTextures : textures;
    }
    
    @Override
    public ModelSpec getModelSpec(IBlockState state) {
        return typedModel != null ? typedModel : model;
    }
    
    // The shape of a gate built on this base when nothing more is known, such as before it merges
    public GateShape getShape() {
        return GateShape.STANDARD;
    }

    // The shapes a gate on this base can have, in the order to try them when merging
    public List<GateShape> getPossibleShapes() {
        return Collections.singletonList(getShape());
    }

    // The shape of the gate built on this base in the world
    public GateShape getShape(IBlockAccess world, BlockPos pos) {
        SGBaseTE te = getTileEntity(world, pos);
        return te != null ? te.getShape() : getShape();
    }

    // The kind of ring block this base merges with
    public SGRingBlock getRingBlock() {
        return SGCraft.sgRingBlock;
    }

    @Override
    public SGBaseTE getBaseTE(IBlockAccess world, BlockPos pos) {
        return getTileEntity(world, pos);
    }

    @Override
    protected String getRendererClassName() {
        return "gcewing.sg.client.renderer.SGRingBlockRenderer";
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    // Lit while the gate's event horizon is up (see SGBaseTE.getEventHorizonLight)
    @Override
    public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        return te instanceof SGBaseTE ? ((SGBaseTE) te).getEventHorizonLight() : super.getLightValue(state, world, pos);
    }
    
    @Override
    public boolean isSideSolid(IBlockState base_state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return true;
    }

    @Override
    public boolean canHarvestBlock(IBlockAccess world, BlockPos pos, EntityPlayer player) {
        return SGCraft.canHarvestSGBaseBlock;
    }

    @Override
    public boolean isMerged(IBlockAccess world, BlockPos pos) {
        SGBaseTE te = getTileEntity(world, pos);
        return te != null && te.isMerged;
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase player, ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, player, stack);
        SGBaseTE te = getTileEntity(world, pos);
        if (te != null) {
            if (te instanceof SGBaseTE) {
                if (debug) {
                    System.out.println("Stargate base block placed " + player.getName() + " on world: " + world.getWorldInfo().getWorldName() + " at: " + pos);
                }
                te.isGenerated = false;
            }
        }
    }
    
    @Override
    public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        if (debug) {
            System.out.println("STARGATE Base block added on world: " + world.getWorldInfo().getWorldName() + " at: " + pos);
        }

        if (gateType != null) {
            SGBaseTE te = getTileEntity(world, pos);
            if (te != null && !te.isMerged) {
                te.setGateType(gateType);
            }
        }
        checkForMerge(world, pos);
        SGBaseTE te = getTileEntity(world, pos);
        if (te != null) {
            if (te instanceof SGBaseTE) {
                if (state.getValue(FACING).toString().equalsIgnoreCase("north")) {
                    te.facingDirectionOfBase = 0;
                }
                if (state.getValue(FACING).toString().equalsIgnoreCase("west")) {
                    te.facingDirectionOfBase = 1;
                }
                if (state.getValue(FACING).toString().equalsIgnoreCase("south")) {
                    te.facingDirectionOfBase = 2;
                }
                if (state.getValue(FACING).toString().equalsIgnoreCase("east")) {
                    te.facingDirectionOfBase = 3;
                }
            }
        }
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing side, float cx, float cy, float cz) {
        String Side = world.isRemote ? "Client" : "Server";
        SGBaseTE te = getTileEntity(world, pos);
        if (te != null) {
            if (debugMerge) {
                System.out.printf("SGBaseBlock.onBlockActivated: %s: isMerged = %s\n", Side, te.isMerged);
            }

            if (te.isMerged && !world.isRemote)  {
                if (player.getHeldItemMainhand().getItem() == SGCraft.pdd || player.getHeldItemMainhand().getItem() == SGCraft.edd || player.getHeldItemMainhand().getItem() == SGCraft.configurator || player.getHeldItemMainhand().getItem() == SGCraft.gdo) {
                    return false; // Return false here so the itemUse method fires
                }
                SGCraft.mod.openGui(player, SGGui.SGBase, world, pos);
                return true;
            }

            if (world.isRemote) {
                if (player.getHeldItemMainhand().getItem() == SGCraft.pdd || player.getHeldItemMainhand().getItem() == SGCraft.edd || player.getHeldItemMainhand().getItem() == SGCraft.configurator || player.getHeldItemMainhand().getItem() == SGCraft.gdo) {
                    return false; // Return false here so the itemUse method fires
                }
                return true; //Client needs to be told too.
            }
        }
        return false;
    }
    
    @Override
    public boolean getWeakChanges(IBlockAccess world, BlockPos pos) {
        return true;
    }
    
    @Override    
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos from) {
        neighbourChanged(world, pos);
    }

    protected void neighbourChanged(IBlockAccess world, BlockPos pos) {
        SGBaseTE te = getTileEntity(world, pos);
        if (te != null)
            te.onNeighborBlockChange();
    }

    // Merges the base with the ring round it, if it is complete: looking for a supergate's ring
    // first, then a stargate's, each standing up or lying flat. A base in the other kind's ring
    // (a stargate base put back in a supergate, say) becomes that kind's base, facing the same way
    // and building the same type of gate, and merges as that.
    public void checkForMerge(World world, BlockPos pos) {
        if (isMerged(world, pos)) {
            return;
        }
        SGBaseBlock superBase = SGCraft.supergateBaseBlock, normalBase = SGCraft.sgBaseBlock;
        Trans3 t = localToGlobalTransformation(world, pos);
        SGBaseBlock kind = superBase != null && superBase.ringAround(world, t) ? superBase
                : normalBase.ringAround(world, t) ? normalBase : null;
        boolean supergate = this instanceof SupergateBaseBlock;
        if (kind != null && (kind == superBase) != supergate) {
            SGBaseTE old = getTileEntity(world, pos);
            GateType type = gateType != null ? gateType : old != null ? old.getGateType() : null;
            IBlockState state = world.getBlockState(pos);
            // The new base merges as it is added (see onBlockAdded)
            world.setBlockState(pos, kind.getDefaultState().withProperty(FACING, state.getValue(FACING)));
            SGBaseTE te = SGBaseTE.at(world, pos);
            if (te != null && type != null) {
                te.setGateType(type);
            }
            return;
        }
        checkForVerticalMerge(world, pos);
        checkForHorizontalMerge(world, pos);
    }

    // Whether a complete, unmerged ring of this base's kind is round a base placed so
    boolean ringAround(World world, Trans3 t) {
        for (GateShape s : getPossibleShapes()) {
            if (ringMatches(world, t, s, false) || ringMatches(world, t, s, true)) {
                return true;
            }
        }
        return false;
    }

    public void checkForVerticalMerge(World world, BlockPos pos) {
        if (!isMerged(world, pos)) {
            Trans3 t = localToGlobalTransformation(world, pos);
            GateShape shape = null;
            for (GateShape s : getPossibleShapes()) {
                if (ringMatches(world, t, s, false)) {
                    shape = s;
                    break;
                }
            }
            if (shape == null) {
                return;
            }

            SGBaseTE te = getTileEntity(world, pos);
            te.setShape(shape);
            // Its orientation first: merging fits its iris across it as it stands
            te.gateOrientation = 1;
            te.setMerged(true);
            BaseBlockUtils.markBlockForUpdate(world, pos);
            for (int i = -shape.halfWidth; i <= shape.halfWidth; i++)
                for (int j = 0; j < shape.height; j++)
                    if (!(i == 0 && j == 0) && shape.cellAt(i, j) != GateShape.EMPTY) {
                        BlockPos rp = t.p(i, j, 0).blockPos();
                        Block block = world.getBlockState(rp).getBlock();
                        if (block instanceof SGRingBlock) {
                            ((SGRingBlock) block).mergeWith(world, rp, pos);
                        }
                    }
            te.checkForLink();
        }
    }

    public void checkForHorizontalMerge(World world, BlockPos pos) {
        boolean debugThisMerge = true;
        if (!isMerged(world, pos)) {
            Trans3 t = localToGlobalTransformation(world, pos);
            GateShape shape = null;
            for (GateShape s : getPossibleShapes()) {
                if (ringMatches(world, t, s, true)) {
                    shape = s;
                    break;
                }
            }
            if (shape == null) {
                return;
            }

            SGBaseTE te = getTileEntity(world, pos);
            te.setShape(shape);
            // Its orientation first: merging fits its iris across it as it stands. Lying flat, it
            // faces up, unless it was turned face down (by the configurator) before
            te.gateOrientation = te.gateOrientation == 3 ? 3 : 2;
            te.setMerged(true);
            BaseBlockUtils.markBlockForUpdate(world, pos);

            for (int x = -shape.halfWidth; x <= shape.halfWidth; x++) {
                for (int z = -(shape.height - 1); z <= 0; z++) {
                    if (!(x == 0 && z == 0) && shape.cellAt(x, -z) != GateShape.EMPTY) {
                        BlockPos rp = t.p(x, 0, z).blockPos();
                        Block block = world.getBlockState(rp).getBlock();
                        if (block instanceof SGRingBlock) {
                            ((SGRingBlock) block).mergeWith(world, rp, pos);
                        }
                    }
                }
            }
            te.checkForLink();
            te.markForUpdate();
        }
    }
    
    // Whether the unmerged ring blocks around the base match the shape's pattern, standing up
    // (vertical) or lying flat out from the base (horizontal)
    protected boolean ringMatches(World world, Trans3 t, GateShape shape, boolean horizontal) {
        for (int x = -shape.halfWidth; x <= shape.halfWidth; x++) {
            for (int y = 0; y < shape.height; y++) {
                int pat = shape.cellAt(x, y);
                if (pat != GateShape.EMPTY && !(x == 0 && y == 0)) {
                    BlockPos rp = (horizontal ? t.p(x, 0, -y) : t.p(x, y, 0)).blockPos();
                    if (getRingBlockType(world, rp) != pat) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    protected int getRingBlockType(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        if (block == Blocks.AIR)
            return GateShape.EMPTY;
        SGRingBlock ringBlock = getRingBlock();
        if (block == ringBlock) {
            if (!ringBlock.isMerged(world, pos)) {
                switch (state.getValue(SGRingBlock.VARIANT)) {
                    case 0: return GateShape.RING;
                    case 1: return GateShape.CHEVRON;
                }
            }
        }
        return -1;
    }

    @Override
    public boolean canDropFromExplosion(Explosion explosionIn) {
        return SGCraft.canHarvestSGBaseBlock;
    }
    
    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        unmerge(world, pos);
        if (SGCraft.canHarvestSGBaseBlock) {
            dropUpgrades(world, pos);
        }
        super.breakBlock(world, pos, state);
    }
    
    // In creative, breaking a gate's base takes the whole gate with it: every ring block merged into it goes too
    @Override
    public void onBlockHarvested(World world, BlockPos pos, IBlockState state, EntityPlayer player) {
        if (!world.isRemote && player.capabilities.isCreativeMode) {
            SGBaseTE te = getTileEntity(world, pos);
            if (te != null && te.isMerged) {
                List<BlockPos> ring = new ArrayList<>();
                GateShape shape = te.getShape();
                Trans3 t = new Trans3(Vector3.blockCenter(pos)).turn(te.facingDirectionOfBase);
                for (int x = -shape.halfWidth; x <= shape.halfWidth; x++) {
                    for (int y = 0; y < shape.height; y++) {
                        if (shape.cellAt(x, y) != GateShape.EMPTY && !(x == 0 && y == 0)) {
                            addRingBlock(world, pos, t.p(x, y, 0).blockPos(), ring);
                            addRingBlock(world, pos, t.p(x, 0, -y).blockPos(), ring);
                        }
                    }
                }
                for (BlockPos ringPos : ring) {
                    world.setBlockToAir(ringPos);
                }
            }
        }
        super.onBlockHarvested(world, pos, state, player);
    }

    // A ring block merged into the gate whose base is at pos
    private static void addRingBlock(World world, BlockPos pos, BlockPos ringPos, List<BlockPos> ring) {
        TileEntity te = world.getTileEntity(ringPos);
        if (te instanceof SGRingTE && ((SGRingTE) te).isMerged && pos.equals(((SGRingTE) te).basePos) && !ring.contains(ringPos)) {
            ring.add(ringPos);
        }
    }

    void dropUpgrades(World world, BlockPos pos) {
        SGBaseTE te = getTileEntity(world, pos);
        if (te != null) {
            if (te.hasChevronUpgrade)
                spawnAsEntity(world, pos, new ItemStack(SGCraft.sgChevronUpgrade));
            if (te.hasIrisUpgrade)
                spawnAsEntity(world, pos, new ItemStack(te.hasShield ? SGCraft.sgShieldUpgrade : SGCraft.sgIrisUpgrade));
        }
    }
    
    public void unmerge(World world, BlockPos pos) {
        SGBaseTE te = getTileEntity(world, pos);
        boolean goBang = false;
        Vector3 bang = null;
        if (te != null /*&& te.isMerged*/) {
            if (te.isMerged && te.state == SGState.Connected) {
                te.state = SGState.Idle;
                goBang = true;
                // In the middle of its ring, however it stands (from the way its base faced, which
                // may already be gone, as unmergeRing does)
                bang = new Trans3(Vector3.blockCenter(pos)).turn(te.facingDirectionOfBase).p(te.ringCentre());
            }
            te.disconnect();
            // Its state was set to idle by hand above, so going idle didn't let its chunks go: they stayed loaded for good
            te.updateChunkLoadingStatus();
            te.unlinkFromController();
            te.setMerged(false);
            BaseBlockUtils.markBlockForUpdate(world, pos);
            unmergeRing(world, pos);
        }
        if (goBang && explosionRadius > 0)
            explode(world, bang, explosionRadius);
    }
    
    void explode(World world, Vector3 p, double s) {
        world.newExplosion(null, p.x, p.y, p.z, (float)s, fieryExplosion, smokyExplosion);
    }
    
    // Visit just the ring's own cells, standing up and lying flat, rather than every block around
    // the base. This can run while the base is being broken, when its block may already be gone,
    // so the gate's facing comes from the tile entity (numbered as Orient4WaysByState turns it).
    void unmergeRing(World world, BlockPos pos) {
        SGBaseTE te = getTileEntity(world, pos);
        if (te == null) {
            return;
        }
        GateShape shape = te.getShape();
        Trans3 t = new Trans3(Vector3.blockCenter(pos)).turn(te.facingDirectionOfBase);
        for (int x = -shape.halfWidth; x <= shape.halfWidth; x++)
            for (int y = 0; y < shape.height; y++)
                if (shape.cellAt(x, y) != GateShape.EMPTY && !(x == 0 && y == 0)) {
                    unmergeRingBlock(world, pos, t.p(x, y, 0).blockPos());
                    unmergeRingBlock(world, pos, t.p(x, 0, -y).blockPos());
                }
    }
    
    void unmergeRingBlock(World world, BlockPos pos, BlockPos ringPos) {
        Block block = world.getBlockState(ringPos).getBlock();
        if (debugMerge)
            System.out.printf("SGBaseBlock.unmergeRingBlock: found %s at %s\n", block, ringPos);
        if (block instanceof SGRingBlock) {
            ((SGRingBlock)block).unmergeFrom(world, ringPos, pos);
        }
    }
    
    @Override
    public boolean canProvidePower(IBlockState state) {
        return true;
    }
    
    @Override
    public int getStrongPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return getWeakPower(state, world, pos, side);
    }
    
    @Override
    public int getWeakPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        SGBaseTE te = getTileEntity(world, pos);
        if (te.allowRedstoneOutput) {
            return (te != null && te.state != SGState.Idle) ? 15 : 0;
        } else {
            return 0;
        }
    }
    
    protected static Trans3 itemTrans = Trans3.sideTurn(0, 2);
    
    @Override
    public Trans3 itemTransformation() {
        return itemTrans;
    }

    @Override
    public BlockFaceShape getBlockFaceShape(IBlockAccess worldIn, IBlockState state, BlockPos pos, EnumFacing face) {
        return BlockFaceShape.UNDEFINED;
    }
    
}
