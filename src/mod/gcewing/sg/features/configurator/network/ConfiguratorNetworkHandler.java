package gcewing.sg.features.configurator.network;

import static gcewing.sg.tileentity.SGBaseTE.sendBasicMsg;

import gcewing.sg.BaseDataChannel;
import gcewing.sg.SGCraft;
import gcewing.sg.features.configurator.client.gui.ConfiguratorScreen;
import gcewing.sg.network.SGChannel;
import gcewing.sg.tileentity.SGBaseTE;
import gcewing.sg.tileentity.data.GateAccessData;
import gcewing.sg.tileentity.data.PlayerAccessData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;

public class ConfiguratorNetworkHandler extends SGChannel {

    protected static BaseDataChannel configuratorChannel;

    public ConfiguratorNetworkHandler(String name) {
        super(name);
        configuratorChannel = this;
    }

    public static void sendConfiguratorInputToServer(SGBaseTE te,
            int secondsToStayOpen,
            double ringRotationSpeed,
            double maxEnergyBuffer,
            double energyPerFuelItem,
            int gateOpeningsPerFuelItem,
            double distanceFactorMultiplier,
            double interDimensionalMultiplier,
            boolean oneWayTravel,
            boolean hasIrisUpgrade,
            boolean hasChevronUpgrade,
            int gateType,
            boolean reverseWormholeKills,
            boolean closeFromEitherEnd,
            boolean preserveInventory,
            boolean requiresNoPower,
            boolean chevronsLockOnDial,
            boolean returnToPreviousIrisState,
            boolean transientDamage,
            boolean transparency,
            int orientation,
            boolean useDHDFuelSource,
            boolean allowRedstoneOutput,
            boolean allowRedstoneInput,
            boolean canPlayerBreakPlayerGate,
            boolean canPlayerBreakGeneratedGate,
            boolean displayGateAddress,
            boolean isGenerated) {

        ChannelOutput data = configuratorChannel.openServer("ConfiguratorInput");
        writeCoords(data, te);
        data.writeInt(secondsToStayOpen);
        data.writeDouble(ringRotationSpeed);
        data.writeDouble(maxEnergyBuffer);
        data.writeDouble(energyPerFuelItem);
        data.writeInt(gateOpeningsPerFuelItem);
        data.writeDouble(distanceFactorMultiplier);
        data.writeDouble(interDimensionalMultiplier);
        data.writeBoolean(oneWayTravel);
        data.writeBoolean(hasIrisUpgrade);
        data.writeBoolean(hasChevronUpgrade);
        data.writeInt(gateType);
        data.writeBoolean(reverseWormholeKills);
        data.writeBoolean(closeFromEitherEnd);
        data.writeBoolean(preserveInventory);
        data.writeBoolean(requiresNoPower);
        data.writeBoolean(chevronsLockOnDial);
        data.writeBoolean(returnToPreviousIrisState);
        data.writeBoolean(transientDamage);
        data.writeBoolean(transparency);
        data.writeInt(orientation);
        data.writeBoolean(useDHDFuelSource);
        data.writeBoolean(allowRedstoneOutput);
        data.writeBoolean(allowRedstoneInput);
        data.writeBoolean(canPlayerBreakPlayerGate);
        data.writeBoolean(canPlayerBreakGeneratedGate);
        data.writeBoolean(displayGateAddress);
        data.writeBoolean(isGenerated);
        data.close();
    }

    @ServerMessageHandler("ConfiguratorInput")
    public void handleConfiguratorInputFromClient(EntityPlayer player, ChannelInput data) {
        BlockPos pos = readCoords(data);
        SGBaseTE te = SGBaseTE.at(player.world, pos);

        int secondsToStayOpen = data.readInt();
        double ringRotationSpeed = data.readDouble();
        double maxEnergyBuffer = data.readDouble();
        double energyPerFuelItem = data.readDouble();
        int gateOpeningsPerFuelItem = data.readInt();
        double distanceFromMultiplier = data.readDouble();
        double interDimensionalMultiplier = data.readDouble();
        boolean oneWayTravel = data.readBoolean();
        boolean hasIrisUpgrade = data.readBoolean();
        boolean hasChevronUpgrade = data.readBoolean();
        int gateType = data.readInt();
        boolean reverseWormholeKills = data.readBoolean();
        boolean closeFromEitherEnd = data.readBoolean();
        boolean preserveInventory = data.readBoolean();
        boolean requiresNoPower = data.readBoolean();
        boolean chevronsLockOnDial = data.readBoolean();
        boolean returnToPreviousIrisState = data.readBoolean();
        boolean transientDamage = data.readBoolean();
        boolean transparency = data.readBoolean();
        int orientation = data.readInt();
        boolean useDHDFuelSource = data.readBoolean();
        boolean allowRedstoneOutput = data.readBoolean();
        boolean allowRedstoneInput = data.readBoolean();
        boolean canPlayerBreakPlayerGate = data.readBoolean();
        boolean canPlayerBreakGeneratedGate = data.readBoolean();
        boolean displayGateAddress = data.readBoolean();
        boolean isGenerated = data.readBoolean();

        boolean isPermissionsAdmin = SGCraft.hasPermissionSystem() && SGCraft.hasPermission(player, "sgcraft.admin"); // Fallback for a full permissions system override to the Access System

        if (SGCraft.hasPermission(player, "sgcraft.gui.configurator") && te.allowAdminAccess(player.getName()) || isPermissionsAdmin) {
            if (SGCraft.hasPermission(player, "sgcraft.configurator.secondsToStayOpen") || isPermissionsAdmin) {
                te.secondsToStayOpen = secondsToStayOpen;
                te.ticksToStayOpen = te.secondsToStayOpen * 20;
            }
            if (SGCraft.hasPermission(player, "sgcraft.configurator.ringRotationSpeed") || isPermissionsAdmin) te.ringRotationSpeed = ringRotationSpeed;
            if (SGCraft.hasPermission(player, "sgcraft.configurator.maxEnergyBuffer") || isPermissionsAdmin) te.maxEnergyBuffer = maxEnergyBuffer;
            if (SGCraft.hasPermission(player, "sgcraft.configurator.energyPerFuelItem") || isPermissionsAdmin) te.energyPerFuelItem = energyPerFuelItem;
            if (SGCraft.hasPermission(player, "sgcraft.configurator.gateOpeningsPerFuelItem") || isPermissionsAdmin) te.gateOpeningsPerFuelItem = gateOpeningsPerFuelItem;
            if (SGCraft.hasPermission(player, "sgcraft.configurator.distanceFactorMultiplier") || isPermissionsAdmin) te.distanceFactorMultiplier = distanceFromMultiplier;
            if (SGCraft.hasPermission(player, "sgcraft.configurator.interDimensionalMultiplier") || isPermissionsAdmin) te.interDimensionMultiplier = interDimensionalMultiplier;
            if (SGCraft.hasPermission(player, "sgcraft.configurator.oneWayTravel") || isPermissionsAdmin) te.oneWayTravel = oneWayTravel;
            if (SGCraft.hasPermission(player, "sgcraft.configurator.hasIrisUpgrade") || isPermissionsAdmin) te.hasIrisUpgrade = hasIrisUpgrade;
            if (SGCraft.hasPermission(player, "sgcraft.configurator.hasChevronUpgrade") || isPermissionsAdmin) te.hasChevronUpgrade = hasChevronUpgrade;
            if (SGCraft.hasPermission(player, "sgcraft.configurator.gateType") || isPermissionsAdmin) te.gateType = gateType;
            if (SGCraft.hasPermission(player, "sgcraft.configurator.reverseWormholeKills") || isPermissionsAdmin) te.reverseWormholeKills = reverseWormholeKills;
            if (SGCraft.hasPermission(player, "sgcraft.configurator.closeFromEitherEnd") || isPermissionsAdmin) te.closeFromEitherEnd = closeFromEitherEnd;
            if (SGCraft.hasPermission(player, "sgcraft.configurator.preserveInventory") || isPermissionsAdmin) te.preserveInventory = preserveInventory;
            if (SGCraft.hasPermission(player, "sgcraft.configurator.noPowerRequired") || isPermissionsAdmin) te.requiresNoPower = requiresNoPower;
            if (SGCraft.hasPermission(player, "sgcraft.configurator.chevronsLockOnDial") || isPermissionsAdmin) te.chevronsLockOnDial = chevronsLockOnDial;
            if (SGCraft.hasPermission(player, "sgcraft.configurator.returnToPreviousIrisState") || isPermissionsAdmin) te.returnToPreviousIrisState = returnToPreviousIrisState;
            if (SGCraft.hasPermission(player, "sgcraft.configurator.transientDamage") || isPermissionsAdmin) te.transientDamage = transientDamage;
            if (SGCraft.hasPermission(player, "sgcraft.configurator.transparency") || isPermissionsAdmin) te.transparency = transparency;
            if (SGCraft.hasPermission(player, "sgcraft.configurator.orientation") || isPermissionsAdmin) te.gateOrientation = orientation;
            if (SGCraft.hasPermission(player, "sgcraft.configurator.useDHDFuelSource") || isPermissionsAdmin) te.useDHDFuelSource = useDHDFuelSource;
            if (SGCraft.hasPermission(player, "sgcraft.configurator.allowRedstoneOutput") || isPermissionsAdmin) te.allowRedstoneOutput = allowRedstoneOutput;
            if (SGCraft.hasPermission(player, "sgcraft.configurator.allowRedstoneInput") || isPermissionsAdmin) te.allowRedstoneInput = allowRedstoneInput;
            if (SGCraft.hasPermission(player, "sgcraft.configurator.canPlayerBreakPlayerGate") || isPermissionsAdmin) te.canPlayerBreakPlayerGate = canPlayerBreakPlayerGate;
            if (SGCraft.hasPermission(player, "sgcraft.configurator.canPlayerBreakGeneratedGate") || isPermissionsAdmin) te.canPlayerBreakGeneratedGate = canPlayerBreakGeneratedGate;
            if (SGCraft.hasPermission(player, "sgcraft.configurator.displayGateAddress") || isPermissionsAdmin) te.displayGateAddress = displayGateAddress;
            if (SGCraft.hasPermission(player, "sgcraft.configurator.isGenerated") || isPermissionsAdmin) te.isGenerated = isGenerated;
            sendBasicMsg(player, "changesSaved");
            te.updateIrisEntity();
            te.markForUpdate();
        }
    }

    public static void sendGateAddressAccessInputToServer(SGBaseTE te, String address, boolean defaultAllowIncoming, boolean defaultAllowOutgoing, boolean allowIncoming, boolean allowOutgoing) {
        ChannelOutput data = configuratorChannel.openServer("GateAddressAccessInput");
        writeCoords(data, te);
        data.writeUTF(address);
        data.writeBoolean(defaultAllowIncoming);
        data.writeBoolean(defaultAllowOutgoing);
        data.writeBoolean(allowIncoming);
        data.writeBoolean(allowOutgoing);
        data.close();
    }

    @ServerMessageHandler("GateAddressAccessInput")
    public void handleGateAddressAccessInputFromClient(EntityPlayer player, ChannelInput data) {
        BlockPos pos = readCoords(data);
        SGBaseTE te = SGBaseTE.at(player.world, pos);

        String address = data.readUTF();
        boolean defaultAllowIncoming = data.readBoolean();
        boolean defaultAllowOutgoing = data.readBoolean();
        boolean allowIncoming = data.readBoolean();
        boolean allowOutgoing = data.readBoolean();

        if (te == null) {
            System.err.println("SGCraft - HandleGAA Exception - SGBaseTE disappeared");
            return;
        }

        boolean isPermissionsAdmin = SGCraft.hasPermissionSystem() && SGCraft.hasPermission(player, "sgcraft.admin"); // Fallback for a full permissions system override to the Access System

        if (SGCraft.hasPermission(player, "sgcraft.gui.configurator") && te.allowAdminAccess(player.getName()) || isPermissionsAdmin) {
            if (address.isEmpty()) { // indicates the user clicked the bottom save button
                te.defaultAllowIncoming = defaultAllowIncoming;
                te.defaultAllowOutgoing = defaultAllowOutgoing;
            }

            if (!address.isEmpty()) {
                te.setAllowIncomingAddress(address, allowIncoming);
                te.setAllowOutgoingAddress(address, allowOutgoing);
            }
        }

        te.markForUpdate();
    }

    public static void sendPlayerAccessInputToServer(SGBaseTE te, String playerName, boolean defaultAllowAccess, boolean defaultAllowIris, boolean defaultAllowAdmin, boolean allowAccess, boolean allowIris, boolean allowAdmin) {
        ChannelOutput data = configuratorChannel.openServer("PlayerAccessInput");
        writeCoords(data, te);
        data.writeUTF(playerName);
        data.writeBoolean(defaultAllowAccess);
        data.writeBoolean(defaultAllowIris);
        data.writeBoolean(defaultAllowAdmin);
        data.writeBoolean(allowAccess);
        data.writeBoolean(allowIris);
        data.writeBoolean(allowAdmin);
        data.close();
    }

    @ServerMessageHandler("PlayerAccessInput")
    public void handlePlayerAccessInputFromClient(EntityPlayer player, ChannelInput data) {
        BlockPos pos = readCoords(data);
        SGBaseTE te = SGBaseTE.at(player.world, pos);

        String playerName = data.readUTF();
        boolean defaultAllowAccess = data.readBoolean();
        boolean defaultAllowIris = data.readBoolean();
        boolean defaultAllowAdmin = data.readBoolean();
        boolean allowAccess = data.readBoolean();
        boolean allowIris = data.readBoolean();
        boolean allowAdmin = data.readBoolean();

        if (te == null) {
            System.err.println("SGCraft - HandleGAA Exception - SGBaseTE disappeared");
            return;
        }

        boolean isPermissionsAdmin = SGCraft.hasPermissionSystem() && SGCraft.hasPermission(player, "sgcraft.admin"); // Fallback for a full permissions system override to the Access System

        if (SGCraft.hasPermission(player, "sgcraft.gui.configurator") && te.allowAdminAccess(player.getName()) || isPermissionsAdmin) {
            if (playerName.isEmpty()) {
                te.defaultAllowGateAccess = defaultAllowAccess;
                te.defaultAllowIrisAccess = defaultAllowIris;
                te.defaultAllowAdminAccess = defaultAllowAdmin;
            }

            te.setAllowGateAccessAccess(playerName, allowAccess);
            te.setAllowAccessToIrisController(playerName, allowIris);
            te.setAllowAccessAdmin(playerName, allowAdmin);

            te.markChanged();
        }
    }

    public static void sendGAAEntryUpdateToServer(SGBaseTE te, String oldAddress, String newAddress, int function) {
        ChannelOutput data = configuratorChannel.openServer("GAAInputEntry");
        writeCoords(data, te);
        data.writeUTF(oldAddress);
        data.writeUTF(newAddress);
        data.writeInt(function);
        data.close();
    }

    @ServerMessageHandler("GAAInputEntry")
    public void handleGAAEntryUpdateFromClient(EntityPlayer player, ChannelInput data) {
        BlockPos pos = readCoords(data);
        SGBaseTE localGate = SGBaseTE.at(player.world, pos);
        String oldAddress = data.readUTF();
        String newAddress = data.readUTF();
        int function = data.readInt();

        if (localGate == null) {
            System.err.println("SGCraft - HandleGAA Exception - SGBaseTE disappeared");
            return;
        }
        boolean isPermissionsAdmin = SGCraft.hasPermissionSystem() && SGCraft.hasPermission(player, "sgcraft.admin"); // Fallback for a full permissions system override to the Access System

        if (SGCraft.hasPermission(player, "sgcraft.gui.configurator") && localGate.allowAdminAccess(player.getName()) || isPermissionsAdmin) {
            if (localGate.getGateAccessData() != null) {
                if (oldAddress.isEmpty() && function == 1) {
                    localGate.getGateAccessData().add(new GateAccessData(newAddress, true, true));
                }

                if (localGate.getGateAccessData() != null) {
                    Optional<GateAccessData> gateAccessEntry = localGate.getGateAccessData().stream().filter(g -> g.getAddress().equalsIgnoreCase(oldAddress)).findFirst();
                    if (gateAccessEntry.isPresent()) {
                        if (!oldAddress.isEmpty() && !newAddress.isEmpty() && function == 2) {
                            gateAccessEntry.get().setAddress(newAddress);
                        }

                        if (!oldAddress.isEmpty() && function == 3) {
                            // Delete
                            localGate.getGateAccessData().remove(gateAccessEntry.get());
                        }
                    }
                }

                localGate.markChanged();
            } else {
                System.out.println("Exception in handleGAAEntryUpdateFromClient Handler");
            }
        }
    }

    public static void sendPAEntryUpdateToServer(SGBaseTE te, String oldName, String newName, int function) {
        ChannelOutput data = configuratorChannel.openServer("PAInputEntry");
        writeCoords(data, te);
        data.writeUTF(oldName);
        data.writeUTF(newName);
        data.writeInt(function);
        data.close();
    }

    @ServerMessageHandler("PAInputEntry")
    public void handlePAEntryUpdateFromClient(EntityPlayer player, ChannelInput data) {
        BlockPos pos = readCoords(data);
        SGBaseTE localGate = SGBaseTE.at(player.world, pos);
        String oldName = data.readUTF();
        String newName = data.readUTF();
        int function = data.readInt();

        if (localGate == null) {
            System.err.println("SGCraft - HandleGAA Exception - SGBaseTE disappeared");
            return;
        }

        boolean isPermissionsAdmin = SGCraft.hasPermissionSystem() && SGCraft.hasPermission(player, "sgcraft.admin"); // Fallback for a full permissions system override to the Access System

        if (SGCraft.hasPermission(player, "sgcraft.gui.configurator") && localGate.allowAdminAccess(player.getName()) || isPermissionsAdmin) {
            if (localGate.getPlayerAccessData() != null) {
                if (oldName.isEmpty() && function == 1) {
                    localGate.getPlayerAccessData().add(new PlayerAccessData(newName, true, true, player.getName().equalsIgnoreCase(newName)));
                }

                if (localGate.getPlayerAccessData() != null) {
                    Optional<PlayerAccessData> playerAccessEntry = localGate.getPlayerAccessData().stream().filter(g -> g.getPlayerName().equalsIgnoreCase(oldName)).findFirst();
                    if (playerAccessEntry.isPresent()) {
                        if (!oldName.isEmpty() && !newName.isEmpty() && function == 2) {
                            playerAccessEntry.get().setPlayerName(newName);
                        }

                        if (!oldName.isEmpty() && function == 3) {
                            // Delete
                            localGate.getPlayerAccessData().remove(playerAccessEntry.get());
                        }
                    }
                }

                localGate.markChanged();
            }
        }
    }

    public static void openGuiAtClient(SGBaseTE te,
            EntityPlayer player,
            int guiType,
            boolean isAdmin,
            boolean secondsToStayOpenPerm,
            boolean gateRotationSpeedPerm,
            boolean energyBufferSizePerm,
            boolean energyPerNaquadahPerm,
            boolean openingsPerNaquadahPerm,
            boolean distanceFactorMultiplierPerm,
            boolean interDimensionalMultiplierPerm,
            boolean oneWayTravelOnlyPerm,
            boolean irisUpgradePerm,
            boolean chevronUpgradePerm,
            boolean pegasusGateTypePerm,
            boolean reverseWormholeKillsPerm,
            boolean closeFromEitherEndPerm,
            boolean preserveInventoryOnIrisDeathPerm,
            boolean noInputPowerRequiredPerm,
            boolean chevronsLockOnDialPerm,
            boolean returnToPreviousIrisStatePerm,
            boolean transientDamagePerm,
            boolean transparencyPerm,
            boolean dhdAsFuelSourcePerm,
            boolean allowRedstoneOutputPerm,
            boolean allowRedstoneInputPerm,
            boolean gateAccessPerm,
            boolean playerAccessPerm,
            boolean canPlayerBreakPlayerGatePerm,
            boolean canPlayerBreakGeneratedGatePerm,
            boolean displayGateAddressPerm,
            boolean isGeneratedPerm) {

        ChannelOutput data = configuratorChannel.openPlayer(player,"OpenConfiguratorGUI");
        // Type is always 1 here. *for now*
        writeCoords(data, te);
        data.writeInt(guiType);
        data.writeBoolean(isAdmin);
        data.writeBoolean(secondsToStayOpenPerm);
        data.writeBoolean(gateRotationSpeedPerm);
        data.writeBoolean(energyBufferSizePerm);
        data.writeBoolean(energyPerNaquadahPerm);
        data.writeBoolean(openingsPerNaquadahPerm);
        data.writeBoolean(distanceFactorMultiplierPerm);
        data.writeBoolean(interDimensionalMultiplierPerm);
        data.writeBoolean(oneWayTravelOnlyPerm);
        data.writeBoolean(irisUpgradePerm);
        data.writeBoolean(chevronUpgradePerm);
        data.writeBoolean(pegasusGateTypePerm);
        data.writeBoolean(reverseWormholeKillsPerm);
        data.writeBoolean(closeFromEitherEndPerm);
        data.writeBoolean(preserveInventoryOnIrisDeathPerm);
        data.writeBoolean(noInputPowerRequiredPerm);
        data.writeBoolean(chevronsLockOnDialPerm);
        data.writeBoolean(returnToPreviousIrisStatePerm);
        data.writeBoolean(transientDamagePerm);
        data.writeBoolean(transparencyPerm);
        data.writeBoolean(dhdAsFuelSourcePerm);
        data.writeBoolean(allowRedstoneOutputPerm);
        data.writeBoolean(allowRedstoneInputPerm);
        data.writeBoolean(gateAccessPerm);
        data.writeBoolean(playerAccessPerm);
        data.writeBoolean(canPlayerBreakPlayerGatePerm);
        data.writeBoolean(canPlayerBreakGeneratedGatePerm);
        data.writeBoolean(displayGateAddressPerm);
        data.writeBoolean(isGeneratedPerm);
        data.close();
    }

    @ClientMessageHandler("OpenConfiguratorGUI")
    public void handleGuiOpenRequest(EntityPlayer player, ChannelInput data) {

        BlockPos pos = readCoords(data);
        int guiType = data.readInt();
        boolean isAdmin = data.readBoolean();
        boolean secondsToStayOpenPerm = data.readBoolean();
        boolean gateRotationSpeedPerm  = data.readBoolean();
        boolean energyBufferSizePerm  = data.readBoolean();
        boolean energyPerNaquadahPerm  = data.readBoolean();
        boolean openingsPerNaquadahPerm = data.readBoolean();
        boolean distanceFactoryMultiplierPerm  = data.readBoolean();
        boolean interDimensionalMultiplierPerm  = data.readBoolean();
        boolean oneWayTravelOnlyPerm  = data.readBoolean();
        boolean irisUpgradePerm  = data.readBoolean();
        boolean chevronUpgradePerm  = data.readBoolean();
        boolean pegasusGateTypePerm  = data.readBoolean();
        boolean reverseWormholeKillsPerm  = data.readBoolean();
        boolean closeFromEitherEndPerm  = data.readBoolean();
        boolean preserveInventoryOnIrisDeathPerm  = data.readBoolean();
        boolean noInputPowerRequiredPerm  = data.readBoolean();
        boolean chevronsLockOnDialPerm  = data.readBoolean();
        boolean returnToPreviousIrisStatePerm  = data.readBoolean();
        boolean transientDamagePerm  = data.readBoolean();
        boolean transparencyPerm  = data.readBoolean();
        boolean dhdAsFuelSourcePerm  = data.readBoolean();
        boolean allowRedstoneOutputPerm  = data.readBoolean();
        boolean allowRedstoneInputPerm  = data.readBoolean();
        boolean gateAccessPerm = data.readBoolean();
        boolean playerAccessPerm = data.readBoolean();
        boolean canPlayerBreakPlayerGatePerm = data.readBoolean();
        boolean canPlayerBreakGeneratedGatePerm = data.readBoolean();
        boolean displayGateAddressPerm = data.readBoolean();
        boolean isGeneratedPerm = data.readBoolean();

        if (guiType == 1) {
            new ConfiguratorScreen(player,
                    player.world,
                    pos,
                    isAdmin,
                    secondsToStayOpenPerm,
                    gateRotationSpeedPerm,
                    energyBufferSizePerm,
                    energyPerNaquadahPerm,
                    openingsPerNaquadahPerm,
                    distanceFactoryMultiplierPerm,
                    interDimensionalMultiplierPerm,
                    oneWayTravelOnlyPerm,
                    irisUpgradePerm,
                    chevronUpgradePerm,
                    pegasusGateTypePerm,
                    reverseWormholeKillsPerm,
                    closeFromEitherEndPerm,
                    preserveInventoryOnIrisDeathPerm,
                    noInputPowerRequiredPerm,
                    chevronsLockOnDialPerm,
                    returnToPreviousIrisStatePerm,
                    transientDamagePerm,
                    transparencyPerm,
                    dhdAsFuelSourcePerm,
                    allowRedstoneOutputPerm,
                    allowRedstoneInputPerm,
                    canPlayerBreakPlayerGatePerm,
                    canPlayerBreakGeneratedGatePerm,
                    displayGateAddressPerm,
                    isGeneratedPerm,
                    gateAccessPerm,
                    playerAccessPerm)
                    .display();
        }
    }
}
