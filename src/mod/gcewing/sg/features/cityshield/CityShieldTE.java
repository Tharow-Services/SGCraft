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
import net.minecraft.entity.Entity;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager.Ticket;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// The City Shield Generator: raises an energy shield in the shape of an ellipsoid round itself, as over Atlantis.
// Nothing gets in while it's up (things already inside can leave), and it runs on ZPMs, taking more the bigger it is.
public class CityShieldTE extends BaseTileEntity implements ITickable, IDirectInventoryProvider, ISidedInventory {

    // ---- Settings (sgcraft.cfg, "city-shield") ----

    // ZPM energy (in SG energy units) each million blocks of the shield's volume take each tick. At 0.333, three full
    // ZPMs run a shield 128 blocks round, 128 above and 8 below (about 4.67 million blocks) for about 30 days.
    public static double energyPerMillionBlocksPerTick = 0.333;
    // The same, for each block
    public static double energyPerBlockPerTick = energyPerMillionBlocksPerTick / 1e6;
    // The largest and smallest the shield can reach from the generator in each direction
    public static int maxRadius = 128;
    public static final int MIN_RADIUS = 3;
    // Whether a raised shield keeps all the chunks it covers loaded, and loads them again with the world
    public static boolean keepChunksLoaded = true;

    // Its own sounds as it rises and falls
    public static SoundEvent raiseSound, lowerSound;

    public static void registerSounds(SGCraft mod) {
        raiseSound = mod.newSound("city_shield_on");
        lowerSound = mod.newSound("city_shield_off");
    }

    public static void configure(BaseConfiguration cfg) {
        energyPerMillionBlocksPerTick = cfg.getDouble("city-shield", "energyPerMillionBlocksPerTick", energyPerMillionBlocksPerTick);
        energyPerBlockPerTick = energyPerMillionBlocksPerTick / 1e6;
        maxRadius = Math.max(MIN_RADIUS, cfg.getInteger("city-shield", "maxRadius", maxRadius));
        keepChunksLoaded = cfg.getBoolean("city-shield", "keepChunksLoaded", keepChunksLoaded);
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
        updateChunkLoading();
        if (!active) {
            lastSeen.clear(); // whoever is inside when it rises may stay there
            return;
        }
        if (!useEnergy(energyPerTick())) {
            setActive(false); // the ZPMs ran dry
            return;
        }
        repelEntities();
    }

    // ---- Keeping its chunks loaded (see CityShieldChunks) ----

    private List<Ticket> chunkTickets;
    // The width the tickets cover, to force the chunks again when it changes
    private int ticketsWidth = -1;
    // Set as the generator's chunk unloads (with the world, say), when its tickets must be kept for Forge to save
    private boolean unloading = false;

    private void updateChunkLoading() {
        if (chunkTickets == null) {
            chunkTickets = CityShieldChunks.adopt(world, pos); // handed back as the world loaded
            if (chunkTickets != null) {
                ticketsWidth = width;
            }
        }
        boolean wanted = active && keepChunksLoaded;
        if (chunkTickets != null && (!wanted || ticketsWidth != width)) {
            CityShieldChunks.release(chunkTickets);
            chunkTickets = null;
        }
        if (wanted && chunkTickets == null) {
            chunkTickets = CityShieldChunks.force(world, pos, width);
            ticketsWidth = width;
        }
    }

    @Override
    public void onChunkUnload() {
        unloading = true;
        super.onChunkUnload();
    }

    // Broken: its chunks needn't stay loaded
    @Override
    public void invalidate() {
        if (!unloading && world != null && !world.isRemote) {
            CityShieldChunks.release(chunkTickets);
            chunkTickets = null;
        }
        super.invalidate();
    }

    // Where each entity in and around the shield was at its last tick (its feet), by entity id: where one crossing into it
    // came from. An entity's own last-tick position is no use for that: a player's moves, and those of whatever they're
    // steering, arrive before the tick, and the world sets their last-tick position to where they then are before the
    // shield gets to look, so players walked (or rode) straight through.
    private final Map<Integer, Vec3d> lastSeen = new HashMap<>();

    // Anything that crossed into the shield since last tick is sent back where it came from; projectiles are destroyed
    private void repelEntities() {
        Map<Integer, Vec3d> seen = new HashMap<>();
        for (Entity entity : world.getEntitiesWithinAABB(Entity.class, bounds().grow(4))) {
            if (entity.isRiding() || entity.isDead) {
                continue; // a rider goes with its mount
            }
            double half = entity.height / 2;
            Vec3d feet = new Vec3d(entity.posX, entity.posY, entity.posZ);
            // One not seen last tick (as the shield rose, or come from further off) by its own last-tick position
            Vec3d from = lastSeen.getOrDefault(entity.getEntityId(), new Vec3d(entity.lastTickPosX, entity.lastTickPosY, entity.lastTickPosZ));
            Vec3d now = feet.add(0, half, 0);
            Vec3d before = from.add(0, half, 0);
            seen.put(entity.getEntityId(), feet);
            if (!contains(now) || contains(before) || now.squareDistanceTo(before) > maxStepSq) {
                continue;
            }
            if (entity instanceof IProjectile || entity instanceof EntityFireball) {
                entity.setDead();
                continue;
            }
            if (entity instanceof EntityPlayerMP) {
                ((EntityPlayerMP) entity).connection.setPlayerLocation(from.x, from.y, from.z, entity.rotationYaw, entity.rotationPitch);
            } else {
                entity.setPositionAndUpdate(from.x, from.y, from.z);
            }
            bounce(entity);
            seen.put(entity.getEntityId(), from);
        }
        lastSeen.clear();
        lastSeen.putAll(seen);
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

    // Hoppers may put ZPMs in, but not take them out: the way out is its screen, for those allowed to work the shield. As
    // a plain inventory, a hopper under it could empty it.
    private static final int[] zpmSlots = {0, 1, 2};

    @Override
    public int[] getSlotsForFace(EnumFacing side) {
        return zpmSlots;
    }

    @Override
    public boolean canInsertItem(int index, ItemStack stack, EnumFacing direction) {
        return isItemValidForSlot(index, stack);
    }

    @Override
    public boolean canExtractItem(int index, ItemStack stack, EnumFacing direction) {
        return false;
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
            SoundEvent sound = on ? raiseSound : lowerSound;
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
