package gcewing.sg.network;

import gcewing.sg.BaseBlockUtils;
import gcewing.sg.BaseDataChannel;
import gcewing.sg.SGCraft;
import gcewing.sg.client.gui.SGBaseScreen;
import gcewing.sg.tileentity.SGBaseTE;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;

// A supergate's ZPMs and what it costs to run, for its screen (see SGBaseScreen). The client can't see the ZPMs'
// energy itself, so asks the server about once a second while the screen is open.
public class SupergatePowerNetworkHandler extends SGChannel {

    protected static BaseDataChannel powerChannel;

    // How far from the gate's base its player may be to ask
    private static final double maxReach = 16;

    public SupergatePowerNetworkHandler(String name) {
        super(name);
        powerChannel = this;
    }

    public static void requestPowerFromServer(BlockPos pos) {
        ChannelOutput data = powerChannel.openServer("SupergatePowerRequest");
        BaseBlockUtils.writeBlockPos(data, pos);
        data.close();
    }

    @ServerMessageHandler("SupergatePowerRequest")
    public void handlePowerRequestFromClient(EntityPlayer player, ChannelInput data) {
        BlockPos pos = BaseBlockUtils.readBlockPos(data);
        if (!player.world.isBlockLoaded(pos)
                || player.getDistanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > maxReach * maxReach) {
            return;
        }
        SGBaseTE te = SGBaseTE.at(player.world, pos);
        if (te == null || !te.isSupergate()) {
            return;
        }
        ChannelOutput reply = powerChannel.openPlayer(player, "SupergatePower");
        BaseBlockUtils.writeBlockPos(reply, pos);
        reply.writeInt(te.zpmCount());
        reply.writeInt(SGBaseTE.supergateZpmsNeeded);
        // In ZPM units, as ZPMs' tooltips and the ZPM console show them (a full ZPM holds 2,147,483,647)
        double unit = SGCraft.ZPMEnergyPerSGEnergyUnit;
        reply.writeDouble(te.zpmEnergy() * unit);
        reply.writeDouble(te.nearbyOpenCost() * unit);
        reply.writeDouble(te.nearbyTickCost() * unit);
        reply.writeBoolean(te.requiresNoPower);
        reply.close();
    }

    @ClientMessageHandler("SupergatePower")
    public void handlePowerFromServer(EntityPlayer player, ChannelInput data) {
        BlockPos pos = BaseBlockUtils.readBlockPos(data);
        int zpms = data.readInt();
        int needed = data.readInt();
        double energy = data.readDouble();
        double openCost = data.readDouble();
        double tickCost = data.readDouble();
        boolean noPower = data.readBoolean();
        if (Minecraft.getMinecraft().currentScreen instanceof SGBaseScreen) {
            ((SGBaseScreen) Minecraft.getMinecraft().currentScreen).setSupergatePower(pos, zpms, needed, energy, openCost, tickCost, noPower);
        }
    }
}
