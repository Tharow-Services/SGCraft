package gcewing.sg.item;

import gcewing.sg.interfaces.ISGBlock;
import gcewing.sg.tileentity.SGBaseTE;
import gcewing.sg.util.GateUtil;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class SGPegasusUpgradeItem extends Item {
    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World worldIn, BlockPos pos, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        Block block = worldIn.getBlockState(pos).getBlock();

        if(!(block instanceof ISGBlock)) { //Case: Block is not an SGBlock
            return EnumActionResult.FAIL;
        }

        SGBaseTE SGBaseTE = ((ISGBlock) block).getBaseTE(worldIn,pos);

        if(SGBaseTE == null) { //Case: Gate has no base block
            return EnumActionResult.FAIL;
        }

        return SGBaseTE.applyPegasusUpgrade(player.getHeldItem(hand),player);
    }

    // A click on a gate's drawn ring that missed its blocks, as most on a supergate do
    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        SGBaseTE te = GateUtil.gateClickedOn(world, player);
        return new ActionResult<>(te != null ? te.applyPegasusUpgrade(stack, player) : EnumActionResult.PASS, stack);
    }
}
