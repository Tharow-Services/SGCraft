package gcewing.sg.features.zpm;

import gcewing.sg.SGCraft;
import gcewing.sg.interfaces.ISGEnergySource;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

// A ZPM in one of a supergate base's slots, as a source of energy. Its energy stays on the item (as in a ZPM console,
// in ZPMItem.ENERGY), so it goes with the ZPM when it's taken out; a ZPM never used yet is full.
public class ZpmSlotSource implements ISGEnergySource {

    private final IInventory inventory;
    private final int slot;

    public ZpmSlotSource(IInventory inventory, int slot) {
        this.inventory = inventory;
        this.slot = slot;
    }

    // The energy (ZPM units) a ZPM item holds, or 0 if it isn't one
    public static double energyOf(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() != SGCraft.zpm) {
            return 0;
        }
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null && tag.hasKey(ZPMItem.ENERGY, 99) ? tag.getDouble(ZPMItem.ENERGY) : Integer.MAX_VALUE;
    }

    // Gives a ZPM never used yet its full energy on the item, as a ZPM console does when one goes in, so its tooltip
    // shows what it holds
    public static void prepare(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() != SGCraft.zpm) {
            return;
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        if (!tag.hasKey(ZPMItem.ENERGY, 99)) {
            tag.setDouble(ZPMItem.ENERGY, Integer.MAX_VALUE);
        }
        tag.setBoolean(ZPMItem.LOADED, false);
    }

    public boolean isEmpty() {
        prepare(inventory.getStackInSlot(slot));
        return energyOf(inventory.getStackInSlot(slot)) <= 0;
    }

    @Override
    public double availableEnergy() {
        return energyOf(inventory.getStackInSlot(slot)) / SGCraft.ZPMEnergyPerSGEnergyUnit;
    }

    @Override
    public double totalAvailableEnergy() {
        return energyOf(inventory.getStackInSlot(slot));
    }

    @Override
    public double drawEnergyDouble(double request) {
        ItemStack stack = inventory.getStackInSlot(slot);
        double stored = energyOf(stack);
        double supply = Math.min(request, stored / SGCraft.ZPMEnergyPerSGEnergyUnit);
        if (supply > 0) {
            NBTTagCompound tag = stack.getTagCompound();
            if (tag == null) {
                tag = new NBTTagCompound();
                stack.setTagCompound(tag);
            }
            tag.setDouble(ZPMItem.ENERGY, Math.max(0, stored - supply * SGCraft.ZPMEnergyPerSGEnergyUnit));
            tag.setBoolean(ZPMItem.LOADED, false); // so its tooltip shows what's left
            inventory.markDirty();
        }
        return supply;
    }
}
