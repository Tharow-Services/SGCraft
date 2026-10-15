package gcewing.sg.features.pdd;

import gcewing.sg.SGCraft;
import gcewing.sg.features.pdd.network.PddNetworkHandler;
import gcewing.sg.network.GuiNetworkHandler;
import gcewing.sg.tileentity.SGBaseTE;
import gcewing.sg.util.GateUtil;
import gcewing.sg.util.SGState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

import static gcewing.sg.tileentity.SGBaseTE.sendErrorMsg;

public class PddItem extends Item {

    public PddItem() {}

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack stack, @Nullable World player, List<String> tooltip, ITooltipFlag advanced) {
        super.addInformation(stack, player, tooltip, advanced);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World worldIn, EntityPlayer player, EnumHand handIn) {
        if (!worldIn.isRemote && player.getHeldItemMainhand().getItem() == SGCraft.pdd) {
            final ItemStack stack = player.getHeldItemMainhand();

            NBTTagCompound compound = stack.getTagCompound();
            if (compound == null) {
                compound = new NBTTagCompound();
            }

            if (!compound.hasKey(AddressData.ADDRESSES)) {
                List<AddressData> genericAddressList = AddressNameRegistry.getDefaultPDDEntries();
                AddressData.writeAddresses(compound, genericAddressList);
                stack.setTagCompound(compound);
                player.inventoryContainer.detectAndSendChanges();
            }

            TileEntity localGateTE = GateUtil.locateGateFor(worldIn, player, SGCraft.toolSearchRange);

            if (localGateTE instanceof SGBaseTE) {
                SGBaseTE localGate = (SGBaseTE) localGateTE;

                boolean canEditLocal = localGate.allowGateAccess(player);
                boolean canEditRemote = false;
                if (localGate.isConnected() && localGate.state == SGState.Connected) {
                    SGBaseTE remoteGate = localGate.getConnectedStargateTE();
                    if (remoteGate != null) {
                        canEditRemote = remoteGate.allowGateAccess(player);
                    }
                }
                // This will return false always unless it detects a permissions plugin AND has sgcraft.admin.
                boolean isPermissionsAdmin = SGCraft.hasPermissionSystem() && SGCraft.hasPermission(player, "sgcraft.admin"); // Fallback for a full permissions system override to the Access System

                if (SGCraft.hasPermission(player, "sgcraft.gui.pdd")) {
                    if (player.isSneaking()) {
                        if (localGate.displayGateAddress) {
                            final List<AddressData> addresses = AddressData.getAddresses(compound);
                            String localGateAddress = localGate.homeAddress.toUpperCase().replace("-", "");
                            if (addresses.stream().noneMatch(data -> data.getAddress().replaceAll("-", "").equalsIgnoreCase(localGateAddress))) {
                                PddNetworkHandler.addPddEntryFromServer(player, localGateAddress, addresses.size() + 10);
                            } else {
                                AddressData currentAddressData = addresses.stream().filter(data -> data.getAddress().replaceAll("-", "").equalsIgnoreCase(localGate.homeAddress.toUpperCase())).findFirst().get();
                                PddNetworkHandler.editPddEntryFromServer(player, localGateAddress, currentAddressData.getName(), currentAddressData.getIndex(), currentAddressData.getUnid(), currentAddressData.isAutoClose());
                            }
                        } else {
                            sendErrorMsg(player, "gateAddressHidden");
                        }
                    } else {
                        if (localGate.allowGateAccess(player) || isPermissionsAdmin){
                            GuiNetworkHandler.openGuiAtClient(localGate, player, 3, isPermissionsAdmin, canEditLocal, canEditRemote);
                        } else {
                            if (!SGCraft.hasPermission(player, "sgcraft.gui.pdd"))
                                sendErrorMsg(player, "pddPermission");
                            if (!localGate.allowGateAccess(player))
                                sendErrorMsg(player, "insufficientPlayerAccessPermission");
                        }
                    }
                } else {
                    sendErrorMsg(player, "pddPermission");
                }

                return new ActionResult<>(EnumActionResult.PASS, player.getHeldItem(handIn));  //Both Server & Client expect a returned value.
            } else {
                // Open GUI so that the player can edit the PDD without being near a Stargate.
                GuiNetworkHandler.openGuiAtClient(null, player, 3, false, false, false);
            }
        }

        return new ActionResult<>(EnumActionResult.PASS, player.getHeldItem(handIn));  //Both Server & Client expect a returned value.
    }
}
