package gcewing.sg.features.cityshield;

import gcewing.sg.SGCraft;
import gcewing.sg.features.zpm.ZpmSlotSource;
import net.malisis.core.inventory.MalisisSlot;
import net.minecraft.item.ItemStack;

// A City Shield Generator slot for one ZPM. A ZPM put in gets its energy on the item, so its tooltip shows its power.
public class ZpmMalisisSlot extends MalisisSlot {

    @Override
    public boolean isItemValid(ItemStack itemStack) {
        return itemStack.getItem() == SGCraft.zpm;
    }

    @Override
    public int getSlotStackLimit() {
        return 1;
    }

    @Override
    public void setItemStack(ItemStack itemStack) {
        ZpmSlotSource.prepare(itemStack);
        super.setItemStack(itemStack);
    }
}
