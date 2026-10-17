package gcewing.sg.features.obelisk;

import static gcewing.sg.tileentity.SGBaseTE.sendBasicMsg;
import static gcewing.sg.tileentity.SGBaseTE.sendErrorMsg;

import gcewing.sg.SGCraft;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.ITeleporter;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

// Linking Asgard obelisks in pairs, and beaming players between them
public final class AsgardObelisks {

    public static SoundEvent beamSound;

    // The obelisk each player has started linking from (sneak-clicked), to link with the next one they sneak-click
    private static final Map<UUID, AsgardObeliskTE> linking = new HashMap<>();
    // When each player last beamed, so arriving next to an obelisk with the button still held doesn't send them back
    private static final Map<UUID, Long> lastBeam = new HashMap<>();
    private static final long cooldownMillis = 1500;
    // How close (blocks) a player must be to touch an obelisk's gem, and how far out from it they arrive
    private static final double activateReach = 4;
    private static final int arriveDistance = 2;

    private AsgardObelisks() {}

    // ---- Linking ----

    // The first obelisk a player sneak-clicks is remembered; sneak-clicking a second links the two, each to the other
    public static void link(EntityPlayer player, AsgardObeliskTE obelisk) {
        if (!obelisk.mayUse(player)) {
            sendErrorMsg(player, "obeliskNoAccess");
            return;
        }
        AsgardObeliskTE first = linking.remove(player.getUniqueID());
        if (first == obelisk) {
            sendBasicMsg(player, "obeliskLinkCancelled");
        } else if (first != null && !first.isInvalid() && first.getWorld() != null && first.getBlockType() != obelisk.getBlockType()) {
            linking.put(player.getUniqueID(), first); // still linking from the first
            sendErrorMsg(player, "obeliskWrongType");
        } else if (first != null && !first.isInvalid() && first.getWorld() != null) {
            unlinkPartner(first);
            unlinkPartner(obelisk);
            first.linkTo(obelisk.getWorld().provider.getDimension(), obelisk.getPos());
            obelisk.linkTo(first.getWorld().provider.getDimension(), first.getPos());
            sendBasicMsg(player, "obeliskLinked");
        } else {
            linking.put(player.getUniqueID(), obelisk);
            sendBasicMsg(player, "obeliskLinkStart");
        }
    }

    // An obelisk linked anew leaves its old partner unlinked, so pairs stay pairs; also unlinks the two, from its screen
    public static void unlinkPartner(AsgardObeliskTE obelisk) {
        AsgardObeliskTE partner = partnerOf(obelisk);
        if (partner != null && partner.linkPos.equals(obelisk.getPos())
                && partner.linkDimension == obelisk.getWorld().provider.getDimension()) {
            partner.unlink();
        }
        obelisk.unlink();
    }

    // The obelisk this one is linked to, loading its world if need be, or null if it isn't there any more
    private static AsgardObeliskTE partnerOf(AsgardObeliskTE obelisk) {
        if (!obelisk.linked) {
            return null;
        }
        WorldServer world = DimensionManager.getWorld(obelisk.linkDimension);
        if (world == null && DimensionManager.isDimensionRegistered(obelisk.linkDimension)) {
            DimensionManager.initDimension(obelisk.linkDimension);
            world = DimensionManager.getWorld(obelisk.linkDimension);
        }
        if (world == null) {
            return null;
        }
        TileEntity te = world.getTileEntity(obelisk.linkPos);
        return te instanceof AsgardObeliskTE ? (AsgardObeliskTE) te : null;
    }

    // The name of a world, as players know it: under Sponge each has its own; otherwise the dimension's name
    public static String worldName(int dimension) {
        WorldServer world = DimensionManager.getWorld(dimension);
        WorldServer overworld = DimensionManager.getWorld(0);
        if (world != null && (dimension == 0 || overworld == null
                || !world.getWorldInfo().getWorldName().equals(overworld.getWorldInfo().getWorldName()))) {
            return world.getWorldInfo().getWorldName();
        }
        return DimensionManager.isDimensionRegistered(dimension) ? DimensionManager.getProviderType(dimension).getName() : "dimension " + dimension;
    }

    // ---- Beaming ----

    public static void transport(EntityPlayer player, AsgardObeliskTE from) {
        if (!(player instanceof EntityPlayerMP)) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastBeam.get(player.getUniqueID());
        if (last != null && now - last < cooldownMillis) {
            return;
        }
        if (!from.mayUse(player)) {
            sendErrorMsg(player, "obeliskNoAccess");
            return;
        }
        BlockPos at = from.getPos();
        if (player.getDistanceSq(at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5) > activateReach * activateReach) {
            sendErrorMsg(player, "obeliskTooFar", (int) activateReach);
            return;
        }
        if (!from.linked) {
            sendErrorMsg(player, "obeliskNotLinked");
            return;
        }
        AsgardObeliskTE to = partnerOf(from);
        if (to == null) {
            from.unlink();
            sendErrorMsg(player, "obeliskLinkBroken");
            return;
        }
        if (beaming.containsKey(player.getUniqueID())) {
            return; // already on its way
        }

        // The sound starts: the player hears it on their own client, carried with them through the beam, with the screen
        // going white (see ObeliskBeamEffects); others hear it from the obelisk. The beam comes at beamDelayTicks.
        beaming.put(player.getUniqueID(), new Beam((EntityPlayerMP) player, from));
        if (beamSound != null) {
            player.world.playSound(player, from.getPos().getX() + 0.5, from.getPos().getY() + 1.5, from.getPos().getZ() + 0.5,
                beamSound, SoundCategory.BLOCKS, 1F, 1F);
        }
        ObeliskNetworkHandler.startBeamOnClient((EntityPlayerMP) player);
        from.startBeam(); // its gem shines as the beam nears
    }

    // ---- The beam itself, 2.5 seconds after the player touched the crystal ----

    public static final int beamDelayTicks = 50;

    private static class Beam {
        final EntityPlayerMP player;
        final AsgardObeliskTE from;
        // Where the player stood when they touched the gem; they must stay there (they can turn and look about)
        final double x, y, z;
        int ticks = 0;

        Beam(EntityPlayerMP player, AsgardObeliskTE from) {
            this.player = player;
            this.from = from;
            this.x = player.posX;
            this.y = player.posY;
            this.z = player.posZ;
        }
    }

    // How far (squared, blocks) a player may drift from where they stood before the beam is called off: barely at all
    private static final double maxDriftSq = 0.05 * 0.05;

    // Players about to be beamed
    private static final Map<UUID, Beam> beaming = new HashMap<>();

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || beaming.isEmpty()) {
            return;
        }
        Iterator<Beam> beams = beaming.values().iterator();
        while (beams.hasNext()) {
            Beam beam = beams.next();
            if (beam.player.isDead || beam.player.hasDisconnected()) {
                beams.remove();
                beam.from.stopBeam();
            } else if (beam.player.getDistanceSq(beam.x, beam.y, beam.z) > maxDriftSq) {
                // They moved off the spot: the beam is called off
                beams.remove();
                beam.from.stopBeam();
                ObeliskNetworkHandler.cancelBeamOnClient(beam.player);
                sendErrorMsg(beam.player, "obeliskBeamCancelled");
            } else if (++beam.ticks >= beamDelayTicks) {
                beams.remove();
                beam(beam.player, beam.from);
            }
        }
    }

    private static void beam(EntityPlayerMP player, AsgardObeliskTE from) {
        AsgardObeliskTE to = partnerOf(from);
        if (to == null) {
            from.unlink();
            sendErrorMsg(player, "obeliskLinkBroken");
            return;
        }
        lastBeam.put(player.getUniqueID(), System.currentTimeMillis());

        // Out in front of the far obelisk's gem, facing it
        World destination = to.getWorld();
        IBlockState state = destination.getBlockState(to.getPos());
        EnumFacing facing = state.getBlock() instanceof AsgardObeliskBlock ? state.getValue(AsgardObeliskBlock.FACING) : EnumFacing.NORTH;
        BlockPos arrive = to.getPos().offset(facing, arriveDistance);
        double x = arrive.getX() + 0.5, y = arrive.getY(), z = arrive.getZ() + 0.5;
        float yaw = facing.getOpposite().getHorizontalAngle();

        flash(player.world, player.posX, player.posY, player.posZ);
        player.dismountRidingEntity();
        EntityPlayerMP mp = (EntityPlayerMP) player;
        if (destination == player.world) {
            mp.connection.setPlayerLocation(x, y, z, yaw, player.rotationPitch);
        } else {
            mp.changeDimension(destination.provider.getDimension(), new ITeleporter() {
                @Override
                public void placeEntity(World world, Entity entity, float entityYaw) {
                    entity.setLocationAndAngles(x, y, z, yaw, entity.rotationPitch);
                    entity.motionX = entity.motionY = entity.motionZ = 0;
                }
            });
            mp.connection.setPlayerLocation(x, y, z, yaw, player.rotationPitch);
        }
        player.fallDistance = 0;
        flash(destination, x, y, z);
    }

    // The white flash of an Asgard beam, where it leaves and arrives
    private static void flash(World world, double x, double y, double z) {
        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            ws.spawnParticle(EnumParticleTypes.END_ROD, x, y + 1, z, 40, 0.3, 0.9, 0.3, 0.04);
            ws.spawnParticle(EnumParticleTypes.FIREWORKS_SPARK, x, y + 1, z, 25, 0.25, 0.8, 0.25, 0.02);
        }
    }
}
