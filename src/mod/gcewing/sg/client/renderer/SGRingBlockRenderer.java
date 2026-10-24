//------------------------------------------------------------------------------------------------
//
//   SG Craft - Stargate ring block renderer
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.client.renderer;

import gcewing.sg.BaseBlockUtils;
import gcewing.sg.BaseRenderingManager;
import gcewing.sg.tileentity.SGBaseTE;
import gcewing.sg.SGCraft;
import gcewing.sg.Trans3;
import gcewing.sg.interfaces.ISGBlock;
import gcewing.sg.util.VSUtil;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.*;
import net.minecraft.world.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;

import gcewing.sg.BaseModClient.*;

public class SGRingBlockRenderer implements ICustomRenderer {

    public SGRingBlockRenderer() {}

    public void renderBlock(IBlockAccess world, BlockPos pos, IBlockState state, IRenderTarget target,
        BlockRenderLayer layer, Trans3 t)
    {
        ISGBlock ringBlock = (ISGBlock)state.getBlock();
        if (target.isRenderingBreakEffects() || (layer == BlockRenderLayer.SOLID && !ringBlock.isMerged(world, pos))) {
            BaseRenderingManager.renderBlockUsingModelSpec(SGCraft.mod.client, world, pos, state, target, layer, t);
        } else {
            SGBaseTE te = ringBlock.getBaseTE(world, pos);
            if (te != null) {
                ItemStack stack = te.getCamouflageStack(pos);
                if (stack != null) {
                    Item item = stack.getItem();
                    if (item instanceof ItemBlock) {
                        IBlockState camoState = BaseBlockUtils.getBlockStateFromItemStack(stack);
                        if (camoState.getBlock().canRenderInLayer(camoState,layer)) {
                            BaseRenderingManager.renderAlternateBlock(SGCraft.mod.client, world, pos, camoState, target);
                        }
                    }
                }
            }
        }
    }
    
    public void renderItemStack(ItemStack stack, IRenderTarget target, Trans3 t) {
        BaseRenderingManager.renderItemStackUsingModelSpec(SGCraft.mod.client, stack, target, t);
    }
}
