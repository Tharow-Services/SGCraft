package gcewing.sg.network;

import gcewing.sg.BaseBlockUtils;
import gcewing.sg.BaseDataChannel;
import gcewing.sg.SGCraft;
import gcewing.sg.features.gdo.client.gui.GdoScreen;
import gcewing.sg.features.pdd.client.gui.PddScreen;
import gcewing.sg.tileentity.SGBaseTE;
import gcewing.sg.util.GateUtil;
import gcewing.sg.util.SGAddressing;
import gcewing.sg.util.SGState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;

import static gcewing.sg.tileentity.SGBaseTE.sendErrorMsg;

public class GuiNetworkHandler extends SGChannel {

    protected static BaseDataChannel guiChannel;

    public GuiNetworkHandler(String name) {
        super(name);
        guiChannel = this;
    }

    public static void sendGuiRequestToServer(SGBaseTE te, EntityPlayer player, int guiType) {
        ChannelOutput data = guiChannel.openServer("requestGUI");
        writeCoords(data, te);
        data.writeInt(guiType);
        data.close();
    }

    // An empty handed click on a gate's drawn ring that hit no block (see SGCraft.onRightClickEmpty)
    public static void sendGateActivationToServer(SGBaseTE te) {
        ChannelOutput data = guiChannel.openServer("activateGate");
        writeCoords(data, te);
        data.close();
    }

    @ServerMessageHandler("activateGate")
    public void handleGateActivationFromClient(EntityPlayer player, ChannelInput data) {
        BlockPos pos = readCoords(data);
        // Only if the player really is looking at that gate's ring
        SGBaseTE gate = GateUtil.gateLookedAt(player.world, player, GateUtil.reach(player) + 1);
        if (gate != null && gate.getPos().equals(pos)) {
            GateUtil.activateGate(gate, player);
        }
    }

    @ServerMessageHandler("requestGUI")
    public void handleGUIRequestFromClient(EntityPlayer player, ChannelInput data) {
        BlockPos pos = readCoords(data);
        SGBaseTE localGate = SGBaseTE.at(player.world, pos);
        boolean canAccessLocal = localGate.allowGateAccess(player.getName());
        boolean canEditRemote = false;
        if (localGate.isConnected() && localGate.state == SGState.Connected) {
            SGBaseTE remoteGate = localGate.getConnectedStargateTE();
            canEditRemote = remoteGate.allowGateAccess(player.getName());
        }
        int guiType = data.readInt();
        if (guiType == 1) {
            if (SGCraft.hasPermission(player, "sgcraft.gui.configurator")) {
                if (canAccessLocal) {
                    this.openGuiAtClient(localGate, player, 1, SGCraft.hasPermission(player, "sgcraft.admin"), canAccessLocal, canEditRemote);
                } else {
                    sendErrorMsg(player, "insufficientGatePermission");
                }
            } else {
                sendErrorMsg(player, "configuratorPermission");
            }
        }

        if (guiType == 2) {
            if (SGCraft.hasPermission(player, "sgcraft.gui.gdo")) {
                this.openGuiAtClient(localGate, player, 2, SGCraft.hasPermission(player, "sgcraft.admin"), canAccessLocal, canEditRemote);
            } else {
                sendErrorMsg(player, "gdoPermission");
            }
        }

    }

    public static void openGuiAtClient(SGBaseTE te, EntityPlayer player, int guiType, boolean isAdmin, boolean canEditLocal, boolean canEditRemote) {
        ChannelOutput data = guiChannel.openPlayer(player,"OpenGUI");
        if (te != null) {
            writeCoords(data, te);
            // Original Code
        } else {
            BaseBlockUtils.writeBlockPos(data, player.getPosition());
            // fill in coordinate so it doesn't thrown an NPE.
        }
        data.writeInt(guiType);
        data.writeBoolean(isAdmin);
        data.writeBoolean(canEditLocal);
        data.writeBoolean(canEditRemote);
        if (guiType == 2) {
            data.writeBoolean(te.isConnected() && te.state == SGState.Connected);
            if (te.isConnected() && te.state == SGState.Connected) {
                SGBaseTE remoteGate = te.getConnectedStargateTE();
                data.writeBoolean(remoteGate.hasIrisUpgrade);
                data.writeBoolean(remoteGate.hasChevronUpgrade);
                data.writeBoolean(remoteGate.irisIsClosed());
                data.writeInt(remoteGate.gateType);
                data.writeUTF(SGAddressing.formatAddress(remoteGate.homeAddress, "-", "-"));
            }
        }
        data.close();
    }

    @ClientMessageHandler("OpenGUI")
    public void handleGuiOpenRequest(EntityPlayer player, ChannelInput data) {

        BlockPos pos = readCoords(data);
        int guiType = data.readInt();
        boolean isAdmin = data.readBoolean();
        boolean canAccessLocal = data.readBoolean();
        boolean canAccessRemote = data.readBoolean();

        if (guiType == 1) {
            // Todo: this is abandoned because it was moved.
            //new ConfiguratorScreen(player, player.world, isAdmin).display();
        }
        if (guiType == 2) {
            boolean r_connected = data.readBoolean();
            boolean r_hasIrisUpgrade = false;
            boolean r_hasChevronUpgrade = false;
            boolean r_isIrisClosed = false;
            int r_gateType = 1;
            String r_address = "";
            if (r_connected) {
                r_hasIrisUpgrade = data.readBoolean();
                r_hasChevronUpgrade = data.readBoolean();
                r_isIrisClosed = data.readBoolean();
                r_gateType = data.readInt();
                r_address = data.readUTF();
            }

            if (Minecraft.getMinecraft().currentScreen instanceof GdoScreen) {
                GdoScreen screen = (GdoScreen)Minecraft.getMinecraft().currentScreen;
                screen.isRemoteConnected = r_connected;
                screen.r_hasIrisUpgrade = r_hasIrisUpgrade;
                screen.r_hasChevronUpgrade = r_hasIrisUpgrade;
                screen.r_isIrisClosed = r_isIrisClosed;
                screen.r_gateType = r_gateType;
                screen.r_address = r_address;
            } else {
                new GdoScreen(player, player.world, pos, isAdmin, r_connected, r_hasIrisUpgrade, r_hasChevronUpgrade, r_isIrisClosed, r_gateType, r_address, canAccessLocal, canAccessRemote).display();
            }
        }
        if (guiType == 3) {
            new PddScreen(player, player.world, pos, isAdmin).display();
        }
    }
}
