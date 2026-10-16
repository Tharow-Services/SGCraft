package gcewing.sg.features.cityshield;

import gcewing.sg.BaseConfiguration;
import gcewing.sg.BaseTileEntity;
import gcewing.sg.SGCraft;
import gcewing.sg.features.zpm.ZpmAddon;
import gcewing.sg.features.zpm.ZpmSlotSource;
import gcewing.sg.features.cityshield.client.gui.CityShieldScreen;
import gcewing.sg.features.cityshield.network.CityShieldStatus;
import net.malisis.core.client.gui.MalisisGui;
import net.malisis.core.inventory.IInventoryProvider.IDirectInventoryProvider;
import net.malisis.core.inventory.MalisisInventory;
import net.malisis.core.inventory.MalisisInventoryContainer;
import net.minecraft.client.Minecraft;
import gcewing.sg.interfaces.ISGEnergySource;
import gcewing.sg.tileentity.SGBaseTE;
import net.minecraft.entity.Entity;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

// The City Shield Generator: raises an energy shield in the shape of an ellipsoid round itself, as over Atlantis.
// Nothing gets in while it's up (things already inside can leave), and it runs on ZPMs, taking more the bigger it is.
public class CityShieldTE extends BaseTileEntity implements ITickable, IDirectInventoryProvider {

    // ---- Settings (sgcraft.cfg, "city-shield") ----

    // ZPM energy (in SG energy units) each million blocks of the shield's volume take each tick. At 0.333, three full
    // ZPMs run a shield 128 blocks round, 128 above and 8 below (about 4.67 million blocks) for about 30 days.
    public static double energyPerMillionBlocksPerTick = 0.333;
    // The same, for each block
    public static double energyPerBlockPerTick = energyPerMillionBlocksPerTick / 1e6;
    // The largest and smallest the shield can reach from the generator in each direction
    public static int maxRadius = 128;
    public static final int MIN_RADIUS = 3;

    public static void configure(BaseConfiguration cfg) {
        energyPerMillionBlocksPerTick = cfg.getDouble("city-shield", "energyPerMillionBlocksPerTick", energyPerMillionBlocksPerTick);
        energyPerBlockPerTick = energyPerMillionBlocksPerTick / 1e6;
        maxRadius = Math.max(MIN_RADIUS, cfg.getInteger("city-shield", "maxRadius", maxRadius));
    }

    // ---- State ----

    public boolean active;
    // How far the shield reaches from the generator: all round it (it's round from above), up, and down
    public int width = 32, above = 24, below = 8;
    // Energy drawn from the ZPMs ahead of need, so they are drawn on about once a second rather than every tick
    private double energyBuffer;
    // How much energy the shield needs to have on hand before it can be raised, in ticks of running it
    private static final int raiseTicks = 200;
    // The ZPMs it draws on, found again every so often (they can be added, emptied or taken away)
    // ZPMs nearby (in consoles, and with IC2 carts and hubs), found again every so often (they can be added, emptied or
    // taken away); those in its own slots are counted afresh each time
    private List<ISGEnergySource> zpmSources = new ArrayList<>();
    private int zpmScanTimer = 0;
    private static final int zpmScanInterval = 40;
    // Entities that move further than this in a tick were teleported (through a gate, say), not walking in
    private static final double maxStepSq = 10 * 10;

    // On the client: how far the shield has spread, 0 to 1, last tick and this (see CityShieldRenderer)
    public float fade, lastFade;
    // It takes at least 5 seconds to form: that for any up to 128 blocks tall, longer for a taller one
    private static final int fullHeight = 128, spreadTicksAtFullHeight = 100;

    public int spreadTicks() {
        return Math.max(spreadTicksAtFullHeight, spreadTicksAtFullHeight * Math.max(above, below) / fullHeight);
    }

    // ---- Shape ----

    public double centreX() { return pos.getX() + 0.5; }
    public double centreY() { return pos.getY() + 0.5; }
    public double centreZ() { return pos.getZ() + 0.5; }

    public boolean contains(double x, double y, double z) {
        double dy = y - centreY();
        dy /= dy >= 0 ? above : below;
        double dx = (x - centreX()) / width, dz = (z - centreZ()) / width;
        return dx * dx + dy * dy + dz * dz <= 1;
    }

    public boolean contains(Vec3d p) {
        return contains(p.x, p.y, p.z);
    }

    public AxisAlignedBB bounds() {
        return new AxisAlignedBB(centreX() - width, centreY() - below, centreZ() - width,
            centreX() + width, centreY() + above, centreZ() + width);
    }

    // Blocks inside the shield: half an ellipsoid above the generator and another below
    public static double volume(int width, int above, int below) {
        return 2.0 / 3.0 * Math.PI * width * width * (above + below);
    }

    public static double energyPerTick(int width, int above, int below) {
        return volume(width, above, below) * energyPerBlockPerTick;
    }

    public double energyPerTick() {
        return energyPerTick(width, above, below);
    }

    public static int clampRadius(int r) {
        return Math.max(MIN_RADIUS, Math.min(maxRadius, r));
    }

    // ---- Ticking ----

    @Override
    public void update() {
        if (world.isRemote) {
            lastFade = fade;
            float fadeStep = 1F / spreadTicks();
            fade = active ? Math.min(1, fade + fadeStep) : Math.max(0, fade - fadeStep);
            return;
        }
        if (!active) {
            return;
        }
        if (!useEnergy(energyPerTick())) {
            setActive(false); // the ZPMs ran dry
            return;
        }
        repelEntities();
    }

    // Anything that crossed into the shield since last tick is sent back where it came from; projectiles are destroyed
    private void repelEntities() {
        for (Entity entity : world.getEntitiesWithinAABB(Entity.class, bounds().grow(2))) {
            if (entity.isRiding() || entity.isDead) {
                continue; // a rider goes with its mount
            }
            double half = entity.height / 2;
            Vec3d now = new Vec3d(entity.posX, entity.posY + half, entity.posZ);
            Vec3d before = new Vec3d(entity.lastTickPosX, entity.lastTickPosY + half, entity.lastTickPosZ);
            if (!contains(now) || contains(before) || now.squareDistanceTo(before) > maxStepSq) {
                continue;
            }
            if (entity instanceof IProjectile || entity instanceof EntityFireball) {
                entity.setDead();
            } else if (entity instanceof EntityPlayerMP) {
                ((EntityPlayerMP) entity).connection.setPlayerLocation(entity.lastTickPosX, entity.lastTickPosY, entity.lastTickPosZ,
                    entity.rotationYaw, entity.rotationPitch);
                bounce(entity);
            } else {
                entity.setPositionAndUpdate(entity.lastTickPosX, entity.lastTickPosY, entity.lastTickPosZ);
                bounce(entity);
            }
        }
    }

    private static void bounce(Entity entity) {
        entity.motionX *= -0.5;
        entity.motionY = Math.min(entity.motionY, 0) * -0.5;
        entity.motionZ *= -0.5;
        entity.fallDistance = 0;
        entity.velocityChanged = true;
    }

    // An explosion outside the shield breaks nothing and hurts no one inside it
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        World world = event.getWorld();
        if (world.isRemote) {
            return;
        }
        Vec3d at = event.getExplosion().getPosition();
        for (TileEntity te : world.loadedTileEntityList) {
            if (te instanceof CityShieldTE && ((CityShieldTE) te).active && !te.isInvalid()) {
                CityShieldTE shield = (CityShieldTE) te;
                if (!shield.contains(at)) {
                    event.getAffectedBlocks().removeIf(b -> shield.contains(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5));
                    event.getAffectedEntities().removeIf(e -> shield.contains(e.posX, e.posY + e.height / 2, e.posZ));
                }
            }
        }
    }

    // ---- Energy ----

    private List<ISGEnergySource> zpmSources() {
        if (zpmScanTimer <= 0) {
            zpmSources = ZpmAddon.findZpmSources(world, pos, SGCraft.zpmSearchRange);
            zpmScanTimer = zpmScanInterval;
        }
        zpmScanTimer--;
        List<ISGEnergySource> sources = new ArrayList<>();
        for (int i = 0; i < numZpmSlots; i++) {
            ZpmSlotSource source = new ZpmSlotSource(this, i);
            if (!source.isEmpty()) {
                sources.add(source);
            }
        }
        sources.addAll(zpmSources);
        return sources;
    }

    // ---- ZPM slots ----

    // The generator holds three ZPMs of its own, as well as drawing on any nearby. They're a MalisisCore inventory, shown
    // in its screen; its slots take only ZPMs (see ZpmMalisisSlot), from players or hoppers alike.
    public static final int numZpmSlots = 3;
    private final MalisisInventory inventory = new MalisisInventory(this, ZpmMalisisSlot::new, numZpmSlots);

    {
        inventory.setName("tile.sgcraft:city_shield_generator.name");
    }

    @Override
    public MalisisInventory getInventory() {
        return inventory;
    }

    // Its screen, opened with its ZPM slots (see CityShieldNetworkHandler.openScreen). The server sends its power once
    // the screen asks; until then it shows what the client knows.
    @SideOnly(Side.CLIENT)
    @Override
    public MalisisGui getGui(MalisisInventoryContainer container) {
        CityShieldStatus status = new CityShieldStatus();
        status.pos = pos;
        status.active = active;
        status.width = width;
        status.above = above;
        status.below = below;
        status.maxRadius = maxRadius;
        status.energyPerBlockPerTick = energyPerBlockPerTick * SGCraft.ZPMEnergyPerSGEnergyUnit; // in ZPM units
        return new CityShieldScreen(Minecraft.getMinecraft().player, status, container, inventory);
    }

    // ZPM energy the generator can reach, counting what it has drawn already
    public double availableEnergy() {
        double total = energyBuffer;
        for (ISGEnergySource source : zpmSources()) {
            total += source.availableEnergy();
        }
        return total;
    }

    private boolean useEnergy(double amount) {
        if (energyBuffer < amount) {
            // About a second's worth at a time, in equal shares from all its ZPMs so they run down together
            energyBuffer += ZpmAddon.drawEvenly(zpmSources(), amount * 20 - energyBuffer);
        }
        if (energyBuffer < amount) {
            return false;
        }
        energyBuffer -= amount;
        return true;
    }

    // ---- Control (server) ----

    // Whether the ZPMs can raise the shield at its size: they must hold enough to run it for raiseTicks
    public boolean canRaise() {
        zpmScanTimer = 0; // look again, the player may just have put a ZPM in
        return availableEnergy() >= energyPerTick() * raiseTicks;
    }

    public void setActive(boolean on) {
        if (active != on) {
            active = on;
            SoundEvent sound = on ? SGBaseTE.shieldActivationSound : SGBaseTE.shieldDeactivationSound;
            world.playSound(null, pos, sound, SoundCategory.BLOCKS, 4F, 1F); // heard 64 blocks away
            markChanged();
        }
    }

    public void setSize(int width, int above, int below) {
        this.width = clampRadius(width);
        this.above = clampRadius(above);
        this.below = clampRadius(below);
        markChanged();
    }

    // ---- Saving and syncing ----

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        active = nbt.getBoolean("active");
        inventory.readFromNBT(nbt);
        if (nbt.hasKey("width")) {
            width = clampRadius(nbt.getInteger("width"));
            if (nbt.hasKey("above")) {
                above = clampRadius(nbt.getInteger("above"));
                below = clampRadius(nbt.getInteger("below"));
            } else { // saved when it had one height, up and down
                above = below = clampRadius(nbt.getInteger("height"));
            }
        }
        energyBuffer = nbt.getDouble("energyBuffer");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setBoolean("active", active);
        inventory.writeToNBT(nbt);
        nbt.setInteger("width", width);
        nbt.setInteger("above", above);
        nbt.setInteger("below", below);
        nbt.setDouble("energyBuffer", energyBuffer);
        return nbt;
    }

    // ---- Drawing ----

    // Drawn after translucent blocks, which then show through it (see CityShieldRenderer)
    @Override
    public boolean shouldRenderInPass(int pass) {
        return pass == 1;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return bounds();
    }

    @SideOnly(Side.CLIENT)
    @Override
    public double getMaxRenderDistanceSquared() {
        double r = Math.max(width, Math.max(above, below)) + 256;
        return r * r;
    }
}
