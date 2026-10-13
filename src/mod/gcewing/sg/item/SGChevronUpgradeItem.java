//------------------------------------------------------------------------------------------------
//
//   SG Craft - Chevron upgrade item
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.item;

import gcewing.sg.interfaces.ISGBlock;
import gcewing.sg.tileentity.SGBaseTE;
import gcewing.sg.util.GateUtil;
import net.minecraft.block.*;
import net.minecraft.item.*;
import net.minecraft.entity.player.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

public class SGChevronUpgradeItem extends Item {

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing side,
                                      float hitX, float hitY, float hitZ) {

        Block block = world.getBlockState(pos).getBlock();
        if (block instanceof ISGBlock) {
            SGBaseTE te = ((ISGBlock)block).getBaseTE(world, pos);
            if (te != null)
                return te.applyChevronUpgrade(player.getHeldItem(hand), player);
        }
        return EnumActionResult.FAIL;
    }

    // A click on a gate's drawn ring that missed its blocks, as most on a supergate do
    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        SGBaseTE te = GateUtil.gateClickedOn(world, player);
        return new ActionResult<>(te != null ? te.applyChevronUpgrade(stack, player) : EnumActionResult.PASS, stack);
    }

}
