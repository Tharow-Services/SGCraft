package gcewing.sg.features.rings;

import gcewing.sg.BaseContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

// A ring platform's fuel slots and the player's inventory, for its vanilla screen (RingFuelScreen), laid out as the DHD's
// fuel screen is, whose background it uses
public class RingFuelContainer extends BaseContainer {

    public static final int guiWidth = 256, guiHeight = 208;
    static final int fuelSlotColumns = 2, fuelSlotsX = 174, fuelSlotsY = 84, playerSlotsX = 48, playerSlotsY = 124;

    public final RingPlatformTE te;

    public static RingFuelContainer create(EntityPlayer player, World world, BlockPos pos) {
        RingPlatformTE te = RingTransports.platformAt(world, pos);
        return te != null ? new RingFuelContainer(player, te) : null;
    }

    public RingFuelContainer(EntityPlayer player, RingPlatformTE te) {
        super(guiWidth, guiHeight);
        this.te = te;
        for (int i = 0; i < RingPlatformTE.numFuelSlots; i++) {
            int x = fuelSlotsX + (i % fuelSlotColumns) * 18, y = fuelSlotsY + (i / fuelSlotColumns) * 18;
            addSlotToContainer(new FuelSlot(te, i, x, y));
        }
        addPlayerSlots(player, playerSlotsX, playerSlotsY);
    }

    // Shift-clicking naquadah from the player's inventory puts it in the fuel slots
    @Override
    protected SlotRange transferSlotRange(int srcSlotIndex, ItemStack stack) {
        SlotRange range = new SlotRange();
        range.firstSlot = 0;
        range.numSlots = RingPlatformTE.numFuelSlots;
        return range;
    }

    @Override
    protected net.minecraft.tileentity.TileEntity tileEntity() {
        return te;
    }

    static class FuelSlot extends Slot {
        FuelSlot(IInventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return RingPlatformTE.isFuel(stack);
        }
    }
}
