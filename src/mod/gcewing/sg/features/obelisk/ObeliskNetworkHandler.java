package gcewing.sg.features.obelisk;

import static gcewing.sg.tileentity.SGBaseTE.sendBasicMsg;
import static gcewing.sg.tileentity.SGBaseTE.sendErrorMsg;

import com.mojang.authlib.GameProfile;
import gcewing.sg.BaseBlockUtils;
import gcewing.sg.BaseDataChannel;
import gcewing.sg.SGCraft;
import gcewing.sg.features.obelisk.client.ObeliskConfiguratorScreen;
import gcewing.sg.features.obelisk.client.ObeliskBeamEffects;
import gcewing.sg.network.SGChannel;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Asgard obelisk messages: its configurator screen, and telling a player's client it's beaming them (to play its sound
// to them and white out their screen)
public class ObeliskNetworkHandler extends SGChannel {

    protected static BaseDataChannel obeliskChannel;

    public ObeliskNetworkHandler(String name) {
        super(name);
        obeliskChannel = this;
    }

    public static void startBeamOnClient(EntityPlayerMP player) {
        ChannelOutput data = obeliskChannel.openPlayer(player, "ObeliskBeam");
        data.writeInt(AsgardObelisks.beamDelayTicks);
        data.close();
    }

    // ---- Its configurator screen ----

    // How far from the obelisk its configurer may be
    private static final double maxReach = 8;

    // A player clicked the obelisk holding the Configurator: show them its settings, if they may change them
    public static void openConfigurator(EntityPlayer player, AsgardObeliskTE te) {
        if (!SGCraft.isModLoaded("malisiscore") || !(player instanceof EntityPlayerMP)) {
            return; // its screen needs MalisisCore, as the Configurator does
        }
        if (!SGCraft.hasPermission(player, "sgcraft.gui.configurator") && !AsgardObeliskTE.isServerAdmin(player)) {
            sendErrorMsg(player, "configuratorPermission");
            return;
        }
        if (!te.mayConfigure(player)) {
            sendErrorMsg(player, "obeliskNoConfigure");
            return;
        }
        sendConfig((EntityPlayerMP) player, te);
    }

    private static void sendConfig(EntityPlayerMP player, AsgardObeliskTE te) {
        ChannelOutput data = obeliskChannel.openPlayer(player, "ObeliskConfig");
        BaseBlockUtils.writeBlockPos(data, te.getPos());
        data.writeBoolean(te.linked);
        if (te.linked) {
            data.writeUTF(AsgardObelisks.worldName(te.linkDimension));
            BaseBlockUtils.writeBlockPos(data, te.linkPos);
        }
        data.writeUTF(te.ownerName == null ? "" : te.ownerName);
        data.writeBoolean(te.allowEveryone);
        data.writeInt(te.access.size());
        for (ObeliskAccess entry : te.access) {
            data.writeUTF(entry.name);
            data.writeBoolean(entry.use);
            data.writeBoolean(entry.admin);
        }
        data.close();
    }

    @ClientMessageHandler("ObeliskConfig")
    public void handleConfigFromServer(EntityPlayer player, ChannelInput data) {
        ObeliskConfig config = new ObeliskConfig();
        config.pos = BaseBlockUtils.readBlockPos(data);
        config.linked = data.readBoolean();
        if (config.linked) {
            config.linkWorld = data.readUTF();
            config.linkPos = BaseBlockUtils.readBlockPos(data);
        }
        config.ownerName = data.readUTF();
        config.allowEveryone = data.readBoolean();
        int count = data.readInt();
        for (int i = 0; i < count; i++) {
            config.access.add(new ObeliskAccess(null, data.readUTF(), data.readBoolean(), data.readBoolean()));
        }
        ObeliskConfiguratorScreen.show(player, config);
    }

    // The obelisk the player may configure at pos, if they're at it holding the Configurator
    private static AsgardObeliskTE configurable(EntityPlayer player, BlockPos pos) {
        if (!player.world.isBlockLoaded(pos) || SGCraft.configurator == null
                || player.getHeldItemMainhand().getItem() != SGCraft.configurator
                || player.getDistanceSq(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5) > maxReach * maxReach) {
            return null;
        }
        TileEntity te = player.world.getTileEntity(pos);
        return te instanceof AsgardObeliskTE && ((AsgardObeliskTE) te).mayConfigure(player) ? (AsgardObeliskTE) te : null;
    }

    // Saves who may use it: everyone by default, or only those allowed; players by name, looked up on the server
    public static void sendConfigToServer(BlockPos pos, boolean allowEveryone, List<ObeliskAccess> access) {
        ChannelOutput data = obeliskChannel.openServer("ObeliskConfigSave");
        BaseBlockUtils.writeBlockPos(data, pos);
        data.writeBoolean(allowEveryone);
        data.writeInt(access.size());
        for (ObeliskAccess entry : access) {
            data.writeUTF(entry.name);
            data.writeBoolean(entry.use);
            data.writeBoolean(entry.admin);
        }
        data.close();
    }

    @ServerMessageHandler("ObeliskConfigSave")
    public void handleConfigSaveFromClient(EntityPlayer player, ChannelInput data) {
        AsgardObeliskTE te = configurable(player, BaseBlockUtils.readBlockPos(data));
        boolean allowEveryone = data.readBoolean();
        int count = Math.min(data.readInt(), 256);
        List<ObeliskAccess> access = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String name = data.readUTF().trim();
            boolean use = data.readBoolean();
            boolean admin = data.readBoolean();
            if (te == null || name.isEmpty() || access.stream().anyMatch(e -> e.name.equalsIgnoreCase(name))) {
                continue;
            }
            // Someone already listed keeps their account; anyone new is looked up by name
            ObeliskAccess known = te.access.stream().filter(e -> e.name.equalsIgnoreCase(name)).findFirst().orElse(null);
            UUID uuid = known != null ? known.uuid : null;
            String realName = known != null ? known.name : name;
            if (uuid == null && player.getServer() != null) {
                GameProfile profile = player.getServer().getPlayerProfileCache().getGameProfileForUsername(name);
                if (profile != null) {
                    uuid = profile.getId();
                    realName = profile.getName();
                }
            }
            if (uuid == null) {
                sendErrorMsg(player, "obeliskUnknownPlayer", name);
                continue;
            }
            access.add(new ObeliskAccess(uuid, realName, use, admin));
        }
        if (te == null) {
            return;
        }
        te.allowEveryone = allowEveryone;
        te.access.clear();
        te.access.addAll(access);
        te.markDirty();
        sendBasicMsg(player, "changesSaved");
        sendConfig((EntityPlayerMP) player, te);
    }

    public static void sendUnlinkToServer(BlockPos pos) {
        ChannelOutput data = obeliskChannel.openServer("ObeliskUnlink");
        BaseBlockUtils.writeBlockPos(data, pos);
        data.close();
    }

    @ServerMessageHandler("ObeliskUnlink")
    public void handleUnlinkFromClient(EntityPlayer player, ChannelInput data) {
        AsgardObeliskTE te = configurable(player, BaseBlockUtils.readBlockPos(data));
        if (te != null) {
            AsgardObelisks.unlinkPartner(te);
            sendConfig((EntityPlayerMP) player, te);
        }
    }

    // ---- The beam ----

    public static void cancelBeamOnClient(EntityPlayerMP player) {
        ChannelOutput data = obeliskChannel.openPlayer(player, "ObeliskBeamCancel");
        data.close();
    }

    @ClientMessageHandler("ObeliskBeamCancel")
    public void handleBeamCancelFromServer(EntityPlayer player, ChannelInput data) {
        ObeliskBeamEffects.cancel();
    }

    @ClientMessageHandler("ObeliskBeam")
    public void handleBeamFromServer(EntityPlayer player, ChannelInput data) {
        ObeliskBeamEffects.start(data.readInt());
    }
}
