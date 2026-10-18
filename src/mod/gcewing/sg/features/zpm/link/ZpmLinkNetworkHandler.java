package gcewing.sg.features.zpm.link;

import static gcewing.sg.tileentity.SGBaseTE.sendErrorMsg;

import gcewing.sg.BaseBlockUtils;
import gcewing.sg.BaseDataChannel;
import gcewing.sg.SGCraft;
import gcewing.sg.features.obelisk.AsgardObelisks;
import gcewing.sg.features.zpm.link.client.ZpmLinkScreen;
import gcewing.sg.network.SGChannel;
import gcewing.sg.tileentity.SGBaseTE;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

// A ZPM device's link screen (opened by clicking it with the Configurator): the gate it's linked to and where, and
// unlinking it
public class ZpmLinkNetworkHandler extends SGChannel {

    protected static BaseDataChannel linkChannel;

    // How far from the device its configurer may be
    private static final double maxReach = 8;

    public ZpmLinkNetworkHandler(String name) {
        super(name);
        linkChannel = this;
    }

    private static boolean mayConfigure(EntityPlayer player) {
        return SGCraft.hasPermission(player, "sgcraft.gui.configurator")
            || (SGCraft.hasPermissionSystem() && SGCraft.hasPermission(player, "sgcraft.admin"));
    }

    // The device at pos, if the player is at it and may configure it
    private static ILinkableZpm deviceFor(EntityPlayer player, BlockPos pos) {
        if (!mayConfigure(player) || !player.world.isBlockLoaded(pos)
                || player.getDistanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > maxReach * maxReach) {
            return null;
        }
        TileEntity te = player.world.getTileEntity(pos);
        return te instanceof ILinkableZpm ? (ILinkableZpm) te : null;
    }

    public static void openScreen(EntityPlayer player, BlockPos pos) {
        if (!SGCraft.isModLoaded("malisiscore") || !(player instanceof EntityPlayerMP)) {
            return; // its screen needs MalisisCore, as the Configurator does
        }
        if (!mayConfigure(player)) {
            sendErrorMsg(player, "configuratorPermission");
            return;
        }
        if (deviceFor(player, pos) != null) {
            sendStatus(player, pos);
        }
    }

    private static void sendStatus(EntityPlayer player, BlockPos pos) {
        TileEntity te = player.world.getTileEntity(pos);
        if (!(te instanceof ILinkableZpm)) {
            return;
        }
        ZpmLink link = ((ILinkableZpm) te).getZpmLink();
        ChannelOutput data = linkChannel.openPlayer(player, "ZpmLinkStatus");
        BaseBlockUtils.writeBlockPos(data, pos);
        data.writeUTF(te.getBlockType().getTranslationKey() + ".name");
        data.writeBoolean(link.linked);
        if (link.linked) {
            SGBaseTE gate = SGBaseTE.at(player.world, link.gatePos);
            BaseBlockUtils.writeBlockPos(data, link.gatePos);
            data.writeBoolean(gate != null && gate.isMerged);
            data.writeUTF(gate != null && gate.homeAddress != null ? gate.homeAddress : "");
            data.writeUTF(AsgardObelisks.worldName(player.world.provider.getDimension()));
        }
        data.close();
    }

    @ClientMessageHandler("ZpmLinkStatus")
    public void handleStatusFromServer(EntityPlayer player, ChannelInput data) {
        ZpmLinkScreen.Status status = new ZpmLinkScreen.Status();
        status.pos = BaseBlockUtils.readBlockPos(data);
        status.deviceName = data.readUTF();
        status.linked = data.readBoolean();
        if (status.linked) {
            status.gatePos = BaseBlockUtils.readBlockPos(data);
            status.gateFound = data.readBoolean();
            status.gateAddress = data.readUTF();
            status.worldName = data.readUTF();
        }
        ZpmLinkScreen.show(status);
    }

    public static void sendUnlinkToServer(BlockPos pos) {
        ChannelOutput data = linkChannel.openServer("ZpmUnlink");
        BaseBlockUtils.writeBlockPos(data, pos);
        data.close();
    }

    // ---- From a gate's Configurator (LinkedZpmsScreen): unlinking one of its devices ----

    public static void sendGateUnlinkToServer(BlockPos gatePos, BlockPos devicePos) {
        ChannelOutput data = linkChannel.openServer("GateZpmUnlink");
        BaseBlockUtils.writeBlockPos(data, gatePos);
        BaseBlockUtils.writeBlockPos(data, devicePos);
        data.close();
    }

    @ServerMessageHandler("GateZpmUnlink")
    public void handleGateUnlinkFromClient(EntityPlayer player, ChannelInput data) {
        BlockPos gatePos = BaseBlockUtils.readBlockPos(data);
        BlockPos devicePos = BaseBlockUtils.readBlockPos(data);
        // As for opening the gate's Configurator: near the gate (a supergate's base can be far from where its ring is
        // clicked), allowed to configure, and an admin of it
        if (!mayConfigure(player) || !player.world.isBlockLoaded(gatePos)) {
            return;
        }
        SGBaseTE gate = SGBaseTE.at(player.world, gatePos);
        if (gate == null) {
            return;
        }
        double reach = SGCraft.toolSearchRange + maxReach + gate.getShape().halfWidth;
        boolean admin = SGCraft.hasPermissionSystem() && SGCraft.hasPermission(player, "sgcraft.admin");
        if (player.getDistanceSq(gatePos.getX() + 0.5, gatePos.getY() + 0.5, gatePos.getZ() + 0.5) > reach * reach
                || !(gate.allowAdminAccess(player) || admin)) {
            return;
        }
        TileEntity te = player.world.getTileEntity(devicePos);
        if (te instanceof ILinkableZpm && ((ILinkableZpm) te).getZpmLink().isLinkedTo(gatePos)) {
            ZpmLinks.unlink(player.world, devicePos);
        } else {
            gate.removeLinkedZpm(devicePos); // gone, or linked elsewhere: just off the gate's list
        }
    }

    @ServerMessageHandler("ZpmUnlink")
    public void handleUnlinkFromClient(EntityPlayer player, ChannelInput data) {
        BlockPos pos = BaseBlockUtils.readBlockPos(data);
        if (deviceFor(player, pos) != null) {
            ZpmLinks.unlink(player.world, pos);
            sendStatus(player, pos);
        }
    }
}
