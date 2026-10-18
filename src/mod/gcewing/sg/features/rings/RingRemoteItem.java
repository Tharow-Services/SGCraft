package gcewing.sg.features.rings;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;

// A Goa'uld ring remote, worn on the hand: using it starts the ring platform its holder stands in, or else the nearest
// within reach
public class RingRemoteItem extends Item {

    public RingRemoteItem() {
        setMaxStackSize(1);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!world.isRemote) {
            RingTransports.activate(player, RingTransports.platformNear(world, player.getPositionVector(), player));
        }
        player.getCooldownTracker().setCooldown(this, 20);
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }
}
