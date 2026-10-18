package gcewing.sg.features.naquadahgenerator.network;

import gcewing.sg.BaseBlockUtils;
import gcewing.sg.BaseDataChannel;
import gcewing.sg.features.naquadahgenerator.NaquadahGeneratorTE;
import gcewing.sg.features.naquadahgenerator.client.gui.NaquadahGeneratorScreen;
import gcewing.sg.network.SGChannel;
import net.malisis.core.inventory.MalisisInventory;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

// A Naquadah generator's screen: opening it, keeping its power reading current, and switching the generator on and off
public class NaquadahGeneratorNetworkHandler extends SGChannel {

    protected static BaseDataChannel generatorChannel;

    // How far from the generator its player may be to work it
    private static final double maxReach = 8;

    public NaquadahGeneratorNetworkHandler(String name) {
        super(name);
        generatorChannel = this;
    }

    // The generator at pos, if the player is at it
    private static NaquadahGeneratorTE generatorFor(EntityPlayer player, BlockPos pos) {
        if (!player.world.isBlockLoaded(pos)
                || player.getDistanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > maxReach * maxReach) {
            return null;
        }
        TileEntity te = player.world.getTileEntity(pos);
        return te instanceof NaquadahGeneratorTE ? (NaquadahGeneratorTE) te : null;
    }

    // MalisisCore opens its screen, with its fuel slots, on the client (see NaquadahGeneratorTE.getGui)
    public static void openScreen(EntityPlayer player, BlockPos pos) {
        NaquadahGeneratorTE te = generatorFor(player, pos);
        if (te != null && player instanceof EntityPlayerMP) {
            MalisisInventory.open((EntityPlayerMP) player, te);
        }
    }

    private static void sendStatus(EntityPlayer player, NaquadahGeneratorTE te) {
        ChannelOutput data = generatorChannel.openPlayer(player, "GeneratorStatus");
        BaseBlockUtils.writeBlockPos(data, te.getPos());
        data.writeBoolean(te.switchedOn);
        data.writeBoolean(te.running);
        data.writeDouble(te.energy);
        data.writeDouble(te.mark().capacity);
        data.writeDouble(te.mark().energyPerTick);
        data.writeDouble(te.fuelLeft + te.fuelInSlots());
        data.close();
    }

    @ClientMessageHandler("GeneratorStatus")
    public void handleStatusFromServer(EntityPlayer player, ChannelInput data) {
        GeneratorStatus status = new GeneratorStatus();
        status.pos = BaseBlockUtils.readBlockPos(data);
        status.switchedOn = data.readBoolean();
        status.running = data.readBoolean();
        status.energy = data.readDouble();
        status.capacity = data.readDouble();
        status.energyPerTick = data.readDouble();
        status.fuelEnergy = data.readDouble();
        if (Minecraft.getMinecraft().currentScreen instanceof NaquadahGeneratorScreen) {
            ((NaquadahGeneratorScreen) Minecraft.getMinecraft().currentScreen).setStatus(status);
        }
    }

    // The screen asks about once a second
    public static void requestStatusFromServer(BlockPos pos) {
        ChannelOutput data = generatorChannel.openServer("GeneratorRequest");
        BaseBlockUtils.writeBlockPos(data, pos);
        data.close();
    }

    @ServerMessageHandler("GeneratorRequest")
    public void handleStatusRequestFromClient(EntityPlayer player, ChannelInput data) {
        NaquadahGeneratorTE te = generatorFor(player, BaseBlockUtils.readBlockPos(data));
        if (te != null) {
            sendStatus(player, te);
        }
    }

    public static void sendSwitchToServer(BlockPos pos, boolean on) {
        ChannelOutput data = generatorChannel.openServer("GeneratorSwitch");
        BaseBlockUtils.writeBlockPos(data, pos);
        data.writeBoolean(on);
        data.close();
    }

    @ServerMessageHandler("GeneratorSwitch")
    public void handleSwitchFromClient(EntityPlayer player, ChannelInput data) {
        NaquadahGeneratorTE te = generatorFor(player, BaseBlockUtils.readBlockPos(data));
        boolean on = data.readBoolean();
        if (te != null) {
            te.setSwitchedOn(on);
            sendStatus(player, te);
        }
    }
}
