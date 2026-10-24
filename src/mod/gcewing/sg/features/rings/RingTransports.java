package gcewing.sg.features.rings;

import static gcewing.sg.tileentity.SGBaseTE.sendBasicMsg;
import static gcewing.sg.tileentity.SGBaseTE.sendErrorMsg;

import gcewing.sg.BaseConfiguration;
import gcewing.sg.SGCraft;
import gcewing.sg.features.obelisk.ObeliskNetworkHandler;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SPacketCustomPayload;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
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

    // A transport runs in two halves, one after the other, each timed to the ring sound (ring_transport), which plays at
    // each in turn. The sender's rings rise and lock over 3.2 seconds (stackTicks); 0.25 seconds later a column of white
    // light snaps on and those inside go white (departTick); it holds, fades (lightFadeTick to lightEndTick) and the rings
    // drop, home by halfTicks. Then the receiver's rings do the same, and those in transit arrive in its light (arriveTick),
    // their screens clearing, before its rings drop too. Ticks are from the start of each half; the whole run is doneTicks.
    public static final int stackTicks = 64, departTick = stackTicks + 5, lightFadeTick = departTick + 28,
        lightEndTick = lightFadeTick + 12, halfTicks = lightEndTick + 32, doneTicks = 2 * halfTicks;
    // Those in transit are moved in the dark, as the receiver's rings set off (halfTicks), so they hear its sound from the
    // start; their screens clear in its light, once it has snapped on (arriveTick, see transitScreen). Those in the
    // receiver's rings, sent the other way, are held and flash white for returnFlashTicks before the swap.
    public static final int arriveTick = departTick + 2, returnFlashTicks = 14;
    // How far (blocks) a remote and a control panel reach to find a platform, and how far below a ceiling platform the
    // floor can be
    public static final double controlReach = 8, panelReach = 16;
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

    // Linking sends everyone who uses either platform somewhere new, so it's for those who may configure both, their
    // owners and admins: with only use asked of them, anyone could link a public platform to one of their own (in a
    // trap, say), then start their own end and pull everyone standing in the public rings into it
    public static void link(EntityPlayer player, RingPlatformTE platform) {
        if (!platform.mayConfigure(player)) {
            sendErrorMsg(player, "ringNoConfigure");
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
        if (!first.mayConfigure(player)) {
            sendErrorMsg(player, "ringNoConfigure"); // no longer theirs to link
            return;
        }
        // Not in the middle of a run: the far end would be swapped mid-way for one whose rings never rose
        if (first.isActive() || platform.isActive()) {
            sendErrorMsg(player, "ringPartnerBusy");
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
    // within the given reach
    public static RingPlatformTE platformNear(World world, Vec3d at, EntityPlayer player, double reach) {
        RingPlatformTE nearest = null;
        double best = reach * reach;
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
        partner.start(now + halfTicks, false); // its half once the sender's is over
        // Those inside are held still and safe from the moment its rings set off until the receiver's are home
        RingHolds.hold(platform, now + doneTicks);
    }

    // ---- The transport itself ----

    // What those leaving see, from the sender's light snapping on (departTick): the screen fades to white, holds, fades to
    // black and stays so in transit, fades back to white as they arrive in the receiver's light, and clears to show it
    static void transitScreen(RingPlatformTE platform) {
        int arrive = (halfTicks + arriveTick - departTick) * 50; // millis from now
        int[] at = {0, 700, 950, 1950, arrive - 800, arrive, arrive + 200, arrive + 1100};
        float[] cover = {0, 1, 1, 1, 1, 1, 1, 0};
        float[] light = {1, 1, 1, 0, 0, 1, 1, 1};
        screen(platform, at, cover, light);
    }

    // What those in the receiver's rings see as they're sent the other way, from returnFlashTicks before the swap: a white
    // flash, full as they go
    static void returnScreen(RingPlatformTE platform) {
        int go = returnFlashTicks * 50;
        screen(platform, new int[] {0, go, go + 200, go + 1100}, new float[] {0, 1, 1, 0}, new float[] {1, 1, 1, 1});
    }

    private static void screen(RingPlatformTE platform, int[] at, float[] cover, float[] light) {
        for (EntityPlayerMP player : platform.getWorld().getEntitiesWithinAABB(EntityPlayerMP.class, platform.volume())) {
            ObeliskNetworkHandler.screenFadeOnClient(player, at, cover, light);
        }
    }

    // Stops the ring sound for those within hearing of a platform, whose half is over as the other's starts: the sound
    // runs on past it. Sent as /stopsound sends it.
    static void stopSound(RingPlatformTE platform) {
        if (transportSound == null || transportSound.getRegistryName() == null) {
            return;
        }
        Vec3d c = platform.centre();
        for (EntityPlayerMP player : platform.getWorld().getPlayers(EntityPlayerMP.class, p -> p.getDistanceSq(c.x, c.y, c.z) < 64 * 64)) {
            PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
            buf.writeString(SoundCategory.BLOCKS.getName());
            buf.writeString(transportSound.getRegistryName().toString());
            player.connection.sendPacket(new SPacketCustomPayload("MC|StopSound", buf));
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
        // Done in the dark, as the receiver's rings set off: those going there sparkle as its light comes on (see
        // RingPlatformTE.update); anything sent back to the sender, whose rings are home, sparkles as it lands
        if (!fromB.isEmpty()) {
            flash(a);
        }
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
        RingHolds.moved(e); // a held player is held where they arrive
    }

    // The space the rings close round, from floorY up
    static AxisAlignedBB volume(BlockPos centre, double floorY) {
        return new AxisAlignedBB(centre.getX() - 1, floorY, centre.getZ() - 1, centre.getX() + 2, floorY + 2.6, centre.getZ() + 2);
    }
}
