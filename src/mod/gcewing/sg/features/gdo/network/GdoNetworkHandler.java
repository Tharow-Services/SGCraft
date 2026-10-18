package gcewing.sg.features.gdo.network;

import gcewing.sg.BaseDataChannel;
import gcewing.sg.SGCraft;
import gcewing.sg.network.SGChannel;
import gcewing.sg.tileentity.SGBaseTE;
import gcewing.sg.features.gdo.client.gui.GdoScreen;
import gcewing.sg.util.GateUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;

public class GdoNetworkHandler extends SGChannel {

    protected static BaseDataChannel gdoChannel;

    public GdoNetworkHandler(String name) {
        super(name);
        gdoChannel = this;
    }

    // Asks for the latest data of the gate at the other end of the local gate's connection, to draw on the GDO
    public static void requestRemoteGateFromServer(SGBaseTE localGate) {
        ChannelOutput data = gdoChannel.openServer("RemoteGateRequest");
        writeCoords(data, localGate);
        data.close();
    }

    @ServerMessageHandler("RemoteGateRequest")
    public void handleRemoteGateRequestFromClient(EntityPlayer player, ChannelInput data) {
        BlockPos pos = readCoords(data);
        SGBaseTE localGate = loadedGateAt(player, pos);
        // Only for a player at a connected gate, as when using the GDO there
        if (localGate == null || !localGate.isConnected() || !SGCraft.hasPermission(player, "sgcraft.gui.gdo")
                || GateUtil.distanceToGate(localGate, player.getPositionEyes(1)) > 2 * Math.max(SGCraft.toolSearchRange, GateUtil.reach(player))) {
            return;
        }
        SGBaseTE remoteGate = localGate.getConnectedStargateTE();
        if (remoteGate == null) {
            return;
        }
        ChannelOutput reply = gdoChannel.openPlayer(player, "RemoteGate");
        writeCoords(reply, localGate);
        writeCoords(reply, remoteGate);
        try {
            // What any player near the remote gate would be sent; the access lists are left out of it
            CompressedStreamTools.write(remoteGate.getUpdateTag(), reply);
        } catch (IOException e) {
            e.printStackTrace();
        }
        reply.close();
    }

    @ClientMessageHandler("RemoteGate")
    public void handleRemoteGateFromServer(EntityPlayer player, ChannelInput data) {
        BlockPos localPos = readCoords(data);
        BlockPos remotePos = readCoords(data);
        NBTTagCompound nbt;
        try {
            nbt = CompressedStreamTools.read(data, new NBTSizeTracker(2097152L));
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        if (Minecraft.getMinecraft().currentScreen instanceof GdoScreen) {
            ((GdoScreen) Minecraft.getMinecraft().currentScreen).updateRemoteGate(localPos, remotePos, nbt);
        }
    }

    public static void sendGdoInputToServer(SGBaseTE te, int function) {
        ChannelOutput data = gdoChannel.openServer("GdoInput");
        writeCoords(data, te);
        data.writeInt(function);
        data.close();
    }

    @ServerMessageHandler("GdoInput")
    public void handleGdoInputFromClient(EntityPlayer player, ChannelInput data) {
        BlockPos pos = readCoords(data);
        int setting = data.readInt();

        if (!SGCraft.hasPermission(player, "sgcraft.gui.gdo")) {
            System.err.println("SGCraft - Hacked Client detected!");
            return;
        }
        // The GDO works the gate its player is at, and the one at the far end of its wormhole; a client could otherwise
        // open or close the iris of any gate in its world from anywhere, killing whoever came through
        SGBaseTE localGate = loadedGateAt(player, pos);
        if (localGate == null || !playerAtGate(player, localGate)) {
            return;
        }

        boolean playerAccessLocalControl = true;
        boolean playerAccessRemoteControl = true;

        boolean isPermissionsAdmin = SGCraft.hasPermissionSystem() && SGCraft.hasPermission(player, "sgcraft.admin"); // Fallback for a full permissions system override to the Access System

        if (SGCraft.hasPermission(player, "sgcraft.gui.gdo") || isPermissionsAdmin) {
            if (localGate != null) {
                    if (!localGate.allowAccessToIrisController(player)) {
                        playerAccessLocalControl = false;
                    }
                    if (playerAccessLocalControl || isPermissionsAdmin) {
                        if (setting == 1) {
                            localGate.openIris();
                        }
                        if (setting == 2) {
                            localGate.closeIris();
                        }
                    }

                if (setting == 3 && (localGate.allowGateAccess(player) || isPermissionsAdmin)) localGate.disconnect(player);

                if (setting == 4 || setting == 5 || setting == 6) {
                    if (localGate.isConnected()) {
                        SGBaseTE remoteGate = localGate.getConnectedStargateTE();
                        if (remoteGate != null) {
                            if (!remoteGate.allowAccessToIrisController(player)) {
                                playerAccessRemoteControl = false;
                            }
                            if (playerAccessRemoteControl || isPermissionsAdmin) {
                                if (setting == 4) {
                                    remoteGate.openIris();
                                }
                                if (setting == 5) {
                                    remoteGate.closeIris();
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
