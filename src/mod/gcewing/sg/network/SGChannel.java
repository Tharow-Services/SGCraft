//------------------------------------------------------------------------------------------------
//
//   SG Craft - Packet Handling
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.network;

import gcewing.sg.BaseBlockUtils;
import gcewing.sg.BaseDataChannel;
import gcewing.sg.tileentity.DHDTE;
import gcewing.sg.tileentity.SGBaseTE;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

public class SGChannel extends BaseDataChannel {

    protected static BaseDataChannel channel;

    public SGChannel(String name) {
        super(name);
        channel = this;
        //         for (Object h : handlers)
        //             System.out.printf("SGChannel: handlers include %s\n", h);
    }

    public static void sendConnectOrDisconnectToServer(SGBaseTE te, String address) {
        ChannelOutput data = channel.openServer("ConnectOrDisconnect");
        writeCoords(data, te);
        data.writeUTF(address);
        data.close();
    }

    @ServerMessageHandler("ConnectOrDisconnect") public void handleConnectOrDisconnectFromClient(EntityPlayer player, ChannelInput data) {
        BlockPos pos = readCoords(data);
        String address = data.readUTF();
        SGBaseTE te = SGBaseTE.at(player.world, pos);
        if (te != null) {
            te.connectOrDisconnect(address, player);
        }
    }

    public static void sendClearAddressToServer(DHDTE te) {
        ChannelOutput data = channel.openServer("ClearAddress");
        writeCoords(data, te);
        data.close();
    }

    @ServerMessageHandler("ClearAddress") public void handleClearAddressFromClient(EntityPlayer player, ChannelInput data) {
        BlockPos pos = readCoords(data);
        DHDTE te = DHDTE.at(player.world, pos);
        if (te != null) te.clearAddress();
    }

    public static void sendUnsetSymbolToServer(DHDTE te) {
        // Network channel for DHD Controller on Client
        ChannelOutput data = channel.openServer("UnsetSymbol");
        writeCoords(data, te);
        data.close();
    }

    @ServerMessageHandler("UnsetSymbol") public void handleUnsetSymbolFromClient(EntityPlayer player, ChannelInput data) {
        // Network channel for DHD Controller on Server
        BlockPos pos = readCoords(data);
        if (player.world.isBlockLoaded(pos)) {
            DHDTE te = DHDTE.at(player.world, pos);
            if (te != null) {
                te.unsetSymbol(player);
            }
        }
    }

    public static void sendEnterSymbolToServer(DHDTE te, char symbol) {
        // Network channel for DHD Controller on Client
        ChannelOutput data = channel.openServer("EnterSymbol");
        writeCoords(data, te);
        data.writeChar(symbol);
        data.close();
    }

    @ServerMessageHandler("EnterSymbol") public void handleEnterSymbolFromClient(EntityPlayer player, ChannelInput data) {
        // Network channel for DHD Controller on Server
        BlockPos pos = readCoords(data);
        char symbol = data.readChar();
        if (player.world.isBlockLoaded(pos)) {
            DHDTE te = DHDTE.at(player.world, pos);
            if (te != null) {
                te.enterSymbol(player, symbol);
            } else {
                SGBaseTE.sendGenericErrorMsg(player,"Dialling exception.  No DHD detected within range.");
            }
        }
    }

    // A player's own access to a gate. Clients aren't sent the gates' access lists, so this is how a client knows
    // which screens and slots to show its player (see SGBaseTE.setLocalPlayerAccess).
    public static void sendPlayerAccessToClient(EntityPlayerMP player, SGBaseTE te, boolean gateAccess, boolean irisAccess, boolean adminAccess) {
        ChannelOutput data = channel.openPlayer(player, "PlayerAccess");
        writeCoords(data, te);
        data.writeBoolean(gateAccess);
        data.writeBoolean(irisAccess);
        data.writeBoolean(adminAccess);
        data.close();
    }

    @ClientMessageHandler("PlayerAccess") public void handlePlayerAccessFromServer(EntityPlayer player, ChannelInput data) {
        BlockPos pos = readCoords(data);
        boolean gateAccess = data.readBoolean();
        boolean irisAccess = data.readBoolean();
        boolean adminAccess = data.readBoolean();
        if (player != null) {
            SGBaseTE te = SGBaseTE.at(player.world, pos);
            if (te != null) {
                te.setLocalPlayerAccess(gateAccess, irisAccess, adminAccess);
            }
        }
    }

    public static void writeCoords(ChannelOutput data, TileEntity te) {
        BaseBlockUtils.writeBlockPos(data, te.getPos());
    }

    public BlockPos readCoords(ChannelInput data) {
        return BaseBlockUtils.readBlockPos(data);
    }
}
