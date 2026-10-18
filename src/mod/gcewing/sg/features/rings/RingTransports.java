package gcewing.sg.features.rings;

import static gcewing.sg.tileentity.SGBaseTE.sendBasicMsg;
import static gcewing.sg.tileentity.SGBaseTE.sendErrorMsg;

import gcewing.sg.BaseConfiguration;
import gcewing.sg.SGCraft;
import gcewing.sg.features.obelisk.ObeliskNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Goa'uld transportation rings (SG-1): a platform in the floor, or in a ceiling, linked to another in the same world.
// Started from a ring control panel near it or a ring remote, its five rings rise from the floor (or drop from the
// ceiling) round whoever stands inside, both platforms at once, and everything inside the one is swapped with everything
// inside the other. The platform that starts it pays in Forge Energy, more the further it sends.
public final class RingTransports {

    // Timed to the ring sound (ring_transport, 8.9 seconds): the rings rise and lock over its first 3.2 seconds (stackTicks),
    // the energy builds, the transport happens with its flash at 6.7 seconds (transportTick), and the rings are back as it
    // fades out (doneTicks)
    public static final int stackTicks = 64, transportTick = 134, doneTicks = 178;
    // How long before the transport those inside are told, for their screens to go white in time (the white-out fades
    // in over 0.7 seconds before the beam: see ObeliskBeamEffects)
    public static final int whiteOutTicks = 16;
    // How far (blocks) a panel or a remote reaches to find a platform, and how far below a ceiling platform the floor
    // can be
    public static final double controlReach = 8;
    public static final int maxDrop = 12;

    public static int energyPerUse = 20000;
    public static double energyPerBlock = 20;
    public static int energyCapacity = 200000;
    public static int maxDistance = 2000;
    // What a piece of naquadah in a platform's fuel slots gives it (Forge Energy)
    public static int energyPerNaquadah = 100000;

    public static SoundEvent transportSound;

    private RingTransports() {}

    public static void configure(BaseConfiguration cfg) {
        energyPerUse = Math.max(0, cfg.getInteger("rings", "energyPerUse", energyPerUse));
        energyPerBlock = Math.max(0, cfg.getDouble("rings", "energyPerBlock", energyPerBlock));
        energyCapacity = Math.max(1, cfg.getInteger("rings", "energyCapacity", energyCapacity));
        maxDistance = Math.max(0, cfg.getInteger("rings", "maxDistance", maxDistance));
        energyPerNaquadah = Math.max(0, cfg.getInteger("rings", "energyPerNaquadah", energyPerNaquadah));
    }

    public static void registerSounds(SGCraft mod) {
        transportSound = mod.newSound("ring_transport");
    }

    // What it costs (Forge Energy) to send from one platform to another
    public static int cost(BlockPos from, BlockPos to) {
        return (int) Math.min(Integer.MAX_VALUE, energyPerUse + energyPerBlock * Math.sqrt(from.distanceSq(to)));
    }

    // ---- Linking: as Asgard obelisks are linked ----

    // Where the platform each player started linking is (sneak-clicked), kept by place, not the platform itself, which
    // may be unloaded and loaded again before the second click
    private static final Map<UUID, BlockPos> linking = new HashMap<>();
    private static final Map<UUID, Integer> linkingWorld = new HashMap<>();

    public static void link(EntityPlayer player, RingPlatformTE platform) {
        if (!platform.mayUse(player)) {
            sendErrorMsg(player, "ringNoAccess");
            return;
        }
        UUID id = player.getUniqueID();
        BlockPos start = linking.remove(id);
        Integer startWorld = linkingWorld.remove(id);
        int dimension = platform.getWorld().provider.getDimension();
        if (start != null && start.equals(platform.getPos()) && startWorld == dimension) {
            sendBasicMsg(player, "ringLinkCancelled");
            return;
        }
        RingPlatformTE first = start != null && startWorld == dimension ? platformAt(platform.getWorld(), start) : null;
        if (start != null && startWorld != dimension) {
            sendErrorMsg(player, "ringLinkOtherWorld");
        }
        if (first == null) {
            linking.put(id, platform.getPos().toImmutable());
            linkingWorld.put(id, dimension);
            sendBasicMsg(player, "ringLinkStart");
            return;
        }
        if (maxDistance > 0 && first.getPos().distanceSq(platform.getPos()) > (double) maxDistance * maxDistance) {
            sendErrorMsg(player, "ringLinkTooFar", maxDistance);
            return;
        }
        unlink(first);
        unlink(platform);
        first.linkTo(platform.getPos());
        platform.linkTo(first.getPos());
        sendBasicMsg(player, "ringLinked");
    }

    // Unlinks a platform, and its partner from it
    public static void unlink(RingPlatformTE platform) {
        RingPlatformTE partner = platform.linked ? platformAt(platform.getWorld(), platform.linkPos) : null;
        if (partner != null && partner.linked && partner.linkPos.equals(platform.getPos())) {
            partner.unlink();
        }
        platform.unlink();
    }

    // The platform at pos (loading its chunk if need be), or null
    public static RingPlatformTE platformAt(World world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        return te instanceof RingPlatformTE ? (RingPlatformTE) te : null;
    }

    // ---- Starting: from a control panel or a ring remote ----

    // The platform a panel or remote at pos controls: the one whose rings the player is standing in, else the nearest
    // within reach
    public static RingPlatformTE platformNear(World world, Vec3d at, EntityPlayer player) {
        RingPlatformTE nearest = null;
        double best = controlReach * controlReach;
        for (TileEntity te : new ArrayList<>(world.loadedTileEntityList)) {
            if (te instanceof RingPlatformTE && !te.isInvalid()) {
                RingPlatformTE platform = (RingPlatformTE) te;
                if (player != null && platform.volume().contains(player.getPositionVector().add(0, 0.1, 0))) {
                    return platform;
                }
                double d = at.squareDistanceTo(platform.centre());
                if (d < best) {
                    best = d;
                    nearest = platform;
                }
            }
        }
        return nearest;
    }

    // Starts the rings of a platform and its partner, if they can go
    public static void activate(EntityPlayer player, RingPlatformTE platform) {
        if (platform == null) {
            sendErrorMsg(player, "ringNoPlatform");
            return;
        }
        if (platform.isActive()) {
            return; // already going
        }
        if (!platform.mayUse(player)) {
            sendErrorMsg(player, "ringNoAccess");
            return;
        }
        if (!platform.linked) {
            sendErrorMsg(player, "ringNotLinked");
            return;
        }
        RingPlatformTE partner = platformAt(platform.getWorld(), platform.linkPos);
        if (partner == null || !partner.linked || !partner.linkPos.equals(platform.getPos())) {
            unlink(platform);
            sendErrorMsg(player, "ringLinkBroken");
            return;
        }
        if (partner.isActive()) {
            sendErrorMsg(player, "ringPartnerBusy");
            return;
        }
        int cost = cost(platform.getPos(), partner.getPos());
        if (platform.energyAvailable() < cost) {
            sendErrorMsg(player, "ringNoPower", platform.energyAvailable(), cost);
            return;
        }
        platform.fuelUp(cost); // naquadah burnt for what it lacks
        platform.energy -= cost;
        long now = platform.getWorld().getTotalWorldTime();
        platform.start(now, true);
        partner.start(now, false);
    }

    // ---- The transport itself ----

    // The players inside a platform's rings see the world go white (see ObeliskBeamEffects)
    static void whiteOut(RingPlatformTE platform) {
        for (EntityPlayerMP player : platform.getWorld().getEntitiesWithinAABB(EntityPlayerMP.class, platform.volume())) {
            ObeliskNetworkHandler.whiteOutOnClient(player, whiteOutTicks);
        }
    }

    // The flash of the transport inside a platform's rings: white sparkles and sparks, as an obelisk's beam gives
    static void flash(RingPlatformTE platform) {
        if (platform.getWorld() instanceof WorldServer) {
            WorldServer ws = (WorldServer) platform.getWorld();
            Vec3d c = platform.floorCentre().add(0, 1.2, 0);
            ws.spawnParticle(EnumParticleTypes.END_ROD, c.x, c.y, c.z, 90, 1.1, 1.0, 1.1, 0.04);
            ws.spawnParticle(EnumParticleTypes.FIREWORKS_SPARK, c.x, c.y, c.z, 60, 1.0, 0.9, 1.0, 0.02);
        }
    }

    // Swaps everything inside one platform's rings with everything inside the other's, each keeping its place
    // relative to the rings, and the way it faces
    static void swap(RingPlatformTE a, RingPlatformTE b) {
        World world = a.getWorld();
        List<Entity> fromA = world.getEntitiesWithinAABB(Entity.class, a.volume(), e -> e != null && e.isEntityAlive() && !e.isRiding());
        List<Entity> fromB = world.getEntitiesWithinAABB(Entity.class, b.volume(), e -> e != null && e.isEntityAlive() && !e.isRiding());
        flash(a);
        flash(b);
        Vec3d offsetAB = b.floorCentre().subtract(a.floorCentre());
        for (Entity e : fromA) {
            move(e, offsetAB);
        }
        for (Entity e : fromB) {
            move(e, offsetAB.scale(-1));
        }
    }

    private static void move(Entity e, Vec3d by) {
        e.dismountRidingEntity();
        double x = e.posX + by.x, y = e.posY + by.y, z = e.posZ + by.z;
        if (e instanceof EntityPlayerMP) {
            ((EntityPlayerMP) e).connection.setPlayerLocation(x, y, z, e.rotationYaw, e.rotationPitch);
        } else {
            e.setPositionAndUpdate(x, y, z);
        }
        e.fallDistance = 0;
    }

    // The space the rings close round, from floorY up
    static AxisAlignedBB volume(BlockPos centre, double floorY) {
        return new AxisAlignedBB(centre.getX() - 1, floorY, centre.getZ() - 1, centre.getX() + 2, floorY + 2.6, centre.getZ() + 2);
    }
}
