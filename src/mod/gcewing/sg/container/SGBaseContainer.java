//------------------------------------------------------------------------------------------------
//
//   SG Craft - Stargate base gui container
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.container;

import gcewing.sg.BaseContainer;
import gcewing.sg.SGCraft;
import gcewing.sg.tileentity.SGBaseTE;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

public class SGBaseContainer extends BaseContainer {

    static final int numFuelSlotColumns = 2;
//  static final int fuelSlotsX = 174;
//  static final int fuelSlotsY = 84;
//  static final int upgradeSlotsX = 48;
//  static final int upgradeSlotsY = 84;
//  static final int playerSlotsX = 48;
//  static final int playerSlotsY = 124;

    SGBaseTE te;
    // How many of the gate's slots are shown: its camouflage, and a supergate's ZPMs after them
    int gateSlots = 0;
    // Where a supergate's ZPM slots are, beside the camouflage
    public static final int zpmSlotsX = 166, zpmSlotsY = 104;

    // The gate's own slots, and the player's, are shown to its admins: on a gate standing up (where its base can be
    // camouflaged), and on any supergate (for its ZPMs)
    public static boolean showsInventory(EntityPlayer player, SGBaseTE te) {
        return (te.gateOrientation == 1 || te.isSupergate()) && te.allowAdminAccess(player);
    }

    public static SGBaseContainer create(EntityPlayer player, World world, BlockPos pos) {
        SGBaseTE te = SGBaseTE.at(world, pos);
        if (te != null)
            return new SGBaseContainer(player, te);
        else
            return null;
    }

    public SGBaseContainer(EntityPlayer player, SGBaseTE te) {
        super(256, 208);
        this.te = te;
//      addFuelSlots();
//      addUpgradeSlots();
        if (showsInventory(player, te)) {
            addCamouflageSlots();
            if (te.isSupergate()) {
                addSlots(te, SGBaseTE.firstZpmSlot, SGBaseTE.numZpmSlots, zpmSlotsX, zpmSlotsY, 1, ZpmSlot.class);
                gateSlots += SGBaseTE.numZpmSlots;
            }
            addPlayerSlots(player); //(player, playerSlotsX, playerSlotsY);
        }
    }
    
    void addCamouflageSlots() {
        addSlots(te, 0, te.numCamouflageSlots, 48, 104, 1, CamouflageSlot.class);
        gateSlots = te.numCamouflageSlots;
    }

    // Shift-clicking moves a gate slot's item to the player, and the player's ZPMs to a supergate's ZPM slots, anything
    // else to the camouflage
    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        Slot slot = inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getStack();
        ItemStack result = stack.copy();
        int camouflage = te.numCamouflageSlots;
        boolean moved;
        if (index < gateSlots) {
            moved = mergeItemStack(stack, gateSlots, inventorySlots.size(), true);
        } else if (gateSlots > camouflage && stack.getItem() == SGCraft.zpm) {
            moved = mergeItemStack(stack, camouflage, gateSlots, false);
        } else {
            moved = mergeItemStack(stack, 0, camouflage, false);
        }
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.putStack(ItemStack.EMPTY);
        } else {
            slot.onSlotChanged();
        }
        return result;
    }

//  void addFuelSlots() {
//      int b = SGBaseTE.firstFuelSlot;
//      int n = SGBaseTE.numFuelSlots;
//      //System.out.printf("SGBaseContainer: %s fuel slots\n", n);
//      for (int i = 0; i < n; i++) {
//          int row = i / numFuelSlotColumns;
//          int col = i % numFuelSlotColumns;
//          int x = fuelSlotsX + col * 18;
//          int y = fuelSlotsY + row * 18;
//          //System.out.printf("SGBaseContainer: adding fuel slot %s at (%s, %s)\n", i, x, y);
//          addSlotToContainer(new Slot(te, b + i, x, y));
//      }
//  }
    
//  void addUpgradeSlots() {
//      int b = SGBaseTE.firstUpgradeSlot;
//      int n = SGBaseTE.numUpgradeSlots;
//      for (int i = 0; i < n; i++) {
//          int x = upgradeSlotsX + i * 18;
//          int y = upgradeSlotsY;
//          addSlotToContainer(new Slot(te, b + i, x, y));
//      }
//  }

}