package gcewing.sg.features.cityshield.network;

import static gcewing.sg.tileentity.SGBaseTE.sendErrorMsg;

import gcewing.sg.BaseBlockUtils;
import gcewing.sg.BaseDataChannel;
import gcewing.sg.SGCraft;
import gcewing.sg.features.cityshield.CityShieldTE;
import gcewing.sg.features.cityshield.client.gui.CityShieldScreen;
import gcewing.sg.network.SGChannel;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.malisis.core.inventory.MalisisInventory;
import net.malisis.core.inventory.MalisisInventoryContainer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

public class CityShieldNetworkHandler extends SGChannel {

    protected static BaseDataChannel cityShieldChannel;

    // How far from the generator its player may be to work it
    private static final double maxReach = 8;

    public CityShieldNetworkHandler(String name) {
        super(name);
        cityShieldChannel = this;
    }

    private static boolean mayUse(EntityPlayer player) {
        return SGCraft.hasPermission(player, "sgcraft.gui.cityshield")
            || (SGCraft.hasPermissionSystem() && SGCraft.hasPermission(player, "sgcraft.admin"));
    }

    // The generator at pos, if the player is at it and may work it
    private static CityShieldTE generatorFor(EntityPlayer player, BlockPos pos) {
        if (!mayUse(player) || !player.world.isBlockLoaded(pos)
                || player.getDistanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > maxReach * maxReach) {
            return null;
        }
        TileEntity te = player.world.getTileEntity(pos);
        return te instanceof CityShieldTE ? (CityShieldTE) te : null;
    }

    // ---- Opening the screen ----

    public static void openScreen(EntityPlayer player, BlockPos pos) {
        if (!mayUse(player)) {
            sendErrorMsg(player, "cityShieldPermission");
            return;
        }
        CityShieldTE te = generatorFor(player, pos);
        if (te != null && player instanceof EntityPlayerMP) {
            // MalisisCore opens its screen, with its ZPM slots, on the client (see CityShieldTE.getGui), which then asks for
            // the generator's power
            MalisisInventory.open((EntityPlayerMP) player, te);
        }
    }

    // The generator's settings and power, for its screen
    private static void sendStatus(EntityPlayer player, CityShieldTE te, String message) {
        ChannelOutput data = cityShieldChannel.openPlayer(player, message);
        BaseBlockUtils.writeBlockPos(data, te.getPos());
        data.writeBoolean(te.active);
        data.writeInt(te.width);
        data.writeInt(te.above);
        data.writeInt(te.below);
        data.writeInt(CityShieldTE.maxRadius);
        // In ZPM units, as ZPMs' tooltips and the ZPM console show them (a full ZPM holds 2,147,483,647)
        data.writeDouble(CityShieldTE.energyPerBlockPerTick * SGCraft.ZPMEnergyPerSGEnergyUnit);
        data.writeDouble(te.availableEnergy() * SGCraft.ZPMEnergyPerSGEnergyUnit);
        data.close();
    }

    @ClientMessageHandler("CityShieldStatus")
    public void handleStatusFromServer(EntityPlayer player, ChannelInput data) {
        CityShieldStatus status = readStatus(data);
        if (Minecraft.getMinecraft().currentScreen instanceof CityShieldScreen) {
            ((CityShieldScreen) Minecraft.getMinecraft().currentScreen).setStatus(status);
        }
    }

    private static CityShieldStatus readStatus(ChannelInput data) {
        CityShieldStatus status = new CityShieldStatus();
        status.pos = BaseBlockUtils.readBlockPos(data);
        status.active = data.readBoolean();
        status.width = data.readInt();
        status.above = data.readInt();
        status.below = data.readInt();
        status.maxRadius = data.readInt();
        status.energyPerBlockPerTick = data.readDouble();
        status.availableEnergy = data.readDouble();
        return status;
    }

    // ---- From the screen ----

    // The screen asks about once a second, to keep its power reading current
    public static void requestStatusFromServer(BlockPos pos) {
        ChannelOutput data = cityShieldChannel.openServer("CityShieldRequest");
        BaseBlockUtils.writeBlockPos(data, pos);
        data.close();
    }

    @ServerMessageHandler("CityShieldRequest")
    public void handleStatusRequestFromClient(EntityPlayer player, ChannelInput data) {
        CityShieldTE te = generatorFor(player, BaseBlockUtils.readBlockPos(data));
        if (te != null) {
            sendStatus(player, te, "CityShieldStatus");
        }
    }

    // New size for the shield, and whether it should be up
    public static void sendInputToServer(BlockPos pos, int width, int above, int below, boolean active) {
        ChannelOutput data = cityShieldChannel.openServer("CityShieldInput");
        BaseBlockUtils.writeBlockPos(data, pos);
        data.writeInt(width);
        data.writeInt(above);
        data.writeInt(below);
        data.writeBoolean(active);
        data.close();
    }

    @ServerMessageHandler("CityShieldInput")
    public void handleInputFromClient(EntityPlayer player, ChannelInput data) {
        CityShieldTE te = generatorFor(player, BaseBlockUtils.readBlockPos(data));
        int width = data.readInt();
        int above = data.readInt();
        int below = data.readInt();
        boolean active = data.readBoolean();
        if (te == null || !screenOpen(player, te)) {
            return;
        }
        te.setSize(width, above, below);
        if (active && !te.active && !te.canRaise()) {
            sendErrorMsg(player, "cityShieldNoPower");
        } else {
            te.setActive(active);
        }
        sendStatus(player, te, "CityShieldStatus");
    }

    // Whether the player has the generator's screen open. It's worked only from there, where it was opened with a click
    // on the generator, which claims and the like see: a client could otherwise drop any shield in reach, through walls,
    // without that.
    private static boolean screenOpen(EntityPlayer player, CityShieldTE te) {
        if (!(player.openContainer instanceof MalisisInventoryContainer)) {
            return false;
        }
        for (MalisisInventory inventory : ((MalisisInventoryContainer) player.openContainer).getInventories()) {
            if (inventory.getProvider() == te) {
                return true;
            }
        }
        return false;
    }
}
