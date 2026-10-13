//------------------------------------------------------------------------------------------------
//
//   SG Craft - Stargate block
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.block;

import gcewing.sg.BaseBlock;
import gcewing.sg.tileentity.SGBaseTE;
import gcewing.sg.interfaces.ISGBlock;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

public abstract class SGBlock<TE extends TileEntity> extends BaseBlock<TE> implements ISGBlock {

    public SGBlock(Material material, Class<TE> teClass) {
        super(material, teClass);
    }

    @Override    
    public boolean removedByPlayer(IBlockState state, World world, BlockPos pos, EntityPlayer player, boolean willHarvest) {
        SGBaseTE te = getBaseTE(world, pos);
        if (te != null) {
            if (!player.capabilities.isCreativeMode) {
                if (te.isConnected()) {
                    if (world.isRemote)
                        SGBaseTE.sendErrorMsg(player, "disconnectFirst");
                    return false;
                }
                if (te.isGenerated && !te.canPlayerBreakGeneratedGate) {
                    return false;
                }
                if (!te.isGenerated && !te.canPlayerBreakPlayerGate) {
                    return false;
                }
            }

            if (player.capabilities.isCreativeMode && te.isConnected()) {
                if (world.isRemote)
                    SGBaseTE.sendErrorMsg(player, "disconnectFirst");
                return false;
            }
        }
        return super.removedByPlayer(state, world, pos, player, willHarvest);
    }

    // Middle-clicking any block of a finished gate gives the block that builds a gate like it
    // (see GateSpawnerBlock), if there is one; otherwise, as usual, the block itself
    @Override
    public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world, BlockPos pos, EntityPlayer player) {
        SGBaseTE te = getBaseTE(world, pos);
        if (te != null && te.isMerged) {
            Block base = world.getBlockState(te.getPos()).getBlock();
            if (base instanceof SGBaseBlock) {
                GateSpawnerBlock spawner = GateSpawnerBlock.forGate((SGBaseBlock) base, te.getGateType());
                if (spawner != null) {
                    return new ItemStack(spawner);
                }
            }
        }
        return super.getPickBlock(state, target, world, pos, player);
    }
}
