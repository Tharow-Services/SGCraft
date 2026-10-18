package gcewing.sg.features.rings;

import gcewing.sg.BaseTileEntity;
import gcewing.sg.SGCraft;
import gcewing.sg.features.obelisk.AsgardObeliskTE;
import gcewing.sg.features.obelisk.AsgardObelisks;
import gcewing.sg.features.obelisk.ConfigurableTransport;
import gcewing.sg.features.obelisk.ObeliskAccess;
import gcewing.sg.features.obelisk.ObeliskConfig;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// A ring platform: the block in the floor (or ceiling) at the middle of its rings. It holds its link, its energy (Forge
// Energy, from cables or machines beside it, and naquadah in its fuel slots, burnt as needed), and while it runs, when
// its rings set off (see RingTransports and RingPlatformRenderer, which draws them from that). With MalisisCore its
// fuel is a MalisisCore inventory, for its MalisisCore screen (RingPlatformMalisisTE); without, a plain one.
public class RingPlatformTE extends BaseTileEntity implements ITickable, ISidedInventory, ConfigurableTransport {

    public static final int numFuelSlots = 4;
    private final NonNullList<ItemStack> fuel = NonNullList.withSize(numFuelSlots, ItemStack.EMPTY);

    // Its fuel slots, read and written: RingPlatformMalisisTE keeps them in its MalisisCore inventory instead
    protected ItemStack getFuel(int slot) {
        return fuel.get(slot);
    }

    protected void setFuel(int slot, ItemStack stack) {
        fuel.set(slot, stack);
    }

    public boolean linked;
    public BlockPos linkPos = BlockPos.ORIGIN;
    public int energy;
    // When its rings set off (world time), or -1; whether it started this run (and so swaps the two); and the floor the
    // rings stack on (for a ceiling platform, found below it when they set off)
    public long activeStart = -1;
    boolean initiator;
    public double floorY;

    private final IEnergyStorage forgeEnergy = new ForgeEnergy();

    // In a ceiling: a ceiling platform, or any with air below it (its rings then hang below it and drop to the floor)
    public boolean isCeiling() {
        if (world == null) {
            return false;
        }
        Block block = getBlockType();
        return block instanceof RingPlatformBlock && ((RingPlatformBlock) block).ceiling || world.isAirBlock(pos.down());
    }

    // ---- Running ----

    public boolean isActive() {
        return activeStart >= 0 && world.getTotalWorldTime() - activeStart < RingTransports.doneTicks;
    }

    void start(long now, boolean initiator) {
        this.activeStart = now;
        this.initiator = initiator;
        this.floorY = findFloor();
        Vec3d c = centre();
        world.playSound(null, c.x, c.y, c.z, RingTransports.transportSound, SoundCategory.BLOCKS, 1.5F, 1F);
        markChanged();
    }

    // The energy last sent to clients, which show it on its screen
    private int energySent = -1;

    @Override
    public void update() {
        if (world.isRemote) {
            return;
        }
        if (energy != energySent && world.getTotalWorldTime() % 20 == 0) {
            energySent = energy;
            markChanged();
        }
        if (activeStart < 0) {
            return;
        }
        long t = world.getTotalWorldTime() - activeStart;
        if (initiator && linked && (t == RingTransports.transportTick - RingTransports.whiteOutTicks || t == RingTransports.transportTick)) {
            RingPlatformTE partner = RingTransports.platformAt(world, linkPos);
            if (partner != null) {
                if (t == RingTransports.transportTick) {
                    RingTransports.swap(this, partner);
                } else {
                    // Those inside either set of rings see the world go white, as the obelisks' beam does
                    RingTransports.whiteOut(this);
                    RingTransports.whiteOut(partner);
                }
            }
        }
        if (t >= RingTransports.doneTicks) {
            activeStart = -1;
            markChanged();
        }
    }

    // The floor its rings stack on: its own top for a floor platform; for a ceiling one, the first thing below to stand
    // on, within reach
    double findFloor() {
        if (!isCeiling()) {
            return pos.getY() + 1;
        }
        for (int dy = 1; dy <= RingTransports.maxDrop; dy++) {
            BlockPos p = pos.down(dy);
            IBlockState s = world.getBlockState(p);
            if (s.isSideSolid(world, p, EnumFacing.UP)) {
                return p.getY() + 1;
            }
        }
        return pos.getY() - RingTransports.maxDrop;
    }

    // The floor, as it is now (while running, where the rings stack; otherwise, where they would)
    public double floor() {
        return activeStart >= 0 ? floorY : findFloor();
    }

    public AxisAlignedBB volume() {
        return RingTransports.volume(pos, floor());
    }

    public Vec3d floorCentre() {
        return new Vec3d(pos.getX() + 0.5, floor(), pos.getZ() + 0.5);
    }

    public Vec3d centre() {
        return floorCentre().add(0, 1.2, 0);
    }

    // ---- Fuel: naquadah in its slots, burnt for energy when a transport needs more than it holds ----

    public static boolean isFuel(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == SGCraft.naquadah;
    }

    public int naquadahCount() {
        int count = 0;
        for (int i = 0; i < numFuelSlots; i++) {
            ItemStack stack = getFuel(i);
            if (isFuel(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    // All it can give: what it holds, and what its naquadah would give
    public long energyAvailable() {
        return energy + (long) naquadahCount() * RingTransports.energyPerNaquadah;
    }

    // Burns naquadah, a piece at a time, until it holds at least needed (or runs out)
    void fuelUp(int needed) {
        for (int i = 0; i < numFuelSlots && energy < needed; i++) {
            while (energy < needed && isFuel(getFuel(i))) {
                decrStackSize(i, 1);
                energy = (int) Math.min(Integer.MAX_VALUE, (long) energy + RingTransports.energyPerNaquadah);
            }
        }
        markChanged();
    }

    // ---- Its fuel slots as an inventory: for its screens, and for hoppers and pipes ----

    @Override
    public int getSizeInventory() {
        return numFuelSlots;
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < numFuelSlots; i++) {
            if (!getFuel(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return slot >= 0 && slot < numFuelSlots ? getFuel(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack decrStackSize(int slot, int count) {
        ItemStack stack = getStackInSlot(slot);
        if (stack.isEmpty() || count <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = stack.splitStack(count);
        setFuel(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
        markDirty();
        return taken;
    }

    @Override
    public ItemStack removeStackFromSlot(int slot) {
        ItemStack stack = getStackInSlot(slot);
        setFuel(slot, ItemStack.EMPTY);
        markDirty();
        return stack;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        if (slot >= 0 && slot < numFuelSlots) {
            setFuel(slot, stack);
            markDirty();
        }
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public boolean isUsableByPlayer(EntityPlayer player) {
        return world.getTileEntity(pos) == this && player.getDistanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }

    @Override
    public void openInventory(EntityPlayer player) {
    }

    @Override
    public void closeInventory(EntityPlayer player) {
    }

    @Override
    public int getField(int id) {
        return 0;
    }

    @Override
    public void setField(int id, int value) {
    }

    @Override
    public int getFieldCount() {
        return 0;
    }

    @Override
    public void clear() {
        for (int i = 0; i < numFuelSlots; i++) {
            setFuel(i, ItemStack.EMPTY);
        }
    }

    @Override
    public String getName() {
        return "tile.sgcraft:ring_platform.name";
    }

    @Override
    public boolean hasCustomName() {
        return false;
    }

    @Override
    public ITextComponent getDisplayName() {
        return new TextComponentTranslation(getName());
    }

    private static final int[] allSlots = {0, 1, 2, 3};

    @Override
    public int[] getSlotsForFace(EnumFacing side) {
        return allSlots;
    }

    // Only naquadah goes in (hoppers and pipes too), and nothing comes back out by them
    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return isFuel(stack);
    }

    @Override
    public boolean canInsertItem(int slot, ItemStack stack, EnumFacing side) {
        return isFuel(stack);
    }

    @Override
    public boolean canExtractItem(int slot, ItemStack stack, EnumFacing side) {
        return false;
    }

    // ---- Who may use it, as for Asgard obelisks: its owner (who placed it), everyone by default or only those allowed ----

    public UUID owner;
    public String ownerName = "";
    public boolean allowEveryone = true;
    public final List<ObeliskAccess> access = new ArrayList<>();

    private ObeliskAccess accessFor(EntityPlayer player) {
        for (ObeliskAccess entry : access) {
            if (entry.uuid.equals(player.getUniqueID())) {
                return entry;
            }
        }
        return null;
    }

    private boolean isOwner(EntityPlayer player) {
        return owner != null && owner.equals(player.getUniqueID());
    }

    public boolean mayUse(EntityPlayer player) {
        if (isOwner(player) || AsgardObeliskTE.isServerAdmin(player)) {
            return true;
        }
        ObeliskAccess entry = accessFor(player);
        return allowEveryone ? entry == null || entry.use : entry != null && entry.use;
    }

    @Override
    public boolean mayConfigure(EntityPlayer player) {
        if (owner == null || isOwner(player) || AsgardObeliskTE.isServerAdmin(player)) {
            return true;
        }
        ObeliskAccess entry = accessFor(player);
        return entry != null && entry.admin;
    }

    // ---- Its configurator screen, the obelisks' (see ConfigurableTransport), with its power ----

    @Override
    public ObeliskConfig transportConfig() {
        ObeliskConfig config = new ObeliskConfig();
        config.pos = pos;
        config.titleKey = "sgcraft.gui.rings.label.title";
        config.linked = linked;
        if (linked) {
            config.linkWorld = AsgardObelisks.worldName(world.provider.getDimension());
            config.linkPos = linkPos;
        }
        config.ownerName = ownerName == null ? "" : ownerName;
        config.allowEveryone = allowEveryone;
        config.access.addAll(access);
        config.hasPower = true;
        config.energy = energy;
        config.capacity = RingTransports.energyCapacity;
        config.naquadah = naquadahCount();
        config.naquadahEnergy = (long) naquadahCount() * RingTransports.energyPerNaquadah;
        config.uses = energyAvailable() / Math.max(1, RingTransports.energyPerUse);
        return config;
    }

    @Override
    public List<ObeliskAccess> transportAccess() {
        return access;
    }

    @Override
    public void setTransportAccess(boolean allowEveryone, List<ObeliskAccess> access) {
        this.allowEveryone = allowEveryone;
        this.access.clear();
        this.access.addAll(access);
        markDirty();
    }

    @Override
    public void unlinkTransport() {
        RingTransports.unlink(this);
    }

    // Clients aren't sent who may use it
    @Override
    protected NBTTagCompound writeToClientNBT(NBTTagCompound nbt) {
        super.writeToClientNBT(nbt);
        nbt.removeTag("access");
        nbt.removeTag("ownerMost");
        nbt.removeTag("ownerLeast");
        return nbt;
    }

    // ---- Link ----

    void linkTo(BlockPos other) {
        linked = true;
        linkPos = other.toImmutable();
        markChanged();
    }

    void unlink() {
        linked = false;
        markChanged();
    }

    // ---- Energy: taken in as Forge Energy from any side ----

    private class ForgeEnergy implements IEnergyStorage {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int taken = Math.max(0, Math.min(maxReceive, RingTransports.energyCapacity - energy));
            if (!simulate && taken > 0) {
                energy += taken;
                markDirty();
            }
            return taken;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return energy;
        }

        @Override
        public int getMaxEnergyStored() {
            return RingTransports.energyCapacity;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        return capability == CapabilityEnergy.ENERGY || super.hasCapability(capability, facing);
    }

    @Override
    @Nullable
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityEnergy.ENERGY) {
            return CapabilityEnergy.ENERGY.cast(forgeEnergy);
        }
        return super.getCapability(capability, facing);
    }

    // ---- Saving and syncing ----

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        // On a client, what the server sends is all its fuel: a slot it leaves out is empty now
        if (world != null && world.isRemote) {
            clear();
        }
        super.readFromNBT(nbt);
        NonNullList<ItemStack> saved = NonNullList.withSize(numFuelSlots, ItemStack.EMPTY);
        ItemStackHelper.loadAllItems(nbt, saved);
        for (int i = 0; i < numFuelSlots; i++) {
            setFuel(i, saved.get(i));
        }
        linked = nbt.getBoolean("linked");
        linkPos = BlockPos.fromLong(nbt.getLong("linkPos"));
        energy = nbt.getInteger("energy");
        activeStart = nbt.hasKey("activeStart") ? nbt.getLong("activeStart") : -1;
        initiator = nbt.getBoolean("initiator");
        floorY = nbt.getDouble("floorY");
        owner = nbt.hasUniqueId("owner") ? nbt.getUniqueId("owner") : null;
        ownerName = nbt.getString("ownerName");
        allowEveryone = !nbt.hasKey("allowEveryone") || nbt.getBoolean("allowEveryone");
        access.clear();
        NBTTagList list = nbt.getTagList("access", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            access.add(ObeliskAccess.fromNBT(list.getCompoundTagAt(i)));
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        NonNullList<ItemStack> saved = NonNullList.withSize(numFuelSlots, ItemStack.EMPTY);
        for (int i = 0; i < numFuelSlots; i++) {
            saved.set(i, getFuel(i));
        }
        ItemStackHelper.saveAllItems(nbt, saved);
        nbt.setBoolean("linked", linked);
        nbt.setLong("linkPos", linkPos.toLong());
        nbt.setInteger("energy", energy);
        nbt.setLong("activeStart", activeStart);
        nbt.setBoolean("initiator", initiator);
        nbt.setDouble("floorY", floorY);
        if (owner != null) {
            nbt.setUniqueId("owner", owner);
        }
        nbt.setString("ownerName", ownerName == null ? "" : ownerName);
        nbt.setBoolean("allowEveryone", allowEveryone);
        NBTTagList list = new NBTTagList();
        for (ObeliskAccess entry : access) {
            list.appendTag(entry.toNBT());
        }
        nbt.setTag("access", list);
        return nbt;
    }

    // ---- Drawing: the rings reach well beyond the block ----

    @SideOnly(Side.CLIENT)
    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        int down = isCeiling() ? RingTransports.maxDrop + 1 : 0;
        int up = isCeiling() ? 1 : 4;
        return new AxisAlignedBB(pos.getX() - 3, pos.getY() - down, pos.getZ() - 3, pos.getX() + 4, pos.getY() + up, pos.getZ() + 4);
    }
}
