package gcewing.sg.features.shield.network;

import static gcewing.sg.tileentity.SGBaseTE.sendErrorMsg;

import gcewing.sg.BaseBlockUtils;
import gcewing.sg.BaseDataChannel;
import gcewing.sg.SGCraft;
import gcewing.sg.features.shield.ShieldConsoleBlock;
import gcewing.sg.features.shield.client.gui.ShieldConsoleScreen;
import gcewing.sg.network.SGChannel;
import gcewing.sg.tileentity.SGBaseTE;
import gcewing.sg.util.GateUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class ShieldConsoleNetworkHandler extends SGChannel {

    protected static BaseDataChannel shieldConsoleChannel;

    // How far from a console its player may be to work it
    private static final double maxReach = 8;

    public ShieldConsoleNetworkHandler(String name) {
        super(name);
        shieldConsoleChannel = this;
    }

    // The nearest gate with a shield whose ring comes within shieldConsoleRange blocks of the console, or null
    public static SGBaseTE findShieldedGate(World world, BlockPos consolePos) {
        Vec3d at = new Vec3d(consolePos).add(0.5, 0.5, 0.5);
        SGBaseTE nearest = null;
        double nearestDistance = SGBaseTE.shieldConsoleRange;
        for (TileEntity te : world.loadedTileEntityList) {
            if (te instanceof SGBaseTE && !te.isInvalid()) {
                SGBaseTE gate = (SGBaseTE) te;
                if (gate.isMerged && gate.hasIrisUpgrade && gate.hasShield) {
                    double distance = GateUtil.distanceToGate(gate, at);
                    if (distance <= nearestDistance) {
                        nearest = gate;
                        nearestDistance = distance;
                    }
                }
            }
        }
        return nearest;
    }

    private static boolean isPermissionsAdmin(EntityPlayer player) {
        return SGCraft.hasPermissionSystem() && SGCraft.hasPermission(player, "sgcraft.admin");
    }

    // A player used the console at consolePos: show them its screen, with the gate it works, if any
    public static void openConsole(EntityPlayer player, BlockPos consolePos) {
        if (!SGCraft.hasPermission(player, "sgcraft.gui.shieldconsole") && !isPermissionsAdmin(player)) {
            sendErrorMsg(player, "shieldConsolePermission");
            return;
        }
        SGBaseTE gate = findShieldedGate(player.world, consolePos);
        ChannelOutput data = shieldConsoleChannel.openPlayer(player, "OpenShieldConsole");
        BaseBlockUtils.writeBlockPos(data, consolePos);
        data.writeInt(SGBaseTE.shieldConsoleRange);
        data.writeBoolean(gate != null);
        if (gate != null) {
            BaseBlockUtils.writeBlockPos(data, gate.getPos());
            data.writeBoolean(gate.allowAccessToIrisController(player) || isPermissionsAdmin(player));
        }
        data.close();
    }

    @ClientMessageHandler("OpenShieldConsole")
    public void handleOpenConsoleFromServer(EntityPlayer player, ChannelInput data) {
        BlockPos consolePos = BaseBlockUtils.readBlockPos(data);
        int range = data.readInt();
        BlockPos gatePos = null;
        boolean canControl = false;
        if (data.readBoolean()) {
            gatePos = BaseBlockUtils.readBlockPos(data);
            canControl = data.readBoolean();
        }
        new ShieldConsoleScreen(player, consolePos, gatePos, canControl, range).display();
    }

    // Raise (true) or lower the shield of the gate the console at consolePos works
    public static void sendShieldInputToServer(BlockPos consolePos, BlockPos gatePos, boolean raise) {
        ChannelOutput data = shieldConsoleChannel.openServer("ShieldConsoleInput");
        BaseBlockUtils.writeBlockPos(data, consolePos);
        BaseBlockUtils.writeBlockPos(data, gatePos);
        data.writeBoolean(raise);
        data.close();
    }

    @ServerMessageHandler("ShieldConsoleInput")
    public void handleShieldInputFromClient(EntityPlayer player, ChannelInput data) {
        BlockPos consolePos = BaseBlockUtils.readBlockPos(data);
        BlockPos gatePos = BaseBlockUtils.readBlockPos(data);
        boolean raise = data.readBoolean();
        World world = player.world;

        // A client can send any positions: the player must be at a real console, and the gate one it works
        if (!world.isBlockLoaded(consolePos) || !(world.getBlockState(consolePos).getBlock() instanceof ShieldConsoleBlock)
                || player.getDistanceSq(consolePos.getX() + 0.5, consolePos.getY() + 0.5, consolePos.getZ() + 0.5) > maxReach * maxReach) {
            return;
        }
        if (!SGCraft.hasPermission(player, "sgcraft.gui.shieldconsole") && !isPermissionsAdmin(player)) {
            return;
        }
        SGBaseTE gate = world.isBlockLoaded(gatePos) ? SGBaseTE.at(world, gatePos) : null;
        if (gate == null || !gate.isMerged || !gate.hasShield
                || GateUtil.distanceToGate(gate, new Vec3d(consolePos).add(0.5, 0.5, 0.5)) > SGBaseTE.shieldConsoleRange) {
            return;
        }
        if (!gate.allowAccessToIrisController(player) && !isPermissionsAdmin(player)) {
            sendErrorMsg(player, "insufficientPlayerAccessPermission");
            return;
        }

        if (raise) {
            if (!gate.requiresNoPower && !gate.energyIsAvailable(SGBaseTE.shieldEnergyPerTick)) {
                sendErrorMsg(player, "shieldNoPower");
                return;
            }
            gate.closeIris();
        } else {
            gate.openIris();
        }
    }
}
