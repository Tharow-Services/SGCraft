package gcewing.sg.features.zat;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;

// The zat: right-click to fire it where the crosshairs are (see Zat)
public class ZatItem extends Item {

    public ZatItem() {
        setMaxStackSize(1);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!world.isRemote) {
            Zat.pullTrigger(player, hand);
        }
        // On both sides, so the cooldown shows at once
        player.getCooldownTracker().setCooldown(this, Zat.cooldownTicks);
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }
}
