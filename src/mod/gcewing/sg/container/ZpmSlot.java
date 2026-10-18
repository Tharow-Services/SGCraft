package gcewing.sg.container;

import gcewing.sg.SGCraft;
import gcewing.sg.features.zpm.ZpmSlotSource;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

// A slot in a supergate's base for one of the ZPMs it runs on
public class ZpmSlot extends Slot {

    public ZpmSlot(IInventory inventory, int index, int x, int y) {
        super(inventory, index, x, y);
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        return stack.getItem() == SGCraft.zpm;
    }

    // A ZPM put in gets its energy on the item straight away, so hovering over it shows its power
    @Override
    public void putStack(ItemStack stack) {
        ZpmSlotSource.prepare(stack);
        super.putStack(stack);
    }

    @Override
    public int getSlotStackLimit() {
        return 1;
    }
}
