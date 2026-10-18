//------------------------------------------------------------------------------------------------
//
//   SG Craft - Packet Handling
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.network;

import gcewing.sg.BaseBlockUtils;
import gcewing.sg.BaseDataChannel;
import gcewing.sg.SGCraft;
import gcewing.sg.tileentity.DHDTE;
import gcewing.sg.tileentity.SGBaseTE;
import gcewing.sg.util.GateUtil;
import gcewing.sg.util.SGAddressing;
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

    // From a DHD's screen: dial (an address), or close the gate (none)
    @ServerMessageHandler("ConnectOrDisconnect") public void handleConnectOrDisconnectFromClient(EntityPlayer player, ChannelInput data) {
        BlockPos pos = readCoords(data);
        String address = data.readUTF();
        SGBaseTE te = loadedGateAt(player, pos);
        if (te == null || !playerAtGate(player, te)) {
            return;
        }
        // Dialling checks the player's access itself; closing the gate didn't, so anyone could close any gate
        if (address.isEmpty() && !te.allowGateAccess(player)) {
            SGBaseTE.sendErrorMsg(player, "insufficientGateAccessPermission");
            return;
        }
        te.connectOrDisconnect(address, player);
    }

    public static void sendClearAddressToServer(DHDTE te) {
        ChannelOutput data = channel.openServer("ClearAddress");
        writeCoords(data, te);
        data.close();
    }

    @ServerMessageHandler("ClearAddress") public void handleClearAddressFromClient(EntityPlayer player, ChannelInput data) {
        BlockPos pos = readCoords(data);
        DHDTE te = loadedDhdAt(player, pos);
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
        DHDTE te = loadedDhdAt(player, pos);
        if (te != null) {
            te.unsetSymbol(player);
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
        char symbol = Character.toUpperCase(data.readChar());
        if (!SGAddressing.isValidSymbolChar(String.valueOf(symbol))) {
            return;
        }
        DHDTE te = loadedDhdAt(player, pos);
        if (te != null) {
            te.enterSymbol(player, symbol);
        } else {
            SGBaseTE.sendGenericErrorMsg(player,"Dialling exception.  No DHD detected within range.");
        }
    }

    // ---- Checks on what clients send: any position, for any player ----

    // How far (blocks) from a DHD its player may be
    static final double dhdReach = 10;

    // The gate at a position a client sent, if its chunk is loaded: looking a tile entity up loads its chunk, generating
    // it if need be, so a player could load or make chunks anywhere in their world
    public static SGBaseTE loadedGateAt(EntityPlayer player, BlockPos pos) {
        return player.world.isBlockLoaded(pos) ? SGBaseTE.at(player.world, pos) : null;
    }

    // The DHD at a position a client sent, if its chunk is loaded and the player is at it, using its screen
    public static DHDTE loadedDhdAt(EntityPlayer player, BlockPos pos) {
        if (!player.world.isBlockLoaded(pos)
                || player.getDistanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > dhdReach * dhdReach) {
            return null;
        }
        return DHDTE.at(player.world, pos);
    }

    // Whether the player is at the gate, as when dialling it from its DHD or using a tool on it (see
    // GateUtil.locateGateFor): near its ring, or at the DHD it's linked to. A gate's packets are only taken from players
    // there; any player could otherwise work any gate in their world from anywhere.
    public static boolean playerAtGate(EntityPlayer player, SGBaseTE gate) {
        // Generous: the tools pick a gate up to toolSearchRange blocks away in each direction
        double range = 2 * Math.max(SGCraft.toolSearchRange, GateUtil.reach(player));
        if (GateUtil.distanceToGate(gate, player.getPositionEyes(1)) <= range) {
            return true;
        }
        BlockPos dhd = gate.isLinkedToController ? gate.linkedPos : null;
        return dhd != null && player.getDistanceSq(dhd.getX() + 0.5, dhd.getY() + 0.5, dhd.getZ() + 0.5) <= dhdReach * dhdReach;
    }

    // An empty handed click on a gate's drawn ring that hit no block (see SGCraft.onRightClickEmpty). Sent on this
    // channel, which is always there: the GUI one is only made with MalisisCore, and without it the click threw.
    public static void sendGateActivationToServer(SGBaseTE te) {
        ChannelOutput data = channel.openServer("activateGate");
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
