package gcewing.sg.features.edd;

import gcewing.sg.SGCraft;
import gcewing.sg.network.GuiNetworkHandler;
import gcewing.sg.tileentity.SGBaseTE;
import gcewing.sg.util.GateUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;

import static gcewing.sg.tileentity.SGBaseTE.sendErrorMsg;

// The Explorer Dialer Device: dials like the PDD, but lists the gates the server has generated, rather than
// addresses the player entered, and shows which of them the player has been to
public class EddItem extends Item {

    public EddItem() {}

    @Override
    public ActionResult<ItemStack> onItemRightClick(World worldIn, EntityPlayer player, EnumHand handIn) {
        if (!worldIn.isRemote && player.getHeldItemMainhand().getItem() == SGCraft.edd) {
            if (!SGCraft.hasPermission(player, "sgcraft.gui.edd")) {
                sendErrorMsg(player, "eddPermission");
                return new ActionResult<>(EnumActionResult.PASS, player.getHeldItem(handIn));
            }

            TileEntity localGateTE = GateUtil.locateGateFor(worldIn, player, SGCraft.toolSearchRange);

            if (localGateTE instanceof SGBaseTE) {
                SGBaseTE localGate = (SGBaseTE) localGateTE;
                // Standing at a generated gate counts as having found it
                if (localGate.isMerged) {
                    ExplorerData.markVisited(player, localGate.homeAddress);
                }

                // This will return false always unless it detects a permissions plugin AND has sgcraft.admin.
                boolean isPermissionsAdmin = SGCraft.hasPermissionSystem() && SGCraft.hasPermission(player, "sgcraft.admin");

                if (localGate.allowGateAccess(player) || isPermissionsAdmin) {
                    GuiNetworkHandler.openGuiAtClient(localGate, player, 4, isPermissionsAdmin, true, false);
                } else {
                    sendErrorMsg(player, "insufficientPlayerAccessPermission");
                }
            } else {
                // Open the list without a gate to dial, so the player can look through it and name entries
                GuiNetworkHandler.openGuiAtClient(null, player, 4, false, false, false);
            }
        }

        return new ActionResult<>(EnumActionResult.PASS, player.getHeldItem(handIn));  //Both Server & Client expect a returned value.
    }
}
