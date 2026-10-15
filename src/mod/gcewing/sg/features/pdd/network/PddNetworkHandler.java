package gcewing.sg.features.pdd.network;

import gcewing.sg.BaseDataChannel;
import gcewing.sg.SGCraft;
import gcewing.sg.features.pdd.AddressData;
import gcewing.sg.features.pdd.PddItem;
import gcewing.sg.features.pdd.client.gui.PddEntryScreen;
import gcewing.sg.features.pdd.client.gui.PddScreen;
import gcewing.sg.network.SGChannel;
import gcewing.sg.tileentity.SGBaseTE;
import gcewing.sg.util.SGAddressing;
import gcewing.sg.util.SGDimensionMap;
import gcewing.sg.util.SGState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class PddNetworkHandler extends SGChannel {

    protected static BaseDataChannel pddChannel;

    public PddNetworkHandler(String name) {
        super(name);
        pddChannel = this;
    }

    public static void addPddEntryFromServer(EntityPlayer player, String address, int nextIndex) {
        ChannelOutput data = pddChannel.openPlayer(player,"AddPddEntry");
        data.writeUTF(address);
        data.writeInt(nextIndex);
        data.close();
    }

    @ClientMessageHandler("EditPddEntry")
    public void handlePddEditAddressRequest(EntityPlayer player, ChannelInput data) {
        String address = data.readUTF();
        String name = data.readUTF();
        int currentIndex = data.readInt();
        int uuid = data.readInt();
        boolean autoClose = data.readBoolean();
        address = SGAddressing.formatAddress(address, "-", "-");
        new PddEntryScreen(null, player, name, address, currentIndex, uuid, false, autoClose,false, 2).display();
    }

    public static void editPddEntryFromServer(EntityPlayer player, String address, String name, int currentIndex, int uuid, boolean autoClose) {
        ChannelOutput data = pddChannel.openPlayer(player,"EditPddEntry");
        data.writeUTF(address);
        data.writeUTF(name);
        data.writeInt(currentIndex);
        data.writeInt(uuid);
        data.writeBoolean(autoClose);
        data.close();
    }

    @ClientMessageHandler("AddPddEntry")
    public void handlePddAddAddressRequest(EntityPlayer player, ChannelInput data) {
        String address = data.readUTF();
        int nextIndex = data.readInt();
        address = SGAddressing.formatAddress(address, "-", "-");
        new PddEntryScreen(null, player, "Name Here", address, nextIndex, 0, false, false,false, 1).display();
    }

    public static void updatePdd(EntityPlayer player, boolean value, int status) {
        ChannelOutput data = pddChannel.openPlayer(player,"UpdatePdd");
        data.writeInt(status);
        data.writeBoolean(value);
        data.close();
    }

    @ClientMessageHandler("UpdatePdd")
    public void handleUpdatePddListRequest(EntityPlayer player, ChannelInput data) {
        int status = data.readInt();
        boolean update = data.readBoolean();
        if (Minecraft.getMinecraft().currentScreen instanceof PddScreen) {
            PddScreen screen = (PddScreen) Minecraft.getMinecraft().currentScreen;
            if (status == 2)
                screen.stopDialing();
        }
    }

    public static void sendPddInputToServer(SGBaseTE te, int function, String origin, String destination) {
        ChannelOutput data = pddChannel.openServer("PddInput");
        writeCoords(data, te);
        data.writeInt(function);
        data.writeUTF(origin);
        data.writeUTF(destination);
        data.close();
    }

    @ServerMessageHandler("PddInput") public void handlePddInputFromClient(EntityPlayer player, ChannelInput data) throws SGAddressing.AddressingError {
        BlockPos pos = readCoords(data);
        int setting = data.readInt();
        SGBaseTE localGate = SGBaseTE.at(player.world, pos);
        String origin = data.readUTF();
        String destination = data.readUTF();

        if (!origin.isEmpty() && !destination.isEmpty()) {
            if (SGAddressing.inSameDimension(origin, destination)) {
                destination = destination.substring(0, 7);
            }
        }

        boolean isPermissionsAdmin = SGCraft.hasPermissionSystem() && SGCraft.hasPermission(player, "sgcraft.admin"); // Fallback for a full permissions system override to the Access System

        if (SGCraft.hasPermission(player, "sgcraft.gui.pdd") || isPermissionsAdmin) {

            if (setting == 1) { // Connect / Dial / Double Click
                if (!localGate.isConnected()) {
                    if (localGate.allowGateAccess(player) || isPermissionsAdmin) {
                        localGate.connect(destination, player, false, false);
                    }
                }
            }
            if (setting == 2) {
                if (localGate.isConnected()) {
                    if (localGate.allowGateAccess(player) || isPermissionsAdmin) {
                        localGate.disconnect(player);
                    }
                }
            }

            if (setting == 3) {
                // Reserved for testing purposes.
            }

            if (setting == 4) {
                if (localGate.allowGateAccess(player) || isPermissionsAdmin) {
                    if (localGate.state == SGState.Idle) {
                        localGate.isInitiator = true;
                    }
                    localGate.connectOrDisconnect("", player);
                    localGate.clearIdleConnection();
                    localGate.errorState = false; // Force this on the servers' TE.
                }
            }
        } else {
            System.err.println("SGCraft - Hacked Client detected!");
            return;
        }
    }

    public static void sendPddEntryUpdateToServer(String name, String address, int index, int unid, boolean locked, boolean autoClose) {
        ChannelOutput data = pddChannel.openServer("PddInputEntry");
        data.writeUTF(name);
        data.writeUTF(address);
        data.writeInt(index);
        data.writeInt(unid);
        data.writeBoolean(locked);
        data.writeBoolean(autoClose);
        data.close();
    }

    @ServerMessageHandler("PddInputEntry")
    public void handlePddEntryUpdateFromClient(EntityPlayer player, ChannelInput data) {
        String name = data.readUTF();
        String address = data.readUTF();
        int index = data.readInt();
        int unid = data.readInt();
        boolean locked = data.readBoolean();
        boolean autoClose = data.readBoolean();

        if (!SGCraft.hasPermission(player, "sgcraft.gui.pdd.edit")) {
            System.err.println("SGCraft - Hacked Client detected!");
            return;
        }

        final ItemStack stack = player.getHeldItemMainhand();
        if (stack != null) {
            NBTTagCompound compound = stack.getTagCompound();
            if (compound != null) {
                AddressData.updateAddress(player, compound, unid, name, address, index, locked, autoClose);
                stack.setTagCompound(compound);
                player.inventoryContainer.detectAndSendChanges();
                PddNetworkHandler.updatePdd(player, false, 1);
            }
        }
    }

    // Moves an address entry up (direction -1) or down (+1) the list on the PDD in the player's main hand
    public static void sendPddEntryMoveToServer(int unid, int direction) {
        ChannelOutput data = pddChannel.openServer("PddMoveEntry");
        data.writeInt(unid);
        data.writeInt(direction);
        data.close();
    }

    @ServerMessageHandler("PddMoveEntry")
    public void handlePddEntryMoveFromClient(EntityPlayer player, ChannelInput data) {
        int unid = data.readInt();
        int direction = data.readInt();

        if (!SGCraft.hasPermission(player, "sgcraft.gui.pdd.edit")) {
            System.err.println("SGCraft - Hacked Client detected!");
            return;
        }

        final ItemStack stack = player.getHeldItemMainhand();
        if (stack.getItem() instanceof PddItem && stack.getTagCompound() != null) {
            NBTTagCompound compound = stack.getTagCompound();
            if (AddressData.moveAddress(compound, unid, direction)) {
                stack.setTagCompound(compound);
                player.inventoryContainer.detectAndSendChanges();
            }
        }
    }

    // Asks the server which world each address leads to; the client can't tell, as the address only carries the
    // server's dimension index
    public static void requestWorldNamesFromServer(Collection<String> addresses) {
        ChannelOutput data = pddChannel.openServer("PddWorldNames");
        data.writeInt(addresses.size());
        for (String address : addresses) {
            data.writeUTF(address);
        }
        data.close();
    }

    @ServerMessageHandler("PddWorldNames")
    public void handleWorldNamesRequestFromClient(EntityPlayer player, ChannelInput data) {
        int count = Math.min(data.readInt(), 512);
        Map<String, String> names = new HashMap<>();
        for (int i = 0; i < count; i++) {
            String address = data.readUTF();
            String name = worldNameFor(player, address);
            if (name != null) {
                names.put(address, name);
            }
        }

        ChannelOutput reply = pddChannel.openPlayer(player, "PddWorldNamesReply");
        reply.writeInt(names.size());
        for (Map.Entry<String, String> entry : names.entrySet()) {
            reply.writeUTF(entry.getKey());
            reply.writeUTF(entry.getValue());
        }
        reply.close();
    }

    @ClientMessageHandler("PddWorldNamesReply")
    public void handleWorldNamesFromServer(EntityPlayer player, ChannelInput data) {
        int count = data.readInt();
        Map<String, String> names = new HashMap<>();
        for (int i = 0; i < count; i++) {
            names.put(data.readUTF(), data.readUTF());
        }
        if (Minecraft.getMinecraft().currentScreen instanceof PddScreen) {
            ((PddScreen) Minecraft.getMinecraft().currentScreen).setWorldNames(names);
        }
    }

    // The name of the world an address leads to, or null if it's not a valid address or that world doesn't exist.
    // A 7-symbol address has no dimension part, so leads to a gate in the dimension it's dialled from: the player's.
    private static String worldNameFor(EntityPlayer player, String address) {
        String normalized = address.toUpperCase().replaceAll("-", "");
        try {
            SGAddressing.validateAddress(normalized);
        } catch (SGAddressing.AddressingError e) {
            return null;
        }
        Integer dimension = player.dimension;
        if (normalized.length() == SGAddressing.maxAddressLength) {
            dimension = SGDimensionMap.dimensionForIndex(SGAddressing.dimensionSymbolsOf(normalized));
        }
        if (dimension == null || !DimensionManager.isDimensionRegistered(dimension)) {
            return null;
        }
        // Under Sponge each world has its own name; under plain Forge every dimension shares the save's name, so
        // fall back to the dimension's own name (the_nether, the_end...) for any but the overworld
        World world = DimensionManager.getWorld(dimension);
        World overworld = DimensionManager.getWorld(0);
        if (world != null && (dimension == 0 || overworld == null
                || !world.getWorldInfo().getWorldName().equals(overworld.getWorldInfo().getWorldName()))) {
            return world.getWorldInfo().getWorldName();
        }
        return DimensionManager.getProviderType(dimension).getName();
    }

    public static void sendEnterSymbolToServer(SGBaseTE te, String address, int digit) {
        ChannelOutput data = pddChannel.openServer("EnterImmediateSymbol");
        writeCoords(data, te);
        data.writeUTF(address);
        data.writeInt(digit);
        data.close();
    }


    @ServerMessageHandler("EnterImmediateSymbol")
    public void handleEnterSymbolFromClient(EntityPlayer player, ChannelInput data) {
        BlockPos pos = readCoords(data);
        String address = data.readUTF();
        int digit = data.readInt();

        SGBaseTE localGate = SGBaseTE.at(player.world, pos);
        if (localGate != null) {
            localGate.immediateDialSymbol(address, player, digit);
        }
    }
}
