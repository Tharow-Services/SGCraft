//------------------------------------------------------------------------------------------------
//
//   SG Craft - Players held still and safe through a ring transport
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.features.rings;

import gcewing.sg.features.obelisk.ObeliskNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// From the moment a platform's rings are in place round them (risen, or dropped from a ceiling) until the receiver's are
// home again, the players inside can turn their heads but not move, and take no damage of any kind (see RingTransports).
// Their clients stop their movement keys (RingHoldInput); the server keeps them where they are whatever else moves them.
public final class RingHolds {

    private static final class Hold {
        final int dimension;
        Vec3d at; // where they're held: where they stood, then where they arrived
        final long until; // world time they're let go

        Hold(int dimension, Vec3d at, long until) {
            this.dimension = dimension;
            this.at = at;
            this.until = until;
        }
    }

    // By player; read by damage events, which single-player also fires on the client's thread
    private static final Map<UUID, Hold> holds = new ConcurrentHashMap<>();

    private RingHolds() {}

    // Holds the players inside a platform's rings until the given world time
    static void hold(RingPlatformTE platform, long until) {
        for (EntityPlayerMP player : platform.getWorld().getEntitiesWithinAABB(EntityPlayerMP.class, platform.volume())) {
            if (holds.put(player.getUniqueID(), new Hold(player.dimension, player.getPositionVector(), until)) == null) {
                ObeliskNetworkHandler.ringHoldOnClient(player, true);
            }
        }
    }

    // A held player has been moved by the transport: they're held where they arrived
    static void moved(Entity e) {
        Hold hold = holds.get(e.getUniqueID());
        if (hold != null) {
            hold.at = e.getPositionVector();
        }
    }

    public static boolean isHeld(Entity e) {
        return e != null && !e.world.isRemote && holds.containsKey(e.getUniqueID());
    }

    private static void release(EntityPlayerMP player) {
        if (holds.remove(player.getUniqueID()) != null) {
            ObeliskNetworkHandler.ringHoldOnClient(player, false);
        }
    }

    // No damage of any kind: refused before it's dealt, and again should anything deal it past that
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(LivingAttackEvent event) {
        if (isHeld(event.getEntityLiving())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onHurt(LivingHurtEvent event) {
        if (isHeld(event.getEntityLiving())) {
            event.setCanceled(true);
        }
    }

    // Kept where they're held, facing however they like; let go once the transport is over, or if they've left the world
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        Hold hold = holds.get(player.getUniqueID());
        if (hold == null) {
            return;
        }
        if (player.world.getTotalWorldTime() >= hold.until || player.dimension != hold.dimension || player.isDead) {
            release(player);
            return;
        }
        player.motionX = player.motionY = player.motionZ = 0;
        player.fallDistance = 0;
        player.extinguish();
        if (player.getPositionVector().squareDistanceTo(hold.at) > 0.01) {
            player.connection.setPlayerLocation(hold.at.x, hold.at.y, hold.at.z, player.rotationYaw, player.rotationPitch);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        holds.remove(event.player.getUniqueID());
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            release((EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntityLiving() instanceof EntityPlayerMP) {
            release((EntityPlayerMP) event.getEntityLiving());
        }
    }
}
