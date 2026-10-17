package gcewing.sg.features.zat;

import gcewing.sg.BaseDataChannel;
import gcewing.sg.features.zat.client.ZatBolts;
import gcewing.sg.network.SGChannel;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.common.network.NetworkRegistry;

// Tells the clients near a zat being fired to draw its bolt
public class ZatNetworkHandler extends SGChannel {

    protected static BaseDataChannel zatChannel;

    // How far away a bolt can be seen
    private static final double boltViewRange = 96;

    public ZatNetworkHandler(String name) {
        super(name);
        zatChannel = this;
    }

    static void sendBolt(EntityPlayer shooter, boolean leftHand, Vec3d from, Vec3d to, boolean struck) {
        NetworkRegistry.TargetPoint point = new NetworkRegistry.TargetPoint(shooter.dimension, shooter.posX, shooter.posY,
            shooter.posZ, boltViewRange);
        ChannelOutput data = zatChannel.openAllAround(point, "ZatBolt");
        data.writeInt(shooter.getEntityId());
        data.writeBoolean(leftHand);
        data.writeDouble(from.x);
        data.writeDouble(from.y);
        data.writeDouble(from.z);
        data.writeDouble(to.x);
        data.writeDouble(to.y);
        data.writeDouble(to.z);
        data.writeBoolean(struck);
        data.close();
    }

    @ClientMessageHandler("ZatBolt")
    public void handleBolt(EntityPlayer player, ChannelInput data) {
        int shooterId = data.readInt();
        boolean leftHand = data.readBoolean();
        Vec3d from = new Vec3d(data.readDouble(), data.readDouble(), data.readDouble());
        Vec3d to = new Vec3d(data.readDouble(), data.readDouble(), data.readDouble());
        boolean struck = data.readBoolean();
        ZatBolts.add(shooterId, leftHand, from, to, struck);
    }
}
