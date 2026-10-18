//------------------------------------------------------------------------------------------------
//
//   SG Craft - Stargate controller fuelling gui container
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.features.ic2.zpm.modulehub;

import gcewing.sg.BaseContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class ZpmHubContainer extends BaseContainer {

    static final int numFuelSlotColumns = 3;
    static final int zpmSlotsX = 120;
    static final int zpmSlotsY = 54;
    static final int playerSlotsX = 48;
    static final int playerSlotsY = 124;

    ZpmHubTE te;

    public static ZpmHubContainer create(EntityPlayer player, World world, BlockPos pos) {
        ZpmHubTE te = ZpmHubTE.at(world, pos);
        if (te != null)
            return new ZpmHubContainer(player, te);
        else
            return null;
    }

    public ZpmHubContainer(EntityPlayer player, ZpmHubTE te) {
        super(ZpmHubScreen.guiWidth, ZpmHubScreen.guiHeight);
        this.te = te;

        addZpmSlots();
        addPlayerSlots(player, playerSlotsX, playerSlotsY);
    }

    void addZpmSlots() {
        int b = ZpmHubTE.firstZpmSlot;
        int n = ZpmHubTE.numZpmSlots;
        for (int i = 0; i < n; i++) {
            int row = i / numFuelSlotColumns;
            int col = i % numFuelSlotColumns;
            int x = zpmSlotsX + col * 19;
            int y = zpmSlotsY + row * 18;
            addSlotToContainer(new ZpmHubSlot(te, b + i, x, y));
        }
    }

    // Shift-click: a ZPM in the hub to the player's inventory, one in the player's inventory to the hub's first free slot.
    // The hub's slots are its first three; only the first used to count as the hub's, so a shift-click on the second or
    // third moved its ZPM into the first, which took the energy the ZPM went in with all over again.
    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        Slot slot = inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) {
            return ItemStack.EMPTY;
        }
        if (index < ZpmHubTE.numZpmSlots) {
            // Back in its slot if the player has no room for it, rather than lost
            ItemStack zpm = te.decrStackSize(index, 1);
            if (!zpm.isEmpty() && !player.inventory.addItemStackToInventory(zpm)) {
                te.setInventorySlotContents(index, zpm);
            }
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getStack();
        ItemStack result = stack.copy();
        if (!mergeItemStackIntoRange(stack, transferSlotRange(index, stack))) {
            return ItemStack.EMPTY;
        }
        if (stack.getCount() == 0) {
            slot.putStack(ItemStack.EMPTY);
        } else {
            slot.onSlotChanged();
        }
        return result;
    }

    @Override
    protected SlotRange transferSlotRange(int srcSlotIndex, ItemStack stack) {
        SlotRange range = new SlotRange();
        range.firstSlot = 0;
        range.numSlots = ZpmHubTE.numZpmSlots;

        return range;
    }

    @Override
    protected TileEntity tileEntity() {
        return te;
    }
}
