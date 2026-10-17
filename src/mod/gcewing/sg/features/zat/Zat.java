package gcewing.sg.features.zat;

import gcewing.sg.BaseConfiguration;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EntitySelectors;
import net.minecraft.util.EnumHandSide;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

// The Goa'uld zat'nik'tel: fires a thin bolt of lightning where its holder is looking. As in the show, one shot stuns,
// and a second while the target is still stunned kills.
public class Zat {

    public static SoundEvent fireSound;

    public static double range = 30; // blocks
    public static int cooldownTicks = 20;
    public static int stunTicks = 200; // how long a shot stuns for, and so how long a second shot kills in
    public static float stunDamage = 4;
    public static boolean secondShotKills = true;

    public static void configure(BaseConfiguration cfg) {
        range = Math.max(1, cfg.getDouble("zat", "range", range));
        cooldownTicks = Math.max(1, cfg.getInteger("zat", "cooldownTicks", cooldownTicks));
        stunTicks = Math.max(1, cfg.getInteger("zat", "stunTicks", stunTicks));
        stunDamage = (float) Math.max(0, cfg.getDouble("zat", "stunDamage", stunDamage));
        secondShotKills = cfg.getBoolean("zat", "secondShotKills", secondShotKills);
    }

    // When the entity was last stunned by a zat (world time), kept with the entity
    private static final String stunnedAtKey = "sgcraft:zatStunnedAt";

    // ---- Firing (server) ----

    // The zat's sound starts with it charging, so the shot comes this long (ticks) after the trigger is pulled
    public static int chargeTicks = 15;

    private static final class Shot {
        final EntityPlayer player;
        final EnumHand hand;
        final World world;
        final long firesAt;

        Shot(EntityPlayer player, EnumHand hand) {
            this.player = player;
            this.hand = hand;
            this.world = player.world;
            this.firesAt = world.getTotalWorldTime() + chargeTicks;
        }
    }

    private static final List<Shot> charging = new ArrayList<>();

    // The trigger is pulled: it charges, then fires where its holder is looking by then
    public static void pullTrigger(EntityPlayer player, EnumHand hand) {
        player.world.playSound(null, player.posX, player.posY, player.posZ, fireSound, SoundCategory.PLAYERS, 1F, 1F);
        charging.add(new Shot(player, hand));
    }

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote || charging.isEmpty()) {
            return;
        }
        for (Iterator<Shot> it = charging.iterator(); it.hasNext(); ) {
            Shot shot = it.next();
            if (shot.player.world != shot.world || shot.player.isDead) {
                it.remove(); // gone, or died, before it fired
            } else if (shot.world == event.world && event.world.getTotalWorldTime() >= shot.firesAt) {
                it.remove();
                fire(shot.player, shot.hand);
            }
        }
    }

    static void fire(EntityPlayer player, EnumHand hand) {
        World world = player.world;
        Vec3d eye = new Vec3d(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        Vec3d look = player.getLookVec();
        Vec3d end = eye.add(look.scale(range));

        // Stops at the first block in the way...
        RayTraceResult blockHit = world.rayTraceBlocks(eye, end, false, true, false);
        if (blockHit != null && blockHit.hitVec != null) {
            end = blockHit.hitVec;
        }
        // ...or the first thing it hits before then
        Entity target = null;
        double nearest = eye.distanceTo(end);
        AxisAlignedBB reach = player.getEntityBoundingBox().expand(end.x - eye.x, end.y - eye.y, end.z - eye.z).grow(1);
        List<Entity> candidates = world.getEntitiesInAABBexcluding(player, reach,
            e -> e != null && e.canBeCollidedWith() && EntitySelectors.NOT_SPECTATING.apply(e) && e != player.getRidingEntity());
        for (Entity e : candidates) {
            AxisAlignedBB box = e.getEntityBoundingBox().grow(e.getCollisionBorderSize());
            RayTraceResult hit = box.calculateIntercept(eye, end);
            double d = box.contains(eye) ? 0 : hit != null ? eye.distanceTo(hit.hitVec) : -1;
            if (d >= 0 && d < nearest) {
                nearest = d;
                target = e;
                end = box.contains(eye) ? eye : hit.hitVec;
            }
        }

        boolean leftHand = isLeftHand(player, hand);
        ZatNetworkHandler.sendBolt(player, leftHand, muzzle(eye, look, leftHand), end, target != null || blockHit != null);
        if (target instanceof EntityLivingBase) {
            hit(player, (EntityLivingBase) target);
        }
    }

    // Whether the zat is in the player's left hand (not using EnumHandSide.opposite, which servers don't have)
    static boolean isLeftHand(EntityPlayer player, EnumHand hand) {
        return (player.getPrimaryHand() == EnumHandSide.LEFT) == (hand == EnumHand.MAIN_HAND);
    }

    // Roughly where the bolt comes from, a little ahead of and below the eyes: clients that can see the shooter start it
    // from the zat's tip as they draw it instead (see ZatBolts)
    static Vec3d muzzle(Vec3d eye, Vec3d look, boolean leftHand) {
        Vec3d right = new Vec3d(-look.z, 0, look.x);
        right = right.lengthSquared() < 1e-6 ? Vec3d.ZERO : right.normalize();
        if (leftHand) {
            right = right.scale(-1);
        }
        return eye.add(look.scale(0.7)).add(right.scale(0.3)).add(0, -0.25, 0);
    }

    // A first shot stuns, a second while still stunned kills
    static void hit(EntityPlayer shooter, EntityLivingBase target) {
        long now = target.world.getTotalWorldTime();
        NBTTagCompound data = target.getEntityData();
        boolean stunned = data.hasKey(stunnedAtKey) && now - data.getLong(stunnedAtKey) <= stunTicks;
        DamageSource source = new EntityDamageSource("sgcraft:zat", shooter).setDamageBypassesArmor();
        if (stunned && secondShotKills) {
            if (target.attackEntityFrom(source, Math.max(1000F, target.getMaxHealth() * 10))) {
                data.removeTag(stunnedAtKey);
            }
            return;
        }
        // Only stun what it may hurt here (not creative players, or players where there's no PvP)
        if (!target.attackEntityFrom(source, Math.max(0.5F, stunDamage))) {
            return;
        }
        data.setLong(stunnedAtKey, now);
        target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, stunTicks, 5, false, false));
        target.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, stunTicks, 2, false, false));
        target.addPotionEffect(new PotionEffect(MobEffects.MINING_FATIGUE, stunTicks, 2, false, false));
        target.addPotionEffect(new PotionEffect(MobEffects.NAUSEA, Math.min(stunTicks, 100), 0, false, false));
    }
}
