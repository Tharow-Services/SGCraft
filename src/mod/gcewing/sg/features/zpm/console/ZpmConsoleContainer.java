//------------------------------------------------------------------------------------------------
//
//   SG Craft - Stargate controller fuelling gui container
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.features.zpm.console;

import gcewing.sg.BaseContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class ZpmConsoleContainer extends BaseContainer {

    static final int numFuelSlotColumns = 2;
    static final int zpmSlotsX = 120;
    static final int zpmSlotsY = 54;
    static final int playerSlotsX = 48;
    static final int playerSlotsY = 124;

    ZpmConsoleTE te;

    public static ZpmConsoleContainer create(EntityPlayer player, World world, BlockPos pos) {
        ZpmConsoleTE te = ZpmConsoleTE.at(world, pos);
        if (te != null) {
            return new ZpmConsoleContainer(player, te);
        } else {
            return null;
        }
    }

    public ZpmConsoleContainer(EntityPlayer player, ZpmConsoleTE te) {
        super(ZPMConsoleScreen.guiWidth, ZPMConsoleScreen.guiHeight);
        this.te = te;

        addZpmSlots();
        addPlayerSlots(player, playerSlotsX, playerSlotsY);
    }
    
    void addZpmSlots() {
        int b = 0; // First Slot
        int n = ZpmConsoleTE.numSlots;
        for (int i = 0; i < n; i++) {
            int row = i / numFuelSlotColumns;
            int col = i % numFuelSlotColumns;
            int x = zpmSlotsX + col * 18;
            int y = zpmSlotsY + row * 18;

            addSlotToContainer(new ZpmConsoleSlot(te, b + i, x, y));
        }
    }

    @Override // Shift-Click Inventory
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = inventorySlots.get(index);
        ItemStack stack = slot.getStack();
        if (slot != null && slot.getHasStack()) {
            BaseContainer.SlotRange destRange = transferSlotRange(index, stack);
            if (destRange != null) {
                if (index >= destRange.numSlots) {
                    result = stack.copy();
                    if (!mergeItemStackIntoRange(stack, destRange))
                        return ItemStack.EMPTY;
                    if (stack.getCount() == 0)
                        slot.putStack(ItemStack.EMPTY);
                    else
                        slot.onSlotChanged();
                } else {
                    // Back in its slot if the player has no room for it, rather than lost
                    ItemStack zpm = te.decrStackSize(0, 1);
                    if (!zpm.isEmpty() && !player.inventory.addItemStackToInventory(zpm)) {
                        te.setInventorySlotContents(0, zpm);
                    }
                }
            }
        }
        return result;
    }

    @Override
    protected TileEntity tileEntity() {
        return te;
    }


    @Override
    protected SlotRange transferSlotRange(int srcSlotIndex, ItemStack stack) {
        SlotRange range = new SlotRange();
        range.firstSlot = 0;
        range.numSlots = 1;
        return range;
    }
}
