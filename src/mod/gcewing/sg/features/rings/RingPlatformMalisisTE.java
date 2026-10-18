package gcewing.sg.features.rings;

import gcewing.sg.features.rings.client.RingFuelMalisisScreen;
import net.malisis.core.client.gui.MalisisGui;
import net.malisis.core.inventory.IInventoryProvider.IDirectInventoryProvider;
import net.malisis.core.inventory.MalisisInventory;
import net.malisis.core.inventory.MalisisInventoryContainer;
import net.malisis.core.inventory.MalisisSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

// A ring platform with MalisisCore: its fuel slots are a MalisisCore inventory, shown on its MalisisCore screen
// (RingFuelMalisisScreen). Only made, and its class only loaded, with MalisisCore (see RingPlatformMalisis).
public class RingPlatformMalisisTE extends RingPlatformTE implements IDirectInventoryProvider {

    private final MalisisInventory inventory = new MalisisInventory(this, FuelSlot::new, numFuelSlots);

    {
        inventory.setName("tile.sgcraft:ring_platform.name");
    }

    @Override
    protected ItemStack getFuel(int slot) {
        return inventory.getItemStack(slot);
    }

    @Override
    protected void setFuel(int slot, ItemStack stack) {
        inventory.getSlot(slot).setItemStack(stack);
    }

    @Override
    public MalisisInventory getInventory() {
        return inventory;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public MalisisGui getGui(MalisisInventoryContainer container) {
        return new RingFuelMalisisScreen(Minecraft.getMinecraft().player, this, container, inventory);
    }

    // A fuel slot takes only naquadah
    public static class FuelSlot extends MalisisSlot {
        @Override
        public boolean isItemValid(ItemStack itemStack) {
            return RingPlatformTE.isFuel(itemStack);
        }
    }
}
